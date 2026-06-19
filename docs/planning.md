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
8. [Rule Engine: Aturan & Pembatasan](#8-rule-engine-aturan--pembatasan)
9. [Sistem Check-In / Check-Out](#9-sistem-check-in--check-out)
10. [Sistem Pelanggaran](#10-sistem-pelanggaran)
11. [Sistem Izin & Kuota](#11-sistem-izin--kuota)
12. [Laporan & Rekapan](#12-laporan--rekapan)
13. [Android Module Breakdown](#13-android-module-breakdown)
14. [UI Screen Map](#14-ui-screen-map)
15. [Hardware Requirement](#15-hardware-requirement)
16. [Phase / Milestone Pengembangan](#16-phase--milestone-pengembangan)
17. [Open Questions](#17-open-questions)

---

## 1. Ringkasan Proyek

### 1.1 Visi
Sistem absensi kampus **tanpa kartu**, **tanpa internet saat operasional**, menggunakan pengenalan wajah di *edge device* (HP/Tablet Android) dengan sistem **check-in/check-out dua arah** dan deteksi pelanggaran otomatis.

### 1.2 Pengguna
| Peran | Perangkat | Jumlah |
|---|---|---|
| Mahasiswa | Wajah mereka (pasif) | ~10.000 |
| Admin/Pengurus | HP Android (Admin App) | ~5-10 orang |
| Petugas Gerbang | — | 1-2 orang |
| Super Admin | Admin App / Web | 1 orang |

### 1.3 Aplikasi
| Aplikasi | Perangkat | Fungsi Utama |
|---|---|---|
| **Kiosk Scanner** | Tablet/HP statis di gerbang | Scan wajah **masuk & keluar**, catat timestamp, verifikasi offline |
| **Admin App** | HP pegangan admin | Registrasi wajah, approve izin, monitoring, atur rules, lihat rekap + pelanggaran |
| **Backend API** | Server (Docker) | Master data, sync, rule engine, generate laporan, audit log |

---

## 2. Struktur Monorepo

```
FaceGateApp/
├── android/                          # Root Gradle — semua module Android
│   ├── core/                         # :core — shared library (Android Library)
│   │   ├── src/main/kotlin/.../
│   │   │   ├── face/                 # TFLite wrapper (ekstraksi vektor)
│   │   │   ├── network/             # Retrofit API client + DTO
│   │   │   ├── database/            # Room DAOs + entities
│   │   │   ├── sync/                # WorkManager sync logic
│   │   │   └── model/               # Domain model
│   │   └── build.gradle.kts
│   │
│   ├── kiosk-scanner/                # :kiosk-scanner — aplikasi scanner
│   │   ├── src/main/
│   │   │   ├── java/.../
│   │   │   │   ├── camera/           # CameraX logic
│   │   │   │   ├── matching/         # Brute-force matching di RAM
│   │   │   │   ├── ui/              # Compose UI
│   │   │   │   └── service/         # Foreground service
│   │   │   └── res/
│   │   └── build.gradle.kts
│   │
│   ├── admin-app/                    # :admin-app — aplikasi admin
│   │   ├── src/main/
│   │   │   ├── java/.../
│   │   │   │   ├── auth/            # Login admin
│   │   │   │   ├── student/         # Manajemen mahasiswa
│   │   │   │   ├── register/        # Registrasi wajah
│   │   │   │   ├── permit/          # Approval izin
│   │   │   │   ├── attendance/      # Monitoring absensi
│   │   │   │   ├── checkinout/      # Rekap check-in/out
│   │   │   │   ├── rules/           # Aturan & pembatasan
│   │   │   │   ├── schedule/        # Jadwal kuliah
│   │   │   │   ├── violation/       # Pelanggaran
│   │   │   │   ├── report/          # Laporan & rekap
│   │   │   │   ├── device/          # Manajemen kiosk
│   │   │   │   ├── import/          # Import Excel/CSV
│   │   │   │   └── ui/             # Komponen shared UI
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
│   │   ├── services/                 # Service layer
│   │   │   ├── rule-engine.ts        # Rule/filter evaluator
│   │   │   ├── violation-detector.ts # Deteksi pelanggaran
│   │   │   ├── report-generator.ts   # Generate laporan
│   │   │   └── scheduler.ts          # Jadwal task background
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
| Background Sync | WorkManager | 2.9.x | Periodic sync + opportunistic |
| Networking | Retrofit + OkHttp + Kotlinx Serialization | | |
| DI | Hilt | 2.50+ | |
| Coroutines | Kotlinx Coroutines | 1.8.x | |
| Vector Storage | FloatArray di RAM | | 10.000 vektor × 128-d = ~5-10 MB |
| Excel/CSV | Apache POI / OpenCSV | | Untuk import/export |
| PDF Report | iText / Android PDF API | | Untuk cetak laporan |
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
| CSV Parsing | PapaParse | Untuk import data |
| PDF Generation | Puppeteer / PDFKit | Untuk export laporan |
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
                                               ↓
                                     Tentukan jenis scan: MASUK atau KELUAR
                                     (berdasarkan arah / session terakhir)
                                               ↓
                                     Cek rule engine: apakah boleh keluar?
                                     Jika tidak → flag sebagai pelanggaran
```

- **Model**: MobileFaceNet .tflite (128-d embedding)
- **Threshold**: default 0.6 (bisa di-tuning)
- **Liveness**: model tambahan deteksi kedipan/gerakan (anti-spoofing)
- **Kecepatan target**: < 50ms per face (deteksi + embedding + matching)

---

## 4. Arsitektur Sistem

```
                            ┌─────────────────────────────┐
                            │    PostgreSQL + pgvector     │
                            │      (Master Data)          │
                            └──────────┬──────────────────┘
                                       │
                            ┌──────────┴──────────────────┐
                            │   Backend API (Bun)         │
                            │   - CRUD semua entity       │
                            │   - Rule Engine             │
                            │   - Violation Detector      │
                            │   - Report Generator        │
                            │   - Sync manager            │
                            └──────────┬──────────────────┘
                                       │
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
   ┌──────────▼──────────┐   ┌─────────▼──────────┐   ┌───────▼────────┐
   │  Kiosk Scanner App   │   │   Admin App        │   │  Web Admin     │
   │  (Android Tablet)    │   │   (Android HP)     │   │  (Opsional)    │
   │                      │   │                    │   │                │
   │  Offline:            │   │  Online:           │   │  Full fitur    │
   │  - Scan wajah        │   │  - Dashboard       │   │  admin +       │
   │  - Check-in/out      │   │  - Manajemen       │   │  laporan       │
   │  - Match di RAM      │   │    mahasiswa       │   │                │
   │  - Simpan log        │   │  - Registrasi      │   │                │
   │  - Verifikasi        │   │    wajah           │   │                │
   │    aturan (cache)    │   │  - Approve izin    │   │                │
   │                      │   │  - Atur rules      │   │                │
   │  Online (sync):      │   │  - Jadwal kuliah   │   │                │
   │  - Download vektor   │   │  - Lihat rekap     │   │                │
   │  - Upload log        │   │  - Export laporan  │   │                │
   │  - Download rules    │   │  - Import data     │   │                │
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
       ├── GET /api/sync/rules
       │      └── Download all active campus rules (cache lokal)
       │
       ├── Update Room Database
       │      └── INSERT OR REPLACE face_vectors, rules
       │
       ├── Load semua vektor ke RAM
       │      └── In-memory FloatArray[10000][128]
       │
       └── POST /api/sync/attendance (batch upload log offline)
              └── Also upload checkin/out + violation logs
              └── Response: {synced_count, failed_ids}
```

### 4.2 Background Opportunistic Sync

Selain sync tengah malam, kiosk akan sync otomatis saat:
- Koneksi internet terdeteksi stabil (WorkManager + NetworkCallback)
- Setelah scan berhasil (upload log langsung jika ada internet)
- Setiap 30 menit jika ada data antrean

---

## 5. Data Model & Database

### 5.1 PostgreSQL (Master — via Prisma)

```prisma
// ==================== MASTER DATA ====================

model Student {
  id              String   @id @default(cuid())
  nim             String   @unique
  name            String
  studyProgram    String?
  academicYear    Int?     // angkatan (2023, 2024, ...)
  phone           String?
  email           String?
  isActive        Boolean  @default(true)
  createdAt       DateTime @default(now())
  updatedAt       DateTime @updatedAt

  faceVectors      FaceVector[]
  attendanceLogs   AttendanceLog[]
  checkInOuts      CheckInOut[]
  permits          Permit[]
  violations       Violation[]
  courseSchedules  CourseSchedule[]
  permitQuotas     PermitQuota[]
}

model FaceVector {
  id        String   @id @default(cuid())
  studentId String
  vector    Unsupported("vector(128)")? // pgvector extension
  createdAt DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])

  @@index([studentId])
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

  auditLogs    AuditLog[]
}


// ==================== ABSENSI & CHECK-IN/OUT ====================

model AttendanceLog {
  id              String   @id @default(cuid())
  studentId       String
  type            String   // "check_in" | "check_out" | "auto"
  timestamp       DateTime @default(now())
  confidenceScore Float?
  isSynced        Boolean  @default(true) // false = dari kiosk offline
  deviceId        String?
  photoCapture    String?  // base64 / URL foto saat scan (untuk audit)
  createdAt       DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])

  @@index([timestamp])
  @@index([studentId, timestamp])
}

model CheckInOut {
  id          String   @id @default(cuid())
  studentId   String
  checkInId   String?  // AttendanceLog.id untuk check-in
  checkOutId  String?  // AttendanceLog.id untuk check-out
  checkIn     DateTime
  checkOut    DateTime?
  duration    Int?     // durasi di luar kampus (menit), null = masih di luar
  isViolation Boolean  @default(false) // apakah check-out ini melanggar aturan
  deviceId    String?
  createdAt   DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])

  @@index([studentId, checkIn])
  @@index([checkIn, checkOut])
}


// ==================== PERIZINAN ====================

model Permit {
  id            String   @id @default(cuid())
  studentId     String
  type          String   // "sakit" | "dispensasi" | "organisasi" | "keluarga" | "lainnya"
  reason        String
  startDate     DateTime // tanggal mulai
  endDate       DateTime // tanggal selesai
  startTime     String?  // "08:00" format HH:mm (null = full day)
  endTime       String?  // "16:00" format HH:mm
  status        String   @default("pending") // pending | approved | rejected
  approvedBy    String?  // adminId
  rejectReason  String?
  attachmentUrl String?  // foto surat sakit / surat izin
  isActive      Boolean  @default(true)
  createdAt     DateTime @default(now())
  updatedAt     DateTime @updatedAt

  student Student @relation(fields: [studentId], references: [id])

  @@index([studentId, status])
  @@index([startDate, endDate])
}

model PermitQuota {
  id          String   @id @default(cuid())
  studentId   String
  month       Int      // 1-12
  year        Int
  permitsUsed Int      @default(0)
  maxPermits  Int      @default(5) // batas izin per bulan
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  student Student @relation(fields: [studentId], references: [id])

  @@unique([studentId, month, year])
}


// ==================== ATURAN & PEMBATASAN ====================

model CampusRule {
  id              String   @id @default(cuid())
  name            String   // "Jam Kuliah Pagi Senin"
  ruleType        String   // "restricted_hours" | "operational_hours" | "permit_limit"
  dayOfWeek       Int?     // 0=Minggu, 1=Senin ... 6=Sabtu | null = berlaku setiap hari
  startTime       String   // "08:00" format HH:mm
  endTime         String   // "16:00" format HH:mm
  isRestricted    Boolean  // true = tidak boleh keluar, false = boleh keluar
  appliesToAll    Boolean  @default(true)
  studyProgram    String?  // null = all, specific = filter prodi
  academicYear    Int?     // null = all, specific = filter angkatan
  description     String?
  priority        Int      @default(0) // makin tinggi = makin prioritas
  isActive        Boolean  @default(true)
  createdAt       DateTime @default(now())
  updatedAt       DateTime @updatedAt

  @@index([dayOfWeek, isActive])
}

model GlobalSetting {
  id          String   @id @default(cuid())
  key         String   @unique // "max_permit_hours", "grace_period_minutes", dll
  value       String
  description String?
  updatedAt   DateTime @updatedAt
}


// ==================== JADWAL KULIAH ====================

model CourseSchedule {
  id            String   @id @default(cuid())
  studentId     String
  courseName    String   // nama mata kuliah
  dayOfWeek     Int      // 0=Minggu ... 6=Sabtu
  startTime     String   // "08:00" format HH:mm
  endTime       String   // "10:00" format HH:mm
  room          String?
  lecturer      String?
  isActive      Boolean  @default(true)
  createdAt     DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])

  @@index([studentId, dayOfWeek])
}


// ==================== PELANGGARAN ====================

model Violation {
  id            String   @id @default(cuid())
  studentId     String
  type          String   // "keluar_jam_kuliah" | "keluar_jam_terlarang" | "keluar_tanpa_checkin" | "tidak_kembali"
  description   String
  timestamp     DateTime
  relatedRuleId  String? // CampusRule.id yang dilanggar
  relatedPermitId String? // Permit.id jika ada izin terkait
  isResolved    Boolean  @default(false)
  resolvedBy    String?  // adminId
  resolvedAt    DateTime?
  notes         String?
  createdAt     DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])

  @@index([studentId])
  @@index([timestamp])
  @@index([type])
}


// ==================== DEVICE & SYNC ====================

model Device {
  id           String   @id @default(cuid())
  name         String   // "Gerbang Utama", "Gerbang Belakang"
  deviceId     String   @unique // Android device ID
  location     String?  // posisi gerbang
  ipAddress    String?
  isActive     Boolean  @default(true)
  lastPingAt   DateTime?
  batteryLevel Int?
  appVersion   String?
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt
}

model SyncLog {
  id         String   @id @default(cuid())
  deviceId   String
  lastSyncAt DateTime
  status     String   // success | partial | failed
  logsCount  Int      @default(0) // jumlah log yang di-sync
  notes      String?
  createdAt  DateTime @default(now())

  @@index([deviceId, lastSyncAt])
}


// ==================== AUDIT & NOTIFIKASI ====================

model AuditLog {
  id          String   @id @default(cuid())
  adminId     String?
  action      String   // "create_student" | "approve_permit" | "import_csv" | "resolve_violation"
  entityType  String   // "Student" | "Permit" | "CampusRule" | dll
  entityId    String?
  details     String?  // JSON
  ipAddress   String?
  createdAt   DateTime @default(now())

  admin Admin @relation(fields: [adminId], references: [id])

  @@index([createdAt])
  @@index([adminId])
}

model ImportBatch {
  id          String   @id @default(cuid())
  filename    String
  totalRows   Int
  successRows Int
  failedRows  Int
  errors      String?  // JSON array
  importedBy  String?  // adminId
  createdAt   DateTime @default(now())
}

model Notification {
  id          String   @id @default(cuid())
  adminId     String?  // null = broadcast ke semua admin
  type        String   // "violation" | "permit_pending" | "sync_failed" | "device_offline"
  title       String
  message     String
  isRead      Boolean  @default(false)
  isDismissed Boolean  @default(false)
  linkTo      String?  // deep link / entity ID
  createdAt   DateTime @default(now())

  @@index([adminId, isRead])
}
```

### 5.2 Room Database (Android — Local Cache)

Tabel di SQLite lokal:

| Entity | Catatan |
|---|---|
| `StudentEntity` | Cache data mahasiswa |
| `FaceVectorEntity` | Vektor wajah (Blob/byte array via TypeConverter) |
| `AttendanceLogEntity` | Log absensi lokal (isSynced = false jika belum dikirim) |
| `CheckInOutEntity` | Cache sesi check-in/out |
| `PermitEntity` | Cache izin (untuk admin app) |
| `CampusRuleEntity` | Cache aturan untuk verifikasi offline |
| `CourseScheduleEntity` | Cache jadwal kuliah |
| `GlobalSettingEntity` | Cache pengaturan global |
| `SyncMetadata` | Tracking kapan terakhir sync |

> **Catatan**: Room tidak support `FloatArray`. Vektor 128-d disimpan sebagai `Blob` dan dikonversi via TypeConverter.

### 5.3 In-Memory Structure (Kiosk Scanner)

```kotlin
// Di-load saat app start, di-refresh setiap sync malam
data class FaceIndex(
    val embeddings: Map<String, FloatArray>,  // studentId → vector
    val studentMap: Map<String, StudentBrief> // studentId → nama/NIM
)

data class StudentBrief(
    val id: String,
    val nim: String,
    val name: String,
    val studyProgram: String?
)

// Cache aturan untuk verifikasi offline
data class RuleCache(
    val restrictedHours: List<CampusRule>,    // jam terlarang
    val operationalHours: List<CampusRule>,   // jam operasional
    val globalSettings: Map<String, String>   // settings
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
| GET | `/api/students` | List mahasiswa (pagination + search + filter prodi/angkatan) |
| GET | `/api/students/:id` | Detail mahasiswa + status terkini |
| POST | `/api/students` | Tambah mahasiswa |
| PUT | `/api/students/:id` | Update data mahasiswa |
| DELETE | `/api/students/:id` | Soft delete (nonaktifkan) |
| POST | `/api/students/:id/face` | Upload vektor wajah baru |
| POST | `/api/students/import` | Import bulk dari CSV/Excel |
| GET | `/api/students/:id/schedules` | Jadwal kuliah mahasiswa |
| GET | `/api/students/:id/permits` | Riwayat izin mahasiswa |
| GET | `/api/students/:id/violations` | Riwayat pelanggaran mahasiswa |
| GET | `/api/students/:id/today` | Status hari ini (check-in, check-out, izin) |

### 6.3 Attendance / Check-In-Out
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/attendance` | Catat kehadiran (dari kiosk) |
| POST | `/api/attendance/checkin` | Check-in mahasiswa masuk kampus |
| POST | `/api/attendance/checkout` | Check-out mahasiswa keluar kampus |
| GET | `/api/attendance` | Riwayat absensi (filter tanggal, prodi, jenis) |
| GET | `/api/attendance/today` | Ringkasan absensi hari ini |
| GET | `/api/attendance/statistics` | Statistik per program studi |
| GET | `/api/checkinout` | Riwayat check-in/out (filter tanggal, student) |
| GET | `/api/checkinout/active` | Mahasiswa yang sedang di luar kampus (check-in tanpa check-out) |

### 6.4 Permit
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/permits` | Buat pengajuan izin |
| GET | `/api/permits` | List izin (filter status, type, tanggal) |
| GET | `/api/permits/pending` | Izin pending (perlu approval) |
| GET | `/api/permits/:id` | Detail izin |
| PUT | `/api/permits/:id/approve` | Approve izin |
| PUT | `/api/permits/:id/reject` | Reject izin (dengan alasan) |
| GET | `/api/permits/quota/:studentId` | Cek kuota izin mahasiswa |

### 6.5 Campus Rules
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/rules` | List semua aturan |
| POST | `/api/rules` | Buat aturan baru |
| PUT | `/api/rules/:id` | Update aturan |
| DELETE | `/api/rules/:id` | Nonaktifkan aturan |
| GET | `/api/rules/effective?time=...&day=...` | Aturan yang berlaku di waktu tertentu |
| GET | `/api/settings` | List global settings |
| PUT | `/api/settings/:key` | Update global setting |

### 6.6 Course Schedule
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/schedules` | List semua jadwal (filter prodi, day) |
| POST | `/api/schedules/batch` | Import jadwal kuliah (CSV/JSON) |
| PUT | `/api/schedules/:id` | Update jadwal |
| DELETE | `/api/schedules/:id` | Hapus jadwal |
| GET | `/api/schedules/student/:studentId` | Jadwal spesifik mahasiswa |

### 6.7 Violation
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/violations` | List pelanggaran (filter tanggal, type, student) |
| GET | `/api/violations/:id` | Detail pelanggaran |
| PUT | `/api/violations/:id/resolve` | Tandai selesai (dengan notes) |
| GET | `/api/violations/statistics` | Statistik pelanggaran (per type, per prodi) |

### 6.8 Report / Laporan
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/reports/daily?date=...` | Rekap harian lengkap |
| GET | `/api/reports/daily/export?format=csv|pdf` | Export rekap harian |
| GET | `/api/reports/monthly?month=&year=` | Rekap bulanan |
| GET | `/api/reports/violations?from=&to=` | Laporan pelanggaran periode |
| GET | `/api/reports/permits?from=&to=` | Laporan penggunaan izin |
| GET | `/api/reports/checkinout?date=&prodi=` | Laporan check-in/out detail |
| GET | `/api/reports/outside-hours?date=` | Mahasiswa keluar di luar jam yang diizinkan |

### 6.9 Sync (khusus Kiosk Scanner)
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/sync/faces` | Download face vectors (with timestamp) |
| GET | `/api/sync/rules` | Download all active campus rules |
| GET | `/api/sync/settings` | Download global settings |
| POST | `/api/sync/attendance` | Batch upload logs (attendance + checkinout + violations) |
| POST | `/api/sync/ping` | Kiosk heartbeat (battery, status) |

### 6.10 Device
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/devices` | List semua kiosk device |
| GET | `/api/devices/:id` | Detail device |
| PUT | `/api/devices/:id` | Update device info |
| GET | `/api/devices/:id/logs` | Sync history device |

### 6.11 Dashboard
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/dashboard/summary` | Hari ini: total hadir, di luar, izin, alpha, pelanggaran |
| GET | `/api/dashboard/weekly` | Grafik absensi 7 hari terakhir |
| GET | `/api/dashboard/checkinout-summary` | Check-in/out real-time count |
| GET | `/api/dashboard/violation-summary` | Pelanggaran hari ini |
| GET | `/api/dashboard/recent-scans` | 20 scan terakhir (real-time feed) |

### 6.12 Audit
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/audit` | Log aktivitas admin (filter tanggal, admin, action) |

### 6.13 Notification
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/notifications` | List notifikasi admin |
| PUT | `/api/notifications/:id/read` | Tandai sudah dibaca |

---

## 7. Alur Data Offline-First

### 7.1 Skenario: Kiosk Check-In (Scan Masuk)

```
1. Mahasiswa berdiri di depan kamera kiosk
2. CameraX capture frame
3. TFLite detect face → loop jika tidak ada face
4. TFLite liveness check → jika gagal, minta ulang
5. TFLite extract embedding (128-d vector)
6. Compare dengan 10.000 vektor di RAM
   └── Jika match (≥ threshold):
       → Tentukan nama + NIM
       → Cek apakah sudah check-in hari ini?
           ├── Ya → tampilkan "Sudah check-in jam 07:15"
           └── Tidak → proses check-in:
               → Buat CheckInOut record (checkIn = now)
               → Buat AttendanceLog type "check_in"
               → Tampilkan "Selamat datang, Andi ✓ 07:15"
   └── Jika tidak match:
       → Tampilkan "Wajah tidak dikenal"
7. Simpan semua log ke Room (isSynced = false)
8. Background: upload saat internet stabil
```

### 7.2 Skenario: Kiosk Check-Out (Scan Keluar)

```
1. Mahasiswa scan wajah (sama seperti check-in)
2. Face match success
3. Cek apakah mahasiswa sudah check-in hari ini?
   └── Tidak → tampilkan "Anda belum check-in hari ini. Silakan scan di gerbang masuk."
   └── Ya → proses check-out:
       → Cari CheckInOut record terakhir (checkOut = null)
       → Hitung durasi di luar kampus
       → Cek Rule Engine (lokal):
           ├── Apakah jam ini termasuk restricted hours?
           │   ├── Ya → apakah ada izin yang aktif?
           │   │   ├── Ada izin → check-out NORMAL
           │   │   └── Tidak ada izin → flag isViolation = true
           │   └── Tidak → check-out NORMAL
       → Update CheckInOut (checkOut = now, duration, isViolation)
       → Buat AttendanceLog type "check_out"
       → Tampilkan "Sampai jumpa, Andi ✓ 16:00"
         (jika violation: tampilkan peringatan)
4. Jika violation, simpan juga ViolationRecord ke Room
5. Upload batch saat sync
```

### 7.3 Skenario: Auto-Detect Masuk/Keluar

Kiosk bisa menentukan mahasiswa mau masuk atau keluar dengan:
- **Mode Manual**: Admin set kiosk ke mode "Masuk" atau "Keluar" (tombol di screen)
- **Mode Otomatis**: Berdasarkan arah mahasiswa (butuh 2 kamera atau sensor jarak)
- **Mode Hybrid** (default): Jika sudah check-in hari ini dan belum check-out → otomatis anggap "Keluar". Jika belum check-in → "Masuk".

### 7.4 Skenario: Admin Registrasi Wajah (Online)

```
1. Admin buka Admin App → pilih mahasiswa (import atau input manual)
2. Kamera capture wajah (dengan bimbingan framing oval)
3. TFLite extract embedding (localhost)
4. Cek kualitas: brightness, blur, face angle
5. POST /api/students/:id/face { vector: [...] }
6. Backend simpan ke PostgreSQL (pgvector)
7. Malam hari: Kiosk sync → download vektor baru → update RAM
```

### 7.5 Conflict Resolution

| Skenario | Resolusi |
|---|---|
| Check-in ganda di hari sama | Backend dedup: update check-in terakhir, tolak yang baru |
| Check-out tanpa check-in | Anggap sebagai "check-in + check-out" dalam 1 menit (abnormal) |
| Log absensi duplikat | Dedup berdasarkan `studentId + date_trunc('minute', timestamp)` |
| Violation sudah ada | Cek uniqueness constraint `studentId + timestamp + type` |
| Update data mahasiswa bentrok | Last-write-wins (updatedAt) |
| Sync gagal (server down) | Retry exponential backoff (30s → 5m → 30m) |

---

## 8. Rule Engine: Aturan & Pembatasan

### 8.1 Jenis Aturan

Admin bisa mengkonfigurasi berbagai jenis aturan:

| Aturan | Contoh | Efek |
|---|---|---|
| **Jam Operasional** | 06:00 - 18:00 | Di luar jam ini, scan tidak valid/tidak diproses |
| **Jam Terlarang Keluar** | Senin-Kamis 08:00-12:00 | Mahasiswa tidak boleh check-out di jam ini (kecuali ada izin) |
| **Jam Terlarang per Prodi** | Jumat 08:00-11:00 (Prodi: TI) | Hanya berlaku untuk prodi tertentu |
| **Jam Terlarang per Angkatan** | 2024: 07:00-16:00 | Hanya untuk angkatan tertentu |
| **Hari Libur Nasional** | 17 Agustus | Semua aturan libur, scan opsional |
| **Periode Ujian** | 10-20 Juni 2026 | Aturan khusus selama periode ujian |

### 8.2 Global Settings yang Bisa Dikonfigurasi

| Key | Default | Deskripsi |
|---|---|---|
| `operational_start` | `06:00` | Jam buka kampus |
| `operational_end` | `18:00` | Jam tutup kampus |
| `max_permit_hours_per_day` | `4` | Maks durasi izin per hari (jam) |
| `max_permit_per_month` | `5` | Maks jumlah izin per bulan |
| `grace_period_minutes` | `15` | Toleransi keterlambatan scan |
| `auto_checkout_hour` | `17:00` | Auto check-out jika lupa scan pulang |
| `violation_threshold` | `3` | Jumlah pelanggaran sebelum notifikasi khusus |

### 8.3 Logika Evaluasi Aturan

```
Function canCheckOut(student, time):
  1. Cek jam operasional:
     - Jika time < operational_start → TOLAK (kampus belum buka)
     - Jika time > operational_end → Boleh (pulang lembur)
  
  2. Cek apakah ada izin aktif hari ini:
     - Jika ada izin → CHECK-OUT DIIZINKAN (lewat semua rule)
  
  3. Cek restricted hours:
     - Cari semua CampusRule dengan:
       dayOfWeek = today && startTime <= time <= endTime && isRestricted = true
     - Filter: appliesToAll = true ATAU prodi cocok ATAU angkatan cocok
     - Jika ada yang cocok → CHECK-OUT DITOLAK (flag violation)
     - Jika tidak ada → CHECK-OUT DIIZINKAN
  
  4. Cek jadwal kuliah:
     - Cari CourseSchedule mahasiswa di hari & jam ini
     - Jika ada jadwal → CHECK-OUT = VIOLATION (kecuali izin)
```

### 8.4 UI Konfigurasi Aturan (Admin App)

Admin dapat:
- **Tambah aturan baru**: Pilih tipe, hari, jam mulai-selesai, filter prodi/angkatan
- **Atur jam operasional**: Set kapan kampus buka/tutup
- **Atur batas izin**: Maks jam per hari, maks izin per bulan
- **Atur periode khusus**: Tanggal-tanggal tertentu dengan aturan berbeda
- **Aktif/nonaktifkan aturan**: Toggle switch
- **Lihat aturan yang berlaku**: Preview berdasarkan hari & jam

### 8.5 Kiosk Cache Rules

Kiosk mendownload aturan aktif saat sync malam dan menyimpannya di Room. Saat scan keluar, kiosk bisa mengecek aturan secara offline tanpa perlu internet. Jika aturan berubah di tengah hari, kiosk akan update saat sync opportunistic.

---

## 9. Sistem Check-In / Check-Out

### 9.1 Alur Lengkap Check-In/Out

```
WAKTU: 07:15
─── Mahasiswa scan di gerbang masuk ───
→ Face match: ANDI (TI, 2024)
→ Belum ada check-in hari ini
→ Buat CheckInOut { checkIn: 07:15 }
→ Status: "DI KAMPUS"
→ Tampilkan: "Selamat datang, Andi! ✓"

WAKTU: 10:00
─── Mahasiswa scan di gerbang keluar ───
→ Face match: ANDI
→ Ada check-in aktif (07:15, belum check-out)
→ Cek rule: jam 10:00 termasuk restricted hours?
   → Senin 08:00-12:00 = restricted ✓
   → Cek izin: tidak ada izin hari ini
   → VIOLATION: "Keluar jam terlarang"
→ Buat CheckOut { checkOut: 10:00, duration: 165 menit, isViolation: true }
→ Buat Violation record
→ Status: "DI LUAR KAMPUS (violation)"
→ Tampilkan: "⚠️ Andi, Anda keluar di jam terlarang!"

WAKTU: 12:30
─── Mahasiswa scan di gerbang masuk ───
→ Face match: ANDI
→ Ada check-in sebelumnya (complete)
→ Buat CheckInOut baru { checkIn: 12:30 }
→ Status: "DI KAMPUS"

WAKTU: 16:30
─── Mahasiswa scan di gerbang keluar ───
→ Face match: ANDI
→ Check-out normal (16:30 tidak restricted)
→ Buat CheckOut { checkOut: 16:30, duration: 240 menit, isViolation: false }
→ Status: "SUDAH PULANG"
→ Tampilkan: "Sampai jumpa, Andi! ✓"
```

### 9.2 Status Mahasiswa Real-Time

Admin dapat melihat status mahasiswa kapan saja:

| Status | Arti |
|---|---|
| `DI KAMPUS` | Sudah check-in, belum check-out |
| `DI LUAR KAMPUS` | Sudah check-out, belum check-in lagi |
| `DI LUAR (IZIN)` | Check-out dengan izin aktif |
| `DI LUAR (VIOLATION)` | Check-out melanggar aturan |
| `BELUM MASUK` | Belum check-in hari ini |
| `ALPHA` | Tidak check-in sama sekali hari ini (setelah jam 10:00) |
| `LIBUR` | Hari libur / tidak ada jadwal |

### 9.3 Auto Check-Out (Force Check-Out)

Jika mahasiswa lupa scan pulang (check-out), sistem akan:
1. **Auto check-out pukul 17:00** (configurable via GlobalSetting)
   - Set checkout = 17:00
   - Set duration = 0 (tidak valid untuk dihitung)
   - Flag isAuto = true
2. **Jika masih di luar pukul 06:00 keesokan hari** tanpa check-in:
   - Auto complete sesi sebelumnya
3. **Report**: Admin bisa lihat siapa saja yang auto check-out (lupa scan)

### 9.4 Multiple Scan dalam Sehari

Mahasiswa bisa check-in/out berkali-kali dalam sehari:
- Masuk pagi → check-in 07:00
- Keluar siang → check-out 12:00 (sesi 1: 5 jam)
- Masuk siang → check-in 13:00
- Keluar sore → check-out 17:00 (sesi 2: 4 jam)
- Total durasi di luar = 5 + 4 = 9 jam (terlacak semua)

---

## 10. Sistem Pelanggaran

### 10.1 Jenis Pelanggaran

| Tipe | Deskripsi | Deteksi |
|---|---|---|
| `keluar_jam_terlarang` | Check-out di jam restricted tanpa izin | Otomatis saat check-out |
| `keluar_jam_kuliah` | Check-out saat ada jadwal kuliah | Otomatis (cocokkan jadwal) |
| `tidak_kembali` | Check-out tapi tidak check-in sampai batas waktu | Auto-deteksi pukul 18:00 |
| `keluar_tanpa_checkin` | Check-out tanpa pernah check-in hari itu | Otomatis saat check-out |
| `melebihi_batas_izin` | Durasi di luar melebihi izin yang diberikan | Saat check-out + pengecekan permit |
| `alpha` | Tidak pernah check-in seharian | Auto-deteksi pukul 10:00 |

### 10.2 Alur Penanganan Pelanggaran

```
1. Violation terdeteksi (otomatis oleh sistem)
2. Record disimpan ke database
3. Admin mendapat notifikasi (via Admin App)
4. Admin buka ViolationScreen → lihat daftar pelanggaran
5. Admin bisa:
   - Klik detail: lihat nama, jam, aturan yang dilanggar
   - "Resolve" dengan notes (misal: "Sudah ditegur")
   - "Ignore" jika dianggap tidak valid
6. Mahasiswa dengan >3 pelanggaran (threshold) mendapat status khusus
7. Rekap pelanggaran masuk ke laporan harian admin
```

### 10.3 Tindak Lanjut Pelanggaran

| Tindakan | Deskripsi |
|---|---|
| `Resolved - Warning` | Pelanggaran dicatat, sudah diberi peringatan |
| `Resolved - Sanksi` | Pelanggaran berat, ada sanksi akademik |
| `Dismissed` | False positive (salah deteksi) |
| `Unresolved` | Belum ditindaklanjuti |

---

## 11. Sistem Izin & Kuota

### 11.1 Tipe Izin

| Tipe | Deskripsi | Perlu Dokumen |
|---|---|---|
| `sakit` | Izin sakit | Surat dokter (opsional) |
| `dispensasi` | Dispensasi akademik | Surat resmi |
| `organisasi` | Kegiatan organisasi/UKM | Surat tugas |
| `keluarga` | Urusan keluarga | - |
| `lainnya` | Alasan lain | - |

### 11.2 Alur Pengajuan & Approval Izin

```
1. Admin input izin untuk mahasiswa (via Admin App)
   Atau mahasiswa mengajukan via admin (admin sebagai perantara)
2. Pilih tipe izin, tanggal, jam (start-end), alasan
3. Upload lampiran (foto surat, dll) — opsional
4. Cek kuota: sudah berapa izin bulan ini?
   ├── Masih ada kuota → lanjut
   └── Kuota penuh → peringatan, butuh approve superadmin
5. Status: PENDING
6. Admin lain (atau superadmin) approve/reject
7. Jika approved → izin aktif
8. Saat mahasiswa check-out di jam restricted:
   Sistem cek apakah ada izin yang mencakup jam ini
   ├── Ada izin valid → check-out normal (tidak kena violation)
   └── Tidak ada → violation
```

### 11.3 Aturan Durasi Izin

| Pengaturan | Default | Dapat Diubah Admin |
|---|---|---|
| Maks durasi per izin | 4 jam | Ya (global setting) |
| Maks izin per bulan | 5 kali | Ya (global setting) |
| Maks izin berturut-turut | 3 hari | Ya (global setting) |
| Minimal waktu pengajuan | H-1 | Ya (global setting) |

### 11.4 Kuota Izin per Mahasiswa

Admin bisa melihat dan mengatur kuota per mahasiswa:
- **Default**: 5 izin/bulan (dari global setting)
- **Override per mahasiswa**: Admin bisa set kuota khusus (misal: mahasiswa sakit kronis dapat 10 izin/bulan)
- **Reset**: Kuota reset setiap awal bulan
- **Sisa kuota**: Tampil di detail mahasiswa

---

## 12. Laporan & Rekapan

### 12.1 Jenis Laporan

#### A. Rekap Harian (Daily Report)
```
Rekapan Harian - Senin, 20 Juni 2026
Prodi: Teknik Informatika | Angkatan: 2024

┌─────┬──────────┬──────┬─────────┬──────────┬──────────┬──────────────────────┐
│ No  │ Nama     │ NIM  │ Masuk   │ Keluar   │ Durasi   │ Status               │
├─────┼──────────┼──────┼─────────┼──────────┼──────────┼──────────────────────┤
│  1  │ Andi     │ 24001│ 07:15   │ 10:00*   │ 2j 45m   │ ⚠️ Violasi: keluar   │
│     │          │      │ 12:30   │ 16:30    │          │ jam terlarang        │
│  2  │ Budi     │ 24002│ 08:00   │ 09:00    │ 1j 0m    │ ✅ Izin Dispensasi    │
│     │          │      │ 12:30   │ 16:45    │          │                      │
│  3  │ Cici     │ 24003│ -       │ -        │ -        │ ❌ Alpha              │
│  4  │ Dedi     │ 24004│ 07:30   │ 16:00    │ -        │ ✅ Normal             │
│  5  │ Eko      │ 24005│ 08:15   │ -        │ -        │ 🔴 Di luar (violasi) │
└─────┴──────────┴──────┴─────────┴──────────┴──────────┴──────────────────────┘
* = tercatat sebagai pelanggaran

Ringkasan:
  Hadir   : 4 dari 5 mahasiswa
  Alpha   : 1 mahasiswa
  Izin    : 1 mahasiswa
  Violasi : 2 mahasiswa
```

#### B. Laporan Violasi / Keluar di Luar Jam yang Diizinkan
```
Laporan Pelanggaran - Periode: 17-21 Juni 2026

┌─────┬──────────┬──────────┬──────────┬──────────────────┬──────────────┐
│ No  │ Nama     │ NIM      │ Tanggal  │ Jam Keluar       │ Status       │
├─────┼──────────┼──────────┼──────────┼──────────────────┼──────────────┤
│  1  │ Andi     │ 24001    │ 20 Jun   │ 10:00 - 12:30    │ Belum ditindak│
│  2  │ Farah    │ 24015    │ 19 Jun   │ 09:30 - 11:00    │ Sudah ditegur│
│  3  │ Gilang   │ 24022    │ 17 Jun   │ 14:00 - 16:00    │ Belum ditindak│
└─────┴──────────┴──────────┴──────────┴──────────────────┴──────────────┘

Total pelanggaran: 3
Rata-rata per hari: 1
```

#### C. Laporan Check-In/Out Detail
```
Laporan Check-In/Out - 20 Juni 2026 - Prodi: Teknik Informatika

┌─────┬──────────┬──────┬──────────┬───────────────────────────────────┐
│ No  │ Nama     │ NIM  │ Sesi     │ Kegiatan                         │
├─────┼──────────┼──────┼──────────┼───────────────────────────────────┤
│  1  │ Andi     │ 24001│ 1        │ 07:15 masuk → 10:00 keluar       │
│     │          │      │          │ (2j 45m di luar - VIOLASI)       │
│     │          │      │ 2        │ 12:30 masuk → 16:30 keluar       │
│     │          │      │          │ (4j 0m di kampus)                │
│  2  │ Budi     │ 24002│ 1        │ 08:00 masuk → 09:00 keluar (izin)│
│     │          │      │          │ (1j 0m di luar - DISPENSASI)     │
│     │          │      │ 2        │ 12:30 masuk → 16:45 keluar       │
└─────┴──────────┴──────┴──────────┴───────────────────────────────────┘
```

#### D. Rekap Kehadiran per Program Studi
```
Rekap Bulanan - Juni 2026 - per Program Studi

┌──────────────────┬────────┬────────┬────────┬──────────┬──────────────┐
│ Program Studi    │ Hadir  │ Alpha  │ Izin   │ Violasi  │ Total Mhs   │
├──────────────────┼────────┼────────┼────────┼──────────┼──────────────┤
│ TI               │ 92%    │ 3%     │ 4%     │ 1%       │ 250          │
│ SI               │ 88%    │ 5%     │ 5%     │ 2%       │ 180          │
│ DK               │ 95%    │ 2%     │ 2%     │ 1%       │ 120          │
│ MI               │ 85%    │ 7%     │ 6%     │ 2%       │ 100          │
└──────────────────┴────────┴────────┴────────┴──────────┴──────────────┘
```

### 12.2 Export Laporan

| Format | Fitur |
|---|---|
| **CSV** | Bisa dibuka di Excel/Google Sheets |
| **PDF** | Siap cetak, dengan kop surat kampus |
| **In-App Preview** | Lihat langsung di Admin App sebelum export |

### 12.3 Filter Laporan

Semua laporan bisa difilter berdasarkan:
- Rentang tanggal (dari - sampai)
- Program studi
- Angkatan
- Status kehadiran (hadir, alpha, izin)
- Tipe pelanggaran
- Per mahasiswa (cari NIM/nama)

---

## 13. Android Module Breakdown

### 13.1 `:core` (Shared Library)

```
core/
├── src/main/kotlin/.../core/
│   ├── di/                           # Hilt modules
│   │   ├── NetworkModule.kt
│   │   ├── DatabaseModule.kt
│   │   └── FaceModule.kt
│   │
│   ├── face/
│   │   ├── FaceDetector.kt           # TFLite face detection
│   │   ├── FaceEmbedder.kt           # TFLite embedding extraction
│   │   ├── FaceMatcher.kt            # Cosine similarity brute-force
│   │   ├── LivenessDetector.kt       # Blink / motion detection
│   │   └── FaceIndex.kt              # In-memory index (data class)
│   │
│   ├── database/
│   │   ├── AppDatabase.kt            # Room database
│   │   ├── entity/                   # Room entities
│   │   ├── dao/                      # DAOs
│   │   └── converter/                # TypeConverters (FloatArray ↔ Blob)
│   │
│   ├── network/
│   │   ├── ApiClient.kt              # Retrofit instance
│   │   ├── ApiService.kt             # Interface endpoints
│   │   ├── dto/                      # Data transfer objects
│   │   └── interceptor/              # Auth interceptor (JWT)
│   │
│   ├── sync/
│   │   ├── FaceSyncWorker.kt         # WorkManager: download vectors
│   │   ├── AttendanceSyncWorker.kt   # WorkManager: upload logs
│   │   ├── RulesSyncWorker.kt        # WorkManager: download rules
│   │   └── SyncManager.kt            # Orchestrator
│   │
│   └── model/
│       ├── Student.kt
│       ├── Attendance.kt
│       ├── CheckInOut.kt
│       ├── Permit.kt
│       ├── CampusRule.kt
│       ├── Violation.kt
│       └── CourseSchedule.kt
```

### 13.2 `:kiosk-scanner` (Aplikasi Scanner)

```
kiosk-scanner/
├── src/main/kotlin/.../scanner/
│   ├── ScannerApp.kt                 # Application class + Hilt
│   ├── MainActivity.kt
│   │
│   ├── camera/
│   │   ├── CameraManager.kt          # CameraX lifecycle wrapper
│   │   ├── FrameAnalyzer.kt          # ImageAnalysis.Analyzer
│   │   └── PreviewView.kt            # Compose wrapper
│   │
│   ├── matching/
│   │   ├── MatchEngine.kt            # Orchestrator: detect → embed → match
│   │   ├── MatchResult.kt            # Sealed class (matched / unknown / error)
│   │   └── ScanTypeDetector.kt       # Deteksi masuk/keluar berdasarkan state
│   │
│   ├── rule/
│   │   ├── RuleChecker.kt            # Evaluasi aturan offline
│   │   └── RuleCache.kt              # Cache aturan dari sync
│   │
│   ├── scanner_state/
│   │   ├── ScannerState.kt           # State machine (idle, scanning, processing)
│   │   └── SessionManager.kt         # Track siapa yang sudah check-in hari ini
│   │
│   ├── ui/
│   │   ├── ScannerScreen.kt          # Main scanner composable
│   │   ├── ResultOverlay.kt          # Overlay nama + status
│   │   ├── ModeToggle.kt             # Tombol ganti mode masuk/keluar
│   │   └── StatusBar.kt              # Sync status, clock, mode
│   │
│   └── service/
│       └── KioskForegroundService.kt # Keep app alive + prevent sleep
```

### 13.3 `:admin-app` (Aplikasi Admin)

```
admin-app/
├── src/main/kotlin/.../admin/
│   ├── AdminApp.kt                   # Application class + Hilt
│   ├── MainActivity.kt               # Navigation host
│   │
│   ├── auth/
│   │   ├── LoginScreen.kt
│   │   └── AuthViewModel.kt
│   │
│   ├── dashboard/
│   │   ├── DashboardScreen.kt
│   │   ├── DashboardViewModel.kt
│   │   └── components/
│   │       ├── StatCard.kt
│   │       ├── RecentScansList.kt
│   │       └── ViolationAlert.kt
│   │
│   ├── student/
│   │   ├── StudentListScreen.kt
│   │   ├── StudentDetailScreen.kt
│   │   ├── StudentFormScreen.kt
│   │   └── StudentViewModel.kt
│   │
│   ├── register/
│   │   ├── FaceRegisterScreen.kt     # Kamera + panduan framing
│   │   └── RegisterViewModel.kt
│   │
│   ├── permit/
│   │   ├── PermitListScreen.kt
│   │   ├── PermitDetailScreen.kt
│   │   ├── PermitFormScreen.kt       # Buat izin baru
│   │   └── PermitViewModel.kt
│   │
│   ├── rules/
│   │   ├── RulesListScreen.kt
│   │   ├── RuleFormScreen.kt         # Tambah/edit aturan
│   │   ├── GlobalSettingsScreen.kt   # Atur global settings
│   │   └── RulesViewModel.kt
│   │
│   ├── schedule/
│   │   ├── ScheduleListScreen.kt
│   │   ├── ScheduleImportScreen.kt   # Import jadwal CSV
│   │   └── ScheduleViewModel.kt
│   │
│   ├── checkinout/
│   │   ├── CheckInOutScreen.kt       # Rekap check-in/out hari ini
│   │   ├── ActiveOutsideScreen.kt    # Mahasiswa yang sedang di luar
│   │   └── CheckInOutViewModel.kt
│   │
│   ├── violation/
│   │   ├── ViolationListScreen.kt
│   │   ├── ViolationDetailScreen.kt
│   │   └── ViolationViewModel.kt
│   │
│   ├── report/
│   │   ├── ReportScreen.kt           # Pilih jenis laporan + filter
│   │   ├── DailyReportScreen.kt
│   │   ├── ViolationReportScreen.kt
│   │   ├── AttendanceReportScreen.kt
│   │   ├── ReportPreviewScreen.kt    # Preview sebelum export
│   │   └── ReportViewModel.kt
│   │
│   ├── device/
│   │   ├── DeviceListScreen.kt
│   │   ├── DeviceDetailScreen.kt
│   │   └── DeviceViewModel.kt
│   │
│   ├── notification/
│   │   ├── NotificationScreen.kt
│   │   └── NotificationViewModel.kt
│   │
│   └── import/
│       ├── ImportScreen.kt           # Import CSV/Excel mahasiswa
│       ├── ImportPreviewScreen.kt    # Preview data sebelum import
│       └── ImportViewModel.kt
```

---

## 14. UI Screen Map

### 14.1 Kiosk Scanner

| Screen | Deskripsi |
|---|---|
| `ScannerScreen` | Layar penuh kamera, overlay tipis. Otomatis scan. Indikator mode (Masuk/Keluar) di atas. Hasil scan muncul sebagai overlay animasi. |
| `ResultOverlay` | Animasi hasil scan. Hijau = sukses, Merah = tidak dikenal, Kuning = peringatan (violation/sudah check-in). Menampilkan nama, NIM, jam. |

**Mode Toggle Screen** (opsional):
| Screen | Deskripsi |
|---|---|
| `ModeSelector` | Layar admin (butuh PIN) untuk ganti mode scanner antara "Check-In" / "Check-Out" / "Auto". |

### 14.2 Admin App

| Screen | Deskripsi |
|---|---|
| `LoginScreen` | Username + password admin |
| `DashboardScreen` | Statistik real-time: total hadir, di luar, izin, alpha, pelanggaran hari ini + feed scan terakhir |
| `StudentListScreen` | Search + filter (prodi, angkatan) + pull-to-refresh |
| `StudentDetailScreen` | Data diri + status terkini (di kampus/luar) + tabel riwayat check-in/out + pelanggaran |
| `StudentFormScreen` | Tambah/edit data mahasiswa |
| `FaceRegisterScreen` | Kamera dengan panduan oval untuk foto wajah |
| `PermitListScreen` | Tab: Pending, Approved, Rejected, All |
| `PermitDetailScreen` | Detail izin + tombol approve/reject + alasan reject |
| `PermitFormScreen` | Buat izin baru: pilih mahasiswa, tipe, tanggal, jam, alasan, upload lampiran |
| `RulesListScreen` | Daftar aturan aktif/nonaktif + toggle |
| `RuleFormScreen` | Tambah/edit aturan: nama, hari, jam, filter prodi/angkatan, tipe |
| `GlobalSettingsScreen` | Setting: jam operasional, batas izin, grace period, dll |
| `ScheduleListScreen` | Daftar jadwal kuliah per mahasiswa/prodi |
| `ScheduleImportScreen` | Pilih file CSV jadwal, mapping kolom, import |
| `CheckInOutScreen` | Tabel check-in/out hari ini, filter prodi, search nama |
| `ActiveOutsideScreen` | List mahasiswa yang sedang di luar kampus (belum check-in lagi) |
| `ViolationListScreen` | Daftar pelanggaran, filter tipe/tanggal, sortir |
| `ViolationDetailScreen` | Detail pelanggaran + tombol resolve/ignore + notes |
| `ReportScreen` | Pilih jenis laporan, filter tanggal/prodi/angkatan |
| `DailyReportScreen` | Preview rekap harian + tombol export CSV/PDF |
| `ViolationReportScreen` | Laporan pelanggaran periode + export |
| `AttendanceReportScreen` | Laporan kehadiran per prodi/bulanan + export |
| `ReportPreviewScreen` | Preview laporan sebelum export |
| `DeviceListScreen` | Daftar kiosk device + status (online/offline, baterai) |
| `DeviceDetailScreen` | Detail device + riwayat sync + log |
| `ImportScreen` | Pilih file CSV/Excel, mapping kolom, preview, import |
| `ImportPreviewScreen` | Preview data sebelum import, validasi error |
| `NotificationScreen` | List notifikasi (violation, permit pending, device offline) |

---

## 15. Hardware Requirement

### 15.1 Kiosk Scanner (Tablet/HP)

| Komponen | Minimal | Rekomendasi |
|---|---|---|
| OS | Android 12 (API 31) | Android 13+ |
| RAM | 4 GB | 6 GB+ |
| Kamera Depan | 5 MP | 8 MP+ dengan autofocus |
| CPU | Octa-core 2.0 GHz | Snapdragon 7xx+ / MediaTek G series |
| Storage | 32 GB | 64 GB+ |
| Layar | 8 inci | 10 inci (agar mahasiswa lihat hasil) |
| Baterai | — | Selalu terhubung charger |
| Konektivitas | Wi-Fi | Wi-Fi + Cellular (backup) |

### 15.2 Posisi Pemasangan Kiosk

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

### 15.3 Server

| Komponen | Minimal | Rekomendasi |
|---|---|---|
| CPU | 2 core | 4 core |
| RAM | 4 GB | 8 GB |
| Storage | 50 GB SSD | 100 GB SSD |
| OS | Linux (Ubuntu 22.04+) | Linux |
| Docker | Yes | Yes |

---

## 16. Phase / Milestone Pengembangan

### Phase 1: Foundation + Core Pipeline (Prioritas Tertinggi)

**Tujuan**: Backend + Kiosk Scanner bisa scan offline, check-in/out, dan sync.

| Task | Estimasi |
|---|---|
| Setup monorepo + Gradle + version catalog | 1 hari |
| Setup backend (Bun + Prisma + PostgreSQL + pgvector + Docker) | 2 hari |
| Database schema + migration + seed | 1 hari |
| API endpoints: CRUD students, attendance, sync | 2 hari |
| `:core` — Room database + TypeConverters | 1 hari |
| `:core` — TFLite face embedder + liveness detection | 2 hari |
| `:core` — Network layer (Retrofit) | 1 hari |
| `:kiosk-scanner` — CameraX + frame analyzer | 2 hari |
| `:kiosk-scanner` — Face matching engine + result overlay | 2 hari |
| `:kiosk-scanner` — Check-in/out state management | 1 hari |
| `:kiosk-scanner` — WorkManager sync tengah malam | 1 hari |
| **Total Phase 1** | **~16 hari** |

### Phase 2: Admin App Essential (Prioritas Sedang)

**Tujuan**: Admin bisa daftarin mahasiswa, approve izin, atur rules.

| Task | Estimasi |
|---|---|
| `:admin-app` — Auth + login screen | 1 hari |
| `:admin-app` — Dashboard screen | 1 hari |
| `:admin-app` — Student list + form + detail | 2 hari |
| `:admin-app` — Face registration dengan kamera | 2 hari |
| `:admin-app` — Import CSV/Excel | 1 hari |
| `:admin-app` — Permit list + form + approve/reject | 2 hari |
| `:admin-app` — Rules management (CRUD) | 2 hari |
| `:admin-app` — Global settings screen | 1 hari |
| API: permit CRUD, rules CRUD, import, settings | 2 hari |
| **Total Phase 2** | **~14 hari** |

### Phase 3: Check-In/Out + Violation + Report (Prioritas Sedang)

**Tujuan**: Sistem check-in/out lengkap dengan deteksi pelanggaran dan laporan.

| Task | Estimasi |
|---|---|
| API: check-in/out endpoints + violation detection | 2 hari |
| API: report generators (daily, monthly, violation) | 2 hari |
| `:kiosk-scanner` — Rule checker (offline) | 1 hari |
| `:kiosk-scanner` — Scan type detector (masuk/keluar) | 1 hari |
| `:admin-app` — Check-in/out monitoring screen | 1 hari |
| `:admin-app` — Violation list + detail + resolve | 2 hari |
| `:admin-app` — Report screens (daily, violation, export) | 3 hari |
| `:admin-app` — Active outside screen | 1 hari |
| **Total Phase 3** | **~13 hari** |

### Phase 4: Advanced Features (Prioritas Rendah)

**Tujuan**: Jadwal kuliah, device management, notifikasi, audit.

| Task | Estimasi |
|---|---|
| API: course schedule CRUD + import | 1 hari |
| API: device management + ping | 1 hari |
| API: notification + audit log | 1 hari |
| `:admin-app` — Schedule management + import | 2 hari |
| `:admin-app` — Device list + detail | 1 hari |
| `:admin-app` — Notification screen | 1 hari |
| `:admin-app` — Schedule-based violation detection | 1 hari |
| **Total Phase 4** | **~8 hari** |

### Phase 5: Polishing & Production (Prioritas Rendah)

**Tujuan**: Siap dipake beneran.

| Task | Estimasi |
|---|---|
| Error handling + edge cases all screens | 2 hari |
| Loading state + empty state + error state | 2 hari |
| Logging + crash reporting (Firebase Crashlytics) | 1 hari |
| Testing (manual + edge case) | 3 hari |
| Performance tuning (10k vectors) | 1 hari |
| Dokumentasi pengguna | 1 hari |
| Deployment guide + docker compose final | 1 hari |
| **Total Phase 5** | **~11 hari** |

---

## 17. Open Questions

Pertanyaan yang perlu dijawab sebelum/selama development:

### Model & AI
1. **Model TFLite**: MobileFaceNet atau model lain? Sumber model pre-trained?
2. **Liveness Detection**: Model blinking atau texture-based (faspe)?
3. **Face Vector Dimensi**: 128-d (cepat) atau 512-d (akurat)?
4. **Threshold**: Default 0.6? Perlu tuning dengan dataset wajah Indonesia?

### Backend
5. **Backend Framework**: Elysia vs Hono? Dua-duanya cocok Bun.
6. **PDF Library**: Puppeteer (butuh Chrome) atau PDFKit (pure JS)?

### Data & Import
7. **CSV Import Format**: Template kolom: NIM, Nama, Prodi, Angkatan, No HP, Email?
8. **Jadwal Kuliah Format**: Template kolom: NIM, Mata Kuliah, Hari, Jam Mulai, Jam Selesai, Ruang?

### Operational
9. **Dashboard Web**: Butuh web dashboard atau cukup dari Admin App?
10. **Multiple Kiosk**: Awalnya 1 kiosk atau langsung support multi-gerbang?
11. **Emergency Mode**: Tombol darurat untuk buka semua gerbang & nonaktifkan aturan?

---

> **Status**: Planning — belum ada coding.
> **Next step**: Kalau dokumen ini sudah OK, lanjut ke setup monorepo + Phase 1.
