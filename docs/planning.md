# FaceGateApp — Planning Document

> Proyek **izin keluar-masuk kampus** berbasis Face Recognition untuk **pondok pesantren / asrama kampus**
> Target: 10.000 mahasiswa | 2 aplikasi Android Native + Backend API

---

## Daftar Isi

1. [Ringkasan Proyek](#1-ringkasan-proyek)
2. [Struktur Monorepo](#2-struktur-monorepo)
3. [Tech Stack Detail](#3-tech-stack-detail)
   - 3.1 Android
   - 3.2 Backend
   - 3.3 AI Pipeline (Face Detection, Liveness, Embedding, Matching)
   - 3.4 Student Face Registration Pipeline
   - 3.5 Verification & Quality Assurance
   - 3.6 Metrics Collection & Monitoring
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
17. [Troubleshooting & Debug Guide](#17-troubleshooting--debug-guide)
18. [Face Recognition Implementation Decisions](#18-face-recognition-implementation-decisions)
19. [Keputusan Final](#19-keputusan-final)

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
│   │   ├── routes/                  # API route handlers (grouped by resource)
│   │   ├── services/                # Business logic + Prisma queries
│   │   │   ├── auth.ts
│   │   │   ├── student.ts
│   │   │   ├── attendance.ts
│   │   │   ├── permit.ts
│   │   │   ├── rule.ts
│   │   │   ├── violation.ts
│   │   │   ├── notification.ts
│   │   │   ├── device.ts
│   │   │   └── prisma.ts
│   │   └── seed.ts
│   ├── prisma/
│   │   └── schema.prisma
│   ├── Dockerfile
│   ├── package.json
│   ├── tsconfig.json
│   └── .env.example
│
├── docker-compose.yml                # PostgreSQL + pgvector + backend (port 8150)
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
| Face Detection | **YOLOv8 Face** (primary) / MediaPipe FaceDetector (fallback) | |
| Face Embedding | TensorFlow Lite (**MobileFaceNet 192-d**) | |
| Database Lokal | Room | 2.6.x |
| Background Sync | WorkManager | 2.9.x |
| Networking | Retrofit + OkHttp + Kotlinx Serialization | |
| DI | Hilt | 2.50+ |
| Coroutines | Kotlinx Coroutines | 1.8.x |
| Vector Storage | FloatArray di RAM (centroid per student) | |
| Excel/CSV | Apache POI / OpenCSV | |
| PDF | iText / Android PDF API | |
| Min SDK | **Android 12 (API 31)** | |

### 3.2 Backend

| Komponen | Pustaka / Nilai |
|---|---|
| Runtime | Bun |
| Framework | **Elysia** |
| ORM | Prisma |
| Database | PostgreSQL + pgvector |
| Auth | JWT (bcrypt) |
| Validation | Zod |
| CSV | PapaParse |
| PDF | PDFKit |
| Container | Docker + docker-compose |
| Port | **8150** |
| Hosting | **Home server** — server sendiri di rumah, di-tunnel dengan Cloudflare Tunnel, port dibelokkan via web panel server |
| Domain | **https://facegate.utc.web.id** — domain yang sudah terhubung ke home server via Cloudflare Tunnel |

### 3.2.1 TensorFlow / TFLite

```
TensorFlow: 2.16+ (latest stable)
TFLite: ensure model compatibility dengan Android 12+ (API 31+)

VALIDATE:
  - MobileFaceNet .tflite model inference di emulator
  - Verify output: 192-d float array
  - Check quantization (FP32? INT8? FP16?)
    → Recommend FP32 untuk accuracy, INT8 kalau resource ketat
```

### 3.3 AI Pipeline

#### 3.3.1 Face Detection & ROI Extraction

**Model**: YOLOv8 Face (primary) / MediaPipe FaceDetector (fallback)

| Komponen | Pilihan 1: YOLOv8 Face | Pilihan 2: MediaPipe |
|---|---|---|
| Akurasi deteksi | 98-99% | 95-97% |
| Bounding box konsistensi | Sangat presisi, tight | Kadang loose |
| Confidence score reliable | Ya | Kadang tinggi palsu |
| Multi-face handling | Excellent | Good |
| Model size (TFLite) | ~6-8 MB | ~350 KB |
| Recommended | **PILIH INI** | Fallback jika resource ketat |

**Decision**: Gunakan **YOLOv8 Face** untuk production-grade cropping accuracy.

**Quality Checks pada Detection**:
```
IF detection.confidence < 0.85:
    REJECT frame → ask user untuk re-scan
ELSE:
    Continue ke ROI extraction
```

**ROI Extraction & Normalization**:
```
1. Dari bounding box YOLOv8, expand 15% (untuk margin)
2. Crop ke standard size: 112 × 112 pixel (sesuai MobileFaceNet input)
3. Normalize input: pixel / 255.0 (range [0, 1])
4. Check image blur/noise (Laplacian variance > threshold 100)
   IF blur: REJECT
```

#### 3.3.2 Liveness Detection

**Metode**: Eye Aspect Ratio (EAR) — rule-based dari landmark mata (MediaPipe 468 titik)

**Improvements**:
```
1. Require 2+ blinks dalam 3 detik window
2. Track EAR trend (bukan hanya threshold jump)
3. Anti-spoofing score: combine EAR + face symmetry check
4. IF liveness_score < 0.70:
    REJECT → ask untuk natural blink
5. IF user take >5 seconds:
    TIMEOUT → retry from start
```

#### 3.3.3 Face Embedding Extraction

**Model**: MobileFaceNet 192-d (TFLite)

**Input Requirements**:
- Size: 112 × 112 pixel
- Format: float32, normalized [0, 1] (pixel / 255.0)
- Ensure: ROI harus **tight crop** (bukan ada background)

**Output Processing**:
```
1. Raw output: [192] float array
2. L2 normalize: vector / ||vector||
3. Quality check:
   - Norm validation: norm harus ~1.0 (setelah normalize)
   - Embedding entropy: jangan flat vectors
   IF norm < 0.1 or entropy too low:
      REJECT → re-scan
```

#### 3.3.4 Matching Strategy — Adaptive Threshold

**CRITICAL CHANGE**: Replace fixed 0.6 threshold dengan adaptive gap-based matching.

```
ALGORITHM: Top-K Ranking + Gap Analysis

Input: scan_embedding (192-d float)

STEP 1: Brute-force similarity
FOR each student_id in database:
    stored_embedding = get_centroid_embedding(student_id)
    score[student_id] = cosine_similarity(scan_embedding, stored_embedding)

STEP 2: Top-3 ranking
top_3 = sort_descending(score).take(3)
top_score = top_3[0].score
runner_up = top_3[1].score  (atau 0 jika hanya ada 1)
gap = top_score - runner_up

STEP 3: Adaptive decision
IF top_score >= 0.90 AND gap >= 0.08:
    CONFIDENCE = 0.98
    DECISION = "MATCH_CONFIDENT"
    ACTION = approve_toggle
    
ELIF top_score >= 0.85 AND gap >= 0.05:
    CONFIDENCE = 0.90
    DECISION = "MATCH_MEDIUM"
    ACTION = approve_toggle
    
ELIF top_score >= 0.80 AND gap >= 0.03:
    CONFIDENCE = 0.75
    DECISION = "MATCH_WEAK"
    ACTION = FLAG_FOR_REVIEW  # Human verification later
    
ELSE:
    CONFIDENCE = 0
    DECISION = "NO_MATCH"
    ACTION = reject_scan
    
RETURN {
    matched: bool,
    student_id: string,
    top_score: float,
    gap: float,
    confidence: float,
    decision: string
}
```

**Tuning Schedule**:
- Week 1-2 (MVP): Start dengan default thresholds
- Week 3-4: Measure real FPR/FNR, adjust gap threshold
- Week 5+: Lock thresholds after <1% FPR/FNR validated

| Fase | top_score >= | gap >= | Action |
|---|---|---|---|
| MVP (Week 1-2) | 0.82 | 0.02 | Approve all |
| Validation (Week 3-4) | 0.85 | 0.04 | Monitor & flag weak |
| Production (Week 5+) | 0.85-0.90 | 0.04-0.08 | Locked |

**Fallback Options**:
```
IF no confident match found:
  1. Log frame + embedding untuk manual review
  2. Benarkan dengan face ID confirmation (manual input)
  3. Update centroid embedding dengan scan result (optional)
```

**Penjelasan**:
- Threshold 0.6 → 0.85-0.90 (more realistic)
- Gap analysis mencegah ambiguity ketika ada 2 orang mirip
- Confidence score jadi transparent
- WEAK matches bisa di-flag untuk human review (audit trail)

| Komponen | Model / Metode | Ukuran | Kecepatan |
|---|---|---|---|
| Face Detection | **YOLOv8 Face** (primary) / MediaPipe (fallback) | ~6-8 MB / ~350 KB | < 5ms |
| Liveness | **Eye Aspect Ratio (EAR)** — 2+ blinks dalam 3 detik | 0 KB | < 2ms |
| Face Embedding | **MobileFaceNet** .tflite — 192-d vector, normalized [0,1] | ~5 MB | ~15ms |
| Matching | Top-K ranking + gap analysis di RAM | 10.000 × 192 = ~7.5 MB | ~30ms |
| **Total** | | **~13-14 MB** | **~50ms per face** |

- **Threshold**: Adaptive 0.80-0.90 dengan gap analysis (bukan fixed 0.6)
- **Anti-spoofing**: Deteksi 2+ kedipan via EAR dalam 3 detik
- **Target performa**: < 100ms per face (termasuk QA checks)

### 3.4 Student Face Registration Pipeline

#### 3.4.1 Registration Flow

```
USER: Mahasiswa scan wajah (registrasi awal)
  ↓
[CameraX video stream — 3-5 detik @ 30fps]
  ↓
[Frame extraction: setiap frame]
  ├─ YOLOv8 detect: confidence < 0.85? → skip frame
  ├─ Crop ROI + quality check (blur, lighting)
  ├─ Liveness check: blink? → if no, reject
  └─ Store 1 embedding per frame
  ↓
[Quality validation cluster]
  ├─ Collect all embeddings dari 5-10 good frames
  ├─ Compute centroid = mean(embeddings)
  ├─ Compute consistency = min(cosine_sim(frame_i, centroid))
  ├─ IF min_consistency < 0.80:
  │    → Signal: "Registrasi tidak stabil, coba lagi"
  │    → Ask untuk re-register dalam kondisi lebih stabil
  └─ IF min_consistency >= 0.80:
       → Proceed ke save
  ↓
[Save to database]
  ├─ StudentFaceRegistration.centroidEmbedding = centroid
  ├─ StudentFaceRegistration.allEmbeddings = [all 5-10 frames]
  ├─ StudentFaceRegistration.consistency = mean consistency
  ├─ StudentFaceRegistration.sampleCount = 10
  └─ StudentFaceRegistration.status = "active"
  ↓
[Success message]
  → Show consistency score (transparency)
  → "Registered dengan 10 samples, quality score: 0.88"
```

#### 3.4.2 Registration Quality Thresholds

| Metrik | Threshold | Action Jika Gagal |
|---|---|---|
| Detection confidence | >= 0.85 | Skip frame, retry |
| Liveness score | >= 0.70 | Reject, ask blink naturally |
| Sample count | >= 5 | Need at least 5 good frames |
| Min consistency | >= 0.80 | Re-register, more stable environment |
| Blur detection (Laplacian) | >= 100 | Skip, ask for better lighting |

#### 3.4.3 Registration Retry Strategy

```
Attempt 1: Ask untuk capture video 3-5 detik, normal pose
  IF fail (min_consistency < 0.80):
    Attempt 2: Try dengan berbeda pose (left, right, neutral)
  IF fail again:
    Attempt 3: Different location (better lighting)
  IF all fail:
    Status = "flagged_retrain"
    Admin review: kemungkinan masalah foto ID atau gesture

Max retries = 3 per student
```

#### 3.4.4 Incremental Learning (Optional)

```
AFTER successful scan in production:
  IF confidence >= 0.90:
    Alpha = 0.05
    centroid_new = centroid_old * (1 - alpha) + scan_embedding * alpha
    Save centroid_new (continuous adaptation)
  
  IF confidence < 0.75:
    Flag untuk manual review (jangan auto-update)
```

### 3.5 Verification & Quality Assurance

#### 3.5.1 Scan Verification Pipeline

```
[Kiosk: User scan wajah]
  ↓
[YOLOv8 Face Detection]
  IF confidence < 0.85:
    → REJECT (not a face or too unclear)
    → UI: "Wajah tidak terdeteksi, coba lagi"
  ↓
[Liveness Check (EAR-based)]
  IF no blink dalam 3 detik:
    → REJECT (might be photo)
    → UI: "Berkedip untuk verifikasi hidup"
  ↓
[MobileFaceNet Embedding]
  Extract 192-d vector dari ROI
  ↓
[Matching: Top-K + Gap Analysis]
  Compute similarity ke semua centroid di database
  Top-3 ranking + gap check (lihat section 3.3.4)
  ↓
[Decision Output]
  {
    matched: bool,
    studentId: string,
    confidence: float,
    decision: MATCH_CONFIDENT | MATCH_MEDIUM | MATCH_WEAK | NO_MATCH
  }
  ↓
[Action dispatch]
  IF decision = MATCH_CONFIDENT:
    → Allow toggle (keluar/kembali) immediately
    → Log success
  
  ELIF decision = MATCH_MEDIUM:
    → Allow toggle, pero mark untuk review
    → Log: "medium confidence"
  
  ELIF decision = MATCH_WEAK:
    → Ask untuk manual confirmation (Show photo + allow/reject)
    → Log: "weak - manual override"
  
  ELSE (NO_MATCH):
    → REJECT
    → UI: "Wajah tidak dikenali"
    → Log failure untuk analytics
```

#### 3.5.2 Quality Assurance Checks

| Stage | Check | Threshold | Action |
|---|---|---|---|
| Detection | Confidence | >= 0.85 | If fail: reject, retry |
| Liveness | EAR blink | 2+ dalam 3s | If fail: reject, retry |
| Embedding | Norm validation | 0.9-1.1 | If fail: re-extract |
| Matching | Top score | >= 0.80 | If fail: no match |
| Matching | Gap | Based on tier | If fail: check tier down |
| Confidence | Final score | >= 0.75 | If < 0.75: manual review |

#### 3.5.3 Fallback & Manual Override

```
IF system cannot decide:
  1. Show face photo + top 3 candidates
  2. Admin/guard tap "Accept" atau "Reject"
  3. Log manual override (audit trail)
  4. Flag untuk post-analysis (why did system fail?)

IF network down (offline mode):
  1. Use local centroid embeddings (synced daily)
  2. Allow matching with stale data
  3. Queue results untuk sync ketika online
  4. Mark: "offline_sync_pending"
```

### 3.6 Metrics Collection & Monitoring

#### 3.6.1 Per-Scan Metrics

Setiap scan log:
```json
{
  "timestamp": "2026-07-21T10:30:45Z",
  "scannedAt": "2026-07-21T10:30:45Z",
  "deviceId": "kiosk_001",
  "detectionConfidence": 0.94,
  "livenessScore": 0.88,
  "topSimilarity": 0.89,
  "gap": 0.06,
  "confidence": 0.92,
  "decision": "MATCH_CONFIDENT",
  "predictedStudentId": "STU-2024-0001",
  "actualStudentId": "STU-2024-0001",
  "isCorrect": true,
  "modelVersion": "mobilefacenet_192d_v1",
  "responseTime_ms": 145
}
```

#### 3.6.2 Daily Aggregation

Setiap hari, hitung:

```
Total scans: N
Successful matches: N_match
Failed matches: N_fail = N - N_match
Rejected by QA: N_qa

Distribution:
  MATCH_CONFIDENT: X%
  MATCH_MEDIUM: Y%
  MATCH_WEAK: Z%
  NO_MATCH: W%

Accuracy (after manual review):
  True positives: TP
  False positives: FP  (wrong person matched)
  True negatives: TN
  False negatives: FN  (right person rejected)
  
  FPR = FP / (FP + TN)  [false positive rate]
  FNR = FN / (FN + TP)  [false negative rate]
  Accuracy = (TP + TN) / (TP + TN + FP + FN)
```

#### 3.6.3 Alert Thresholds

```
IF FPR > 1.0%:
  → Alert: "High false positive rate"
  → Action: Review last 100 scans, re-check threshold
  
IF FNR > 2.0%:
  → Alert: "High false negative rate"
  → Action: Check lighting, camera, rerun registration
  
IF median(confidence) < 0.80:
  → Warning: "Embeddings quality degrading"
  → Action: Check camera quality, lighting changes
  
IF >20% scans = MATCH_WEAK:
  → Warning: "Too many marginal matches"
  → Action: Increase threshold, tighten gap requirement
```

#### 3.6.4 Monitoring Dashboard (Admin App)

```
Dashboard harus show:
  • Real-time scan counter
  • Today's FPR / FNR trends (hourly)
  • Decision distribution pie chart
  • Response time histogram
  • Device health (kiosk online/offline)
  • Flagged scans untuk manual review
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

### 4.3 Realtime Data (Admin ↔ Server)

Admin App membutuhkan data realtime dari server untuk monitoring. Server menggunakan **Server-Sent Events (SSE)** untuk push data ke admin.

```
┌────────────┐     SSE (persistent)      ┌────────────┐
│ Admin App  │◄──────────────────────────│   Server   │
│            │   event: dashboard_update │            │
│            │   event: violation_new    │            │
│            │   event: scan_realtime    │            │
│            │   event: outside_update   │            │
└────────────┘                           └────────────┘
```

**Event types**:
| Event | Trigger | Data |
|---|---|---|
| `dashboard_update` | Setiap 5 detik / saat ada perubahan | Summary stats (di kampus, di luar, izin, violation) |
| `violation_new` | Violation terdeteksi (via scan kiosk) | Violation record baru |
| `scan_realtime` | AttendanceLog baru dibuat | Data scan terbaru (nama, aksi, jam) |
| `outside_update` | Mahasiswa keluar/kembali | Jumlah & daftar mahasiswa di luar |
| `sync_status` | Sync selesai/gagal | Status sync per device |

**Implementasi**:
- Endpoint: `GET /api/events/stream` (SSE) — admin connect dan terima event secara realtime
- Admin App menggunakan `EventSource` / OkHttp SSE untuk listen event
- Dashboard otomatis terupdate tanpa polling

### 4.4 Kiosk Auto-Fetch (Efisien: Cek Flag Dulu, Baru Download)

Kiosk polling ringan setiap 10 detik — **hanya cek boolean**, tidak download data. Download data berat (faces, rules) HANYA dilakukan saat server memberi sinyal ada perubahan.

```
SETIAP 10 DETIK (polling ringan):
┌──────────┐  GET /api/sync/requested   ┌──────────┐
│  Kiosk   │───────────────────────────►│  Server  │
│          │◄──── { requested: false }──│          │  ← 99% waktu: tidak ada perubahan
└──────────┘                            └──────────┘
     │                                       ▲
     │  Tidak download apa pun              │
     └──────────────────────────────────────┘

SAAT ADA PERUBAHAN DB:
┌──────────┐  GET /api/sync/requested   ┌──────────┐
│  Kiosk   │───────────────────────────►│  Server  │
│          │◄──── { requested: true }───│          │  ← Flag dari DB Change Hook
└────┬─────┘                            └──────────┘
     │
     │  GET /api/sync/faces   (download faces + students)
     │  GET /api/sync/rules   (download rules)
     │  POST /api/sync/complete (konfirmasi)
     │
     └── Rebuild FaceIndex di RAM
```

**Perbandingan traffic**:
| | Sebelumnya | Sekarang |
|---|---|---|
| Polling tiap | 10 menit | **10 detik** |
| Data per poll | — | **~100 bytes** (boolean doang) |
| Download faces | Tiap poll (boros) | **Hanya saat ada perubahan** |
| Download rules | Tiap poll (boros) | **Hanya saat ada perubahan** |
| Estimasi data/bulan | ~500 MB | **~5 MB** (99% poll ringan) |

**DB Change Hook** — diimplementasikan di backend service layer:
- Setiap `createStudent`, `updateStudent`, `deleteStudent`, `uploadFace`, `approvePermit`, `rejectPermit`, `createRule`, `updateRule`, `deleteRule`, `createHoliday`, `deleteHoliday` → otomatis set `syncRequested = true` untuk **semua device aktif**.
- Kiosk mendeteksi perubahan dalam waktu < 10 detik.

### 4.5 Offline-First Face Matching

```
At startup (sync):
  1. :kiosk-scanner download: StudentFaceRegistration.centroidEmbedding x N
  2. Store di local Room database + FloatArray cache
  3. Size: 10,000 students × 192 float × 4 bytes = ~7.5 MB
  4. Cache di RAM untuk fast cosine similarity

At runtime:
  1. User scan wajah
  2. Extract embedding (local MobileFaceNet model)
  3. Matching: Brute-force cosine similarity di RAM (~30ms)
  4. Decide + toggle
  5. Log ScanMetric ke local queue
  
At sync (nightly atau manual):
  1. Push queued ScanMetric ke backend
  2. Backend: compute DailyMetrics, detect anomalies
  3. Pull updated StudentFaceRegistration (jika ada re-register)
  4. Merge + update local cache
```

---

## 5. Data Model & Database

### 5.1 PostgreSQL (Master — via Prisma)

```prisma
// ==================== MASTER DATA ====================

model Student {
  id            String   @id @default(uuid()) @map("id")
  nim           String   @unique
  name          String
  studyProgram  String   @map("study_program")
  academicYear  String   @map("academic_year")
  phone         String?
  email         String?
  isActive      Boolean  @default(true) @map("is_active")
  photoUrl      String?  @map("photo_url")
  createdAt     DateTime @default(now()) @map("created_at")
  updatedAt     DateTime @updatedAt @map("updated_at")

  faceRegistration StudentFaceRegistration?
  scans            ScanMetric[]
  attendanceLogs AttendanceLog[]
  permits       Permit[]
  violations    Violation[]
  courseSchedules CourseSchedule[]
  permitQuotas  PermitQuota[]

  @@map("students")
}

model StudentFaceRegistration {
  id                String       @id @default(uuid())
  studentId         String       @unique
  student           Student      @relation(fields: [studentId], references: [id], onDelete: Cascade)
  
  // Centroid (mean embedding dari semua samples)
  centroidEmbedding Unsupported("vector(192)")
  
  // All samples untuk flexibility matching (optional tapi recommended)
  allEmbeddings     String?      // JSON array dari semua 192-d vectors
  
  // Quality metrics
  sampleCount       Int
  consistency       Float        // Rata-rata similarity dari sample ke centroid (0.0-1.0)
  minConsistency    Float        // Minimum similarity (early warning jika ada outlier)
  
  // Metadata
  registeredAt      DateTime     @default(now())
  updatedAt         DateTime     @updatedAt
  modelVersion      String       // "mobilefacenet_192d_v1"
  
  // Lifecycle
  status            String       @default("active")  // active, flagged_retrain, archived
  retryCount        Int          @default(0)
  notes             String?
  
  // Audit
  lastSuccessfulScanAt DateTime?
  lastFailedScanAt      DateTime?
  
  @@index([studentId])
  @@index([registeredAt])
  @@map("student_face_registrations")
}

model ScanMetric {
  id                String       @id @default(uuid())
  timestamp         DateTime     @default(now())
  
  // Predicted vs actual
  predictedStudentId String?
  actualStudentId    String
  
  // Scores
  topSimilarity     Float
  gap               Float
  confidence        Float
  decision          String       // MATCH_CONFIDENT, MATCH_MEDIUM, MATCH_WEAK, NO_MATCH
  
  // Detection quality
  detectionConfidence Float
  livenessScore     Float
  
  // Metadata
  scannedAt         DateTime
  deviceId          String?
  isCorrect         Boolean?     // Filled after human review (nullable = not yet verified)
  
  @@index([timestamp])
  @@index([actualStudentId])
  @@index([predictedStudentId])
  @@index([decision])
  @@map("scan_metrics")
}

model DailyMetrics {
  id                String       @id @default(uuid())
  date              DateTime     @unique
  
  // Counts
  totalScans        Int
  successfulMatches Int
  failedMatches     Int
  rejectedByQA      Int
  
  // Distribution
  matchConfidentCount Int
  matchMediumCount    Int
  matchWeakCount      Int
  noMatchCount        Int
  
  // Accuracy
  falsePositiveRate Float
  falseNegativeRate Float
  accuracy          Float
  
  // Metadata
  notes             String?
  
  @@index([date])
  @@map("daily_metrics")
}

model Admin {
  id           String   @id @default(uuid())
  username     String   @unique
  passwordHash String   @map("password_hash")
  displayName  String   @map("display_name")
  role         String   @default("admin") // admin | superadmin
  createdAt    DateTime @default(now()) @map("created_at")
  updatedAt    DateTime @updatedAt @map("updated_at")

  auditLogs    AuditLog[]
  syncRequests SyncRequest[]
  permits      Permit[]       // izin yang di-approve

  @@map("admins")
}


// ==================== ABSENSI (SCAN TOGGLE) ====================

// Setiap scan wajah = satu baris AttendanceLog.
// action = "keluar" | "kembali"
// Untuk cek status saat ini: ambil log terakhir hari ini.
//   - Jika terakhir "keluar" → saat ini DI LUAR KAMPUS
//   - Jika terakhir "kembali" atau tidak ada log → DI KAMPUS
model AttendanceLog {
  id              String   @id @default(uuid()) @map("id")
  studentId       String   @map("student_id")
  studentName     String   @map("student_name")
  action          String   // "keluar" | "kembali"
  timestamp       DateTime
  confidenceScore Float    @map("confidence_score")
  isViolation     Boolean  @default(false) @map("is_violation")
  violationType   String?  @map("violation_type")
  deviceId        String?  @map("device_id")
  photoCapture    String?  @map("photo_capture") // base64 foto saat scan (audit)
  isSynced        Boolean  @default(true) @map("is_synced")
  createdAt       DateTime @default(now()) @map("created_at")

  student         Student  @relation(fields: [studentId], references: [id])

  @@index([studentId])
  @@index([timestamp])
  @@index([action])
  @@map("attendance_logs")
}


// ==================== PERIZINAN ====================

// Dua jenis izin:
//   1. izin_harian    → auto-approved, hanya 1 hari, tanpa approval (kuota: 10x/bulan)
//   2. pengajuan_izin → butuh approval admin, bisa multi-hari, tanpa batas kuota
model Permit {
  id              String    @id @default(uuid()) @map("id")
  studentId       String    @map("student_id")
  type            String    // "izin_harian" | "pengajuan_izin"
  startDate       DateTime  @map("start_date")
  endDate         DateTime  @map("end_date")
  startTime       String?   @map("start_time") // "08:00" (null = full day)
  endTime         String?   @map("end_time")   // "16:00"
  status          String    @default("pending") // "approved" | "pending" | "rejected"
  // izin_harian → status langsung "approved"
  // pengajuan_izin → status default "pending", perlu approve admin
  reason          String?
  attachmentUrl   String?   @map("attachment_url")
  approvedById    String?   @map("approved_by_id") // adminId (null untuk izin_harian)
  approvedAt      DateTime? @map("approved_at")
  createdAt       DateTime  @default(now()) @map("created_at")
  updatedAt       DateTime  @updatedAt @map("updated_at")

  student         Student   @relation(fields: [studentId], references: [id])
  approvedBy      Admin?    @relation(fields: [approvedById], references: [id])

  @@index([studentId])
  @@index([status])
  @@index([type])
  @@map("permits")
}

model PermitQuota {
  id          String   @id @default(uuid()) @map("id")
  studentId   String   @map("student_id")
  month       Int      // 1-12
  year        Int
  permitsUsed Int      @default(0) @map("permits_used")
  maxPermits  Int      @default(10) @map("max_permits") // quota izin_harian per bulan

  @@unique([studentId, month, year])
  @@map("permit_quotas")
}


// ==================== ATURAN & PEMBATASAN ====================

model CampusRule {
  id            String   @id @default(uuid()) @map("id")
  dayOfWeek     Int      @map("day_of_week") // 0=Minggu ... 6=Sabtu
  startTime     String   @map("start_time")  // "08:00"
  endTime       String   @map("end_time")    // "16:00"
  isRestricted  Boolean  @default(true) @map("is_restricted") // true = tidak boleh keluar
  appliesToAll  Boolean  @default(true) @map("applies_to_all")
  studyProgram  String?  @map("study_program") // filter prodi
  academicYear  String?  @map("academic_year")  // filter angkatan
  priority      Int      @default(0)
  createdAt     DateTime @default(now()) @map("created_at")
  updatedAt     DateTime @updatedAt @map("updated_at")

  @@index([dayOfWeek])
  @@map("campus_rules")
}

model GlobalSetting {
  key         String   @id
  value       String
  description String?

  @@map("global_settings")
}


// ==================== JADWAL KULIAH ====================
// PER MAHASISWA — setiap mahasiswa punya jadwal sendiri
// Format import CSV: NIM, Matkul, Hari, Jam Mulai, Jam Selesai, Ruang, Dosen

model CourseSchedule {
  id            String   @id @default(uuid()) @map("id")
  studentId     String   @map("student_id")
  courseName    String   @map("course_name")
  dayOfWeek     Int      @map("day_of_week") // 0=Minggu ... 6=Sabtu
  startTime     String   @map("start_time")  // "08:00"
  endTime       String   @map("end_time")    // "10:00"
  room          String?
  lecturer      String?
  isActive      Boolean  @default(true) @map("is_active")
  createdAt     DateTime @default(now()) @map("created_at")

  student       Student  @relation(fields: [studentId], references: [id], onDelete: Cascade)

  @@index([studentId, dayOfWeek])
  @@map("course_schedules")
}

// ==================== HARI LIBUR ====================
// Admin input manual: tanggal merah / libur nasional
// Saat libur, semua aturan restricted dinonaktifkan (bebas keluar)

model Holiday {
  id          String   @id @default(uuid()) @map("id")
  date        DateTime @unique // tanggal libur
  name        String   // nama hari libur (contoh: "Idul Fitri", "17 Agustus")
  isActive    Boolean  @default(true) @map("is_active")
  createdAt   DateTime @default(now()) @map("created_at")

  @@map("holidays")
}


// ==================== PELANGGARAN ====================

model Violation {
  id              String    @id @default(uuid()) @map("id")
  studentId       String    @map("student_id")
  type            String    // "keluar_tanpa_izin" | "keluar_jam_terlarang" | "keluar_jam_kuliah" | "tidak_kembali" | "melebihi_batas_izin"
  description     String?
  action          String?   // action saat violation terjadi ("keluar" / "kembali")
  timestamp       DateTime
  relatedRuleId   String?   @map("related_rule_id")
  relatedPermitId String?   @map("related_permit_id")
  isResolved      Boolean   @default(false) @map("is_resolved")
  resolvedAt      DateTime? @map("resolved_at")
  resolvedNote    String?   @map("resolved_note")
  createdAt       DateTime  @default(now()) @map("created_at")

  @@index([studentId])
  @@index([type])
  @@index([timestamp])
  @@map("violations")
}


// ==================== SYNC & DEVICE ====================

model Device {
  deviceId    String    @id @map("device_id")
  name        String
  location    String?
  isActive    Boolean   @default(true) @map("is_active")
  lastPingAt  DateTime? @map("last_ping_at")
  batteryLevel Float?   @map("battery_level")
  createdAt   DateTime  @default(now()) @map("created_at")
  updatedAt   DateTime  @updatedAt @map("updated_at")

  syncRequests SyncRequest[]
  syncLogs     SyncLog[]

  @@map("devices")
}

model SyncRequest {
  id            String   @id @default(uuid()) @map("id")
  deviceId      String   @map("device_id")
  requestedAt   DateTime @default(now()) @map("requested_at")
  isProcessed   Boolean  @default(false) @map("is_processed")
  processedAt   DateTime? @map("processed_at")
  requestedById String?  @map("requested_by_id")

  device        Device   @relation(fields: [deviceId], references: [deviceId], onDelete: Cascade)
  requestedBy   Admin?   @relation(fields: [requestedById], references: [id])

  @@map("sync_requests")
}

model SyncLog {
  id          String   @id @default(uuid()) @map("id")
  deviceId    String   @map("device_id")
  syncType    String   @map("sync_type") // "midnight" | "manual" | "reconnect"
  status      String   // "success" | "partial" | "failed"
  logsCount   Int      @default(0) @map("logs_count")
  createdAt   DateTime @default(now()) @map("created_at")

  device      Device   @relation(fields: [deviceId], references: [deviceId], onDelete: Cascade)

  @@map("sync_logs")
}


// ==================== AUDIT & NOTIFIKASI ====================

model AuditLog {
  id          String   @id @default(uuid()) @map("id")
  adminId     String   @map("admin_id")
  action      String
  entityType  String   @map("entity_type")
  entityId    String   @map("entity_id")
  details     String?  // JSON
  createdAt   DateTime @default(now()) @map("created_at")

  admin       Admin    @relation(fields: [adminId], references: [id])

  @@index([adminId])
  @@index([entityType])
  @@index([createdAt])
  @@map("audit_logs")
}

model ImportBatch {
  id          String   @id @default(uuid()) @map("id")
  filename    String
  totalRows   Int      @map("total_rows")
  successRows Int      @map("success_rows")
  failedRows  Int      @map("failed_rows")
  errors      String?
  createdAt   DateTime @default(now()) @map("created_at")

  @@map("import_batches")
}

model Notification {
  id          String   @id @default(uuid()) @map("id")
  adminId     String?  @map("admin_id") // null = broadcast ke semua admin
  type        String   // "violation" | "permit_pending" | "sync_done" | "device_offline"
  title       String
  message     String
  isRead      Boolean  @default(false) @map("is_read")
  linkTo      String?  // deeplink ke screen terkait
  createdAt   DateTime @default(now()) @map("created_at")

  @@index([adminId, isRead])
  @@index([createdAt])
  @@map("notifications")
}
```

### 5.2 Room Database (Local Cache)

| Entity | Catatan |
|---|---|
| `StudentEntity` | Cache data mahasiswa |
| `FaceVectorEntity` | Vektor wajah (FloatArray → ByteArray via TypeConverter) |
| `AttendanceLogEntity` | Log scan lokal (isSynced = false = belum dikirim) |
| `PermitEntity` | Cache izin (harian + pengajuan) |
| `CampusRuleEntity` | Aturan untuk verifikasi offline |
| `CourseScheduleEntity` | Jadwal kuliah per mahasiswa |
| `GlobalSettingEntity` | Pengaturan global (key-value) |
| `HolidayEntity` | Tanggal libur nasional |
| `SyncMetadata` | Tracking sync terakhir (lastSyncTimestamp) |

### 5.3 In-Memory (Kiosk Scanner)

```kotlin
data class FaceIndex(
    val centroids: Map<String, FloatArray>,  // studentId → centroid embedding (192-d)
    val studentMap: Map<String, StudentBrief>
)

data class StudentBrief(
    val id: String, val nim: String, val name: String
)

// Match result dari adaptive threshold algorithm
data class MatchResult(
    val matched: Boolean,
    val studentId: String?,
    val topScore: Float,
    val runnerUpScore: Float,
    val gap: Float,
    val confidence: Float,
    val decision: MatchDecision
)

enum class MatchDecision {
    MATCH_CONFIDENT,  // score >= 0.90, gap >= 0.08 → auto approve
    MATCH_MEDIUM,     // score >= 0.85, gap >= 0.05 → approve + mark review
    MATCH_WEAK,       // score >= 0.80, gap >= 0.03 → manual confirmation
    NO_MATCH          // below thresholds → reject
}

// State toggle per mahasiswa hari ini
data class ToggleState(
    val studentId: String,
    val currentState: State, // DI_KAMPUS atau DI_LUAR
    val lastAction: String?, // "keluar" atau "kembali"
    val scanCount: Int
)

enum class State { DI_KAMPUS, DI_LUAR }

// Scan metric untuk logging ke backend
data class ScanMetricLog(
    val timestamp: Long,
    val deviceId: String,
    val detectionConfidence: Float,
    val livenessScore: Float,
    val topSimilarity: Float,
    val gap: Float,
    val confidence: Float,
    val decision: String,
    val predictedStudentId: String?,
    val responseTimeMs: Long
)
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

### 6.13 Holiday (Hari Libur)
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/holidays` | List semua hari libur |
| POST | `/api/holidays` | Tambah hari libur |
| PUT | `/api/holidays/:id` | Edit hari libur |
| DELETE | `/api/holidays/:id` | Hapus hari libur |
| GET | `/api/holidays/today` | Cek apakah hari ini libur |

### 6.14 Audit & Notifikasi
| Method | Endpoint |
|---|---|
| GET | `/api/audit` |
| GET | `/api/notifications` |
| PUT | `/api/notifications/:id/read` |

### 6.15 Realtime Events (SSE)
| Method | Endpoint | Deskripsi |
|---|---|---|
| GET | `/api/events/stream` | SSE stream — admin terima event realtime |
| POST | `/api/events/trigger-change` | Internal — trigger sync flag untuk semua kiosk saat DB berubah |

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
| Scan duplikat (studentId + timestamp sama dalam 2 detik) | Kiosk debounce 2 detik, abaikan scan berulang |
| Log sudah terlanjur dikirim (retry) | Backend ignore duplicate berdasarkan id unik |
| Sync gagal di tengah jalan | Batch dikirim partial, lanjut batch berikutnya. Gunakan idempotency key. |
| Dua kiosk berbeda (gerbang A & B) scan mahasiswa sama | Kedua log TETAP MASUK. Ini valid: keluar gerbang A, kembali gerbang B. Toggle state dihitung dari log TERAKHIR hari ini. |
| Admin approve izin saat kiosk offline | Flag syncRequested=true, nanti sync saat online. Kiosk download rules + permits terbaru. |

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
| `sync_poll_interval_seconds` | `10` | Interval polling sync request (detik) |

### 8.3 Logika Evaluasi (Offline-capable)

```
Function canScanOut(student, time, today):
  1. Cek hari libur (Holiday)
     - Hari ini libur nasional? → IZINKAN (skip semua aturan)

  2. Cek jam operasional
     - time < operational_start → TOLAK (tampilkan "di luar jam operasional — kampus belum buka")
     - time > operational_end   → TOLAK (tampilkan "di luar jam operasional — kampus sudah tutup")

  3. Cek izin aktif hari ini
     - Ada izin_harian ATAU pengajuan_izin approved? → IZINKAN (skip aturan restricted + jadwal kuliah)
     - ⚠️ PENTING: Izin hanya meng-override aturan jam, BUKAN jam operasional.
       Mahasiswa tetap TIDAK BISA keluar di luar jam operasional meskipun punya izin.

  4. Cek restricted hours (CampusRule)
     - Cocokkan dayOfWeek dan time di antara startTime-endTime
     - Filter prodi/angkatan cocok?
     - Ada yang cocok (isRestricted=true) → VIOLATION (kecuali point 3 terpenuhi)

  5. Cek jadwal kuliah (CourseSchedule)
     - Cocokkan dayOfWeek + startTime <= time <= endTime
     - Ada jadwal → VIOLATION (kecuali point 3 terpenuhi)

  6. Jika violation → simpan Violation + flag isViolation di AttendanceLog
     Tampilkan peringatan di layar kiosk (overlay kuning)
     Semua violation auto-resolved saat mahasiswa scan "kembali"
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
| Lupa scan pas balik (masuk tanpa scan) | Auto close sesi pukul 23.59. Jika state terakhir masih "keluar" → generate violation "tidak_kembali" otomatis |
| Libur nasional (Holiday) | Semua aturan restricted + jadwal kuliah di-nonaktifkan. Bebas keluar tanpa izin. Admin input tanggal libur manual via Settings. |
| Mahasiswa tidak bisa berkedip (medis/kacamata) | Liveness EAR threshold diturunkan (0.15 default → 0.10). Fallback: admin bisa matikan liveness via GlobalSettings. |

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

- Default: 10 izin harian **per bulan kalender** per mahasiswa (Januari, Februari, dst.)
- Bukan rolling 30 hari — reset tiap tanggal 1 pukul 00:00
- Bisa diubah via GlobalSettings (`max_daily_permit_per_month`)
- Admin bisa lihat sisa kuota di StudentDetailScreen: `GET /api/permits/quota/:studentId`
- Reset otomatis: sistem cek bulan berjalan saat membuat izin baru. Jika bulan berbeda dari record terakhir → buat record PermitQuota baru dengan counter 0
- Kuota override per mahasiswa bisa di-set manual di PermitQuota.maxPermits

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
│   ├── FaceDetector.kt          # YOLOv8 Face (primary) + MediaPipe (fallback)
│   ├── FaceEmbedder.kt          # MobileFaceNet 192-d, pixel/255.0 normalization
│   ├── FaceMatcher.kt           # Adaptive threshold: Top-K + gap analysis
│   ├── LivenessDetector.kt      # EAR-based: 2+ blinks in 3s window
│   ├── FaceIndex.kt             # In-memory centroid cache
│   └── QualityChecker.kt        # Blur detection, norm validation, embedding entropy
├── database/
│   ├── AppDatabase.kt
│   ├── entity/
│   │   ├── StudentFaceRegistrationEntity.kt
│   │   ├── ScanMetricEntity.kt
│   │   └── DailyMetricsEntity.kt
│   ├── dao/
│   └── converter/
├── network/
│   ├── ApiClient.kt
│   ├── ApiService.kt
│   ├── dto/
│   └── interceptor/
├── sync/
│   ├── FaceSyncWorker.kt        # Sync centroid embeddings
│   ├── AttendanceSyncWorker.kt  # Upload queued ScanMetrics
│   ├── ScanMetricSyncWorker.kt  # Push ScanMetric ke backend
│   ├── SyncPoller.kt            # Polling sync request
│   └── SyncManager.kt
├── metrics/
│   ├── ScanMetricsCollector.kt  # Collect per-scan metrics
│   ├── DailyMetricsAggregator.kt # Aggregate FPR/FNR/accuracy
│   └── AlertMonitor.kt          # Threshold alerts (FPR > 1%, FNR > 2%)
└── model/
    ├── Student.kt
    ├── AttendanceLog.kt
    ├── Permit.kt
    ├── CampusRule.kt
    ├── Violation.kt
    ├── CourseSchedule.kt
    ├── MatchResult.kt           # Adaptive match result
    └── ScanMetricLog.kt         # Per-scan metric log
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
├── registration/
│   ├── RegistrationEngine.kt    # Multi-frame capture + centroid computation
│   ├── QualityValidator.kt      # Blur, lighting, detection confidence checks
│   └── RegistrationScreen.kt    # UI: video capture + quality score + retry
├── matching/
│   ├── MatchEngine.kt           # Adaptive threshold: Top-K + gap analysis
│   └── MatchResult.kt           # MatchDecision enum + scores
├── toggle/
│   ├── ToggleEngine.kt          # Logic determine keluar/kembali
│   ├── ToggleState.kt           # State management per-student
│   └── SessionTracker.kt        # Track durasi di luar
├── rule/
│   ├── RuleChecker.kt
│   └── RuleCache.kt
├── metrics/
│   └── ScanMetricsCollector.kt  # Collect per-scan metrics ke local queue
├── ui/
│   ├── ScannerScreen.kt
│   ├── RegistrationScreen.kt    # Registration UI
│   ├── ResultOverlay.kt         # Updated: shows MATCH_CONFIDENT/MEDIUM/WEAK/NO_MATCH
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
├── metrics/                              # Scan metrics & monitoring
│   ├── ScanMetricsScreen.kt             # Decision distribution, FPR/FNR, response time
│   ├── ScanMetricsViewModel.kt
│   ├── MatchReviewScreen.kt             # Review MATCH_WEAK scans + manual override
│   └── MatchReviewViewModel.kt
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
| `RegistrationScreen` | Video capture 3-5 detik + quality check (blur, lighting) + centroid computation + consistency score display. |
| `ResultOverlay` | Animasi hasil. Hijau = scan sukses (MATCH_CONFIDENT) + nama mahasiswa + aksi (keluar/kembali). Kuning = MATCH_MEDIUM (approved, flagged for review). Orange = MATCH_WEAK (manual confirmation). Merah = tidak dikenal (NO_MATCH). |

### 14.2 Admin App

| Screen | Deskripsi |
|---|---|
| `LoginScreen` | Username + password |
| `DashboardScreen` | Card stats: di kampus, di luar, izin aktif, violation hari ini + recent scan feed + **real-time scan counter + FPR/FNR trends** |
| `StudentListScreen` | Search + filter prodi/angkatan |
| `StudentDetailScreen` | Data + status toggle + history scan + violation + **registration quality (consistency, sample count, model version)** |
| `StudentFormScreen` | Tambah/edit |
| `FaceRegisterScreen` | Kamera + panduan oval + **quality check (blur, lighting, detection confidence) + retry strategy** |
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
| **`ScanMetricsScreen`** | **Decision distribution pie chart + response time histogram + flagged scans untuk manual review** |
| **`MatchReviewScreen`** | **Review MATCH_WEAK scans: show face photo + top 3 candidates + accept/reject manual override** |

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
| Tunnel | **Cloudflare Tunnel** — dikonfigurasi di server, domain facegate.utc.web.id mengarah ke port 8150 |
| Port forwarding | Diatur via web panel server — membelokkan traffic ke backend:8150 |

---

## 16. Phase / Milestone Pengembangan

### Phase 1: Foundation + Core Pipeline

**Tujuan**: Backend + Kiosk bisa scan toggle end-to-end offline dengan production-grade face recognition.

#### Week 1: Core Foundation
- [ ] Setup monorepo + Gradle + version catalog
- [ ] Setup backend (Bun + Prisma + PostgreSQL + pgvector + Docker)
- [ ] Database schema + migration + seed (termasuk StudentFaceRegistration, ScanMetric, DailyMetrics)
- [ ] API: CRUD students, attendance scan, sync, rules
- [ ] `:core` — Room database + TypeConverters
- [ ] `:core` — TFLite MobileFaceNet loader (192-d)
- [ ] `:core` — YOLOv8 Face detector (primary) + MediaPipe (fallback)
- [ ] `:core` — Cosine similarity matcher dengan adaptive threshold
- [ ] Test dengan synthetic data

#### Week 2: Registration + Verification Pipeline
- [ ] `:core` — Network layer (Retrofit)
- [ ] `:kiosk-scanner` — CameraX + frame analyzer
- [ ] `:kiosk-scanner` — Registration screen (video capture + quality check + centroid computation)
- [ ] `:kiosk-scanner` — Verification screen (scan + matching + Top-K + gap analysis + UI)
- [ ] `:kiosk-scanner` — Toggle engine (keluar/kembali)
- [ ] `:kiosk-scanner` — Metrics collection + local queue
- [ ] `:kiosk-scanner` — Sync (midnight + polling sync request + push ScanMetric)
- [ ] Backend: POST /scans endpoint (save ScanMetric)
- [ ] Test dengan 10-20 volunteer scans
- [ ] Measure: detection rate, liveness rate, end-to-end latency

#### Week 3: Validation & Tuning
- [ ] Expand test: 50-100 volunteer registrations
- [ ] Collect raw FPR/FNR data
- [ ] Analyze confidence distribution
- [ ] Adjust thresholds berdasarkan data
- [ ] Document results (spreadsheet: date, FPR, FNR, threshold_config)

#### Week 4: Soft Launch Prep
- [ ] Re-test dengan updated thresholds
- [ ] Validate: FPR < 1%, FNR < 2%
- [ ] Quality review: inspect MATCH_WEAK cases
- [ ] Decide: threshold lock or keep tuning
- [ ] Sign-off: ready untuk 1 kiosk production

**Total Phase 1**: ~4 minggu (28 hari)

| Task | Estimasi |
|---|---|
| Setup monorepo + Gradle + version catalog | 1 hari |
| Setup backend (Bun + Prisma + PostgreSQL + pgvector + Docker) | 2 hari |
| Database schema + migration + seed | 1 hari |
| API: CRUD students, attendance scan, sync, rules, scans | 3 hari |
| `:core` — Room database + TypeConverters | 1 hari |
| `:core` — TFLite face embedder (MobileFaceNet 192-d) + YOLOv8 Face detector | 3 hari |
| `:core` — Cosine similarity matcher + adaptive threshold | 2 hari |
| `:core` — Network layer (Retrofit) | 1 hari |
| `:kiosk-scanner` — CameraX + frame analyzer | 2 hari |
| `:kiosk-scanner` — Registration screen + centroid computation | 2 hari |
| `:kiosk-scanner` — Verification screen + Top-K matching + gap analysis | 3 hari |
| `:kiosk-scanner` — Toggle engine (keluar/kembali) | 1 hari |
| `:kiosk-scanner` — Metrics collection + local queue | 1 hari |
| `:kiosk-scanner` — Sync (midnight + polling + push ScanMetric) | 2 hari |
| Validation & tuning (50-100 volunteers, FPR/FNR measurement) | 4 hari |
| Soft launch prep + threshold lock | 2 hari |
| **Total Phase 1** | **~32 hari** |

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
| Dokumentasi + deployment guide (Docker + Cloudflare Tunnel + domain facegate.utc.web.id) | 2 hari |
| **Total Phase 5** | **~11 hari** |

---

## 17. Troubleshooting & Debug Guide

### Common Issues & Resolution

#### Issue 1: High FPR (False Positives)

```
Symptom: Wrong student matched (e.g., STU-001 scanned, STU-002 matched)

Debugging:
  1. Check similarity distribution:
     SELECT decision, topSimilarity, gap FROM ScanMetric
     WHERE isCorrect = false AND predictedStudentId != actualStudentId
  2. Look for FP cases: top_score >= 0.85, but gap < 0.05
  3. Root cause: two very similar faces (twins? relatives?)
  
Solution:
  • Increase gap threshold: 0.05 → 0.08
  • OR increase top_score requirement: 0.85 → 0.90
  • Manual override untuk problem pairs
```

#### Issue 2: High FNR (False Negatives)

```
Symptom: Registered student rejected (should match, but decision=NO_MATCH)

Debugging:
  1. Check: topSimilarity < 0.80 untuk rejected scans
  2. Possible causes:
     a) Registration was poor (low consistency)
     b) Lighting different between registration & scan
     c) Significant pose variation
  
Solution:
  • Re-register problematic students (new centroid)
  • Adjust kiosk lighting
  • Lower threshold temporarily: 0.85 → 0.80 (aber monitor FPR)
```

#### Issue 3: Liveness Detection Fails

```
Symptom: Users can't pass liveness check (need multiple retries)

Debugging:
  1. Check: livenessScore < 0.70 di rejected scans
  2. Possible causes:
     a) Dim lighting (EAR detection unreliable)
     b) Glasses/sunglasses (landmark occlusion)
     c) User not blinking naturally
  
Solution:
  • Increase kiosk lighting (add LED ring light)
  • Relax EAR threshold slightly: 0.70 → 0.65
  • Add user guidance: "Berkedip secara alami, jangan dilebih-lebihkan"
```

#### Issue 4: Model Quality Degradation

```
Symptom: Median(confidence) dropped, FPR trending up

Debugging:
  1. Check if camera hardware changed (e.g., replacement)
  2. Check if model quantization mismatch (FP32 vs INT8)
  3. Check if centroid embeddings became stale
  
Solution:
  • Verify camera specs (resolution, lens quality)
  • Re-export TFLite model dari training environment
  • Re-register all students (force refresh centroids)
```

---

## 18. Face Recognition Implementation Decisions

| No | Keputusan | Pilihan | Alasan | Trade-off |
|---|---|---|---|---|
| 1 | Embedding dimension | 192-d (MobileFaceNet) | Resource efficient, mobile-friendly | Accuracy 96-97%, bukan 99.8%+ |
| 2 | Face detection | YOLOv8 Face (primary) MediaPipe (fallback) | Better bounding box consistency | +6-8 MB model size |
| 3 | Threshold matching | Adaptive gap-based (0.80-0.90) | Prevents ambiguity, transparent confidence | Require frequent tuning week 1-2 |
| 4 | Registration samples | 5-10 frames per student | Robust centroid, consistency check | Longer registration time (5-10s) |
| 5 | Liveness detection | EAR (eye blink) only | Fast, rule-based, no ML overhead | Vulnerable ke advanced spoofing (rare) |
| 6 | Matching strategy | Top-K ranking + gap analysis | Production standard, auditable | Higher latency (~30ms vs ~10ms brute-force) |
| 7 | Metrics collection | Full ScanMetric logging | Audit trail, FPR/FNR tracking | +1-2 MB database per 1000 scans |
| 8 | Incremental learning | Alpha=0.05 untuk high confidence scans | Adapt ke penampilan changes | Risk: drift jika confidence score miscalibrated |
| 9 | Offline matching | Local centroid embeddings + daily sync | Kiosk survive network outage | Stale data jika student re-register |
| 10 | Fallback: no match | Manual admin review + override | Prevent false rejection, audit trail | UX interruption for borderline cases |

---

## 19. Keputusan Final

| No | Pertanyaan | Keputusan |
|---|---|---|
| 1 | Model Face Detection | ✅ **YOLOv8 Face** (primary) — MediaPipe FaceDetector sebagai fallback |
| 2 | Model Face Embedding | ✅ **MobileFaceNet 192-d** — TFLite, normalized [0,1] (pixel/255.0) |
| 3 | Liveness Detection | ✅ **Eye Aspect Ratio (EAR)** — 2+ blinks dalam 3 detik, rule-based |
| 4 | Matching Strategy | ✅ **Adaptive gap-based** — Top-K ranking + gap analysis (0.80-0.90 threshold) |
| 5 | Registration | ✅ **Multi-sample centroid** — 5-10 frames per student, consistency >= 0.80 |
| 6 | Face Storage | ✅ **StudentFaceRegistration** — centroid + all embeddings + quality metrics |
| 7 | Metrics | ✅ **Full ScanMetric logging** — FPR/FNR tracking, DailyMetrics aggregation |
| 8 | Backend Framework | ✅ **Elysia** (Bun-native) |
| 9 | CSV Import Format | ✅ NIM, Nama, Prodi, Angkatan, No HP, Email |
| 10 | Jadwal Kuliah Format | ✅ NIM, Matkul, Hari, Jam Mulai, Jam Selesai, Ruang, Dosen |
| 11 | Dashboard Web | ✅ **Skip dulu** — fokus ke Admin App dulu |
| 12 | Multiple Kiosk | ✅ **Langsung multi-gerbang** dari awal |
| 13 | Emergency Mode | ✅ **Skip** — tidak perlu untuk sekarang |
| 14 | Domain | ✅ **facegate.utc.web.id** — Cloudflare Tunnel dari home server |
| 15 | Backend Port | ✅ **8150** — dibelokkan via web panel server |
| 16 | Hosting | ✅ **Docker** di home server + Cloudflare Tunnel |
| 17 | Kursus Kuliah | ✅ **Per Mahasiswa** — ada relasi studentId |
| 18 | Jam Operasional | ✅ **TOLAK** scan di luar jam operasional (baik sebelum start maupun setelah end) |
| 19 | Violation Auto-Resolve | ✅ **Semua tipe** violation auto-resolved saat mahasiswa kembali |
| 20 | Libur Nasional | ✅ **Input manual admin** — model Holiday, saat libur semua aturan skip |
| 21 | Kuota Izin | ✅ **Per bulan kalender** (reset tiap tanggal 1), bukan rolling 30 hari |
| 22 | Incremental Learning | ✅ **Alpha=0.05** untuk high confidence scans (>= 0.90), adaptasi penampilan |
| 23 | Offline Matching | ✅ **Local centroid embeddings** + daily sync — kiosk survive network outage |
| 24 | Fallback: No Match | ✅ **Manual admin review** + override — prevent false rejection |

---

> **Status**: ✅ Planning selesai — sudah sinkron dengan implementasi production-grade face recognition.
> **Fase saat ini**: Phase 1 (Foundation + Core Pipeline) — focus ke validation FPR/FNR dengan 50-100 volunteers.
> **Next step**: Implementasi YOLOv8 Face detector + adaptive threshold matching + registration pipeline + metrics collection.
