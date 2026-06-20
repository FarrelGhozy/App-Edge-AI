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

# Build Android
cd android && ./gradlew :kiosk-scanner:assembleDebug
cd android && ./gradlew :admin-app:assembleDebug
```

Default admin login: `admin` / `admin123`

## API

Backend runs on `localhost:8150` with Swagger docs at `/docs`.

## License

MIT — do whatever.
