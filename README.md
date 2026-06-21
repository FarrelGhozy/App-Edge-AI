# FaceGate

**Face recognition gate system** for pesantren / asrama kampus — tracking who leaves and returns through the gate. No attendance, no alpha — just *"keluar"* dan *"kembali"*.

> Projek iseng-iseng aja sih, tapi siapa tau bermanfaat buat yang butuh sistem scan gate pake face recognition.

## Stack

- **Android** — Kotlin, Jetpack Compose, Hilt, Room, CameraX, TFLite, MediaPipe
- **Backend** — Bun, Elysia, Prisma, PostgreSQL + pgvector
- **Infra** — Docker, Cloudflare Tunnel

## Modules

| Module | Deskripsi |
|--------|-----------|
| `:core` | Shared library: Room DB, TFLite pipeline, Retrofit, Session |
| `:kiosk-scanner` | Aplikasi scanner di gate (fullscreen camera) |
| `:admin-app` | Admin panel: manage students, permits, rules, reports |

## Quick Start

```bash
# Start database
docker compose up -d postgres

# Start backend
cd backend && bun install && bunx prisma db push && bun run src/seed.ts && bun run dev

# Build Android (debug — emulator)
cd android && ./gradlew :kiosk-scanner:assembleDebug && ./gradlew :admin-app:assembleDebug

# Install
adb install -r android/kiosk-scanner/build/outputs/apk/debug/*.apk
adb install -r android/admin-app/build/outputs/apk/debug/*.apk
```

Default admin login: `admin` / `admin123`

## Build Variants & API URL

Setiap module punya dua build type, masing-masing dengan `API_BASE_URL` berbeda:

| Build Type | API URL | Penggunaan |
|------------|---------|------------|
| `debug` | `http://10.0.2.2:8150` | Emulator (localhost host) |
| `release` | `https://facegate.utc.web.id` | Production (Cloudflare Tunnel) |

Untuk install di HP real (bukan emulator), ada dua opsi:

1. **Via WiFi lokal** — ubah `API_BASE_URL` di `build.gradle.kts` ke IP server di jaringan yang sama (misal `http://192.168.1.42:8150`), lalu `assembleDebug` + install
2. **Via Cloudflare Tunnel** — pastikan tunnel jalan, lalu build release:
   ```bash
   cd android && ./gradlew :kiosk-scanner:assembleRelease :admin-app:assembleRelease
   ```

## API

Backend runs on `localhost:8150` with Swagger docs at `/docs`.

## License

MIT — do whatever.
