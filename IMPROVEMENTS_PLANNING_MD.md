# 📋 PLANNING.MD REVISIONS — 192-d Production Pipeline

Ini adalah **checklist point-by-point** yang harus diubah di planning.md untuk implement production-grade 192-d face recognition system.

---

## 🔴 CRITICAL CHANGES (MUST DO)

### **1. Section 3.3 — AI Pipeline (REVISE COMPLETELY)**

**Status Saat Ini:**
```
Face Detection: MediaPipe
Threshold: 0.6 (TERLALU PERMISIF!)
```

**Masalah:**
- Threshold 0.6 adalah cosine similarity 53° — TERLALU RENDAH untuk 1:N matching
- Tidak ada quality check pada detection confidence
- Tidak ada adaptive threshold atau gap analysis
- Assumption: satu embedding per orang (padahal perlu multi-sample)

**Perbaikan Yang Diperlukan:**

```markdown
### 3.3 AI Pipeline

#### 3.3.1 Face Detection & ROI Extraction

**Model**: YOLOv8 Face OR MediaPipe FaceDetector

| Komponen | Pilihan 1: YOLOv8 Face | Pilihan 2: MediaPipe |
|---|---|---|
| Akurasi deteksi | 98-99% | 95-97% |
| Bounding box konsistensi | Sangat presisi, tight | Kadang loose |
| Confidence score reliable | ✅ Ya | ⚠️ Kadang tinggi palsu |
| Multi-face handling | Excellent | Good |
| Model size (TFLite) | ~6-8 MB | ~350 KB |
| Recommended | ✅ PILIH INI | Fallback jika resource ketat |

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
2. Crop ke standard size: 224 × 224 pixel
3. Normalize input: [0..1] float32 atau [-1..1] sesuai model
4. Check image blur/noise (Laplacian variance > threshold 100)
   IF blur: REJECT
```

#### 3.3.2 Liveness Detection

**Current**: EAR-based (eye blinking)
**Status**: Good, keep it but improve.

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
- Size: 112 × 112 atau 224 × 224 (check model spec)
- Format: float32, normalized [-1, 1] atau [0, 1]
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

| Fase | top_score ≥ | gap ≥ | Action |
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
```

**Penjelasan:**
- Threshold 0.6 → 0.85-0.90 (more realistic)
- Gap analysis mencegah ambiguity ketika ada 2 orang mirip
- Confidence score jadi transparent
- WEAK matches bisa di-flag untuk human review (audit trail)

---

### **2. Database Schema (ADD/MODIFY)**

**Status Saat Ini:**
```
StudentFaceVector (baru):
  studentId (PK)
  faceVector (192-d float array)
```

**Masalah:**
- Hanya simpan 1 vector per student (tidak robust)
- Tidak ada metadata kualitas atau timestamp
- Tidak bisa track multi-sample consistency
- Tidak ada field untuk update history

**Perbaikan:**

Tambah `prisma/schema.prisma`:

```prisma
// Add this model SEBELUM atau SESUDAH StudentFaceVector

model StudentFaceRegistration {
  id                String       @id @default(cuid())
  studentId         String       @unique  // Foreign key
  student           Student      @relation(fields: [studentId], references: [id], onDelete: Cascade)
  
  // Centroid (mean embedding dari semua samples)
  centroidEmbedding Bytes        // 192 * 4 bytes = 768 bytes binary
  
  // All samples untuk flexibility matching (optional tapi recommended)
  allEmbeddings     Bytes?       // JSON array dari semua 192-d vectors [stored as JSONB or blob]
  
  // Quality metrics
  sampleCount       Int          // Berapa banyak samples saat registrasi (5-10)
  consistency       Float        // Rata-rata similarity dari sample ke centroid (0.0-1.0)
  minConsistency    Float        // Minimum similarity (early warning jika ada outlier)
  
  // Metadata
  registeredAt      DateTime     @default(now())
  updatedAt         DateTime     @updatedAt  // Last time centroid updated
  modelVersion      String       // "mobilefacenet_192d_v1" - track model changes
  
  // Lifecycle
  status            String       @default("active")  // active, flagged_retrain, archived
  retryCount        Int          @default(0)  // Berapa kali user re-register
  notes             String?      // Admin notes
  
  // Audit
  lastSuccessfulScanAt DateTime?
  lastFailedScanAt      DateTime?
  
  @@index([studentId])
  @@index([registeredAt])
}

model ScanMetric {
  id                String       @id @default(cuid())
  timestamp         DateTime     @default(now())
  
  // Predicted vs actual
  predictedStudentId String?
  actualStudentId    String      // Dari toggle yang sebelumnya (actual ground truth)
  
  // Scores
  topSimilarity     Float
  gap               Float        // gap = top - runner_up
  confidence        Float        // Derived confidence
  decision          String       // MATCH_CONFIDENT, MATCH_MEDIUM, MATCH_WEAK, NO_MATCH
  
  // Detection quality
  detectionConfidence Float
  livenessScore     Float
  
  // Metadata
  scannedAt         DateTime
  deviceId          String?      // Which kiosk
  isCorrect         Boolean?     // Filled after human review (nullable = not yet verified)
  
  @@index([timestamp])
  @@index([actualStudentId])
  @@index([predictedStudentId])
  @@index([decision])
}

model DailyMetrics {
  id                String       @id @default(cuid())
  date              DateTime     @unique
  
  // Counts
  totalScans        Int
  successfulMatches Int
  failedMatches     Int
  rejectedByQA      Int
  
  // Distribution
  matchConfidentCount Int       // decision = MATCH_CONFIDENT
  matchMediumCount    Int       // decision = MATCH_MEDIUM
  matchWeakCount      Int       // decision = MATCH_WEAK
  noMatchCount        Int       // decision = NO_MATCH
  
  // Accuracy
  falsePositiveRate Float       // Dari manual review
  falseNegativeRate Float       // Dari manual review
  accuracy          Float       // (correct / total) * 100
  
  // Metadata
  notes             String?
  
  @@index([date])
}
```

**Penjelasan:**
- `StudentFaceRegistration`: Simpan centroid + semua samples + quality metrics
- `ScanMetric`: Log setiap scan untuk audit trail + analytics
- `DailyMetrics`: Aggregate FPR/FNR/accuracy harian (untuk monitoring)

---

### **3. TAMBAH Section Baru: Registration Process (DETAIL)**

**Lokasi**: Masukkan SETELAH section 3.3, before section 4.

```markdown
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
  └─ IF min_consistency ≥ 0.80:
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
| Detection confidence | ≥ 0.85 | Skip frame, retry |
| Liveness score | ≥ 0.70 | Reject, ask blink naturally |
| Sample count | ≥ 5 | Need at least 5 good frames |
| Min consistency | ≥ 0.80 | Re-register, more stable environment |
| Blur detection (Laplacian) | ≥ 100 | Skip, ask for better lighting |

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
  IF confidence ≥ 0.90:
    Alpha = 0.05
    centroid_new = centroid_old * (1 - alpha) + scan_embedding * alpha
    Save centroid_new (continuous adaptation)
  
  IF confidence < 0.75:
    Flag untuk manual review (jangan auto-update)
```

**Penjelasan**: Incremental learning bantu adapt ke perubahan penampilan, tapi hanya kalau confidence tinggi.

---

### **4. TAMBAH Section Baru: Verification & Quality Assurance**

**Lokasi**: SETELAH registration, sebelum toggle engine.

```markdown
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
| Detection | Confidence | ≥ 0.85 | If fail: reject, retry |
| Liveness | EAR blink | 2+ dalam 3s | If fail: reject, retry |
| Embedding | Norm validation | 0.9-1.1 | If fail: re-extract |
| Matching | Top score | ≥ 0.80 | If fail: no match |
| Matching | Gap | Based on tier | If fail: check tier down |
| Confidence | Final score | ≥ 0.75 | If < 0.75: manual review |

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

---

### **5. TAMBAH Section Baru: Metrics & Monitoring**

**Lokasi**: SETELAH verification, sebelum toggle engine.

```markdown
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

### **6. MODIFY Section 5: Database Schema**

**Status saat ini:**
```
StudentFaceVector hanya ada studentId + vector
```

**Action**: 
- DELETE model `StudentFaceVector`
- GUNAKAN model baru `StudentFaceRegistration` (dari point 2 di atas)

**Update teknis di schema.prisma**:
```
Hapus:
model StudentFaceVector { ... }

Tambah:
model StudentFaceRegistration { ... }
model ScanMetric { ... }
model DailyMetrics { ... }

Update Student relation:
model Student {
  ...
  faceRegistration  StudentFaceRegistration?  // one-to-one
  scans             ScanMetric[]              // one-to-many
  ...
}
```

---

### **7. MODIFY Section 3.1 (Tech Stack) — Update TensorFlow version**

**Status:**
```
TensorFlow 2.15 + CUDA 12.4
```

**Update ke:**
```
TensorFlow 2.16+ (latest stable)
TFLite: ensure model compatibility dengan Android 12+ (API 31+)

VALIDATE:
  - MobileFaceNet .tflite model inference di emulator
  - Verify output: 192-d float array
  - Check quantization (FP32? INT8? FP16?)
    → Recommend FP32 untuk accuracy, INT8 kalau resource ketat
```

---

### **8. TAMBAH Deployment Checklist (Section 16 Phase 1)**

**Current section 16 (Phase/Milestone) terlalu high-level.**

**Action**: Expand Phase 1 dengan deployment sub-steps:

```markdown
### Phase 1.1: Foundation + Core Pipeline (Weeks 1-2)

#### Week 1
- [ ] `:core` — TFLite MobileFaceNet loader
- [ ] `:core` — YOLOv8 Face detector (alternative)
- [ ] `:core` — Cosine similarity matcher
- [ ] Database: StudentFaceRegistration schema + migration
- [ ] Database: ScanMetric schema + migration
- [ ] Test dengan synthetic data

#### Week 2
- [ ] `:kiosk-scanner` — Registration screen (video capture + quality check)
- [ ] `:kiosk-scanner` — Verification screen (scan + matching + UI)
- [ ] `:kiosk-scanner` — Metrics collection + local storage
- [ ] Backend: POST /scans endpoint (save ScanMetric)
- [ ] Test dengan 10-20 volunteer scans
- [ ] Measure: detection rate, liveness rate, end-to-end latency

### Phase 1.2: Validation (Weeks 3-4)

#### Week 3
- [ ] Expand test: 50-100 volunteer registrations
- [ ] Collect raw FPR/FNR data
- [ ] Analyze confidence distribution
- [ ] Adjust thresholds berdasarkan data
- [ ] Document results (spreadsheet: date, FPR, FNR, threshold_config)

#### Week 4
- [ ] Re-test dengan updated thresholds
- [ ] Validate: FPR < 1%, FNR < 2%
- [ ] Quality review: inspect MATCH_WEAK cases
- [ ] Decide: threshold lock or keep tuning
- [ ] Sign-off: ready untuk 1 kiosk production

### Phase 1.3: Soft Launch (Week 5)

- [ ] Deploy ke 1 gerbang (limited hours: 8am-10am)
- [ ] Monitor real-world scans
- [ ] Collect FPR/FNR dari actual usage
- [ ] Admin review MATCH_WEAK scans daily
- [ ] If stable > 5 hari: expand ke 2 kiosk

---

### Phase 2: Admin App + Izin (Weeks 6-8)

[Keep existing]

---

### Phase 3: Monitoring + Violation (Weeks 9-10)

[Keep existing]

---

### Phase 4: Full Production (Week 11+)

[Keep existing]
```

---

### **9. TAMBAH Section: Decision Log (untuk transparency)**

**Lokasi**: Tambah SEBELUM section 17 (Keputusan Final).

```markdown
## 16.5 Face Recognition Implementation Decisions

| No | Keputusan | Pilihan | Alasan | Trade-off |
|---|---|---|---|---|
| 1 | Embedding dimension | 192-d (MobileFaceNet) | Resource efficient, mobile-friendly | Accuracy 96-97%, bukan 99.8%+ |
| 2 | Face detection | YOLOv8 Face (primary) MediaPipe (fallback) | Better bounding box consistency | +6-8 MB model size |
| 3 | Threshold matching | Adaptive gap-based (0.80-0.90) | Prevents ambiguity, transparent confidence | Require frequent tuning week 1-2 |
| 4 | Registration samples | 5-10 frames per student | Robust centroid, consistency check | Longer registration time (5-10s) |
| 5 | Liveness detection | EAR (eye blink) only | Fast, rule-based, no ML overhead | Vulnerable ke advanced spoofing (rare) |
| 6 | Matching strategy | Top-K ranking + gap analysis | Production standard, auditable | Higher latency (~50ms vs ~10ms brute-force) |
| 7 | Metrics collection | Full ScanMetric logging | Audit trail, FPR/FNR tracking | +1-2 MB database per 1000 scans |
| 8 | Incremental learning | Alpha=0.05 untuk high confidence scans | Adapt ke penampilan changes | Risk: drift jika confidence score miscalibrated |
| 9 | Offline matching | Local centroid embeddings + daily sync | Kiosk survive network outage | Stale data jika student re-register |
| 10 | Fallback: no match | Manual admin review + override | Prevent false rejection, audit trail | UX interruption for borderline cases |
```

---

## 🟡 IMPORTANT BUT NON-CRITICAL

### **10. MODIFY: Section 4 (Arsitektur Sistem) — ADD Offline-First detail**

**Current:**
```
Offline-First untuk toggle, tapi face matching tidak jelas
```

**Improve:**
```markdown
### 4.2 Offline-First Face Matching

```
At startup (sync):
  1. `:kiosk-scanner` download: StudentFaceRegistration.centroidEmbedding x N
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

### **11. ADD: Troubleshooting & Debug Guide**

**Lokasi**: Baru section SEBELUM Keputusan Final.

```markdown
## 15. Troubleshooting & Debug Guide

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
  • Add user guidance: "Blink naturally, don't exaggerate"
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

## 🔵 OPTIONAL (CAN DO LATER)

### **12. Section: A/B Testing Framework**

**Status**: NOT YET needed, pero document untuk future.

```markdown
### Optional: Threshold A/B Testing (Phase 2+)

IF metrics show room for improvement:

```
Config A: old thresholds (baseline)
Config B: new thresholds (experiment)

Split kiosk traffic:
  50% → Config A
  50% → Config B

Measure daily:
  FPR_A, FNR_A vs FPR_B, FNR_B
  UX: rejection rate, retry rate

After 1 week: pick winner
```
```

---

### **13. Section: Future Model Upgrades**

```markdown
### Future: 512-d Upgrade Path (NOT NOW)

IF after 2-4 weeks, FPR/FNR still > 1%:

1. Download ArcFace ResNet50 pre-trained (512-d)
2. Convert ke TFLite
3. Benchmark: latency, accuracy, model size
4. If acceptable:
   - Update MobileFaceNet → ArcFace in `:core`
   - Re-register all students (new embeddings)
   - A/B test: 10% traffic to 512-d, monitor FPR/FNR
   - Rollout jika superior

Timeline: Week 6-8 (after solid data)
```

---

## 📋 SUMMARY: CHANGES PER SECTION

| Section | Change | Priority |
|---|---|---|
| 3.3 (AI Pipeline) | REVISE complete (threshold, gap analysis, QA checks) | 🔴 CRITICAL |
| 3.4 (NEW) | Add registration process detail | 🔴 CRITICAL |
| 3.5 (NEW) | Add verification & QA detail | 🔴 CRITICAL |
| 3.6 (NEW) | Add metrics collection & monitoring | 🔴 CRITICAL |
| Sec 5 (Database) | Replace StudentFaceVector → StudentFaceRegistration + ScanMetric | 🔴 CRITICAL |
| Sec 16 (Phase 1) | Expand dengan weekly checklist + validation steps | 🟡 IMPORTANT |
| Sec 16.5 (NEW) | Add decision log | 🟡 IMPORTANT |
| Sec 15 (NEW) | Add troubleshooting guide | 🟡 IMPORTANT |
| Sec 4.2 (Update) | Clarify offline-first embeddings sync | 🟡 IMPORTANT |
| Sec 18 (NEW Optional) | A/B testing framework | 🔵 OPTIONAL |

---

## ✅ NEXT STEPS

1. **Hari ini**: Review checklist ini, agree dengan semua changes
2. **Besok**: Update planning.md dengan CRITICAL changes (sections 3.3-3.6, 5)
3. **Hari ketiga**: Mulai implementation dari Phase 1.1 (Week 1)
4. **Testing**: Week 1-2 dengan 20 volunteer, collect data
5. **Tuning**: Week 3-4 adjust thresholds, validate FPR/FNR

