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
│   ├── core/                   # :core — shared library (InsightFace ONNX, Room, Retrofit)
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
- `FaceVector` — `vector(512)` via pgvector extension (InsightFace MBF@WebFace600K 512-d embedding)
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
CameraX → RetinaFace-500MF ONNX (detection, det_500m.onnx)
  → EAR Liveness (blink detection, 3.5s window)
  → MBF@WebFace600K ONNX (embedding, w600k_mbf.onnx 512-d)
  → Video multi-frame collection + Quality-Weighted Fusion
  → Brute-force cosine similarity match (10k faces in RAM)
  → Adaptive threshold (AMBIGUITY_RATIO gap analysis)
```

**Stack saat ini (branch `insightface`)**: ONNX Runtime Mobile — InsightFace buffalo_sc.
- Detection: `det_500m.onnx` — RetinaFace-500MF (input `[1,3,H,W]`, output boxes+scores+landmarks)
- Embedding: `w600k_mbf.onnx` — MobileFaceNet @ WebFace600K (**output `[1,512]`**, L2-normalized, 112×112, preprocessing `(pix - 127.5) / 128` = range ~[-1,1))
- Fallback (legacy, tidak aktif): ML Kit detection + MobileFaceNet TFLite

> ✅ **AKTUAL (terverifikasi)**: `OnnxFaceEmbedder.kt: embeddingDim = 512` dan schema DB `vector(512)` sudah sinkron. Tidak ada mismatch dimensi. (Klaim lama "masih 192/vector(192)" di bawah sudah usang.)

## Model History (Jul 2026)
- **Old**: `arcface_512.tflite` — FP16, **broken architecture** (conversion script was a skeleton, not real Inception-ResNet v1). Produced non-discriminative embeddings → false positive 100%.
- **Then**: `mobilefacenet.tflite` — proven 192-d model from GitHub release, 5 MB, LFW 99.4% (digunakan di pipeline TFLite lama).
- **Now**: InsightFace `w600k_mbf.onnx` — 512-d, LFW 99.70%, CFP-FP 98.00% (migrasi aktif di branch `insightface`).

**Preprocessing fix**: Changed from `pixel / 127.5 - 1.0` (range [-1, 1]) to `(pix - 127.5) / 128` (range ~[-1,1)) — InsightFace `buffalo_sc` convention, konsisten antara embedder & detector.

**Specifications (AKTUAL, branch `insightface`)**:
- Input: 112×112 RGB, normalized `(pix - 127.5) / 128` (range ~[-1,1))
- Output: **512-d** L2-normalized float vector (ONNX w600k_mbf, dimension = 512)
- Database: `vector(512)` (sudah disinkronkan — tidak ada mismatch)

**IMPORTANT**: Embedding saat ini **512-d** (ONNX w600k_mbf). `OnnxFaceEmbedder.EMBEDDING_DIM = 512` dan `schema.prisma vector(512)` sudah sinkron — jangan ubah ke 192. Jangan ubah preprocessing ke pixel/255; model InsightFace memakai `(pix - 127.5) / 128`.

## Realtime Data Architecture

### Admin App (SSE — Server-Sent Events)
Admin App receives realtime push from server via SSE:
- Endpoint: `GET /api/events/stream`
- Events: `dashboard_update`, `violation_new`, `scan_realtime`, `outside_update`, `sync_status`
- No polling needed — dashboard auto-updates

### Kiosk Auto-Fetch (Efisien — cek flag dulu, baru download)
Setiap 10 detik kiosk cek `GET /api/sync/requested` (ringan: boolean saja, ~100 bytes):
- **Tidak ada perubahan** → tidak download apa pun (hemat bandwidth)
- **Ada perubahan** (`requested=true`) → download faces + students + rules, lalu rebuild face index
- Server auto-set `syncRequested=true` untuk semua device aktif saat ada DB mutation

### Sync Mechanism
- **Polling ringan**: Setiap 10 detik — cuma cek boolean `GET /api/sync/requested`
- **Download berat**: HANYA saat `requested=true` atau midnight — `GET /api/sync/faces` + `GET /api/sync/rules`
- **Midnight auto**: 00:00 WIB — full sync (selalu download faces+rules, tidak cek flag)
- **Manual trigger**: Admin klik "Sync" → `POST /api/sync/request/:deviceId` → flag jadi true → kiosk download
- **Auto trigger**: Backend service layer auto-set flag setiap create/update/delete student, upload face, approve permit, dll.
- **Offline**: Logs queue locally (`isSynced=false`). Saat internet kembali, WorkManager auto-upload.

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
- **Android**: Kotlin, Compose + Material 3, CameraX, Room, Hilt, WorkManager, Retrofit, ONNX Runtime (InsightFace)
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
- In-memory FaceIndex: `Map<String, FloatArray>` (studentId → 512-d vector)

## Common Issues & Fixes

### Error :500 saat upload face
1. Pastikan ekstensi `pgvector` sudah aktif: `CREATE EXTENSION IF NOT EXISTS vector;`
2. Model ONNX (w600k_mbf) menghasilkan **512-dimensi** — schema sudah `vector(512)`, `OnnxFaceEmbedder.kt: EMBEDDING_DIM = 512` (jangan pakai klaim lama 192-d — model InsightFace asli output 512)
3. Jalankan `npx prisma db push` setelah mengubah schema
4. Cek error detail di log backend — sekarang uploadFace memberikan pesan error spesifik

### Masalah akurasi face recognition
1. **Preprocessing salah** — Pastikan FaceEmbedder pakai `(pix - 127.5) / 128` (range ~[-1,1), konvensi InsightFace buffalo_sc), bukan `pixel/255.0` ([0,1])
2. **Model rusak** — Jangan gunakan `arcface_512.tflite` (dari conversion script skeleton). Pakai InsightFace ONNX `w600k_mbf.onnx` (512-d)
3. **Enrollment tidak crop** — Pastikan admin app crop wajah sebelum embed (fix ada di FaceRegisterViewModel.kt)
4. **SyncWorker missing pose** — Semua 5 pose harus tersimpan dengan label CENTER/LEFT/RIGHT/UP/DOWN

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

## Fix History (Jul 2026)
| # | File | Perubahan |
|---|---|---|
| 1 | `FaceEmbedder.kt` | Model default: `arcface_512.tflite` (broken) → `mobilefacenet.tflite` (proven) → **InsightFace ONNX `w600k_mbf.onnx` (512-d, branch `insightface`)** |
| 2 | `FaceEmbedder.kt` | Preprocessing: `pixel/127.5-1` ([-1,1]) → `(pix - 127.5) / 128` (~[-1,1), konvensi InsightFace buffalo_sc) |
| 3 | `FaceEmbedder.kt` | Dequantization: output quantized sekarang di-dequantize pakai scale + zeroPoint |
| 4 | `SyncWorker.kt` | FaceVectorEntity dibuat dengan `pose` field (sebelumnya empty → PK conflict → 1 vector per student) |
| 5 | `SyncWorker.kt` | Ganti insert loop dengan `deleteAll()` + `insertAll()` batch |
| 6 | `FaceRegisterViewModel.kt` | Crop wajah sebelum embed (konsisten dengan kiosk) |
| 7 | `student.ts` | Backend validation: menerima 192-d (sebelumnya cuma 512) → **harus update ke 512-d lagi (mengikuti model InsightFace)** |
| 8 | `schema.prisma` | `vector(512)` → `vector(192)` (era MobileFaceNet TFLite) → **kembali `vector(512)` (era InsightFace ONNX)** |
| 9 | Database | pgvector extension + prisma db push |
