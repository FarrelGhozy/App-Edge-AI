# FaceGateApp — Agent Guide

## Project Overview
Face recognition gate system for **pondok pesantren / asrama kampus**. Students live in campus dorms. System tracks who leaves and returns (not attendance/alpha).

**Key concept**: Scan toggle — first scan = "keluar", next scan = "kembali", then toggle again. No morning attendance check.

## Quick Commands
- `npm run dev` — Start backend (Bun + Elysia) on port 8150 (run from `backend/`)
- `docker compose up -d` — Start PostgreSQL + pgvector
- `cd android && ./gradlew :kiosk-scanner:assembleDebug` — Build kiosk APK
- `cd android && ./gradlew :admin-app:assembleDebug` — Build admin APK

## Project Structure
```
FaceGateApp/
├── android/                    # Android apps (Gradle) — `cd android` to build
│   ├── core/                   # :core — shared library (TFLite, Room, Retrofit)
│   ├── kiosk-scanner/          # :kiosk-scanner — gate scanner app
│   ├── admin-app/              # :admin-app — admin management app
│   ├── gradle/libs.versions.toml  # Version catalog
│   ├── build.gradle.kts        # Project-level build file
│   └── settings.gradle.kts     # Module registry
├── backend/                    # Bun + Elysia backend (port 8150)
│   ├── prisma/schema.prisma    # Database schema (16 models)
│   ├── src/routes/             # API route handlers (auth, students, attendance, sync, rules, devices, permits, violations, notifications, settings, dashboard, holidays, schedules, audit, reports)
│   └── src/services/           # Business logic
├── docker-compose.yml          # PostgreSQL + pgvector + backend
└── docs/planning.md            # Full planning document
```

## Architecture Rules
1. **Android modules** depend on `:core` — never the reverse
2. **Offline-first** — kiosk works 100% offline, sync only when admin requests or DB changes
3. **Sync**: midnight 00:00 WIB auto + manual trigger via admin button + auto-trigger saat DB berubah
4. **No attendance/alpha** — students live in campus, scan is only for gate movement

## Database (PostgreSQL + pgvector)
Key models (see `prisma/schema.prisma`):
- `Student` — master data with `nim`, `studyProgram`, `academicYear`
- `FaceVector` — `vector(192)` via pgvector extension (MobileFaceNet 192-d embedding)
- `AttendanceLog` — scan logs with `action: "keluar" | "kembali"`
- `Permit` — `type: "izin_harian" | "pengajuan_izin"` (harian auto-approved)
- `CampusRule` — restricted hours configuration
- `CourseSchedule` — per-student course schedules with `studentId` relation
- `Violation` — violation records
- `SyncRequest` — manual/auto sync trigger flag
- `Device` — kiosk device management
- `AuditLog` — all admin activity logging
- `Notification` — in-app notifications (with `adminId` for targeting)
- `ImportBatch` — CSV import tracking
- `GlobalSetting` — key-value settings
- `Admin` — admin users with roles
- `PermitQuota` — monthly permit usage tracking
- `SyncLog` — sync audit trail
- `Holiday` — national holidays (rules skipped on holidays)

## Face Recognition Pipeline
```
CameraX → MediaPipe FaceDetection → Face Landmarks (468pts)
  → EAR Liveness (blink detection, 3s window)
  → MobileFaceNet TFLite (192-d embedding) 
  → Brute-force cosine similarity match (10k faces in RAM, ~3ms)
  → Threshold: 0.6 (configurable)
```
Total pipeline: ~25ms per face. All models cached locally in RAM.

**IMPORTANT**: FaceVector uses `vector(128)` in PostgreSQL. MobileFaceNet produces exactly 128-dimensional embeddings. If upload fails with dimension mismatch, verify the TFLite model outputs 128 floats.

## Realtime Data Architecture

### Admin App (SSE — Server-Sent Events)
Admin App receives realtime push from server via SSE:
- Endpoint: `GET /api/events/stream`
- Events: `dashboard_update`, `violation_new`, `scan_realtime`, `outside_update`, `sync_status`
- No polling needed — dashboard auto-updates

### Kiosk Auto-Fetch (DB Change Trigger)
When database changes (student CRUD, face upload, permit approved, rules changed), server automatically sets `syncRequested=true` for all active devices:
- Kiosk polls `GET /api/sync/requested` every **10 seconds** (was 10 minutes)
- Polling is lightweight — only returns boolean + timestamp
- When DB changes → kiosk fetches within 10 seconds

### Sync Mechanism
- **Midnight auto**: WorkManager periodic at 00:00 WIB — upload logs, download faces+rules
- **Manual trigger**: Admin clicks "Sync" button → `POST /api/sync/request/:deviceId` → kiosk polls → sync
- **Auto trigger**: Backend sets `syncRequested=true` for all active devices on any DB mutation
- **Poll interval**: 10 seconds (configurable via GlobalSetting `sync_poll_interval_seconds`)
- **Offline**: Logs queue locally (`isSynced=false`). When internet returns, WorkManager auto-runs.

## Permit System
Two types:
| Type | Approval | Duration | Quota |
|---|---|---|---|
| `izin_harian` | Auto-approved | Max 1 day, with time range | 10x/month (configurable) |
| `pengajuan_izin` | Needs admin approve | Multi-day, with attachment | No limit |

## API Base
- Backend: `localhost:8150` (via Docker)
- Production: `https://facegate.utc.web.id` — home server di-tunnel dengan Cloudflare Tunnel, port dibelokkan via web panel
- Android app sudah menggunakan `https://facegate.utc.web.id/` sebagai base URL
- Auth: JWT (bcrypt)
- Framework: Elysia + Prisma + Zod validation

## Android Min SDK
**Android 12 (API 31)**

## Tech Stack
- **Android**: Kotlin, Compose + Material 3, CameraX, Room, Hilt, WorkManager, Retrofit, TFLite, MediaPipe
- **Backend**: Bun, Elysia, Prisma, PostgreSQL + pgvector, Zod
- **Infra**: Docker, home server + Cloudflare Tunnel (facegate.utc.web.id)

## Key Files
- `docs/planning.md` — Full architecture, data model, API endpoints, milestone breakdown
- `android/gradle/libs.versions.toml` — Version catalog with all dependencies
- `backend/prisma/schema.prisma` — Complete database schema (16 models)
- `backend/src/index.ts` — Main server entry point
- `docker-compose.yml` — Infrastructure setup

## Coding Conventions
- Kotlin: Use Kotlin Coroutines + Flow, Hilt DI, sealed classes for states
- TypeScript: Elysia routes grouped by resource, Zod schemas in service files
- All UI in Jetpack Compose (no XML)
- Face vector stored as Blob in Room (FloatArray → ByteArray via TypeConverter)
- In-memory FaceIndex: `Map<String, FloatArray>` (studentId → 192-d vector)

## Common Issues & Fixes

### Error :500 saat upload face
1. Pastikan ekstensi `pgvector` sudah aktif: `CREATE EXTENSION IF NOT EXISTS vector;`
2. Model TFLite menghasilkan **192-dimensi** — schema harus `vector(192)`, cek `FaceEmbedder.kt: embeddingDim = 192`
3. Jalankan `npx prisma db push` setelah mengubah schema
4. Cek error detail di log backend — sekarang uploadFace memberikan pesan error spesifik

### Error :500 lainnya
- Pastikan semua relasi model di schema.prisma lengkap (Student ↔ Violation, CourseSchedule, PermitQuota)
- Pastikan tidak ada route duplikat (cek `GET /api/settings`, `GET /api/sync/logs`)
- `CourseSchedule` wajib memiliki `studentId`

### Database schema changes
Setelah mengubah `schema.prisma`, jalankan:
```bash
cd backend
npx prisma db push          # Update database tanpa migration
npx prisma generate         # Regenerate Prisma client
```
