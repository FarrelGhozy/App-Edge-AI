# Konfigurasi Server API (App-Edge-AI)

Dokumen ini adalah **single source of truth** untuk konfigurasi endpoint server API
yang dipakai kedua aplikasi Android (kiosk-scanner & admin-app).

## Lokasi Konfigurasi

Base URL di-set via **BuildConfig** di masing-masing module:

| App | File | Field |
|-----|------|-------|
| Kiosk Scanner | `android/kiosk-scanner/build.gradle.kts` | `API_BASE_URL` |
| Admin App | `android/admin-app/build.gradle.kts` | `API_BASE_URL` |

> ⚠️ JANGAN hardcode URL di kode Kotlin (`ApiClient.kt`, `CoreModule.kt`, dsb.)
> — semuanya harus lewat `@ApiBaseUrl` / BuildConfig agar ganti environment cukup
> di satu file.

## Mode Deployment

### 1. Production (`https://facegate.utc.web.id`)

Server publik (deploy di VPS). Dipakai untuk release build.

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://facegate.utc.web.id\"")
```

### 2. Local Development (emulator → WSL host) — MODE DEFAULT SAAT INI

Backend dijalankan di WSL (`bun run start`), emulator Android mengakses host
WSL lewat alamat ajaib **`10.0.2.2`** (loopback host dari emulator).

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8150\"")
```

> ⚠️ **Cleartext HTTP**: build debug sudah mengizinkan traffic http (network
> security config / `usesCleartextTraffic` debug). Release build TIDAK boleh
> pakai http tanpa HTTPS.

### 3. Local Development (device fisik / HP asli)

Device fisik TIDAK bisa pakai `10.0.2.2`. Gunakan IP LAN host WSL:

```bash
ip addr show eth0 | grep inet   # cari IP WSL, contoh 172.24.x.x
# pastikan device & host di WiFi/network yang sama
```

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://172.24.x.x:8150\"")
```

## Backend Lokal (WSL)

### Prasyarat
- PostgreSQL 18 + pgvector (`sudo apt-get install -y postgresql postgresql-18-pgvector`)
- Bun (`bun`)

### Setup sekali jalan

```bash
cd backend
# 1. Buat DB & user lokal
sudo -u postgres psql -c "CREATE USER facegate WITH PASSWORD 'facegate-local-2024';"
sudo -u postgres psql -c "CREATE DATABASE facegate OWNER facegate;"
sudo -u postgres psql -d facegate -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 2. Buat .env (sudah ada di repo? cek dulu; jangan commit .env)
# DATABASE_URL="postgresql://facegate:facegate-local-2024@localhost:5432/facegate?schema=public"
# JWT_SECRET="local-dev-secret-change-me"
# PORT=8150

# 3. Push schema + seed
bunx prisma db push --accept-data-loss
bun run db:seed
```

### Menjalankan backend

```bash
cd backend
bun run start          # production-ish
# atau
bun run dev            # watch mode
```

Verifikasi: `curl http://localhost:8150/api/health` → `{"status":"ok",...}`

### Kredensial seed lokal

| Akun | Username | Password | Role |
|------|----------|----------|------|
| Admin | `admin` | `admin123` | superadmin |
| Device Kiosk | `kiosk-gate1` | `facegate-kiosk-2024` | device |

> Kredensial device harus SAMA dengan `DEVICE_USERNAME`/`DEVICE_PASSWORD` di
> `android/kiosk-scanner/build.gradle.kts`.

## Checklist Ganti Mode

1. Edit `API_BASE_URL` di **kedua** `build.gradle.kts` (kiosk-scanner & admin-app)
2. Backend lokal: pastikan `bun run start` jalan + health OK
3. Rebuild: `cd android && ./gradlew :kiosk-scanner:assembleDebug`
4. Install & tes login: logcat `SyncWorker` / `DevicePingWorker` sukses
5. Jangan commit perubahan ke production URL tanpa kebutuhan nyata
