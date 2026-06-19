# FaceGateApp — Planning Document

> Proyek absensi & perizinan kampus berbasis **Face Recognition Offline-First**
> Target: 10.000 mahasiswa | 2 aplikasi Android Native + Backend API

---

## Daftar Isi

1. [Ringkasan Proyek](#1-ringkasan-proyek)
2. [Struktur Monorepo](#2-struktur-monorepo)
3. [Tech Stack Detail](#3-tech-stack-detail)
4. [Arsitektur Sistem](#4-arsitektur-sistem)
5. [Data Model & Database](#5-data-model--database)
6. [API Endpoints](#6-api-endpoints)
7. [Alur Data Offline-First](#7-alur-data-offline-first)
8. [Android Module Breakdown](#8-android-module-breakdown)
9. [UI Screen Map](#9-ui-screen-map)
10. [Hardware Requirement](#10-hardware-requirement)
11. [Phase / Milestone Pengembangan](#11-phase--milestone-pengembangan)
12. [Open Questions](#12-open-questions)

---

## 1. Ringkasan Proyek

### 1.1 Visi
Sistem absensi kampus **tanpa kartu**, **tanpa internet saat operasional**, menggunakan pengenalan wajah di *edge device* (HP/Tablet Android).

### 1.2 Pengguna
| Peran | Perangkat | Jumlah |
|---|---|---|
| Mahasiswa | Wajah mereka (pasif) | ~10.000 |
| Admin/Pengurus | HP Android (Admin App) | ~5-10 orang |
| Petugas Gerbang | — | 1-2 orang |

### 1.3 Aplikasi
| Aplikasi | Perangkat | Fungsi Utama |
|---|---|---|
| **Kiosk Scanner** | Tablet/HP statis di gerbang | Scan wajah offline, catat absensi |
| **Admin App** | HP pegangan admin | Registrasi wajah, approve izin, monitoring |
| **Backend API** | Server (Docker) | Master data, sync, dashboard |

---

## 2. Struktur Monorepo

```
FaceGateApp/
├── android/                          # Root Gradle — semua module Android
│   ├── core/                         # :core — shared library (Android Library)
│   │   ├── src/main/kotlin/.../
│   │   │   ├── face/                 # TFLite wrapper (ekstraksi vektor)
│   │   │   ├── network/             # Retrofit API client + model DTO
│   │   │   ├── database/            # Room DAOs + entities (shared)
│   │   │   ├── sync/                # WorkManager sync logic
│   │   │   └── model/               # Domain model (shared)
│   │   └── build.gradle.kts
│   │
│   ├── kiosk-scanner/                # :kiosk-scanner — aplikasi scanner
│   │   ├── src/main/
│   │   │   ├── java/.../
│   │   │   │   ├── camera/           # CameraX logic
│   │   │   │   ├── matching/         # Brute-force matching di RAM
│   │   │   │   ├── ui/              # Compose UI
│   │   │   │   └── service/         # Foreground service (jaga kamera)
│   │   │   └── res/
│   │   └── build.gradle.kts
│   │
│   ├── admin-app/                    # :admin-app — aplikasi admin
│   │   ├── src/main/
│   │   │   ├── java/.../
│   │   │   │   ├── auth/            # Login admin
│   │   │   │   ├── register/        # Registrasi wajah mahasiswa
│   │   │   │   ├── permit/          # Approval izin
│   │   │   │   ├── attendance/      # Monitoring absensi
│   │   │   │   ├── import/          # Import Excel/CSV
│   │   │   │   └── ui/             # Compose UI
│   │   │   └── res/
│   │   └── build.gradle.kts
│   │
│   ├── build.gradle.kts              # Root build (version catalog)
│   ├── settings.gradle.kts
│   └── gradle/
│       └── libs.versions.toml        # Version catalog
│
├── backend/                          # Node.js (Bun) backend
│   ├── src/
│   │   ├── index.ts                  # Entry point
│   │   ├── routes/                   # Route handlers
│   │   ├── controllers/              # Business logic
│   │   ├── middleware/               # Auth, validation
│   │   └── utils/                    # Helpers
│   ├── prisma/
│   │   ├── schema.prisma             # Database schema
│   │   └── seed.ts                   # Data awal
│   ├── docker/
│   │   └── Dockerfile
│   ├── package.json
│   └── tsconfig.json
│
├── docker-compose.yml                # PostgreSQL + pgvector + backend
├── docs/
│   ├── planning.md                   # ← file ini
│   └── api-spec.md                   # Spesifikasi API detail
└── README.md
```

### 2.1 Dependency Antar Module Android

```
:kiosk-scanner ──→ :core
:admin-app     ──→ :core
```

Semua module Android bergantung ke `:core`. `:core` berisi:
- TFLite face embedding
- Room database (entities + DAO)
- Retrofit API client
- Sync worker (WorkManager)
- Domain model

---

## 3. Tech Stack Detail

### 3.1 Android

| Komponen | Pustaka | Versi | Catatan |
|---|---|---|---|
| Bahasa | Kotlin | 2.0.x | |
| UI | Jetpack Compose + Material 3 | | Bukan XML |
| Kamera | CameraX | 1.4.x | lifecycle-aware |
| Face Embedding | TensorFlow Lite (MobileFaceNet) | | Model .tflite |
| Database Lokal | Room | 2.6.x | SQLite |
| Background Sync | WorkManager | 2.9.x | Periodic sync tengah malam + opportunistic |
| Networking | Retrofit + OkHttp + Kotlinx Serialization | | |
| DI | Hilt | 2.50+ | |
| Coroutines | Kotlinx Coroutines | 1.8.x | |
| Vector Storage | FloatArray di RAM | | 10.000 vektor × 128-d = ~5-10 MB |
| Minimum SDK | **Android 12 (API 31)** | | |

### 3.2 Backend

| Komponen | Pustaka | Catatan |
|---|---|---|
| Runtime | Bun | Lebih cepat dari Node.js biasa |
| Framework | Elysia / Hono | Ringan, TypeScript-native |
| ORM | Prisma | Type-safe database client |
| Database | PostgreSQL + pgvector | Penyimpanan vektor wajah master |
| Auth | JWT (bcrypt) | |
| Validation | Zod | Request validation |
| Container | Docker + docker-compose | |

### 3.3 AI / Face Recognition Pipeline

```
[CameraX frame] → [TFLite: Face Detection] → [TFLite: Face Embedding]
     ↓                                               ↓
  Crop face ROI                              Vector 128-d FloatArray
     ↓                                               ↓
  [TFLite: Liveness Detection]               [Brute-force compare]
  (blink detection / motion)                 (cosine similarity) 
                                               ↓
                                         [Match ≥ threshold?]
                                               ↓
                                     YES → Catat absensi + nama
                                     NO  → Tampilkan "wajah tidak dikenal"
```

- **Model**: MobileFaceNet .tflite (128-d embedding)
- **Threshold**: default 0.6 (bisa di-tuning)
- **Liveness**: model tambahan untuk deteksi kedipan/gerakan
- **Kecepatan target**: < 50ms per face (deteksi + embedding + matching)

---

## 4. Arsitektur Sistem

```
                            ┌─────────────────────────┐
                            │   PostgreSQL + pgvector   │
                            │     (Master Data)         │
                            └──────────┬──────────────┘
                                       │
                            ┌──────────┴──────────────┐
                            │  Backend API (Bun)       │
                            │  - CRUD mahasiswa        │
                            │  - Manajemen izin        │
                            │  - Sync face vectors     │
                            │  - Dashboard/rekap       │
                            └──────────┬──────────────┘
                                       │
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
   ┌──────────▼──────────┐   ┌─────────▼──────────┐   ┌───────▼────────┐
   │  Kiosk Scanner App   │   │   Admin App        │   │   Web Admin    │
   │  (Android Tablet)    │   │   (Android HP)     │   │   (Opsional)   │
   │                      │   │                    │   │                │
   │  Offline:            │   │  Online:           │   │                │
   │  - Scan wajah        │   │  - Registrasi      │   │                │
   │  - Match di RAM      │   │  - Approve izin    │   │                │
   │  - Simpan log lokal  │   │  - Monitoring      │   │                │
   │                      │   │  - Import data     │   │                │
   │  Online (sync):      │   │                    │   │                │
   │  - Download vektor   │   │                    │   │                │
   │  - Upload log        │   │                    │   │                │
   └──────────────────────┘   └────────────────────┘   └────────────────┘
```

### 4.1 Alur Tengah Malam (Background Sync)

```
WorkManager (setiap 00:00)
       │
       ├── Cek koneksi internet
       │      └── Tidak ada → coba lagi 30 menit kemudian
       │
       ├── GET /api/sync/faces?since=<timestamp>
       │      └── Response: daftar {id, nim, vector[], updated_at}
       │
       ├── Update Room Database
       │      └── INSERT OR REPLACE face_vectors
       │
       ├── Load semua vektor ke RAM
       │      └── In-memory FloatArray[10000][128]
       │
       └── POST /api/sync/attendance (batch upload log offline)
              └── Response: {synced_count, failed_ids}
```

---

## 5. Data Model & Database

### 5.1 PostgreSQL (Master — via Prisma)

```prisma
model Student {
  id          String   @id @default(cuid())
  nim         String   @unique
  name        String
  studyProgram String?
  phone       String?
  email       String?
  isActive    Boolean  @default(true)
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  faceVectors    FaceVector[]
  attendanceLogs AttendanceLog[]
  permits        Permit[]
}

model FaceVector {
  id        String   @id @default(cuid())
  studentId String
  vector    Unsupported("vector(128)")? // pgvector extension
  createdAt DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])
}

model AttendanceLog {
  id              String   @id @default(cuid())
  studentId       String
  timestamp       DateTime @default(now())
  confidenceScore Float?
  isSynced        Boolean  @default(true) // false untuk log dari kiosk
  deviceId        String?  // identitas kiosk
  createdAt       DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])

  @@index([timestamp])
  @@index([studentId])
}

model Permit {
  id          String   @id @default(cuid())
  studentId   String
  type        String   // "sakit", "ijin", "dispensasi"
  reason      String
  startDate   DateTime
  endDate     DateTime
  status      String   @default("pending") // pending | approved | rejected
  approvedBy  String?  // adminId
  attachment  String?  // URL file (surat sakit, dll)
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  student Student @relation(fields: [studentId], references: [id])
}

model Admin {
  id           String   @id @default(cuid())
  username     String   @unique
  passwordHash String
  displayName  String
  role         String   @default("admin") // admin | superadmin
  isActive     Boolean  @default(true)
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt
}

model SyncLog {
  id         String   @id @default(cuid())
  deviceId   String
  lastSyncAt DateTime
  status     String   // success | partial | failed
  notes      String?
}
```

### 5.2 Room Database (Android — Local Cache)

Tabel di SQLite lokal (sama struktur, subset dari master):

| Entity | Catatan |
|---|---|
| `StudentEntity` | Cache data mahasiswa |
| `FaceVectorEntity` | Vektor wajah (disimpan sebagai Blob/byte array) |
| `AttendanceLogEntity` | Log absensi lokal (isSynced = false jika belum dikirim) |
| `PermitEntity` | Cache izin (untuk admin app) |
| `SyncMetadata` | Tracking kapan terakhir sync |

> **Catatan**: Room tidak support `FloatArray` langsung. Vektor 128-d akan disimpan sebagai `Blob` (byte array dari FloatArray) dan dikonversi via TypeConverter.

### 5.3 In-Memory Structure (Kiosk Scanner)

```kotlin
// Di-load saat app start, di-refresh setiap sync malam
data class FaceIndex(
    val embeddings: Map<String, FloatArray>,  // studentId → vector
    val studentMap: Map<String, String>       // studentId → nama/NIM
)
```

---

## 6. API Endpoints

### 6.1 Auth
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/auth/login` | Login admin → JWT |
| POST | `/api/auth/refresh` | Refresh token |

### 6.2 Student
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/students` | List mahasiswa (pagination + search) |
| GET | `/api/students/:id` | Detail mahasiswa |
| POST | `/api/students` | Tambah mahasiswa |
| PUT | `/api/students/:id` | Update data mahasiswa |
| DELETE | `/api/students/:id` | Soft delete (nonaktifkan) |
| POST | `/api/students/:id/face` | Upload vektor wajah baru |
| POST | `/api/students/import` | Import bulk dari CSV/Excel |

### 6.3 Attendance
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/attendance` | Catat absensi (dari kiosk) |
| GET | `/api/attendance` | Riwayat absensi (filter tanggal, status) |
| GET | `/api/attendance/today` | Ringkasan absensi hari ini |
| GET | `/api/attendance/statistics` | Statistik per program studi |

### 6.4 Permit
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/permits` | Buat pengajuan izin |
| GET | `/api/permits` | List izin (filter status) |
| GET | `/api/permits/pending` | Izin pending (perlu approval) |
| PUT | `/api/permits/:id/approve` | Approve/reject izin |

### 6.5 Sync (khusus Kiosk Scanner)
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/sync/faces` | Download semua face vectors (dengan timestamp) |
| POST | `/api/sync/attendance` | Batch upload log absensi offline |

### 6.6 Dashboard
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/dashboard/summary` | Total mahasiswa, hadir hari ini, izin pending |
| GET | `/api/dashboard/weekly` | Grafik absensi mingguan |

---

## 7. Alur Data Offline-First

### 7.1 Skenario: Kiosk Scanning (100% Offline)

```
1. Mahasiswa berdiri di depan kamera
2. CameraX capture frame
3. TFLite detect face → jika tidak ada face, loop
4. TFLite liveness check → jika gagal, minta ulang
5. TFLite extract embedding (128-d vector)
6. Compare dengan 10.000 vektor di RAM (cosine similarity)
   └── Jika match (≥ threshold):
       → Tampilkan nama + "Hadir ✓"
       → Simpan log ke Room (isSynced = false)
   └── Jika tidak match:
       → Tampilkan "Wajah tidak dikenal"
7. Background: WorkManager mendeteksi internet stabil
   → POST /api/sync/attendance (batch)
   → Tandai log sebagai isSynced = true
```

### 7.2 Skenario: Admin Registrasi Wajah (Online)

```
1. Admin buka Admin App → pilih mahasiswa (dari import atau input manual)
2. Kamera capture wajah (dengan bimbingan framing)
3. TFLite extract embedding (localhost)
4. POST /api/students/:id/face { vector: [...] }
5. Backend simpan ke PostgreSQL (pgvector)
6. Malam hari: Kiosk sync → download vektor baru → update RAM
```

### 7.3 Conflict Resolution

| Skenario | Resolusi |
|---|---|
| Log absensi ganda (duplicate timestamp + studentId) | Backend dedup berdasarkan `studentId + date_trunc('hour', timestamp)` |
| Update data mahasiswa bentrok | Last-write-wins (updatedAt) |
| Sync gagal (server down) | Retry exponential backoff (30s → 5m → 30m) |

---

## 8. Android Module Breakdown

### 8.1 `:core` (Shared Library)

```
core/
├── src/main/kotlin/.../core/
│   ├── di/                       # Hilt modules
│   │   ├── NetworkModule.kt
│   │   ├── DatabaseModule.kt
│   │   └── FaceModule.kt
│   │
│   ├── face/
│   │   ├── FaceDetector.kt       # TFLite face detection
│   │   ├── FaceEmbedder.kt       # TFLite embedding extraction
│   │   ├── FaceMatcher.kt        # Cosine similarity brute-force
│   │   ├── LivenessDetector.kt   # Blink / motion detection
│   │   └── FaceIndex.kt          # In-memory index (data class)
│   │
│   ├── database/
│   │   ├── AppDatabase.kt        # Room database
│   │   ├── entity/               # Room entities
│   │   ├── dao/                  # DAOs
│   │   └── converter/            # TypeConverters (FloatArray ↔ Blob)
│   │
│   ├── network/
│   │   ├── ApiClient.kt          # Retrofit instance
│   │   ├── ApiService.kt         # Interface endpoints
│   │   ├── dto/                  # Data transfer objects
│   │   └── interceptor/          # Auth interceptor (JWT)
│   │
│   ├── sync/
│   │   ├── FaceSyncWorker.kt     # WorkManager: download vectors
│   │   ├── AttendanceSyncWorker.kt # WorkManager: upload logs
│   │   └── SyncManager.kt        # Orchestrator
│   │
│   └── model/
│       ├── Student.kt            # Domain model
│       ├── Attendance.kt
│       └── Permit.kt
```

### 8.2 `:kiosk-scanner` (Aplikasi Scanner)

```
kiosk-scanner/
├── src/main/kotlin/.../scanner/
│   ├── ScannerApp.kt             # Application class + Hilt
│   ├── MainActivity.kt
│   │
│   ├── camera/
│   │   ├── CameraManager.kt      # CameraX lifecycle wrapper
│   │   ├── FrameAnalyzer.kt      # ImageAnalysis.Analyzer
│   │   └── PreviewView.kt        # Compose wrapper
│   │
│   ├── matching/
│   │   ├── MatchEngine.kt        # Orchestrator: detect → embed → match
│   │   └── MatchResult.kt        # Sealed class (matched / unknown / error)
│   │
│   ├── ui/
│   │   ├── ScannerScreen.kt      # Main scanner composable
│   │   ├── ResultOverlay.kt      # Overlay nama + status
│   │   └── StatusBar.kt          # Sync status, clock
│   │
│   └── service/
│       └── KioskForegroundService.kt # Keep app alive + prevent sleep
```

### 8.3 `:admin-app` (Aplikasi Admin)

```
admin-app/
├── src/main/kotlin/.../admin/
│   ├── AdminApp.kt               # Application class + Hilt
│   ├── MainActivity.kt           # Navigation host
│   │
│   ├── auth/
│   │   ├── LoginScreen.kt
│   │   └── AuthViewModel.kt
│   │
│   ├── student/
│   │   ├── StudentListScreen.kt
│   │   ├── StudentDetailScreen.kt
│   │   ├── StudentFormScreen.kt
│   │   └── StudentViewModel.kt
│   │
│   ├── register/
│   │   ├── FaceRegisterScreen.kt # Kamera + panduan framing
│   │   └── RegisterViewModel.kt
│   │
│   ├── permit/
│   │   ├── PermitListScreen.kt
│   │   ├── PermitDetailScreen.kt
│   │   └── PermitViewModel.kt
│   │
│   ├── attendance/
│   │   ├── AttendanceScreen.kt   # Monitoring + rekap
│   │   └── AttendanceViewModel.kt
│   │
│   └── import/
│       ├── ImportScreen.kt       # Import CSV/Excel
│       └── ImportViewModel.kt
```

---

## 9. UI Screen Map

### 9.1 Kiosk Scanner

| Screen | Deskripsi |
|---|---|
| `ScannerScreen` | Layar penuh kamera, overlay tipis, tidak ada tombol. Otomatis scan. |
| `ResultOverlay` | Animasi muncul saat wajah terdeteksi (hijau = dikenal, merah = tidak dikenal). |

### 9.2 Admin App

| Screen | Deskripsi |
|---|---|
| `LoginScreen` | Username + password admin |
| `DashboardScreen` | Statistik hari ini (hadir, izin, alpha) |
| `StudentListScreen` | Search + list mahasiswa (pull-to-refresh) |
| `StudentDetailScreen` | Detail mahasiswa + riwayat absensi |
| `StudentFormScreen` | Tambah/edit data mahasiswa |
| `FaceRegisterScreen` | Kamera untuk foto wajah (dengan panduan oval) |
| `PermitListScreen` | List pengajuan izin (tab: pending, approved, rejected) |
| `PermitDetailScreen` | Detail izin + tombol approve/reject |
| `AttendanceScreen` | Rekap absensi (filter per tanggal/prodi) |
| `ImportScreen` | Pilih file CSV/Excel, preview, import |

---

## 10. Hardware Requirement

### 10.1 Kiosk Scanner (Tablet/HP)

| Komponen | Minimal | Rekomendasi |
|---|---|---|
| OS | Android 12 (API 31) | Android 13+ |
| RAM | 4 GB | 6 GB+ |
| Kamera Depan | 5 MP | 8 MP+ dengan autofocus |
| CPU | Octa-core 2.0 GHz | Snapdragon 7xx+ / MediaTek G series |
| Storage | 32 GB | 64 GB+ |
| Layar | 8 inci | 10 inci (agar mahasiswa lihat hasil) |
| Baterai | — | Selalu terhubung charger |

### 10.2 Posisi Pemasangan Kiosk

```
        ☀️ Cahaya dari depan (searah mahasiswa)
        
        ┌─────────────────────┐
        │    HP Kiosk         │  ← Kamera menghadap mahasiswa
        │  ┌─────────────┐   │
        │  │   Kamera     │───┼──▶ Mahasiswa
        │  └─────────────┘   │
        └─────────────────────┘
                │
                ⛔ JANGAN backlight (matahari dari belakang mahasiswa)
```

---

## 11. Phase / Milestone Pengembangan

### Phase 1: Foundation (Prioritas Tertinggi)

**Tujuan**: Backend + Kiosk Scanner bisa ngelarin scan offline end-to-end.

| Task | Estimasi |
|---|---|
| Setup monorepo + Gradle + version catalog | 1 hari |
| Setup backend (Bun + Prisma + PostgreSQL + pgvector + Docker) | 2 hari |
| Database schema + migration + seed | 1 hari |
| API endpoints: CRUD students, sync faces, attendance | 2 hari |
| `:core` — Room database + TypeConverters | 1 hari |
| `:core` — TFLite face embedder + liveness detection | 2 hari |
| `:core` — Network layer (Retrofit) | 1 hari |
| `:kiosk-scanner` — CameraX + frame analyzer | 2 hari |
| `:kiosk-scanner` — Face matching engine + result overlay | 2 hari |
| `:kiosk-scanner` — WorkManager sync tengah malam | 1 hari |
| **Total Phase 1** | **~15 hari** |

### Phase 2: Admin App (Prioritas Sedang)

**Tujuan**: Admin bisa daftarin mahasiswa + approve izin.

| Task | Estimasi |
|---|---|
| `:admin-app` — Auth + login screen | 1 hari |
| `:admin-app` — Student list + form + detail | 2 hari |
| `:admin-app` — Face registration dengan kamera | 2 hari |
| `:admin-app` — Import CSV/Excel | 1 hari |
| `:admin-app` — Permit list + approve/reject | 2 hari |
| `:admin-app` — Attendance monitoring | 1 hari |
| API endpoints: permit CRUD, import, dashboard | 2 hari |
| **Total Phase 2** | **~11 hari** |

### Phase 3: Polishing & Production (Prioritas Rendah)

**Tujuan**: Siap dipake beneran.

| Task | Estimasi |
|---|---|
| Error handling + edge cases | 2 hari |
| Loading state + empty state di semua screen | 1 hari |
| Logging + crash reporting | 1 hari |
| Testing (manual + automated) | 3 hari |
| Dokumentasi pengguna | 1 hari |
| Deployment guide | 1 hari |
| **Total Phase 3** | **~9 hari** |

---

## 12. Open Questions

Pertanyaan yang perlu dijawab sebelum/selama development:

1. **Model TFLite**: MobileFaceNet atau model lain? Butuh model pre-trained .tflite. Sumber?
2. **Liveness Detection**: Model blinking atau texture-based (faspe)? Butuh research.
3. **Backend Framework**: Elysia vs Hono? Dua-duanya cocok Bun, perlu dipilih satu.
4. **Container Registry**: Docker Hub atau self-hosted?
5. **Face Vector Dimensi**: 128-d atau 512-d? 128-d lebih cepat, 512-d lebih akurat.
6. **CSV Import Format**: Template kolom apa saja? NIM, Nama, Prodi, dsb.
7. **Dashboard Web**: Butuh web dashboard atau cukup dari Admin App?

---

> **Status**: Planning — belum ada coding.
> **Next step**: Kalau dokumen ini sudah OK, lanjut ke setup monorepo + Phase 1.
