# GitHub Issues — FaceGateApp Development

> Copy-paste issues ini ke GitHub. Setiap issue sudah termasuk title, body, labels, dan milestone.
> Urutan issue mengikuti Phase 1-5 dari planning.md.

---

## 🏗️ MILESTONE: Phase 1 — Foundation + Core Pipeline (Week 1-4)

---

### Issue #1: Setup Monorepo + Gradle + Version Catalog

**Title:** `[Phase1][Setup] Setup monorepo Android + Gradle version catalog`

**Body:**
```
## Deskripsi
Setup struktur monorepo Android dengan Gradle version catalog (`libs.versions.toml`) sebagai single source of truth untuk semua dependencies.

## Checklist
- [ ] Buat `android/settings.gradle.kts` — register modules: `:core`, `:kiosk-scanner`, `:admin-app`
- [ ] Buat `android/build.gradle.kts` — project-level build file
- [ ] Buat `android/gradle/libs.versions.toml` — versi semua libraries:
  - Kotlin 2.0.x, Compose BOM, CameraX 1.4.x, Room 2.6.x, Hilt 2.50+, WorkManager 2.9.x, Retrofit, OkHttp, TFLite
- [ ] Buat module structure:
  - `:core` — shared library (face, network, database, sync, model)
  - `:kiosk-scanner` — gate scanner app
  - `:admin-app` — admin management app
- [ ] Pastikan build berhasil: `./gradlew assembleDebug`

## Referensi
- `docs/planning.md` → Section 2 (Struktur Monorepo), Section 3.1 (Tech Stack Android)
- `android/gradle/libs.versions.toml`
```

**Labels:** `phase-1`, `setup`, `android`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #2: Setup Backend (Bun + Elysia + Prisma + PostgreSQL + pgvector)

**Title:** `[Phase1][Backend] Setup backend infrastructure — Bun + Elysia + Prisma + PostgreSQL + pgvector`

**Body:**
```
## Deskripsi
Setup backend server dengan stack: Bun runtime, Elysia framework, Prisma ORM, PostgreSQL + pgvector extension.

## Checklist
- [ ] Buat `backend/` directory structure:
  ```
  backend/
  ├── src/
  │   ├── index.ts          # Main server entry
  │   ├── routes/           # API route handlers
  │   ├── services/         # Business logic
  │   └── seed.ts           # Database seed
  ├── prisma/
  │   └── schema.prisma     # Database schema
  ├── Dockerfile
  ├── package.json
  ├── tsconfig.json
  └── .env.example
  ```
- [ ] Setup `docker-compose.yml` — PostgreSQL + pgvector + backend (port 8150)
- [ ] Install dependencies: `bun install` (elysia, prisma, @prisma/client, zod, papaparse, pdfkit, bcrypt, jsonwebtoken)
- [ ] Setup Prisma + PostgreSQL connection
- [ ] Enable pgvector extension: `CREATE EXTENSION IF NOT EXISTS vector;`
- [ ] Jalankan `npx prisma db push` — pastikan schema sync
- [ ] Jalankan `bun run dev` — pastikan server start di port 8150
- [ ] Test basic endpoint: `GET /api/health`

## Referensi
- `docs/planning.md` → Section 3.2 (Backend Tech Stack)
- `backend/`
- `docker-compose.yml`
```

**Labels:** `phase-1`, `setup`, `backend`, `infrastructure`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #3: Database Schema — Student, FaceVector, Admin, AttendanceLog

**Title:** `[Phase1][Database] Schema v1 — Student, FaceRegistration, Admin, AttendanceLog`

**Body:**
```
## Deskripsi
Buat database schema untuk model-model inti: Student, StudentFaceRegistration (centroid + allEmbeddings + quality metrics), Admin, AttendanceLog.

## Model yang harus dibuat

### Student
- id, nim (unique), name, studyProgram, academicYear, phone, email, isActive, photoUrl
- Relations: faceRegistration (1:1), attendanceLogs, permits, violations, courseSchedules, permitQuotas

### StudentFaceRegistration (pengganti FaceVector lama)
- id, studentId (unique), centroidEmbedding (vector(192)), allEmbeddings (JSON, debugging only)
- sampleCount, consistency, minConsistency
- registeredAt, updatedAt, modelVersion
- status (active/flagged_retrain/archived), retryCount, notes
- lastSuccessfulScanAt, lastFailedScanAt

### Admin
- id, username (unique), passwordHash, displayName, role (admin/superadmin)
- Relations: auditLogs, syncRequests, permits

### AttendanceLog
- id, studentId, studentName, action ("keluar"/"kembali"), timestamp
- confidenceScore, isViolation, violationType, deviceId, photoCapture, isSynced

## Checklist
- [ ] Update `prisma/schema.prisma` dengan semua model di atas
- [ ] Jalankan `npx prisma db push`
- [ ] Jalankan `npx prisma generate`
- [ ] Verifikasi: `npx prisma studio` — cek semua model bisa diakses

## Referensi
- `docs/planning.md` → Section 5.1 (PostgreSQL via Prisma)
- `backend/prisma/schema.prisma`
```

**Labels:** `phase-1`, `database`, `backend`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #4: Database Schema — ScanMetric, DailyMetrics, Permit, CampusRule, dll

**Title:** `[Phase1][Database] Schema v2 — ScanMetric, DailyMetrics, Permit, CampusRule, CourseSchedule, Holiday, Violation`

**Body:**
```
## Deskripsi
Lengkapi database schema untuk model-model pendukung: ScanMetric (analytics), DailyMetrics (aggregation), Permit, CampusRule, CourseSchedule, Holiday, Violation, Device, SyncRequest, SyncLog, AuditLog, Notification, ImportBatch, GlobalSetting, PermitQuota.

## Model Prioritas Tinggi

### ScanMetric
- id, timestamp, predictedStudentId?, actualStudentId? (nullable — diisi saat review)
- topSimilarity, gap, confidence, decision (MATCH_CONFIDENT/MEDIUM/WEAK/NO_MATCH)
- detectionConfidence, livenessScore
- scannedAt, deviceId?, isCorrect? (nullable), verifiedAt?

### DailyMetrics
- id, date (unique), totalScans, successfulMatches, failedMatches, rejectedByQA
- matchConfidentCount, matchMediumCount, matchWeakCount, noMatchCount
- falsePositiveRate, falseNegativeRate, accuracy

### CampusRule
- id, dayOfWeek, startTime, endTime, isRestricted, appliesToAll, studyProgram?, academicYear?, priority

### CourseSchedule
- id, studentId, courseName, dayOfWeek, startTime, endTime, room?, lecturer?, isActive

### Holiday
- id, date (unique), name, isActive

### Permit
- id, studentId, type ("izin_harian"/"pengajuan_izin"), startDate, endDate, startTime?, endTime?
- status ("approved"/"pending"/"rejected"), reason, attachmentUrl?, approvedById?, approvedAt?

### Violation
- id, studentId, type, description?, action?, timestamp, relatedRuleId?, relatedPermitId?
- isResolved, resolvedAt?, resolvedNote?

### Lainnya
- Device, SyncRequest, SyncLog, AuditLog, Notification, ImportBatch, GlobalSetting, PermitQuota

## Checklist
- [ ] Update `prisma/schema.prisma` dengan semua model
- [ ] Jalankan `npx prisma db push` + `npx prisma generate`
- [ ] Verifikasi semua relasi berfungsi

## Referensi
- `docs/planning.md` → Section 5.1 (PostgreSQL via Prisma)
```

**Labels:** `phase-1`, `database`, `backend`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #5: Backend API — Auth (Login + JWT)

**Title:** `[Phase1][Backend][API] Auth endpoints — login + JWT + bcrypt`

**Body:**
```
## Deskripsi
Implementasi autentikasi admin dengan JWT + bcrypt.

## Endpoints
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/auth/login` | Login admin, return JWT token |
| POST | `/api/auth/refresh` | Refresh JWT token |

## Checklist
- [ ] Buat `backend/src/services/auth.ts` — login logic + JWT sign/verify
- [ ] Buat `backend/src/routes/auth.ts` — route handlers
- [ ] Validasi input dengan Zod: `{ username: string, password: string }`
- [ ] Response: `{ token, admin: { id, username, displayName, role } }`
- [ ] Seed 1 admin default: username `admin`, password `admin123`
- [ ] Test: login → dapat token → refresh token

## Referensi
- `docs/planning.md` → Section 6.1 (Auth endpoints)
```

**Labels:** `phase-1`, `backend`, `api`, `auth`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #6: Backend API — Student CRUD + Import

**Title:** `[Phase1][Backend][API] Student endpoints — CRUD + import + face upload`

**Body:**
```
## Deskripsi
API untuk mengelola data mahasiswa: CRUD, import CSV, upload face embedding.

## Endpoints
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/students` | List semua mahasiswa (filter: prodi, angkatan, search) |
| GET | `/api/students/:id` | Detail mahasiswa |
| POST | `/api/students` | Tambah mahasiswa |
| PUT | `/api/students/:id` | Edit mahasiswa |
| DELETE | `/api/students/:id` | Hapus mahasiswa |
| POST | `/api/students/:id/face` | Upload face embedding (centroid 192-d) |
| POST | `/api/students/import` | Import CSV/Excel |
| GET | `/api/students/:id/schedules` | Jadwal kuliah mahasiswa |
| GET | `/api/students/:id/permits` | Izin mahasiswa |
| GET | `/api/students/:id/violations` | Pelanggaran mahasiswa |
| GET | `/api/students/:id/status` | Status terkini (di kampus/luar) |

## Checklist
- [ ] Buat `backend/src/services/student.ts` — business logic
- [ ] Buat `backend/src/routes/student.ts` — route handlers
- [ ] Validasi input dengan Zod untuk semua endpoint
- [ ] Face upload: simpan ke `StudentFaceRegistration.centroidEmbedding` (vector(192))
- [ ] Import: parse CSV dengan PapaParse, bulk insert
- [ ] Trigger `syncRequested = true` saat create/update/delete student

## Referensi
- `docs/planning.md` → Section 6.2 (Student endpoints)
```

**Labels:** `phase-1`, `backend`, `api`, `student`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #7: Backend API — Attendance (Scan Toggle)

**Title:** `[Phase1][Backend][API] Attendance endpoints — scan toggle + history + statistics`

**Body:**
```
## Deskripsi
API untuk scan toggle (keluar/kembali) dan riwayat attendance.

## Endpoints
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/attendance/scan` | Catat scan toggle (keluar/kembali) |
| GET | `/api/attendance` | Riwayat scan (filter: tgl, prodi, student) |
| GET | `/api/attendance/today` | Ringkasan hari ini |
| GET | `/api/attendance/status/:studentId` | Status toggle mahasiswa |
| GET | `/api/attendance/outside-now` | Semua mahasiswa yg sedang di luar |
| GET | `/api/attendance/statistics` | Statistik per prodi |

## Logic
- POST `/api/attendance/scan`: terima `{ studentId, confidenceScore, deviceId }`
- Tentukan action: cek log terakhir hari ini → toggle (keluar/kembali)
- Cek izin + rules + jadwal kuliah → tentukan apakah violation
- Simpan `AttendanceLog` + `Violation` (jika applicable)
- Return: `{ action, isViolation, violationType?, studentName }`

## Checklist
- [ ] Buat `backend/src/services/attendance.ts`
- [ ] Buat `backend/src/routes/attendance.ts`
- [ ] Implement toggle logic (lihat Section 7.2 planning.md)
- [ ] Implement rule checking (lihat Section 8.3 planning.md)
- [ ] Auto-resolve violations saat scan "kembali"
- [ ] SSE broadcast `scan_realtime` event ke admin

## Referensi
- `docs/planning.md` → Section 6.3 (Attendance), Section 7 (Alur Data), Section 8 (Rule Engine)
```

**Labels:** `phase-1`, `backend`, `api`, `attendance`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #8: Backend API — Campus Rules + Global Settings

**Title:** `[Phase1][Backend][API] Campus Rules + Global Settings endpoints`

**Body:**
```
## Deskripsi
API untuk mengelola aturan kampus (restricted hours) dan pengaturan global.

## Endpoints
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/rules` | List semua aturan |
| POST | `/api/rules` | Tambah aturan |
| PUT | `/api/rules/:id` | Edit aturan |
| DELETE | `/api/rules/:id` | Hapus aturan |
| GET | `/api/rules/effective?time=&day=` | Aturan aktif untuk waktu tertentu |
| GET | `/api/settings` | List global settings |
| PUT | `/api/settings/:key` | Update setting |

## Global Settings Default
- `operational_start`: "06:00"
- `operational_end`: "21:00"
- `max_permit_hours_per_day`: "8"
- `max_daily_permit_per_month`: "10"
- `violation_threshold`: "3"
- `sync_poll_interval_seconds`: "10"

## Checklist
- [ ] Buat `backend/src/services/rule.ts`
- [ ] Buat `backend/src/routes/rule.ts`
- [ ] Seed default global settings
- [ ] Trigger `syncRequested = true` saat CRUD rules

## Referensi
- `docs/planning.md` → Section 6.5 (Campus Rules), Section 8.2 (Global Settings)
```

**Labels:** `phase-1`, `backend`, `api`, `rules`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #9: Backend API — Sync (Kiosk + Admin)

**Title:** `[Phase1][Backend][API] Sync endpoints — kiosk sync + admin trigger`

**Body:**
```
## Deskripsi
API untuk sinkronisasi data antara backend ↔ kiosk scanner.

## Endpoints (Kiosk)
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/sync/requested` | Cek apakah admin minta sync (polling ringan) |
| GET | `/api/sync/faces` | Download face vectors (centroid) |
| GET | `/api/sync/rules` | Download aturan aktif |
| GET | `/api/sync/settings` | Download global settings |
| POST | `/api/sync/attendance` | Upload batch log scan |
| POST | `/api/sync/complete` | Konfirmasi sync selesai |

## Endpoints (Admin)
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/sync/request/:deviceId` | Admin minta sync ke kiosk |
| GET | `/api/sync/status/:deviceId` | Status sync terakhir |

## Logic
- GET `/api/sync/requested`: return `{ requested: boolean, timestamp }` — sangat ringan (~100 bytes)
- GET `/api/sync/faces`: return semua `StudentFaceRegistration` (centroidEmbedding + student info)
- POST `/api/sync/attendance`: terima batch AttendanceLog, bulk insert, set `isSynced = true`

## Checklist
- [ ] Buat `backend/src/services/sync.ts`
- [ ] Buat `backend/src/routes/sync.ts`
- [ ] Implement DB Change Hook: setiap create/update/delete student/face/permit/rule → set `syncRequested = true` untuk semua device aktif
- [ ] Seed 1 device default: deviceId `kiosk_001`

## Referensi
- `docs/planning.md` → Section 6.9 (Sync Kiosk), Section 6.10 (Sync Admin), Section 4.4 (Kiosk Auto-Fetch)
```

**Labels:** `phase-1`, `backend`, `api`, `sync`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #10: Backend API — Scan Metrics + Monitoring

**Title:** `[Phase1][Backend][API] Scan Metrics endpoints — upload + review + daily metrics`

**Body:**
```
## Deskripsi
API untuk metrics scan: upload dari kiosk, review manual, dan daily aggregation.

## Endpoints
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/sync/scan-metrics` | Upload batch ScanMetric dari kiosk |
| GET | `/api/scan-metrics?decision=MATCH_WEAK` | List scan yang perlu review manual |
| PATCH | `/api/scan-metrics/:id/review` | Admin correct actualStudentId + isCorrect |
| GET | `/api/metrics/daily?from=&to=` | DailyMetrics untuk dashboard FPR/FNR |
| GET | `/api/metrics/today` | Real-time counter |

## Logic
- PATCH `/api/scan-metrics/:id/review`: terima `{ actualStudentId, isCorrect }` + set `verifiedAt = now()`
- GET `/api/metrics/daily`: aggregate data dari ScanMetric → hitung FPR/FNR/accuracy per hari
- GET `/api/metrics/today`: return `{ totalScans, confidentCount, mediumCount, weakCount, noMatchCount }`

## Checklist
- [ ] Buat `backend/src/services/scanMetric.ts`
- [ ] Buat `backend/src/routes/scanMetric.ts`
- [ ] Implement DailyMetrics aggregation logic (bisa cron job atau on-demand)
- [ ] Validasi: pastikan `actualStudentId` nullable di schema

## Referensi
- `docs/planning.md` → Section 6.14 (Scan Metrics), Section 3.6 (Metrics Collection)
```

**Labels:** `phase-1`, `backend`, `api`, `metrics`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #11: `:core` — Room Database + TypeConverters

**Title:** `[Phase1][Core] Room database + TypeConverters (FloatArray ↔ ByteArray)`

**Body:**
```
## Deskripsi
Setup Room database di module `:core` sebagai local cache untuk kiosk scanner.

## Entities
- `StudentEntity` — cache data mahasiswa
- `FaceVectorEntity` — centroid vektor wajah (FloatArray → ByteArray via TypeConverter)
- `AttendanceLogEntity` — log scan lokal (isSynced = false = belum dikirim)
- `PermitEntity` — cache izin
- `CampusRuleEntity` — aturan untuk verifikasi offline
- `CourseScheduleEntity` — jadwal kuliah per mahasiswa
- `HolidayEntity` — tanggal libur
- `GlobalSettingEntity` — pengaturan global
- `ScanMetricEntity` — scan metrics queue (untuk sync ke backend)
- `SyncMetadata` — tracking sync terakhir

## Checklist
- [ ] Buat `core/src/main/kotlin/.../core/database/AppDatabase.kt`
- [ ] Buat semua Entity classes
- [ ] Buat semua DAO interfaces
- [ ] Buat TypeConverter: `FloatArray ↔ ByteArray` (untuk vektor)
- [ ] Buat TypeConverter: `List<String> ↔ String` (JSON arrays)
- [ ] Database version = 1, exportSchema = true
- [ ] Test: insert + query setiap entity

## Referensi
- `docs/planning.md` → Section 5.2 (Room Database), Section 13.1 (`:core` module)
```

**Labels:** `phase-1`, `android`, `core`, `database`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #12: `:core` — TFLite MobileFaceNet Loader + Face Embedder

**Title:** `[Phase1][Core] TFLite MobileFaceNet loader + 192-d face embedding extraction`

**Body:**
```
## Deskripsi
Implementasi face embedding extraction menggunakan MobileFaceNet TFLite model (192-d vector).

## Spesifikasi
- Model: `mobilefacenet.tflite` (~5 MB)
- Input: 112×112 RGB, normalized [0,1] (pixel / 255.0)
- Output: 192-d L2-normalized float vector
- Preprocessing: `pixel / 255.0` (bukan `pixel / 127.5 - 1`)

## Checklist
- [ ] Bundle model `mobilefacenet.tflite` di `assets/`
- [ ] Buat `core/src/main/kotlin/.../core/face/FaceEmbedder.kt`
  - Load TFLite model
  - Preprocess: resize 112×112 + normalize [0,1]
  - Run inference → dapat 192-d float array
  - L2 normalize output
  - Quality check: norm validation (0.9-1.1)
- [ ] Test: embed 10 gambar wajah berbeda → pastikan output 192-d + norm ~1.0
- [ ] Benchmark: measure latency per embedding (target < 15ms)

## Referensi
- `docs/planning.md` → Section 3.3.3 (Face Embedding Extraction)
- `AGENTS.md` → Face Recognition Pipeline
```

**Labels:** `phase-1`, `android`, `core`, `face`, `tflite`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #13: `:core` — YOLOv8 Face Detector + MediaPipe Fallback

**Title:** `[Phase1][Core] Face detection — YOLOv8 (primary) + MediaPipe (fallback)`

**Body:**
```
## Deskripsi
Implementasi face detection dengan YOLOv8 Face sebagai primary detector dan MediaPipe sebagai fallback.

## Spesifikasi
- YOLOv8 Face: akurasi 98-99%, bounding box presisi, ~6-8 MB
- MediaPipe FaceDetector: akurasi 95-97%, ~350 KB (fallback)
- Minimum detection confidence: 0.85

## Checklist
- [ ] Bundle YOLOv8 Face TFLite model di `assets/`
- [ ] Buat `core/src/main/kotlin/.../core/face/FaceDetector.kt`
  - Interface: `detect(frame: Bitmap): List<FaceDetection>`
  - Data class: `FaceDetection(bbox: RectF, confidence: Float, landmarks: List<PointF>)`
  - Quality check: confidence >= 0.85
  - Blur detection: Laplacian variance > 100
- [ ] Implement YOLOv8 detector (primary)
- [ ] Implement MediaPipe detector (fallback)
- [ ] Auto-switch: if YOLOv8 fails → try MediaPipe
- [ ] Test: detect wajah di 20 gambar → pastikan bounding box benar

## Referensi
- `docs/planning.md` → Section 3.3.1 (Face Detection & ROI Extraction)
```

**Labels:** `phase-1`, `android`, `core`, `face`, `detection`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #14: `:core` — Liveness Detection (EAR-based)

**Title:** `[Phase1][Core] Liveness detection — EAR eye blink + anti-spoofing`

**Body:**
```
## Deskripsi
Implementasi liveness detection menggunakan Eye Aspect Ratio (EAR) untuk mendeteksi kedipan alami.

## Logic
1. Track EAR dari 468 MediaPipe landmarks (mata kiri + kanan)
2. Require 2+ blinks dalam 3 detik window
3. Anti-spoofing score: combine EAR + face symmetry check
4. Jika liveness_score < 0.70 → REJECT
5. Jika user take > 5 detik → TIMEOUT

## Checklist
- [ ] Buat `core/src/main/kotlin/.../core/face/LivenessDetector.kt`
  - Hitung EAR dari MediaPipe face landmarks
  - Track EAR trend (bukan hanya threshold jump)
  - Count blinks dalam 3 detik window
  - Return: `{ passed: Boolean, score: Float, blinkCount: Int }`
- [ ] EAR threshold: 0.15 default → 0.10 untuk accessibility
- [ ] Test: simulate blink + no-blink scenarios

## Referensi
- `docs/planning.md` → Section 3.3.2 (Liveness Detection)
```

**Labels:** `phase-1`, `android`, `core`, `face`, `liveness`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #15: `:core` — Adaptive Face Matcher (Top-K + Gap Analysis)

**Title:** `[Phase1][Core] Adaptive face matcher — Top-K ranking + gap analysis`

**Body:**
```
## Deskripsi
Implementasi face matching dengan adaptive threshold menggunakan Top-K ranking dan gap analysis.

## Algorithm
1. Brute-force cosine similarity: scan_embedding ke SEMUA centroid di database
2. Top-3 ranking
3. Gap = top_score - runner_up
4. Adaptive decision:
   - score >= 0.90 AND gap >= 0.08 → MATCH_CONFIDENT
   - score >= 0.85 AND gap >= 0.05 → MATCH_MEDIUM
   - score >= 0.80 AND gap >= 0.03 → MATCH_WEAK
   - else → NO_MATCH

## Checklist
- [ ] Buat `core/src/main/kotlin/.../core/face/FaceMatcher.kt`
  - Input: scan_embedding (192-d FloatArray), centroids (Map<String, FloatArray>)
  - Output: `MatchResult(matched, studentId, topScore, gap, confidence, decision)`
  - Cosine similarity function
  - Top-K ranking
  - Configurable thresholds
- [ ] Buat `core/src/main/kotlin/.../core/face/FaceIndex.kt`
  - In-memory cache: `Map<String, FloatArray>` (studentId → centroid)
  - Load from Room database
  - Rebuild when sync complete
- [ ] Test: 100 synthetic vectors → pastikan matching benar
- [ ] Benchmark: 10k vectors matching < 30ms

## Referensi
- `docs/planning.md` → Section 3.3.4 (Matching Strategy — Adaptive Threshold)
```

**Labels:** `phase-1`, `android`, `core`, `face`, `matching`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #16: `:core` — Network Layer (Retrofit)

**Title:** `[Phase1][Core] Retrofit network layer — API client + all endpoints`

**Body:**
```
## Deskripsi
Setup networking layer menggunakan Retrofit + OkHttp + Kotlinx Serialization.

## Checklist
- [ ] Buat `core/src/main/kotlin/.../core/network/ApiService.kt`
  - Define semua API endpoints (Student, Attendance, Permit, Rules, Sync, Metrics)
  - Menggunakan suspend functions
- [ ] Buat `core/src/main/kotlin/.../core/network/ApiClient.kt`
  - Base URL configurable (local: http://10.0.2.2:8150, production: https://facegate.utc.web.id)
  - JWT interceptor (auto-attach token)
  - Error handling interceptor
- [ ] Buat DTO classes untuk semua request/response
- [ ] Buat `core/src/main/kotlin/.../core/di/NetworkModule.kt` (Hilt module)
- [ ] Test: login → dapat token → call authenticated endpoint

## Referensi
- `docs/planning.md` → Section 6 (API Endpoints), Section 13.1 (`:core` network/)
```

**Labels:** `phase-1`, `android`, `core`, `network`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #17: `:kiosk-scanner` — CameraX + Frame Analyzer

**Title:** `[Phase1][Kiosk] CameraX integration + frame analyzer pipeline`

**Body:**
```
## Deskripsi
Setup CameraX di kiosk-scanner dengan frame analyzer untuk real-time face detection.

## Checklist
- [ ] Buat `kiosk-scanner/src/main/kotlin/.../scanner/camera/CameraManager.kt`
  - Setup CameraX Preview + ImageAnalysis
  - Configure analysis backpressure strategy
- [ ] Buat `kiosk-scanner/src/main/kotlin/.../scanner/camera/FrameAnalyzer.kt`
  - Convert ImageProxy → Bitmap
  - Pipeline: detect → crop → liveness → embed → match
  - Debounce: skip scan dalam 2 detik setelah scan terakhir
- [ ] Buat `kiosk-scanner/src/main/kotlin/.../scanner/camera/PreviewView.kt`
  - Fullscreen camera preview
  - Overlay: jam, status koneksi, mode kiosk
- [ ] Test: camera preview muncul + face detection berjalan

## Referensi
- `docs/planning.md` → Section 13.2 (`:kiosk-scanner` camera/)
```

**Labels:** `phase-1`, `android`, `kiosk`, `camera`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #18: `:kiosk-scanner` — Registration Screen (Multi-Sample Centroid)

**Title:** `[Phase1][Kiosk] Face registration — video capture + quality check + centroid computation`

**Body:**
```
## Deskripsi
Screen untuk registrasi wajah mahasiswa: capture video 3-5 detik, extract multiple embeddings, compute centroid.

## Flow
1. CameraX video stream — 3-5 detik @ 30fps
2. Setiap frame: detect → quality check (blur, lighting, confidence)
3. Collect 5-10 good embeddings
4. Compute centroid = mean(embeddings)
5. Compute consistency = min(cosine_sim(frame_i, centroid))
6. Jika consistency < 0.80 → "Registrasi tidak stabil, coba lagi"
7. Simpan ke database: centroid + allEmbeddings + consistency + sampleCount

## Checklist
- [ ] Buat `kiosk-scanner/.../scanner/registration/RegistrationEngine.kt`
  - Multi-frame capture logic
  - Centroid computation
  - Consistency check
- [ ] Buat `kiosk-scanner/.../scanner/registration/QualityValidator.kt`
  - Blur detection (Laplacian variance > 100)
  - Lighting check
  - Detection confidence >= 0.85
- [ ] Buat `kiosk-scanner/.../scanner/registration/RegistrationScreen.kt`
  - UI: video capture + progress bar + quality score
  - Retry strategy (3 attempts)
  - Success message: "Registered dengan 10 samples, quality score: 0.88"
- [ ] POST `/api/students/:id/face` — upload centroid ke server

## Referensi
- `docs/planning.md` → Section 3.4 (Student Face Registration Pipeline)
```

**Labels:** `phase-1`, `android`, `kiosk`, `registration`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #19: `:kiosk-scanner` — Verification Screen + Adaptive Matching

**Title:** `[Phase1][Kiosk] Face verification — scan + adaptive matching + result overlay`

**Body:**
```
## Deskripsi
Screen utama kiosk: auto-scan wajah, match dengan adaptive threshold, tampilkan hasil.

## Flow
1. CameraX auto-scan (continuous)
2. Detect face → confidence >= 0.85?
3. Liveness check → 2+ blinks dalam 3 detik?
4. Embed wajah → 192-d vector
5. Match: Top-K + gap analysis → decision
6. Tampilkan hasil:
   - MATCH_CONFIDENT → hijau + nama + aksi (keluar/kembali)
   - MATCH_MEDIUM → kuning + nama + "approved (flagged for review)"
   - MATCH_WEAK → orange + "Konfirmasi manual" → admin tap accept/reject
   - NO_MATCH → merah + "Wajah tidak dikenali"
7. Log ScanMetric ke local queue

## Checklist
- [ ] Buat `kiosk-scanner/.../scanner/matching/MatchEngine.kt`
  - Pipeline: detect → liveness → embed → match → decision
  - Integrate FaceDetector, LivenessDetector, FaceEmbedder, FaceMatcher
- [ ] Buat `kiosk-scanner/.../scanner/ui/ScannerScreen.kt`
  - Fullscreen camera + auto-scan
  - Indikator: jam, status koneksi, mode kiosk
- [ ] Buat `kiosk-scanner/.../scanner/ui/ResultOverlay.kt`
  - Animasi hasil: hijau/kuning/orange/merah
  - Show: nama mahasiswa, aksi (keluar/kembali), confidence score
- [ ] Log ScanMetric ke local queue (untuk sync ke backend)

## Referensi
- `docs/planning.md` → Section 3.5 (Verification & QA), Section 9 (Scan Toggle)
```

**Labels:** `phase-1`, `android`, `kiosk`, `verification`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #20: `:kiosk-scanner` — Toggle Engine (Keluar/Kembali)

**Title:** `[Phase1][Kiosk] Toggle engine — state management + rule checking`

**Body:**
```
## Deskripsi
Logic untuk menentukan aksi scan: "keluar" atau "kembali" berdasarkan state terakhir hari ini.

## Logic (Section 7.2)
```
Function determineAction(studentId, today):
    lastLog = SELECT * FROM AttendanceLog
              WHERE studentId = ? AND date(timestamp) = today
              ORDER BY timestamp DESC LIMIT 1

    IF lastLog == null: return "keluar"  // scan pertama hari ini
    IF lastLog.action == "keluar": return "kembali"
    IF lastLog.action == "kembali": return "keluar"
```

## Checklist
- [ ] Buat `kiosk-scanner/.../scanner/toggle/ToggleEngine.kt`
  - State per student per day
  - Determine action based on last log
- [ ] Buat `kiosk-scanner/.../scanner/toggle/ToggleState.kt`
  - State: DI_KAMPUS / DI_LUAR
  - Track: lastAction, scanCount
- [ ] Buat `kiosk-scanner/.../scanner/toggle/SessionTracker.kt`
  - Track durasi di luar (startTime → endTime)
  - Auto-close sesi pukul 23.59 → generate violation "tidak_kembali" jika masih DI_LUAR
- [ ] Integrate dengan MatchEngine

## Referensi
- `docs/planning.md` → Section 7.2 (Logika Toggle State), Section 9 (Sistem Scan Toggle)
```

**Labels:** `phase-1`, `android`, `kiosk`, `toggle`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #21: `:kiosk-scanner` — Rule Checker (Offline)

**Title:** `[Phase1][Kiosk] Offline rule checker — operational hours + restricted hours + permits`

**Body:**
```
## Deskripsi
Logic untuk memeriksa aturan kampus secara offline di kiosk.

## Logic (Section 8.3)
1. Cek hari libur → jika libur, skip semua aturan
2. Cek jam operasional → di luar jam = tolak
3. Cek izin aktif → ada izin = skip aturan restricted + jadwal kuliah
4. Cek restricted hours (CampusRule) → match dayOfWeek + time
5. Cek jadwal kuliah (CourseSchedule) → match dayOfWeek + time
6. Jika violation → simpan + flag AttendanceLog

## Checklist
- [ ] Buat `kiosk-scanner/.../scanner/rule/RuleChecker.kt`
  - `canScanOut(student, time, today): RuleResult`
  - Check: holiday → operational hours → permits → restricted hours → course schedule
- [ ] Buat `kiosk-scanner/.../scanner/rule/RuleCache.kt`
  - Cache rules + permits + holidays dari Room database
  - Update saat sync
- [ ] Test: simulate berbagai skenario (libur, jam terlarang, ada izin)

## Referensi
- `docs/planning.md` → Section 8 (Rule Engine), Section 13.2 (`:kiosk-scanner` rule/)
```

**Labels:** `phase-1`, `android`, `kiosk`, `rules`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #22: `:kiosk-scanner` — Metrics Collection + Local Queue

**Title:** `[Phase1][Kiosk] Scan metrics collection — local queue + sync`

**Body:**
```
## Deskripsi
Kumpulkan metrics setiap scan (detection confidence, liveness score, similarity, gap, decision) dan queue untuk sync ke backend.

## Checklist
- [ ] Buat `kiosk-scanner/.../scanner/metrics/ScanMetricsCollector.kt`
  - Collect per-scan metrics
  - Store ke Room database (ScanMetricEntity)
  - Queue untuk sync ke backend
- [ ] Data yang dikumpulkan:
  - timestamp, deviceId, detectionConfidence, livenessScore
  - topSimilarity, gap, confidence, decision
  - predictedStudentId, responseTimeMs
- [ ] Batch upload saat sync ke backend

## Referensi
- `docs/planning.md` → Section 3.6 (Metrics Collection), Section 6.14 (Scan Metrics API)
```

**Labels:** `phase-1`, `android`, `kiosk`, `metrics`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #23: `:kiosk-scanner` — Sync (Midnight + Polling + Push ScanMetric)

**Title:** `[Phase1][Kiosk] Sync engine — midnight auto + polling + push ScanMetric`

**Body:**
```
## Deskripsi
Sinkronisasi data kiosk ↔ backend: midnight auto-sync, polling sync request (10 detik), push scan metrics.

## Checklist
- [ ] Buat `core/.../sync/SyncPoller.kt`
  - Polling `GET /api/sync/requested` setiap 10 detik
  - Jika `requested = true` → trigger full sync
- [ ] Buat `core/.../sync/FaceSyncWorker.kt`
  - Download faces + students + rules dari server
  - Update Room database + rebuild FaceIndex di RAM
- [ ] Buat `core/.../sync/AttendanceSyncWorker.kt`
  - Upload AttendanceLog yang `isSynced = false`
- [ ] Buat `core/.../sync/ScanMetricSyncWorker.kt`
  - Upload ScanMetric queue ke backend
- [ ] Buat `core/.../sync/SyncManager.kt`
  - Orchestrate semua sync workers
  - Midnight auto-sync (00:00 WIB) via WorkManager
  - NetworkType.CONNECTED constraint
- [ ] Test: sync berjalan saat internet tersedia

## Referensi
- `docs/planning.md` → Section 4.1 (Sync Tengah Malam), Section 4.4 (Kiosk Auto-Fetch)
```

**Labels:** `phase-1`, `android`, `kiosk`, `sync`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #24: `:kiosk-scanner` — Kiosk Foreground Service

**Title:** `[Phase1][Kiosk] Foreground service — keep alive + notifications`

**Body:**
```
## Deskripsi
Foreground service untuk menjaga kiosk scanner tetap berjalan saat app di-background.

## Checklist
- [ ] Buat `kiosk-scanner/.../scanner/service/KioskForegroundService.kt`
  - Start foreground notification: "Kiosk Scanner aktif"
  - Keep service alive saat screen off
  - Handle service lifecycle
- [ ] Notification channel: "Kiosk Service"
- [ ] Auto-restart service jika killed (REDelivery pattern)
- [ ] Test: service tetap jalan saat app di-background

## Referensi
- `docs/planning.md` → Section 13.2 (`:kiosk-scanner` service/)
```

**Labels:** `phase-1`, `android`, `kiosk`, `service`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

### Issue #25: Validation — Test dengan 50-100 Volunteer

**Title:** `[Phase1][Validation] Face recognition validation — 50-100 volunteers, FPR/FNR measurement`

**Body:**
```
## Deskripsi
Validasi akurasi face recognition dengan 50-100 volunteer. Hitung FPR (False Positive Rate) dan FNR (False Negative Rate).

## Checklist
- [ ] Setup 1 kiosk scanner (tablet/HP)
- [ ] Registrasi 50-100 volunteer (multi-sample centroid)
- [ ] Setiap volunteer scan 5-10 kali (berbagai pose, lighting, waktu)
- [ ] Catat semua scan di ScanMetric:
  - predictedStudentId, actualStudentId, isCorrect, decision, confidence
- [ ] Hitung:
  - FPR = FP / (FP + TN) — target < 1%
  - FNR = FN / (FN + TP) — target < 2%
  - Accuracy = (TP + TN) / total
- [ ] Analisis: confidence distribution, MATCH_WEAK cases
- [ ] Document results di spreadsheet: date, FPR, FNR, threshold_config
- [ ] Decision: lock thresholds atau keep tuning

## Referensi
- `docs/planning.md` → Section 16 (Phase 1 Week 3-4), Section 3.6.3 (Alert Thresholds)
```

**Labels:** `phase-1`, `validation`, `testing`
**Milestone:** Phase 1 — Foundation + Core Pipeline

---

## 🏗️ MILESTONE: Phase 2 — Admin App + Izin (Week 5-7)

---

### Issue #26: `:admin-app` — Auth + Login + Dashboard

**Title:** `[Phase2][Admin] Auth + login screen + dashboard with SSE realtime`

**Body:**
```
## Deskripsi
Auth screen, dashboard dengan real-time data via SSE.

## Screens
- `LoginScreen` — username + password
- `DashboardScreen` — card stats (di kampus, di luar, izin aktif, violation hari ini) + recent scan feed

## Checklist
- [ ] Buat auth module: login → JWT → store token
- [ ] Buat dashboard: summary stats + recent scans
- [ ] SSE connection: `GET /api/events/stream`
- [ ] Handle events: dashboard_update, scan_realtime, violation_new, outside_update
- [ ] Auto-update dashboard tanpa polling

## Referensi
- `docs/planning.md` → Section 4.3 (Realtime Data), Section 14.2 (Admin Screens)
```

**Labels:** `phase-2`, `android`, `admin`, `auth`, `dashboard`
**Milestone:** Phase 2 — Admin App + Izin

---

### Issue #27: `:admin-app` — Student CRUD + Face Register

**Title:** `[Phase2][Admin] Student management — list, detail, form, face registration`

**Body:**
```
## Deskripsi
CRUD mahasiswa + face registration screen.

## Screens
- `StudentListScreen` — search + filter prodi/angkatan
- `StudentDetailScreen` — data + status toggle + history scan + violation + registration quality
- `StudentFormScreen` — tambah/edit
- `FaceRegisterScreen` — kamera + quality check + retry strategy

## Checklist
- [ ] Student list with search + filter
- [ ] Student detail: data diri + toggle status + scan history
- [ ] Student form: create + edit
- [ ] Face register: capture → quality check → upload centroid ke server
- [ ] Show registration quality: consistency score, sample count, model version

## Referensi
- `docs/planning.md` → Section 14.2 (Admin Screens)
```

**Labels:** `phase-2`, `android`, `admin`, `student`
**Milestone:** Phase 2 — Admin App + Izin

---

### Issue #28: `:admin-app` — Import CSV/Excel

**Title:** `[Phase2][Admin] CSV/Excel import — bulk student import`

**Body:**
```
## Deskripsi
Import mahasiswa dari CSV/Excel file.

## Format
NIM, Nama, Prodi, Angkatan, No HP, Email

## Checklist
- [ ] `ImportScreen` — pick file + preview + confirm
- [ ] Parse CSV/Excel (Apache POI / OpenCSV)
- [ ] Validate: NIM unique, required fields
- [ ] Bulk insert ke backend
- [ ] Show results: success count, failed count, error details
- [ ] Import batch tracking (ImportBatch model)

## Referensi
- `docs/planning.md` → Section 6.2 (POST /api/students/import)
```

**Labels:** `phase-2`, `android`, `admin`, `import`
**Milestone:** Phase 2 — Admin App + Izin

---

### Issue #29: `:admin-app` — Permit System (Harian + Pengajuan)

**Title:** `[Phase2][Admin] Permit system — izin harian (auto-approved) + pengajuan izin (approve/reject)`

**Body:**
```
## Deskripsi
Sistem izin: harian (auto-approved, kuota 10x/bulan) + pengajuan (perlu approval admin).

## Screens
- `PermitListScreen` — tab: Izin Harian, Pengajuan, All
- `PermitFormScreen` — pilih jenis, pilih mahasiswa, tanggal, jam, alasan, lampiran
- `PendingApprovalScreen` — list pengajuan pending + approve/reject

## Checklist
- [ ] Izin harian: auto-approved, kuota check (PermitQuota)
- [ ] Pengajuan izin: status pending → approve/reject by admin
- [ ] Kuota: per bulan kalender (reset tiap tanggal 1)
- [ ] Active permit check: `GET /api/permits/active/:studentId`
- [ ] Quota display: `GET /api/permits/quota/:studentId`
- [ ] Trigger sync ke kiosk saat approve/reject

## Referensi
- `docs/planning.md` → Section 11 (Sistem Izin), Section 6.4 (Permit endpoints)
```

**Labels:** `phase-2`, `android`, `admin`, `permit`
**Milestone:** Phase 2 — Admin App + Izin

---

### Issue #30: `:admin-app` — Rules Management + Global Settings

**Title:** `[Phase2][Admin] Rules management — CRUD aturan + global settings`

**Body:**
```
## Deskripsi
Kelola aturan kampus (restricted hours) dan pengaturan global.

## Screens
- `RulesListScreen` — CRUD aturan + toggle aktif/nonaktif
- `RuleFormScreen` — form: dayOfWeek, startTime, endTime, isRestricted, filter prodi/angkatan
- `GlobalSettingsScreen` — jam operasional, kuota, threshold

## Checklist
- [ ] Rules CRUD
- [ ] Global settings edit
- [ ] Trigger sync ke kiosk saat CRUD rules
- [ ] Holiday management (input tanggal libur manual)

## Referensi
- `docs/planning.md` → Section 8.2 (Global Settings), Section 6.5 (Rules endpoints)
```

**Labels:** `phase-2`, `android`, `admin`, `rules`
**Milestone:** Phase 2 — Admin App + Izin

---

## 🏗️ MILESTONE: Phase 3 — Monitoring + Violation + Report (Week 8-10)

---

### Issue #31: `:admin-app` — Toggle Status Monitoring

**Title:** `[Phase3][Admin] Real-time monitoring — toggle status + outside now`

**Body:**
```
## Deskripsi
Monitoring real-time status semua mahasiswa.

## Screens
- `ToggleStatusScreen` — tabel semua mahasiswa + status toggle real-time
- `OutsideNowScreen` — filter mahasiswa yang saat ini "di luar"

## Checklist
- [ ] Toggle status: show all students + DI_KAMPUS/DI_LUAR
- [ ] Outside now: filter yang sedang di luar + durasi
- [ ] Auto-update via SSE (outside_update event)
- [ ] Search + filter by prodi/angkatan

## Referensi
- `docs/planning.md` → Section 14.2 (Admin Screens)
```

**Labels:** `phase-3`, `android`, `admin`, `monitoring`
**Milestone:** Phase 3 — Monitoring + Violation + Report

---

### Issue #32: `:admin-app` — Violation Management

**Title:** `[Phase3][Admin] Violation list + detail + resolve`

**Body:**
```
## Deskripsi
Kelola pelanggaran: list, detail, resolve/ignore.

## Screens
- `ViolationListScreen` — filter tipe/tanggal
- `ViolationDetailScreen` — detail + resolve/ignore + notes

## Checklist
- [ ] Violation list with filters
- [ ] Violation detail: student info, rule broken, timestamp
- [ ] Resolve: mark resolved + add notes
- [ ] Dismiss: false positive
- [ ] Violation statistics: `GET /api/violations/statistics`
- [ ] Auto-resolve saat mahasiswa scan "kembali"

## Referensi
- `docs/planning.md` → Section 10 (Sistem Pelanggaran), Section 6.7 (Violation endpoints)
```

**Labels:** `phase-3`, `android`, `admin`, `violation`
**Milestone:** Phase 3 — Monitoring + Violation + Report

---

### Issue #33: `:admin-app` — Report Screens + Export

**Title:** `[Phase3][Admin] Reports — daily, violation, outside-hours + CSV/PDF export`

**Body:**
```
## Deskripsi
Laporan: rekap harian, pelanggaran, keluar di luar jam izin + export CSV/PDF.

## Screens
- `ReportScreen` — pilih jenis laporan + filter
- `DailyReportScreen` — rekap harian preview
- `ViolationReportScreen` — laporan pelanggaran
- `OutsideHoursReportScreen` — keluar di luar jam izin
- `ReportPreviewScreen` — preview sebelum export

## Checklist
- [ ] Daily report: rekap pergerakan per prodi
- [ ] Violation report: filter periode + tipe
- [ ] Outside hours report: keluar di luar jam izin
- [ ] Export CSV + PDF
- [ ] In-app preview

## Referensi
- `docs/planning.md` → Section 12 (Laporan & Rekapan), Section 6.8 (Report endpoints)
```

**Labels:** `phase-3`, `android`, `admin`, `report`
**Milestone:** Phase 3 — Monitoring + Violation + Report

---

### Issue #34: `:admin-app` — Scan Metrics Dashboard + Match Review

**Title:** `[Phase3][Admin] Scan metrics dashboard — FPR/FNR + match review`

**Body:**
```
## Deskripsi
Dashboard metrics: decision distribution, FPR/FNR trends, review MATCH_WEAK scans.

## Screens
- `ScanMetricsScreen` — decision distribution pie chart, response time histogram, flagged scans
- `MatchReviewScreen` — show face photo + top 3 candidates + accept/reject manual override

## Checklist
- [ ] Scan metrics dashboard: today's stats, hourly trends
- [ ] Decision distribution: CONFIDENT/MEDIUM/WEAK/NO_MATCH pie chart
- [ ] Match review: list MATCH_WEAK scans → admin review → correct actualStudentId
- [ ] Manual override: accept/reject + notes
- [ ] FPR/FNR calculation from reviewed scans

## Referensi
- `docs/planning.md` → Section 3.6 (Metrics), Section 6.14 (Scan Metrics API), Section 14.2 (UI)
```

**Labels:** `phase-3`, `android`, `admin`, `metrics`, `review`
**Milestone:** Phase 3 — Monitoring + Violation + Report

---

## 🏗️ MILESTONE: Phase 4 — Sync + Device + Notifikasi (Week 11-12)

---

### Issue #35: `:admin-app` — Sync Trigger + Status

**Title:** `[Phase4][Admin] Sync trigger screen — manual sync + status monitoring`

**Body:**
```
## Deskripsi
Screen untuk trigger sync manual + monitor status sync semua kiosk.

## Screens
- `SyncScreen` — status koneksi kiosk + tombol "Sync Sekarang" + log sync

## Checklist
- [ ] Show all kiosks + online/offline status
- [ ] "Sync Sekarang" button → POST /api/sync/request/:deviceId
- [ ] Sync log: last sync time, status, device
- [ ] Auto-update via SSE (sync_status event)

## Referensi
- `docs/planning.md` → Section 4.2 (Sync Manual), Section 6.10 (Sync Admin endpoints)
```

**Labels:** `phase-4`, `android`, `admin`, `sync`
**Milestone:** Phase 4 — Sync + Device + Notifikasi

---

### Issue #36: `:admin-app` — Device Management

**Title:** `[Phase4][Admin] Device management — kiosk list + health monitoring`

**Body:**
```
## Deskripsi
Kelola device kiosk: list, status, battery, last ping.

## Screens
- `DeviceListScreen` — daftar kiosk + status
- `DeviceDetailScreen` — detail device + sync history

## Checklist
- [ ] Device list: show all kiosks + online/offline + battery level
- [ ] Device detail: last ping, sync history, location
- [ ] Device ping endpoint: POST /api/devices/ping (heartbeat)
- [ ] Auto-detect offline devices (last ping > 5 menit)

## Referensi
- `docs/planning.md` → Section 6.11 (Device endpoints), Section 14.2 (Admin Screens)
```

**Labels:** `phase-4`, `android`, `admin`, `device`
**Milestone:** Phase 4 — Sync + Device + Notifikasi

---

### Issue #37: `:admin-app` — Notifications

**Title:** `[Phase4][Admin] Notification screen — in-app notifications`

**Body:**
```
## Deskripsi
In-app notifications: violation alerts, sync done, permit pending, device offline.

## Screens
- `NotificationScreen` — list notifikasi + mark as read

## Checklist
- [ ] Notification list: filter by type + read/unread
- [ ] Mark as read: PUT /api/notifications/:id/read
- [ ] Badge count: jumlah notifikasi belum dibaca
- [ ] Auto-refresh via SSE

## Referensi
- `docs/planning.md` → Section 6.15 (Audit & Notifikasi)
```

**Labels:** `phase-4`, `android`, `admin`, `notification`
**Milestone:** Phase 4 — Sync + Device + Notifikasi

---

### Issue #38: Course Schedule Management + Import

**Title:** `[Phase4][Admin] Course schedule — per-student schedule + CSV import`

**Body:**
```
## Deskripsi
Jadwal kuliah per mahasiswa + import dari CSV.

## Format CSV
NIM, Matkul, Hari, Jam Mulai, Jam Selesai, Ruang, Dosen

## Checklist
- [ ] Schedule list per student
- [ ] CRUD schedule
- [ ] CSV import: parse + validate + bulk insert
- [ ] Trigger sync ke kiosk

## Referensi
- `docs/planning.md` → Section 6.6 (Course Schedule endpoints)
```

**Labels:** `phase-4`, `android`, `admin`, `schedule`, `import`
**Milestone:** Phase 4 — Sync + Device + Notifikasi

---

## 🏗️ MILESTONE: Phase 5 — Polishing & Production (Week 13+)

---

### Issue #39: Error Handling + Edge Cases

**Title:** `[Phase5] Error handling + edge cases — all screens + API`

**Body:**
```
## Deskripsi
Standardisasi error handling di semua layer: API, network, database, UI.

## Checklist
- [ ] Backend: consistent error response format `{ error, message, details }`
- [ ] Network: handle timeout, no internet, server error
- [ ] Database: handle constraint violations, migration errors
- [ ] UI: error state semua screens (error message + retry button)
- [ ] Edge cases:
  - Scan duplikat (debounce 2 detik)
  - Sync gagal di tengah jalan (partial batch)
  - Dua kiosk scan mahasiswa sama (kedua log tetap masuk)
  - Admin approve izin saat kiosk offline

## Referensi
- `docs/planning.md` → Section 7.6 (Conflict Resolution)
```

**Labels:** `phase-5`, `polishing`, `error-handling`
**Milestone:** Phase 5 — Polishing & Production

---

### Issue #40: Loading/Empty/Error States

**Title:** `[Phase5] UI states — loading, empty, error untuk semua screens`

**Body:**
```
## Deskripsi
Pastikan semua screens punya: loading state, empty state, error state.

## Checklist
- [ ] Loading: shimmer/skeleton screen
- [ ] Empty: pesan "Belum ada data" + illustration
- [ ] Error: pesan error + retry button
- [ ] Apply ke semua screens (kiosk + admin)

## Referensi
- `docs/planning.md` → Section 16 (Phase 5)
```

**Labels:** `phase-5`, `polishing`, `ui`
**Milestone:** Phase 5 — Polishing & Production

---

### Issue #41: Performance Tuning (10k Vectors)

**Title:** `[Phase5] Performance tuning — 10k face vectors matching < 30ms`

**Body:**
```
## Deskripsi
Optimasi performa untuk 10.000 face vectors di kiosk.

## Target
- Face matching: < 30ms untuk 10k vectors
- End-to-end scan: < 100ms
- RAM usage: < 100MB untuk face cache

## Checklist
- [ ] Benchmark: 10k vectors matching latency
- [ ] Optimasi: SIMD/vectorized cosine similarity (jika available)
- [ ] Memory: verify FaceIndex cache size
- [ ] Profile: identifikasi bottlenecks
- [ ] Test di low-end device (4GB RAM)

## Referensi
- `docs/planning.md` → Section 16 (Phase 5)
```

**Labels:** `phase-5`, `performance`, `optimization`
**Milestone:** Phase 5 — Polishing & Production

---

### Issue #42: Documentation + Deployment Guide

**Title:** `[Phase5] Documentation — API docs + deployment guide + user guide`

**Body:**
```
## Deskripsi
Dokumentasi lengkap: API docs, deployment guide (Docker + Cloudflare Tunnel), user guide.

## Checklist
- [ ] API documentation (OpenAPI/Swagger atau markdown)
- [ ] Deployment guide:
  - Docker setup (docker-compose.yml)
  - Cloudflare Tunnel configuration
  - Domain facegate.utc.web.id setup
- [ ] User guide:
  - Kiosk scanner: cara setup + operasional
  - Admin app: cara kelola data, izin, monitoring
- [ ] Developer guide:
  - Architecture overview
  - How to add new features
  - How to debug common issues

## Referensi
- `docs/planning.md` → Section 16 (Phase 5)
- `AGENTS.md`
```

**Labels:** `phase-5`, `documentation`
**Milestone:** Phase 5 — Polishing & Production

---

### Issue #43: Soft Launch — 1 Kiosk Production

**Title:** `[Phase5] Soft launch — deploy 1 kiosk, limited hours (8am-10am)`

**Body:**
```
## Deskripsi
Deploy ke 1 gerbang real untuk testing production.

## Checklist
- [ ] Deploy backend ke home server (Docker)
- [ ] Setup Cloudflare Tunnel → facegate.utc.web.id
- [ ] Install kiosk scanner di tablet/HP gerbang
- [ ] Registrasi 50-100 mahasiswa pertama
- [ ] Operasional: jam 8am-10am saja (limited hours)
- [ ] Monitor: scan success rate, error rate, latency
- [ ] Admin review: MATCH_WEAK scans daily
- [ ] Jika stabil > 5 hari → expand ke 2 kiosk

## Referensi
- `docs/planning.md` → Section 16 (Phase 1 Week 4 — Soft Launch)
```

**Labels:** `phase-5`, `deployment`, `production`
**Milestone:** Phase 5 — Polishing & Production

---

## 📋 RINGKASAN

| Phase | Issues | Estimasi |
|---|---|---|
| Phase 1 — Foundation + Core Pipeline | #1 - #25 | 32 hari |
| Phase 2 — Admin App + Izin | #26 - #30 | 14 hari |
| Phase 3 — Monitoring + Violation + Report | #31 - #34 | 11 hari |
| Phase 4 — Sync + Device + Notifikasi | #35 - #38 | 7 hari |
| Phase 5 — Polishing & Production | #39 - #43 | 11 hari |
| **Total** | **43 issues** | **~75 hari** |

## 🏷️ Labels yang dibutuhkan di GitHub

```
phase-1, phase-2, phase-3, phase-4, phase-5
setup, database, backend, api, android, core, kiosk, admin
face, detection, liveness, matching, registration, metrics
camera, toggle, rules, sync, service, monitoring, violation, report, permit, import
auth, dashboard, student, device, notification, schedule
performance, polishing, error-handling, ui, documentation
validation, testing, deployment, production, infrastructure
```
