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
│   ├── src/routes/             # API route handlers (auth, students, attendance, sync, rules, devices, permits, violations, notifications, settings)
│   └── src/services/           # Business logic
├── cloudflared/                # Cloudflare Tunnel config
├── docker-compose.yml          # PostgreSQL + pgvector + backend
└── docs/planning.md            # Full planning document
```

## Architecture Rules
1. **Android modules** depend on `:core` — never the reverse
2. **Offline-first** — kiosk works 100% offline, sync only when admin requests
3. **Sync**: midnight 00:00 WIB auto + manual trigger via admin button (kiosk polls every 10min)
4. **No attendance/alpha** — students live in campus, scan is only for gate movement

## Database (PostgreSQL + pgvector)
Key models (see `prisma/schema.prisma`):
- `Student` — master data with `nim`, `studyProgram`, `academicYear`
- `FaceVector` — `vector(128)` via pgvector extension
- `AttendanceLog` — scan logs with `action: "keluar" | "kembali"`
- `Permit` — `type: "izin_harian" | "pengajuan_izin"` (harian auto-approved)
- `CampusRule` — restricted hours configuration
- `Violation` — violation records
- `SyncRequest` — manual sync trigger flag
- `Device` — kiosk device management
- `AuditLog` — all admin activity logging
- `Notification` — in-app notifications
- `ImportBatch` — CSV import tracking
- `CourseSchedule` — course schedule data
- `GlobalSetting` — key-value settings
- `Admin` — admin users with roles
- `PermitQuota` — monthly permit usage tracking
- `SyncLog` — sync audit trail

## Face Recognition Pipeline
```
CameraX → MediaPipe FaceDetection → Face Landmarks (468pts)
  → EAR Liveness (blink detection, 3s window)
  → MobileFaceNet TFLite (128-d embedding) 
  → Brute-force cosine similarity match (10k faces in RAM, ~3ms)
  → Threshold: 0.6 (configurable)
```
Total pipeline: ~25ms per face. All models cached locally in RAM.

## Sync Mechanism
- **Midnight auto**: WorkManager periodic at 00:00 WIB — upload logs, download faces+rules
- **Manual trigger**: Admin clicks "Sync" button → `POST /api/sync/request/:deviceId` → kiosk polls `GET /api/sync/requested` every 10min → runs sync if flag is true
- **Offline**: Logs queue locally (`isSynced=false`). When internet returns, WorkManager auto-runs and checks pending sync flag.

## Permit System
Two types:
| Type | Approval | Duration | Quota |
|---|---|---|---|
| `izin_harian` | Auto-approved | Max 1 day, with time range | 10x/month (configurable) |
| `pengajuan_izin` | Needs admin approve | Multi-day, with attachment | No limit |

## API Base
- Backend: `localhost:8150` (via Docker)
- Public: `https://facegate.utc.web.id` (via Cloudflare Tunnel)
- Auth: JWT (bcrypt)
- Framework: Elysia + Prisma + Zod validation

## Android Min SDK
**Android 12 (API 31)**

## Tech Stack
- **Android**: Kotlin, Compose + Material 3, CameraX, Room, Hilt, WorkManager, Retrofit, TFLite, MediaPipe
- **Backend**: Bun, Elysia, Prisma, PostgreSQL + pgvector, Zod
- **Infra**: Docker, Cloudflare Tunnel (cloudflared)

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
- In-memory FaceIndex: `Map<String, FloatArray>` (studentId → 128-d vector)
