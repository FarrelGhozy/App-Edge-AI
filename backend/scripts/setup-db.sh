#!/usr/bin/env bash
# ============================================================
#  FaceGate — Database Setup (satu perintah, hasil konsisten)
#
#  Menangani semua masalah setup yang dulu harus manual:
#   1. .env dibuat otomatis dari .env.example (kalau belum ada)
#   2. prisma generate + db push (bukan `migrate dev` — shadow DB
#      butuh superuser + extension vector, sering gagal di CI/fresh)
#   3. extension `vector` dipastikan ada (superuser bila perlu)
#   4. partial unique index sync_requests (#131) — dedup request
#      pending per device — yang Prisma tidak bisa express
#   5. seed data awal (admin, kiosk, campus rules, settings)
#
#  Cara pakai:  cd backend && bash scripts/setup-db.sh
#  Idempotent — aman dijalankan ulang kapan saja.
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

BOLD='\033[1m'; GREEN='\033[32m'; YELLOW='\033[33m'; RED='\033[31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}  ✓ $1${NC}"; }
warn() { echo -e "${YELLOW}  ⚠ $1${NC}"; }
step() { echo -e "${BOLD}▶ $1${NC}"; }

echo -e "${BOLD}═══════════════════════════════════════════${NC}"
echo -e "${BOLD}  FaceGate — Database Setup${NC}"
echo -e "${BOLD}═══════════════════════════════════════════${NC}"

# ── 1. .env ────────────────────────────────────────────────
step "[1/5] Pastikan .env ada"
if [[ ! -f .env ]]; then
  cp .env.example .env
  warn ".env dibuat dari .env.example — GANTI JWT_SECRET & password bila perlu!"
else
  ok ".env sudah ada"
fi

# ── 2. Dependensi ───────────────────────────────────────────
step "[2/5] Pastikan dependensi ter-install"
if [[ ! -d node_modules ]]; then
  bun install
  ok "bun install selesai"
else
  ok "node_modules sudah ada"
fi

# ── 3. Prisma client + db push ──────────────────────────────
step "[3/5] Prisma generate + db push (schema → database)"
npx prisma generate >/dev/null
# `db push` dipakai, BUKAN `migrate dev`: migrate dev butuh shadow DB
# dengan hak superuser + extension vector, yang tidak selalu tersedia.
npx prisma db push --accept-data-loss
ok "Schema tersinkron (16+ model)"

# ── 4. Extension vector + partial unique index ──────────────
step "[4/5] Extension vector & partial unique index (#131)"

# Parse DATABASE_URL dari .env (tanpa menampilkan password)
DBURL=$(grep -oE '^DATABASE_URL="?[^"]*"?$' .env | head -1 | sed -E 's/^DATABASE_URL="?//; s/"?$//')
DB_HOST=$(echo "$DBURL" | sed -E 's|.*@([^:/]+).*|\1|')
DB_PORT=$(echo "$DBURL" | sed -E 's|.*:([0-9]+)/.*|\1|')
DB_NAME=$(echo "$DBURL" | sed -E 's|.*/([^?]+).*|\1|')
DB_USER=$(echo "$DBURL" | sed -E 's|.*://([^:]+):.*|\1|')
DB_PASS=$(echo "$DBURL" | sed -E 's|.*://[^:]+:([^@]+)@.*|\1|')

psql_cmd() { PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q "$@"; }

# extension vector: user biasa → superuser (WSL: sudo -u postgres peer auth)
if psql_cmd -c "CREATE EXTENSION IF NOT EXISTS vector;" >/dev/null 2>&1; then
  ok "Extension vector tersedia"
elif sudo -n -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS vector;" >/dev/null 2>&1; then
  ok "Extension vector dibuat via superuser postgres"
else
  warn "Extension vector belum ada & tidak bisa dibuat otomatis."
  echo -e "     Jalankan manual sebagai superuser:"
  echo -e "       docker exec -it facegate-db psql -U postgres -c 'CREATE EXTENSION IF NOT EXISTS vector;'"
  echo -e "       # atau WSL: sudo -u postgres psql -c 'CREATE EXTENSION IF NOT EXISTS vector;'"
  exit 1
fi

# partial unique index (#131): 1 request pending per device.
# Prisma tidak mendukung partial index → dibuat manual, idempotent.
# Drop nama lama (duplikat fungsional, kalau ada) supaya idempotent
# & tidak menumpuk index ganda di database yang pernah di-setup manual.
psql_cmd -c "DROP INDEX IF EXISTS sync_requests_pending_device;"
psql_cmd -c "CREATE UNIQUE INDEX IF NOT EXISTS sync_requests_pending_per_device ON sync_requests (device_id) WHERE is_processed = false;"
ok "Partial unique index sync_requests (dedup pending per device)"

# ── 5. Seed ─────────────────────────────────────────────────
step "[5/5] Seed data awal (idempotent)"
bun src/seed.ts

echo -e "${BOLD}═══════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ Setup selesai!${NC}"
echo -e "   Jalankan backend:  ${BOLD}bun run start${NC}  (port 8150)"
echo -e "   Login admin:       ${BOLD}admin / admin123${NC}"
echo -e "   Kiosk gate:        ${BOLD}kiosk-gate1 / facegate-kiosk-2024${NC}"
echo -e "${BOLD}═══════════════════════════════════════════${NC}"
