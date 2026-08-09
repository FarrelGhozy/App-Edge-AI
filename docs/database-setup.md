# FaceGate — Database Setup

Setup database dibuat **satu perintah** agar hasilnya konsisten di lingkungan
mana pun (local WSL, Docker, server dosen, CI) dan menangani semua masalah
yang dulu harus dikerjakan manual.

## Prasyarat

- **Bun** (untuk backend & seed)
- **PostgreSQL + pgvector** — cara termudah: `docker compose up -d postgres`
  (pakai image `pgvector/pgvector:pg16`, DB `facegate` user `facegate`)
- File `.env` — otomatis dibuat dari `.env.example` oleh script

## Jalankan setup (sekali jalan, idempotent)

```bash
cd backend
bun run db:setup
# atau langsung: bash scripts/setup-db.sh
```

Script melakukan 5 langkah:

| # | Langkah | Mengatasi |
|---|---------|-----------|
| 1 | Buat `.env` dari `.env.example` (jika belum ada) | env hilang di mesin baru |
| 2 | `bun install` (jika `node_modules` belum ada) | dependensi |
| 3 | `prisma generate` + `prisma db push --accept-data-loss` | `prisma migrate dev` gagal di env non-superuser karena shadow DB butuh extension `vector` |
| 4 | Pastikan extension `vector` (bila perlu via superuser) + buat **partial unique index** `sync_requests_pending_per_device` | index partial `sync_requests` (#131) yang **tidak bisa** di-express Prisma |
| 5 | Seed idempotent: admin, kiosk, 7 campus rules, 5 global settings | data awal |

Idempotent = aman dijalankan ulang kapan saja (tidak membuat duplikat).

## Setelah setup

```bash
bun run start        # backend di port 8150
```

Akun default (di-seed):

| Akun | Username | Password | Role |
|------|----------|----------|------|
| Admin | `admin` | `admin123` | superadmin |
| Kiosk Gate 1 | `kiosk-gate1` | `facegate-kiosk-2024` | device |

> ⚠️ **Produksi**: ganti `JWT_SECRET` dan password di `.env` sebelum deploy.

## Reset total (jika mau mulai dari nol)

```bash
docker compose down -v      # hapus volume postgres_data
docker compose up -d postgres
cd backend && bun run db:setup
```

## Kenapa bukan `prisma migrate dev`?

`prisma migrate dev` membuat **shadow database** untuk deteksi drift, dan
shadow DB itu butuh superuser + extension `vector` yang di-create di awal
migrasi. Di mesin WSL / user non-superuser / Docker default, ini sering gagal
dengan error `permission denied to create extension "vector"`.

Solusi yang dipakai: **`prisma db push --accept-data-loss`** — langsung
menyinkronkan schema ke database tanpa shadow DB. Migrasi SQL tetap ada di
`prisma/migrations/` sebagai jejak audit; `db push` dipakai untuk eksekusi.

## Partial unique index (#131)

Prisma tidak mendukung **partial unique index** (index unik hanya untuk baris
tertentu). Requirement: maksimal **1 sync request pending per device**
(`is_processed = false`), tapi boleh banyak baris yang sudah diproses.

Index dibuat manual di langkah 4:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS sync_requests_pending_per_device
  ON sync_requests (device_id) WHERE is_processed = false;
```

Ditambah logika **find-then-write + ignore P2002** di kode
(`services/events.ts` & `routes/devices.ts`) untuk menangani race condition.
