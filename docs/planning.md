# FaceGateApp — Planning Document

> Proyek **izin keluar-masuk kampus** berbasis Face Recognition untuk **pondok pesantren / asrama kampus**
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
9. [Sistem Scan Toggle (Keluar / Kembali)](#9-sistem-scan-toggle-keluar--kembali)
10. [Sistem Pelanggaran](#10-sistem-pelanggaran)
11. [Sistem Izin: Harian & Pengajuan](#11-sistem-izin-harian--pengajuan)
12. [Laporan & Rekapan](#12-laporan--rekapan)
13. [Android Module Breakdown](#13-android-module-breakdown)
14. [UI Screen Map](#14-ui-screen-map)
15. [Hardware Requirement](#15-hardware-requirement)
16. [Phase / Milestone Pengembangan](#16-phase--milestone-pengembangan)
17. [Open Questions](#17-open-questions)

---

## 1. Ringkasan Proyek

### 1.1 Visi
Sistem **izin keluar-masuk kampus** berbasis pengenalan wajah untuk **pondok pesantren / asrama kampus**. Mahasiswa tinggal di dalam kampus, sistem digunakan untuk:
- Mencatat siapa yang keluar kampus, jam berapa, dan kembali jam berapa
- Memvalidasi apakah keluar dengan izin atau tidak
- Aturan jam terlarang keluar (jam kuliah, jam malam, dll)
- Laporan pelanggaran keluar tanpa izin

**Tidak ada konsep absensi/alpha** — karena mahasiswa memang sudah berada di kampus. Scan hanya untuk pergerakan keluar-masuk gerbang.

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
| **Kiosk Scanner** | Tablet/HP statis di gerbang | Scan wajah toggle keluar/kembali, verifikasi offline, simpan log |
| **Admin App** | HP pegangan admin | Registrasi wajah, kelola izin harian & pengajuan, atur jam terlarang keluar, monitoring siapa di luar, rekap + trigger sync |
| **Backend API** | Server (Docker) | Master data, rule engine, violation detector, laporan |

---

## 2. Struktur Monorepo

```
FaceGateApp/
├── android/
│   ├── core/                         # :core — shared library
│   │   ├── src/main/kotlin/.../core/
│   │   │   ├── face/                 # TFLite wrapper
│   │   │   ├── network/             # Retrofit
│   │   │   ├── database/            # Room
│   │   │   ├── sync/                # WorkManager sync
│   │   │   └── model/               # Domain model
│   │   └── build.gradle.kts
│   │
│   ├── kiosk-scanner/                # :kiosk-scanner
│   │   ├── src/main/
│   │   │   ├── java/.../scanner/
│   │   │   │   ├── camera/
│   │   │   │   ├── matching/
│   │   │   │   ├── toggle/          # Toggle state management
│   │   │   │   ├── rule/
│   │   │   │   ├── ui/
│   │   │   │   └── service/
│   │   │   └── res/
│   │   └── build.gradle.kts
│   │
│   ├── admin-app/                    # :admin-app
│   │   ├── src/main/
│   │   │   ├── java/.../admin/
│   │   │   │   ├── auth/
│   │   │   │   ├── dashboard/
│   │   │   │   ├── student/
│   │   │   │   ├── register/
│   │   │   │   ├── permit/          # Izin harian + pengajuan
│   │   │   │   ├── monitor/         # Monitoring toggle status
│   │   │   │   ├── rules/
│   │   │   │   ├── violation/
│   │   │   │   ├── report/
│   │   │   │   ├── sync/            # Trigger sync manual
│   │   │   │   ├── device/
│   │   │   │   └── import/
│   │   │   └── res/
│   │   └── build.gradle.kts
│   │
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle/libs.versions.toml
│
├── backend/
│   ├── src/
│   │   ├── index.ts
│   │   ├── routes/
│   │   ├── controllers/
│   │   ├── services/
│   │   │   ├── rule-engine.ts
│   │   │   ├── violation-detector.ts
│   │   │   ├── report-generator.ts
│   │   │   └── sync-service.ts      # Kelola sync request
│   │   ├── middleware/
│   │   └── utils/
│   ├── prisma/
│   │   ├── schema.prisma
│   │   └── seed.ts
│   ├── docker/
│   │   └── Dockerfile
│   ├── package.json
│   └── tsconfig.json
│
├── docker-compose.yml
├── docs/
│   ├── planning.md
│   └── api-spec.md
└── README.md
```

### 2.1 Dependency

```
:kiosk-scanner ──→ :core
:admin-app     ──→ :core
```

---

## 3. Tech Stack Detail

### 3.1 Android

| Komponen | Pustaka | Versi |
|---|---|---|
| Bahasa | Kotlin | 2.0.x |
| UI | Jetpack Compose + Material 3 | |
| Kamera | CameraX | 1.4.x |
| Face Embedding | TensorFlow Lite (MobileFaceNet) | |
| Database Lokal | Room | 2.6.x |
| Background Sync | WorkManager | 2.9.x |
| Networking | Retrofit + OkHttp + Kotlinx Serialization | |
| DI | Hilt | 2.50+ |
| Coroutines | Kotlinx Coroutines | 1.8.x |
| Vector Storage | FloatArray di RAM | |
| Excel/CSV | Apache POI / OpenCSV | |
| PDF | iText / Android PDF API | |
| Min SDK | **Android 12 (API 31)** | |

### 3.2 Backend

| Komponen | Pustaka |
|---|---|
| Runtime | Bun |
| Framework | Elysia / Hono |
| ORM | Prisma |
| Database | PostgreSQL + pgvector |
| Auth | JWT (bcrypt) |
| Validation | Zod |
| CSV | PapaParse |
| PDF | PDFKit |
| Container | Docker + docker-compose |

### 3.3 AI Pipeline

```
[CameraX frame] → [Face Detection] → [Face Embedding 128-d]
     ↓                                    ↓
  Liveness Check                    Brute-force match di RAM
     ↓                                    ↓
  Anti-spoofing                    [Match ≥ threshold?]
                                        ↓
                                  YES → Dapatkan identitas
                                  NO  → Wajah tidak dikenal
                                        ↓
                                  Tentukan aksi toggle:
                                  Jika sebelumnya "di_kampus" → "keluar"
                                  Jika sebelumnya "di_luar"   → "kembali"
```

---

## 4. Arsitektur Sistem

```
┌─────────────────────────────────────┐
│       PostgreSQL + pgvector         │
│         (Master Data)               │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│     Backend API (Bun)               │
│  - CRUD master data                 │
│  - Rule engine                      │
│  - Violation detector               │
│  - Report generator                 │
│  - Sync request manager             │
└────────────┬────────────────────────┘
             │
      ┌──────┴──────┐
      │             │
┌─────▼─────┐ ┌─────▼──────┐
│ Kiosk     │ │ Admin App  │
│ Scanner   │ │ (HP Admin) │
│ (Tablet)  │ │            │
│           │ │ Online:    │
│ Offline:  │ │ - Dashboard│
│ - Scan    │ │ - Atur izin│
│   toggle  │ │ - Trigger  │
│ - Match   │ │   sync     │
│   RAM     │ │ - Rekap    │
│ - Simpan  │ │ - Approval │
│   log     │ │   pengajuan│
│           │ │   izin     │
│ Online:   │ │            │
│ - Sync    │ │            │
│   (saat   │ │            │
│   diminta │ │            │
│   admin)  │ │            │
└───────────┘ └────────────┘
```

### 4.1 Sync Tengah Malam (Otomatis)

```
⏰ 00:00 WIB — WorkManager periodic task
       │
       ├── Cek koneksi internet
       │      └── Tidak ada → retry 30 menit kemudian
       │
       ├── POST /api/sync/attendance (upload log offline)
       │      └── Kirim semua log yang isSynced = false
       │
       ├── GET /api/sync/faces?since=<lastSync>
       │      └── Download face vectors baru/update
       │
       ├── GET /api/sync/rules
       │      └── Download aturan aktif terbaru
       │
       ├── Update Room database + load ulang RAM
       │
       └── POST /api/sync/complete (konfirmasi selesai)
```

Sync tengah malam adalah **default otomatis**. Tidak ada sync otomatis di luar jam itu untuk menjaga server tidak kebanjiran request.

### 4.2 Sync Manual (On-Demand dari Admin)

```
Admin App                          Server                       Kiosk Scanner
   │                                │                              │
   │── [Tombol "Sync Sekarang"] ──▶ │                              │
   │                                │── store syncRequested=true  │
   │                                │   untuk device ini          │
   │                                │                              │
   │                                │◀── poll tiap 10 menit ──────│
   │                                │    GET /api/sync/requested  │
   │                                │── response: true ──────────▶│
   │                                │                              │
   │                                │                              │── trigger sync:
   │                                │                              │   1. Upload attendance logs
   │                                │                              │   2. Download faces + rules
   │                                │                              │   3. Update RAM
   │                                │                              │
   │                                │◀── POST /api/sync/complete ─│
   │                                │── set syncRequested=false   │
   │◀─ Notifikasi "Sync selesai" ──│                              │
   │                                │                              │
```

**Polling**: Kiosk mengecek `GET /api/sync/requested` setiap **10 menit** saat ada internet. Request ini sangat ringan (return boolean + timestamp, tanpa transfer data besar). Jika `syncRequested = true`, kiosk langsung menjalankan sync penuh.

**Saat offline**: Jika kiosk tidak punya internet, log menumpuk di lokal. Saat internet kembali, WorkManager otomatis jalan (NetworkType.CONNECTED constraint), kiosk cek flag syncRequested. Jika admin sudah meminta sync, langsung proses. Jika tidak, kiosk tetap upload log yang tertunda.

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
  academicYear    Int?
  phone           String?
  email           String?
  isActive        Boolean  @default(true)
  createdAt       DateTime @default(now())
  updatedAt       DateTime @updatedAt

  faceVectors      FaceVector[]
  attendanceLogs   AttendanceLog[]
  permits          Permit[]
  violations       Violation[]
  courseSchedules  CourseSchedule[]
  permitQuotas     PermitQuota[]
}

model FaceVector {
  id        String   @id @default(cuid())
  studentId String
  vector    Unsupported("vector(128)")?
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


// ==================== ABSENSI (SCAN TOGGLE) ====================

// Setiap scan wajah = satu baris AttendanceLog.
// action = "keluar" | "kembali"
// Untuk cek status saat ini: ambil log terakhir hari ini.
//   - Jika terakhir "keluar" → saat ini DI LUAR KAMPUS
//   - Jika terakhir "kembali" atau tidak ada log → DI KAMPUS
model AttendanceLog {
  id              String   @id @default(cuid())
  studentId       String
  action          String   // "keluar" | "kembali"
  timestamp       DateTime @default(now())
  confidenceScore Float?
  isViolation     Boolean  @default(false) // flag jika melanggar aturan
  violationId     String?  // terkait pelanggaran jika ada
  isSynced        Boolean  @default(true)
  deviceId        String?
  photoCapture    String?  // base64 foto saat scan (audit)
  createdAt       DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])
  violation Violation? @relation(fields: [violationId], references: [id])

  @@index([studentId, timestamp])
  @@index([timestamp])
}


// ==================== PERIZINAN ====================

// Dua jenis izin:
//   1. izin_harian    → auto-approved, hanya 1 hari, tanpa approval
//   2. pengajuan_izin → butuh approval admin, bisa multi-hari
model Permit {
  id            String   @id @default(cuid())
  studentId     String
  type          String   // "izin_harian" | "pengajuan_izin"
  reason        String
  startDate     DateTime // tanggal mulai
  endDate       DateTime // tanggal selesai (bisa sama dgn startDate)
  startTime     String?  // "08:00" (null = full day)
  endTime       String?  // "16:00"
  status        String   // "approved" | "pending" | "rejected"
  // izin_harian → status langsung "approved"
  // pengajuan_izin → status default "pending", perlu approve admin
  approvedBy    String?  // adminId (null untuk izin_harian)
  rejectReason  String?
  attachmentUrl String?  // foto surat (untuk pengajuan_izin)
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
  maxPermits  Int      @default(10) // quota izin_harian per bulan
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  student Student @relation(fields: [studentId], references: [id])
  @@unique([studentId, month, year])
}


// ==================== ATURAN & PEMBATASAN ====================

model CampusRule {
  id              String   @id @default(cuid())
  name            String
  ruleType        String   // "restricted_hours" | "operational_hours" | "permit_limit"
  dayOfWeek       Int?     // 0=Minggu ... 6=Sabtu | null = tiap hari
  startTime       String   // "08:00"
  endTime         String   // "16:00"
  isRestricted    Boolean  // true = tidak boleh keluar
  appliesToAll    Boolean  @default(true)
  studyProgram    String?  // filter prodi
  academicYear    Int?     // filter angkatan
  description     String?
  priority        Int      @default(0)
  isActive        Boolean  @default(true)
  createdAt       DateTime @default(now())
  updatedAt       DateTime @updatedAt

  @@index([dayOfWeek, isActive])
}

model GlobalSetting {
  id          String   @id @default(cuid())
  key         String   @unique
  value       String
  description String?
  updatedAt   DateTime @updatedAt
}


// ==================== JADWAL KULIAH ====================

model CourseSchedule {
  id            String   @id @default(cuid())
  studentId     String
  courseName    String
  dayOfWeek     Int      // 0=Minggu ... 6=Sabtu
  startTime     String   // "08:00"
  endTime       String   // "10:00"
  room          String?
  lecturer      String?
  isActive      Boolean  @default(true)
  createdAt     DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])
  @@index([studentId, dayOfWeek])
}


// ==================== PELANGGARAN ====================

model Violation {
  id              String   @id @default(cuid())
  studentId       String
  type            String   // "keluar_tanpa_izin" | "keluar_jam_terlarang" | "keluar_jam_kuliah" | "tidak_kembali" | "melebihi_batas_izin"
  description     String
  timestamp       DateTime
  relatedRuleId   String?
  relatedPermitId String?
  relatedLogId    String?  // AttendanceLog.id
  isResolved      Boolean  @default(false)
  resolvedBy      String?
  resolvedAt      DateTime?
  notes           String?
  createdAt       DateTime @default(now())

  student Student @relation(fields: [studentId], references: [id])
  @@index([studentId])
  @@index([timestamp])
}


// ==================== SYNC & DEVICE ====================

model Device {
  id           String   @id @default(cuid())
  name         String
  deviceId     String   @unique
  location     String?
  ipAddress    String?
  isActive     Boolean  @default(true)
  lastPingAt   DateTime?
  batteryLevel Int?
  appVersion   String?
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt
}

model SyncRequest {
  id          String   @id @default(cuid())
  deviceId    String
  requestedAt DateTime @default(now())
  isProcessed Boolean  @default(false)
  processedAt DateTime?
  requestedBy String?  // adminId yang trigger
}

model SyncLog {
  id         String   @id @default(cuid())
  deviceId   String
  syncType   String   // "midnight" | "manual" | "reconnect"
  lastSyncAt DateTime
  status     String   // success | partial | failed
  logsCount  Int      @default(0)
  notes      String?
  createdAt  DateTime @default(now())

  @@index([deviceId, lastSyncAt])
}


// ==================== AUDIT & NOTIFIKASI ====================

model AuditLog {
  id          String   @id @default(cuid())
  adminId     String?
  action      String
  entityType  String
  entityId    String?
  details     String?  // JSON
  ipAddress   String?
  createdAt   DateTime @default(now())

  admin Admin @relation(fields: [adminId], references: [id])
  @@index([createdAt])
}

model ImportBatch {
  id          String   @id @default(cuid())
  filename    String
  totalRows   Int
  successRows Int
  failedRows  Int
  errors      String?
  importedBy  String?
  createdAt   DateTime @default(now())
}

model Notification {
  id          String   @id @default(cuid())
  adminId     String?  // null = broadcast
  type        String   // "violation" | "permit_pending" | "sync_done" | "device_offline"
  title       String
  message     String
  isRead      Boolean  @default(false)
  linkTo      String?
  createdAt   DateTime @default(now())

  @@index([adminId, isRead])
}
```

### 5.2 Room Database (Local Cache)

| Entity | Catatan |
|---|---|
| `StudentEntity` | Cache data mahasiswa |
| `FaceVectorEntity` | Vektor wajah (Blob via TypeConverter) |
| `AttendanceLogEntity` | Log scan lokal (isSynced = false = belum dikirim) |
| `PermitEntity` | Cache izin (harian + pengajuan) |
| `CampusRuleEntity` | Aturan untuk verifikasi offline |
| `CourseScheduleEntity` | Jadwal kuliah |
| `GlobalSettingEntity` | Pengaturan global |
| `SyncMetadata` | Tracking sync terakhir |

### 5.3 In-Memory (Kiosk Scanner)

```kotlin
data class FaceIndex(
    val embeddings: Map<String, FloatArray>,  // studentId → vector
    val studentMap: Map<String, StudentBrief>
)

data class StudentBrief(
    val id: String, val nim: String, val name: String
)

// State toggle per mahasiswa hari ini
data class ToggleState(
    val studentId: String,
    val currentState: State, // DI_KAMPUS atau DI_LUAR
    val lastAction: String?, // "keluar" atau "kembali"
    val scanCount: Int
)

enum class State { DI_KAMPUS, DI_LUAR }
```

---

## 6. API Endpoints

### 6.1 Auth
| Method | Endpoint |
|---|---|
| POST | `/api/auth/login` |
| POST | `/api/auth/refresh` |

### 6.2 Student
| Method | Endpoint |
|---|---|
| GET | `/api/students` |
| GET | `/api/students/:id` |
| POST | `/api/students` |
| PUT | `/api/students/:id` |
| DELETE | `/api/students/:id` |
| POST | `/api/students/:id/face` |
| POST | `/api/students/import` |
| GET | `/api/students/:id/schedules` |
| GET | `/api/students/:id/permits` |
| GET | `/api/students/:id/violations` |
| GET | `/api/students/:id/status` | Status terkini: di kampus/luar |

### 6.3 Attendance (Scan Toggle)
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/attendance/scan` | Catat scan toggle (keluar/kembali) |
| GET | `/api/attendance` | Riwayat scan (filter tgl, prodi, student) |
| GET | `/api/attendance/today` | Ringkasan hari ini |
| GET | `/api/attendance/status/:studentId` | Status toggle mahasiswa (di kampus/luar) |
| GET | `/api/attendance/outside-now` | Semua mahasiswa yg sedang di luar kampus |
| GET | `/api/attendance/statistics` | Statistik per prodi |

### 6.4 Permit (Izin)
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/permits` | Buat izin (harian auto-approved, pengajuan pending) |
| GET | `/api/permits` | List izin (filter type, status, tgl) |
| GET | `/api/permits/:id` | Detail izin |
| GET | `/api/permits/pending` | Pengajuan izin yang pending |
| PUT | `/api/permits/:id/approve` | Approve pengajuan_izin |
| PUT | `/api/permits/:id/reject` | Reject pengajuan_izin |
| GET | `/api/permits/active/:studentId` | Izin aktif mahasiswa hari ini |
| GET | `/api/permits/quota/:studentId` | Sisa kuota izin harian bulan ini |

### 6.5 Campus Rules
| Method | Endpoint |
|---|---|
| GET | `/api/rules` |
| POST | `/api/rules` |
| PUT | `/api/rules/:id` |
| DELETE | `/api/rules/:id` |
| GET | `/api/rules/effective?time=&day=` |
| GET | `/api/settings` |
| PUT | `/api/settings/:key` |

### 6.6 Course Schedule
| Method | Endpoint |
|---|---|
| GET | `/api/schedules` |
| POST | `/api/schedules/batch` |
| PUT | `/api/schedules/:id` |
| DELETE | `/api/schedules/:id` |
| GET | `/api/schedules/student/:studentId` |

### 6.7 Violation
| Method | Endpoint |
|---|---|
| GET | `/api/violations` |
| GET | `/api/violations/:id` |
| PUT | `/api/violations/:id/resolve` |
| GET | `/api/violations/statistics` |

### 6.8 Report
| Method | Endpoint |
|---|---|
| GET | `/api/reports/daily?date=` |
| GET | `/api/reports/daily/export?format=csv|pdf` |
| GET | `/api/reports/monthly?month=&year=` |
| GET | `/api/reports/violations?from=&to=` |
| GET | `/api/reports/permits?from=&to=` |
| GET | `/api/reports/toggle-history?date=&studentId=` |
| GET | `/api/reports/outside-hours?date=` | Keluar di luar jam izin |

### 6.9 Sync (Kiosk Scanner)
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/sync/requested` | Cek apakah admin minta sync (polling) |
| GET | `/api/sync/faces` | Download face vectors |
| GET | `/api/sync/rules` | Download aturan aktif |
| GET | `/api/sync/settings` | Download global settings |
| POST | `/api/sync/attendance` | Upload batch log scan |
| POST | `/api/sync/complete` | Konfirmasi sync selesai |

### 6.10 Sync (Admin)
| Method | Endpoint | Deskripsi |
|---|---|---|
| POST | `/api/sync/request/:deviceId` | Admin minta sync ke kiosk |
| GET | `/api/sync/status/:deviceId` | Status sync terakhir |

### 6.11 Device
| Method | Endpoint |
|---|---|
| GET | `/api/devices` |
| GET | `/api/devices/:id` |
| PUT | `/api/devices/:id` |
| POST | `/api/devices/ping` | Heartbeat kiosk |

### 6.12 Dashboard
| Method | Endpoint |
|---|---|
| GET | `/api/dashboard/summary` |
| GET | `/api/dashboard/weekly` |
| GET | `/api/dashboard/outside-now` | Jumlah mahasiswa di luar saat ini |
| GET | `/api/dashboard/violation-summary` |
| GET | `/api/dashboard/recent-scans` | 20 scan terakhir |

### 6.13 Audit & Notifikasi
| Method | Endpoint |
|---|---|
| GET | `/api/audit` |
| GET | `/api/notifications` |
| PUT | `/api/notifications/:id/read` |

---

## 7. Alur Data Offline-First

### 7.1 Skenario: Scan Toggle (100% Offline)

```
Konsep: Mahasiswa tinggal di asrama dalam kampus.
        Setiap scan = toggle keluar/masuk gerbang.
        Scan ke-1 → "keluar kampus"
        Scan ke-2 → "kembali ke kampus"
        Scan ke-3 → "keluar lagi"
        Dan seterusnya.

State awal hari (default): DI KAMPUS (karena tinggal di asrama)
Tidak ada konsep "absensi/alpha" — sudah pasti di kampus.

Contoh Alur:
═══════════════════════════════════════════════════════════════
WAKTU: 10.00 — Andi mau ke luar kampus (beli buku), scan di kiosk
═══════════════════════════════════════════════════════════════
1. CameraX capture + face detection
2. TFLite liveness check + embedding
3. Match dengan 10.000 vektor di RAM → ANDI (TI, 24001)
4. Cek state: belum ada scan hari ini → DI_KAMPUS
5. Toggle: DI_KAMPUS → KELUAR
6. Cek izin: apakah Andi punya izin harian untuk jam ini?
   ├── Ada izin harian (10.00-12.00) → ✅ Normal, keluar dengan izin
   └── Tidak ada izin → ⚠️ Cek aturan:
       ├── Jam 10.00 restricted? (misal: Senin 08.00-12.00 terlarang)
       │   ├── Ya + tanpa izin → VIOLATION
       │   └── Tidak restricted → ✅ Normal (keluar tanpa izin di jam bebas)
       └── Cek jadwal kuliah → ada jadwal? → VIOLATION
7. Simpan AttendanceLog { action: "keluar", isViolation: true/false }
8. Tampilkan hasil di layar kiosk

═══════════════════════════════════════════════════════════════
WAKTU: 12.30 — Andi kembali ke kampus, scan lagi
═══════════════════════════════════════════════════════════════
1. Face match → ANDI
2. Cek state: sebelumnya "keluar" → DI_LUAR
3. Toggle: DI_LUAR → KEMBALI
4. Hitung durasi di luar: 12.30 - 10.00 = 2j 30m
5. Jika ada violation di scan keluar → otomatis resolved saat kembali
6. Simpan AttendanceLog { action: "kembali" }
7. Tampilkan: "🏫 Selamat datang kembali, Andi! Di luar selama 2 jam 30 menit"

═══════════════════════════════════════════════════════════════
WAKTU: 16.00 — Andi keluar lagi (main ke tetangga), scan
═══════════════════════════════════════════════════════════════
1. Face match → ANDI
2. Cek state: sebelumnya "kembali" → DI_KAMPUS
3. Toggle: DI_KAMPUS → KELUAR
4. Jam 16.00 tidak restricted → ✅ Normal
5. Simpan AttendanceLog { action: "keluar" }

═══════════════════════════════════════════════════════════════
WAKTU: 17.30 — Andi kembali, scan
═══════════════════════════════════════════════════════════════
1. Face match → ANDI
2. Toggle: KEMBALI
3. Durasi: 17.30 - 16.00 = 1j 30m
4. Total di luar hari ini: 2j 30m + 1j 30m = 4 jam

═══════════════════════════════════════════════════════════════
Ringkasan harian Andi:
Keluar 2×, Kembali 2×, Total di luar: 4 jam
Izin harian: 1 (10.00-12.00) — ✅ dipakai
═══════════════════════════════════════════════════════════════
```

### 7.2 Logika Toggle State

```
Function determineAction(studentId, today):
    lastLog = SELECT * FROM AttendanceLog
              WHERE studentId = ? AND date(timestamp) = today
              ORDER BY timestamp DESC LIMIT 1

    IF lastLog == null:
        // Belum scan hari ini → ini scan pertama
        // Asumsi awal: DI_KAMPUS → aksi = "keluar"
        return "keluar"

    IF lastLog.action == "keluar":
        // Sebelumnya keluar → sekarang kembali
        return "kembali"

    IF lastLog.action == "kembali":
        // Sebelumnya kembali → sekarang keluar lagi
        return "keluar"
```

### 7.3 Absensi? Tidak Ada.

Karena mahasiswa **tinggal di asrama dalam kampus**, maka:
- **Default**: Semua mahasiswa sudah di kampus
- **Tidak ada** konsep "hadir", "alpha", "terlambat"
- **Tidak ada** scan masuk pagi hari
- Satu-satunya yang dicatat: **siapa keluar, jam berapa, kembali jam berapa, dengan izin atau tidak**

Scan pertama hari ini = saat mahasiswa mau **keluar** kampus.
Scan kedua = saat **kembali** ke kampus.
Tidak ada scan = mahasiswa tidak keluar sama sekali hari itu (normal).

### 7.4 Skenario: Admin Registrasi Wajah

```
1. Admin buka Admin App → cari/import mahasiswa
2. Kamera capture wajah + panduan framing oval
3. TFLite extract embedding lokal
4. Cek kualitas: brightness, blur, angle
5. POST /api/students/:id/face
6. Backend simpan ke PostgreSQL (pgvector)
7. Admin klik "Sync ke Scanner"
8. Kiosk mendapat flag syncRequested = true
9. Polling berikutnya: kiosk download vektor baru
```

### 7.5 Skenario: Kiosk Offline / No Internet

```
1. Kiosk kehilangan internet
2. Semua scan tetap berjalan 100% normal (offline)
3. Log menumpuk di Room (isSynced = false)
4. WorkManager gagal karena NetworkType.CONNECTED tidak terpenuhi
5. Kiosk cek koneksi setiap 5 menit
6. Saat internet kembali:
   → WorkManager auto-jalan
   → GET /api/sync/requested
   → Jika true (admin minta sync) → sync penuh
   → Jika false → upload semua log yang tertunda
   → Set isSynced = true
```

### 7.6 Conflict Resolution

| Skenario | Resolusi |
|---|---|
| Scan duplikat (studentId + timestamp sama) | Backend dedup, tolak duplikat |
| Log sudah terlanjur dikirim (retry) | Ignore duplicate (idempotent key) |
| Sync gagal di tengah jalan | Batch dikirim partial, lanjut batch berikutnya |
| Dua kiosk berbeda kirim scan mahasiswa sama | Last-write-wins (timestamp terlambat diabaikan jika sudah ada log lebih baru) |
| Admin approve izin saat kiosk offline | Flag syncRequested=true, nanti sync saat online |

---

## 8. Rule Engine: Aturan & Pembatasan

### 8.1 Jenis Aturan

| Aturan | Contoh | Efek |
|---|---|---|
| **Jam Operasional** | 06.00 - 18.00 | Di luar jam ini scan tidak valid |
| **Jam Terlarang Keluar** | Senin-Kamis 08.00-12.00 | Scan "keluar" di jam ini = violation (kecuali ada izin) |
| **Jam Terlarang per Prodi** | Jumat 08.00-11.00 (TI) | Filter per prodi |
| **Jam Terlarang per Angkatan** | 2024: 07.00-16.00 | Filter per angkatan |
| **Periode Khusus** | 10-20 Juni 2026 | Aturan khusus ujian |

### 8.2 Global Settings

| Key | Default | Deskripsi |
|---|---|---|
| `operational_start` | `06:00` | Jam awal kiosk bisa discan |
| `operational_end` | `21:00` | Jam akhir kiosk bisa discan |
| `max_permit_hours_per_day` | `8` | Maks durasi izin harian (jam) |
| `max_daily_permit_per_month` | `10` | Kuota izin harian per bulan |
| `violation_threshold` | `3` | Batas pelanggaran sebelum notifikasi khusus |
| `sync_poll_interval_minutes` | `10` | Interval polling sync request |

### 8.3 Logika Evaluasi (Offline-capable)

```
Function canScanOut(student, time, today):
  1. Cek jam operasional
     - time < operational_start → TOLAK (tampilkan "kampus belum buka")
     - time > operational_end → IZINKAN

  2. Cek izin aktif hari ini
     - Ada izin_harian ATAU pengajuan_izin approved? → IZINKAN (skip aturan)

  3. Cek restricted hours (CampusRule)
     - dayOfWeek cocok && startTime <= time <= endTime && isRestricted
     - Filter prodi/angkatan cocok?
     - Ada yang cocok → VIOLATION (kecuali point 2 terpenuhi)

  4. Cek jadwal kuliah (CourseSchedule)
     - Ada jadwal di jam ini? → VIOLATION

  5. Jika violation → simpan Violation + flag isViolation di AttendanceLog
     Tampilkan peringatan di layar kiosk
```

---

## 9. Sistem Scan Toggle (Keluar / Kembali)

### 9.1 Konsep Toggle

```
🏁 STATE AWAL (setiap hari): DI KAMPUS

  Scan ────→ KELUAR ────→ DI LUAR KAMPUS
                              │
                         Scan ┘
                              │
                              ▼
                         KEMBALI ──→ DI KAMPUS
                              │
                         Scan ┘
                              │
                              ▼
                         KELUAR → DI LUAR (lagi)
                              │
                         Dan seterusnya...
```

Setiap scan = toggle. Tidak ada logika "ini scan masuk atau keluar". Sistem hanya track state terakhir.

### 9.2 Status Mahasiswa Real-Time

| Status | Arti |
|---|---|
| 🏫 `DI KAMPUS` | Berada di dalam kampus (default — tinggal di asrama) |
| 🚶 `DI LUAR` | Scan terakhir = "keluar", belum kembali |
| ✅ `IZIN AKTIF` | Punya izin harian / pengajuan yang mencakup hari ini |
| ⚠️ `VIOLASI` | Keluar tanpa izin / melanggar aturan |

### 9.3 Durasi di Luar

Setiap toggle dihitung:
- Saat "keluar" → catat startTime
- Saat "kembali" → catat endTime, hitung durasi (endTime - startTime)
- Akumulasi total durasi di luar = jumlah seluruh sesi "keluar→kembali" hari ini

### 9.4 Edge Cases

| Skenario | Penanganan |
|---|---|
| Scan pertama setelah tengah malam | Anggap state DI_KAMPUS (default, reset tiap hari) |
| Scan berulang dalam 1 detik | Debounce 2 detik, abaikan duplikat |
| Scan di luar jam operasional | Tampilkan pesan "di luar jam operasional" |
| Scan wajah tidak dikenal | Tampilkan "wajah tidak dikenal" tanpa toggle |
| Mahasiswa scan di 2 kiosk berbeda | Kedua log tetap masuk. Last-write-wins untuk state |
| Lupa scan pas balik (masuk tanpa scan) | Auto close sesi pukul 23.59. Jika masih "keluar" → tandai "tidak_kembali" |
| Libur nasional | Semua aturan restricted dinonaktifkan, bebas keluar |

---

## 10. Sistem Pelanggaran

### 10.1 Jenis Pelanggaran

| Tipe | Deteksi | Kapan |
|---|---|---|---|
| `keluar_tanpa_izin` | Scan "keluar" tanpa punya izin harian aktif | Real-time di kiosk |
| `keluar_jam_terlarang` | Scan "keluar" di restricted hours (walau punya izin, jam tidak tercakup) | Real-time di kiosk |
| `keluar_jam_kuliah` | Scan "keluar" saat ada jadwal kuliah | Real-time di kiosk |
| `tidak_kembali` | Scan "keluar" tapi belum "kembali" sampai pukul 23.59 | Auto tengah malam |
| `melebihi_batas_izin` | Durasi di luar melebihi batas izin (misal: izin 4 jam, keluar 6 jam) | Saat scan "kembali" |

### 10.2 Alur Penanganan

```
1. Violation terdeteksi → record + flag AttendanceLog.isViolation = true
2. Admin dapat notifikasi (badge di Admin App)
3. Admin buka ViolationScreen
4. Lihat detail: nama, jam, aturan dilanggar
5. Tindakan:
   - "Resolve" + notes ("Sudah ditegur")
   - "Dismiss" (false positive)
6. Mahasiswa dengan >3 pelanggaran dapat status khusus
```

---

## 11. Sistem Izin: Harian & Pengajuan

### 11.1 Dua Jenis Izin

| | Izin Harian | Pengajuan Izin |
|---|---|---|
| **Kode** | `izin_harian` | `pengajuan_izin` |
| **Approval** | ✅ Auto-approved | ⏳ Perlu approve admin |
| **Durasi** | Maks 1 hari | Multi-hari (2+ hari) |
| **Jam** | Bisa set jam (misal 10.00-12.00) | Bisa set jam |
| **Kuota** | 10× per bulan (configurable) | Tidak ada kuota |
| **Dibuat oleh** | Admin (atas permintaan mahasiswa) | Admin |
| **Lampiran** | Opsional | Disarankan (surat) |
| **Status awal** | Langsung "approved" | "pending" |

### 11.2 Alur Izin Harian

```
1. Mahasiswa minta izin ke admin (datang langsung / chat)
2. Admin buka Admin App → PermitFormScreen
3. Pilih "Izin Harian", pilih mahasiswa, isi alasan, jam
4. Simpan → langsung active (auto-approved)
5. Kuota bulanan berkurang 1
6. Saat mahasiswa scan "keluar" di jam restricted:
   Sistem cek izin harian → ada izin → TIDAK kena violation
7. Selesai
```

### 11.3 Alur Pengajuan Izin (Multi-Hari)

```
1. Mahasiswa ajukan izin (via admin, dengan surat/bukti)
2. Admin buat "Pengajuan Izin" dengan lampiran
3. Status: PENDING
4. Admin lain / superadmin approve atau reject
5. Jika approved → izin aktif untuk tanggal yang diajukan
6. Mahasiswa bisa keluar-masuk bebas di tanggal tersebut
   tanpa kena violation (untuk jam yang tercakup)
7. Jika reject → admin beri alasan
```

### 11.4 Kuota Izin Harian

- Default: 10 izin harian per bulan per mahasiswa
- Bisa diubah via GlobalSettings
- Admin bisa lihat sisa kuota di StudentDetailScreen
- Reset otomatis tiap awal bulan
- Kuota khusus bisa di-set per mahasiswa (Override)

---

## 12. Laporan & Rekapan

### 12.1 Jenis Laporan

#### A. Rekap Harian Pergerakan
```
Rekapan Harian - Senin, 20 Juni 2026 - TI

No  Nama      NIM    Keluar    Kembali   Durasi Luar   Izin?      Status
―   ────      ───    ──────    ───────   ──────────   ─────      ──────
1   Andi      24001  10.00     12.30     2j 30m        ✅ Ada     🏫 Di kampus
                      16.00     17.30     1j 30m
2   Budi      24002  08.00     09.00     1j 0m         ❌ Tidak   ⚠ Violasi
3   Cici      24003   -          -         -            -         🏫 Di kampus (tdk keluar)
4   Dedi      24004  07.30     16.00     8j 30m        ✅ Izin    🏫 Di kampus
5   Eko       24005  08.15       -         -            ❌ Tidak   🚶 Di luar (violasi)

Ringkasan:
  Keluar hari ini : 4 mahasiswa
  Kembali         : 3 mahasiswa
  Masih di luar   : 1 mahasiswa
  Izin            : 2 mahasiswa
  Violasi         : 2 mahasiswa
  Tidak keluar    : 1 mahasiswa
```

#### B. Laporan Pelanggaran
```
Laporan Pelanggaran - Periode: 17-21 Juni 2026

No  Nama      NIM      Tgl       Keluar    Kembali   Jenis            Status
―   ────      ───      ───       ──────    ───────   ─────            ──────
1   Andi      24001    20 Jun    10.00     12.30     keluar tanpa izin ⚠ Belum
2   Farah     24015    19 Jun    09.30     11.00     jam terlarang     ✅ Selesai
3   Gilang    24022    17 Jun    14.00       -       tidak kembali     ❌ Belum
```

#### C. Status Real-Time (Saat Ini)
```
Mahasiswa Sedang Di Luar Kampus - 20 Jun 2026 14:30

No  Nama      NIM      Keluar Jam  Durasi      Izin?      Status
―   ────      ───      ──────────  ───────     ─────      ──────
1   Andi      24001    10.00       4j 30m      Tidak      ⚠ Violasi
2   Budi      24002    12.30       2j 0m       Izin       ✅ Normal
3   Dewi      24010    13.00       1j 30m      Tidak      ✅ Normal (jam bebas)
```

#### D. Rekap Bulanan per Prodi
```
Rekap Juni 2026 - per Program Studi

Prodi     Total   Pernah     Rata²      Violasi   Izin      Tidak
          Mhs     Keluar     Jam Luar             Dipakai   Keluar
────      ────    ──────     ─────────  ───────   ──────    ──────
TI        250     180 (72%)  3.2 j/hari  12 (5%)   45        70
SI        180     120 (67%)  2.8 j/hari  8 (4%)    30        60
DK        120      90 (75%)  4.1 j/hari  5 (4%)    25        30
MI        100      50 (50%)  2.5 j/hari  10 (10%)  15        50
```

### 12.2 Export
| Format | Keterangan |
|---|---|
| CSV | Buka di Excel/Spreadsheet |
| PDF | Siap cetak |
| In-App | Preview langsung di Admin App |

### 12.3 Filter
- Tanggal (from - to)
- Program studi
- Angkatan
- Status (di kampus, di luar, izin)
- Tipe pelanggaran
- NIM / Nama mahasiswa

---

## 13. Android Module Breakdown

### 13.1 `:core`

```
core/src/main/kotlin/.../core/
├── di/
│   ├── NetworkModule.kt
│   ├── DatabaseModule.kt
│   └── FaceModule.kt
├── face/
│   ├── FaceDetector.kt
│   ├── FaceEmbedder.kt
│   ├── FaceMatcher.kt
│   ├── LivenessDetector.kt
│   └── FaceIndex.kt
├── database/
│   ├── AppDatabase.kt
│   ├── entity/
│   ├── dao/
│   └── converter/
├── network/
│   ├── ApiClient.kt
│   ├── ApiService.kt
│   ├── dto/
│   └── interceptor/
├── sync/
│   ├── FaceSyncWorker.kt
│   ├── AttendanceSyncWorker.kt
│   ├── SyncPoller.kt         # Polling sync request
│   └── SyncManager.kt
└── model/
    ├── Student.kt
    ├── AttendanceLog.kt
    ├── Permit.kt
    ├── CampusRule.kt
    ├── Violation.kt
    └── CourseSchedule.kt
```

### 13.2 `:kiosk-scanner`

```
kiosk-scanner/src/main/kotlin/.../scanner/
├── ScannerApp.kt
├── MainActivity.kt
├── camera/
│   ├── CameraManager.kt
│   ├── FrameAnalyzer.kt
│   └── PreviewView.kt
├── matching/
│   ├── MatchEngine.kt
│   └── MatchResult.kt
├── toggle/
│   ├── ToggleEngine.kt        # Logic determine keluar/kembali
│   ├── ToggleState.kt         # State management per-student
│   └── SessionTracker.kt      # Track durasi di luar
├── rule/
│   ├── RuleChecker.kt
│   └── RuleCache.kt
├── ui/
│   ├── ScannerScreen.kt
│   ├── ResultOverlay.kt
│   └── StatusBar.kt
└── service/
    └── KioskForegroundService.kt
```

### 13.3 `:admin-app`

```
admin-app/src/main/kotlin/.../admin/
├── AdminApp.kt
├── MainActivity.kt
├── auth/
│   ├── LoginScreen.kt
│   └── AuthViewModel.kt
├── dashboard/
│   ├── DashboardScreen.kt
│   ├── DashboardViewModel.kt
│   └── components/
│       ├── StatCard.kt
│       ├── RecentScansList.kt
│       └── ViolationAlert.kt
├── student/
│   ├── StudentListScreen.kt
│   ├── StudentDetailScreen.kt
│   ├── StudentFormScreen.kt
│   └── StudentViewModel.kt
├── register/
│   ├── FaceRegisterScreen.kt
│   └── RegisterViewModel.kt
├── permit/                              # Izin Harian + Pengajuan
│   ├── PermitListScreen.kt
│   ├── PermitDetailScreen.kt
│   ├── PermitFormScreen.kt              # Pilih jenis: harian/pengajuan
│   ├── PendingApprovalScreen.kt         # Pengajuan pending
│   └── PermitViewModel.kt
├── monitor/                             # Monitoring toggle status
│   ├── ToggleStatusScreen.kt            # Status real-time semua mhs
│   ├── OutsideNowScreen.kt              # Yg sedang di luar
│   └── MonitorViewModel.kt
├── rules/
│   ├── RulesListScreen.kt
│   ├── RuleFormScreen.kt
│   ├── GlobalSettingsScreen.kt
│   └── RulesViewModel.kt
├── violation/
│   ├── ViolationListScreen.kt
│   ├── ViolationDetailScreen.kt
│   └── ViolationViewModel.kt
├── report/
│   ├── ReportScreen.kt
│   ├── DailyReportScreen.kt
│   ├── ViolationReportScreen.kt
│   ├── OutsideHoursReportScreen.kt      # Laporan keluar di luar jam izin
│   ├── ReportPreviewScreen.kt
│   └── ReportViewModel.kt
├── sync/
│   ├── SyncScreen.kt                    # Tombol sync manual + status
│   └── SyncViewModel.kt
├── device/
│   ├── DeviceListScreen.kt
│   ├── DeviceDetailScreen.kt
│   └── DeviceViewModel.kt
├── notification/
│   ├── NotificationScreen.kt
│   └── NotificationViewModel.kt
└── import/
    ├── ImportScreen.kt
    └── ImportViewModel.kt
```

---

## 14. UI Screen Map

### 14.1 Kiosk Scanner

| Screen | Deskripsi |
|---|---|
| `ScannerScreen` | Fullscreen kamera, auto-scan. Tidak ada tombol. Indikator: jam, status koneksi, mode kiosk. |
| `ResultOverlay` | Animasi hasil. Hijau = scan sukses + nama mahasiswa + aksi (keluar/kembali). Merah = tidak dikenal. Kuning = violation warning. |

### 14.2 Admin App

| Screen | Deskripsi |
|---|---|
| `LoginScreen` | Username + password |
| `DashboardScreen` | Card stats: di kampus, di luar, izin aktif, violation hari ini + recent scan feed |
| `StudentListScreen` | Search + filter prodi/angkatan |
| `StudentDetailScreen` | Data + status toggle + history scan + violation |
| `StudentFormScreen` | Tambah/edit |
| `FaceRegisterScreen` | Kamera + panduan oval |
| `PermitListScreen` | Tab: Izin Harian, Pengajuan, All |
| `PermitFormScreen` | Pilih jenis (harian/pengajuan), pilih mahasiswa, tanggal, jam, alasan, lampiran |
| `PendingApprovalScreen` | List pengajuan izin yang pending + tombol approve/reject |
| `ToggleStatusScreen` | Tabel: semua mahasiswa + status toggle real-time |
| `OutsideNowScreen` | Filter mahasiswa yang saat ini "di luar" |
| `RulesListScreen` | CRUD aturan + toggle aktif/nonaktif |
| `GlobalSettingsScreen` | Setting jam operasional, kuota, dll |
| `ViolationListScreen` | Filter tipe/tanggal |
| `ViolationDetailScreen` | Detail + resolve/ignore + notes |
| `ReportScreen` | Pilih jenis laporan + filter + preview + export |
| `DailyReportScreen` | Rekap harian preview |
| `OutsideHoursReportScreen` | Laporan khusus: keluar di luar jam izin |
| `SyncScreen` | Status koneksi kiosk + tombol "Sync Sekarang" + log sync |
| `DeviceListScreen` | Daftar kiosk + status |
| `NotificationScreen` | Notifikasi violation, sync done, dll |
| `ImportScreen` | Import CSV/Excel mahasiswa |

---

## 15. Hardware Requirement

### 15.1 Kiosk Scanner

| Komponen | Minimal | Rekomendasi |
|---|---|---|
| OS | Android 12 | Android 13+ |
| RAM | 4 GB | 6 GB+ |
| Kamera Depan | 5 MP | 8 MP+ autofocus |
| CPU | Octa-core 2.0 GHz | Snapdragon 7xx+ |
| Storage | 32 GB | 64 GB |
| Layar | 8" | 10" |
| Koneksi | Wi-Fi | Wi-Fi + backup seluler |

### 15.2 Posisi

```
Cahaya dari depan (searah mahasiswa)
         │
         ▼
┌────────────────┐
│  HP Kiosk      │ kamera ▶ Mahasiswa
└────────────────┘
⛔ Jangan backlight
```

### 15.3 Server

| Komponen | Minimal |
|---|---|
| CPU | 2 core |
| RAM | 4 GB |
| Storage | 50 GB SSD |
| OS | Ubuntu 22.04+ |
| Docker | Yes |

---

## 16. Phase / Milestone Pengembangan

### Phase 1: Foundation + Core Pipeline

**Tujuan**: Backend + Kiosk bisa scan toggle end-to-end offline.

| Task | Estimasi |
|---|---|
| Setup monorepo + Gradle + version catalog | 1 hari |
| Setup backend (Bun + Prisma + PostgreSQL + pgvector + Docker) | 2 hari |
| Database schema + migration + seed | 1 hari |
| API: CRUD students, attendance scan, sync, rules | 2 hari |
| `:core` — Room database + TypeConverters | 1 hari |
| `:core` — TFLite face embedder + liveness | 2 hari |
| `:core` — Network layer (Retrofit) | 1 hari |
| `:kiosk-scanner` — CameraX + frame analyzer | 2 hari |
| `:kiosk-scanner` — Face matching engine | 2 hari |
| `:kiosk-scanner` — Toggle engine (keluar/kembali) | 1 hari |
| `:kiosk-scanner` — Sync (midnight + polling sync request) | 1 hari |
| **Total Phase 1** | **~16 hari** |

### Phase 2: Admin App + Izin

**Tujuan**: Admin bisa kelola data, izin harian & pengajuan.

| Task | Estimasi |
|---|---|
| `:admin-app` — Auth + login + dashboard | 2 hari |
| `:admin-app` — Student CRUD + detail + face register | 3 hari |
| `:admin-app` — Import CSV/Excel | 1 hari |
| `:admin-app` — Izin harian (auto-approved) | 2 hari |
| `:admin-app` — Pengajuan izin + approve/reject | 2 hari |
| `:admin-app` — Rules management + global settings | 2 hari |
| API: permit endpoints + rules + import | 2 hari |
| **Total Phase 2** | **~14 hari** |

### Phase 3: Monitoring + Violation + Report

| Task | Estimasi |
|---|---|
| API: violation detection + report generators | 2 hari |
| `:kiosk-scanner` — Rule checker offline | 1 hari |
| `:admin-app` — Toggle status monitoring + outside now | 2 hari |
| `:admin-app` — Violation list + detail + resolve | 2 hari |
| `:admin-app` — Report screens (daily, violation, outside-hours) | 3 hari |
| `:admin-app` — Export CSV/PDF | 1 hari |
| **Total Phase 3** | **~11 hari** |

### Phase 4: Sync + Device + Notifikasi

| Task | Estimasi |
|---|---|
| API: sync request/status, device ping, notifications | 2 hari |
| `:admin-app` — Sync trigger screen + status | 1 hari |
| `:admin-app` — Device management | 1 hari |
| `:admin-app` — Notification screen | 1 hari |
| `:admin-app` — Schedule management + import | 2 hari |
| **Total Phase 4** | **~7 hari** |

### Phase 5: Polishing & Production

| Task | Estimasi |
|---|---|
| Error handling + edge cases | 2 hari |
| Loading/empty/error state all screens | 2 hari |
| Logging + crash reporting | 1 hari |
| Testing manual + edge case | 3 hari |
| Performance tuning (10k vectors) | 1 hari |
| Dokumentasi + deployment guide | 2 hari |
| **Total Phase 5** | **~11 hari** |

---

## 17. Open Questions

1. **Model TFLite**: MobileFaceNet atau model lain? Sumber pre-trained model?
2. **Liveness Detection**: Model blinking atau texture-based (faspe)?
3. **Face Vector Dimensi**: 128-d (cepat) vs 512-d (akurat)?
4. **Backend Framework**: Elysia vs Hono?
5. **CSV Import Format**: Template kolom: NIM, Nama, Prodi, Angkatan, No HP, Email?
6. **Jadwal Kuliah Format**: Template kolom: NIM, Matkul, Hari, Jam Mulai, Jam Selesai, Ruang?
7. **Dashboard Web**: Butuh web dashboard atau cukup dari Admin App?
8. **Multiple Kiosk**: 1 kiosk dulu atau langsung multi-gerbang?
9. **Emergency Mode**: Tombol darurat untuk nonaktifkan semua aturan?

---

> **Status**: Planning — belum ada coding.
> **Next step**: Kalau dokumen ini sudah OK, lanjut ke setup monorepo + Phase 1.
