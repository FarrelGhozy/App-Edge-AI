# Face Recognition Accuracy — Improvement Plan

## Analisis Root Cause

Setelah mempelajari seluruh pipeline (enrollment → preprocessing → embedding → matching → liveness → anti-spoof), ditemukan **5 masalah kritis** yang menyebabkan akurasi kurang, khususnya **false rejection** ( siswa asli ditolak ).

---

## 1. Ambiguity Penalty Terlalu Agresif (Penyebab Utama)

**File:** `android/core/src/main/java/com/facegate/core/face/FaceMatcher.kt:28-31`

### Masalah:

```kotlin
private const val AMBIGUITY_RATIO = 0.15f

// Line 77-78:
val adjustedScore = if (diff < AMBIGUITY_RATIO && bestScore > 0) {
    bestScore - (AMBIGUITY_RATIO - diff) * 0.5f
}
```

Dengan **10rb+ siswa × 5 pose = 50rb entries**, wajar kalau 2 siswa berbeda punya cosine similarity berdekatan.

**Contoh konkret:**
| Best | Second | Diff | Adjusted | Threshold | Hasil |
|------|--------|------|----------|-----------|-------|
| 0.75 | 0.72 | 0.03 | **0.69** | 0.70 | ❌ Rejected |
| 0.80 | 0.71 | 0.09 | **0.77** | 0.70 | ✅ Match |
| 0.85 | 0.85 | 0.00 | **0.775** | 0.70 | ✅ Match |

Banyak genuine match di skenario pertama (beda tipis) kena false reject karena penalty.

### Solusi:

Turunkan `AMBIGUITY_RATIO` dari `0.15f` ke `0.08f`. Ini tetap mencegah false accept untuk kembar/lookalike tapi tidak terlalu agresif nge-penalty.

### Risiko:

Dengan ratio lebih kecil, false accept rate (FAR) akan naik sedikit. Tapi untuk use case **pondok pesantren** (bukan security high-risk seperti bank), trade-off ini acceptable.

---

## 2. Threshold Anti-Spoof Tidak Konsisten

**File:**
- `android/core/src/main/java/com/facegate/core/face/LivenessDetector.kt:29`
- `android/kiosk-scanner/src/main/java/com/facegate/kioskscanner/matching/MatchEngine.kt:53`

### Masalah:

Dua threshold berbeda untuk hal yang sama (minimum real confidence):

| Lokasi | Threshold | Akibat |
|--------|-----------|--------|
| `LivenessDetector.SPOOF_CONFIDENCE_THRESHOLD` | **0.5** | Real face confidence < 0.5 dianggap SPOOF |
| `MatchEngine.SPOOF_CONFIDENCE_MIN` | **0.3** | Hanya reject kalau < 0.3 |

### Dampak:

Real face dengan confidence 0.3–0.5 (wajar terjadi di kondisi kurang cahaya / angle miring):

1. `LivenessDetector.checkLiveness()` → confidence 0.4 < 0.5 → `isSpoof = true` → early return `passed=false`
2. `MatchEngine.matchAfterDetection()` → cek `antiSpoofResult.isSpoof && confidence < 0.3` → **tidak reject** (karena 0.4 >= 0.3)
3. Pipeline lanjut ke embedding + matching

**Masalahnya:** Di LivenessDetector, `isSpoof=true` TAPI MatchEngine tidak nge-reject karena threshold beda. Ini inkonsistensi logika yang bikin real face kadang ditolak kadang diterima secara tidak terprediksi.

### Solusi:

Samakan threshold di kedua tempat. Pilih **0.3** sebagai threshold tunggal karena lebih realistis untuk kondisi lapangan.

---

## 3. Quality Gate Terlalu Strict

**File:** `android/core/src/main/java/com/facegate/core/face/QualityAnalyzer.kt:35-38`

### Masalah:

```kotlin
private const val MIN_LAPLACIAN_VARIANCE = 80f    // terlalu tinggi
private const val MIN_BRIGHTNESS = 40f             // terlalu terang
private const val MAX_BRIGHTNESS = 215f
private const val MIN_FACE_SIZE_RATIO = 0.05f
```

Lingkungan **gerbang pondok malam hari** punya pencahayaan minimal. Laplacian variance di bawah 80 sangat umum terjadi di kondisi:
- Lampu teras kuning (kurang terang)
- Wajah terkena shadow
- Kamera malam (noise tinggi)

Akibat: frame genuine breach quality gate → user disuruh ulang terus → pengalaman buruk → akurasi terasa rendah.

### Solusi:

Turunkan threshold untuk kondisi low-light:

| Parameter | Saat Ini | Rekomendasi |
|-----------|----------|-------------|
| `MIN_LAPLACIAN_VARIANCE` | 80 | **50** |
| `MIN_BRIGHTNESS` | 40 | **25** |
| `MAX_BRIGHTNESS` | 215 | tetap (tidak masalah) |

---

## 4. Enrollment: Single Frame Per Pose (No Averaging)

**File:** `android/admin-app/src/main/java/com/facegate/adminapp/register/FaceRegisterViewModel.kt:425-445`

### Masalah:

Pipeline enrollment saat ini:
1. Collect **2 frame** per pose (total 10 frame)
2. Pilih **1 frame terbaik** per pose (berdasarkan quality score)
3. **Frame satunya dibuang**
4. Upload 5 embeddings (1 per pose) secara individual

`averageEmbeddings()` di `FaceEmbedder.kt:138` sudah diimplementasikan tapi **tidak pernah dipanggil**.

### Dampak:

- Setiap pose cuma diwakili 1 embedding yang bisa mengandung noise (blur ringan, ekspresi tidak netral, dll)
- Kalau kebetulan frame "terbaik" masih kurang bagus, matching akan sulit

### Solusi:

Averaging 2 frame per pose sebelum upload:

```kotlin
// Daripada:
finalFrames.map { data ->
    val faceCrop = cropFace(data.bitmap, data.faceRect)
    faceEmbedder.embed(faceCrop)  // 1 frame → 1 embedding
}

// Jadi:
// Per pose: embed 2 frame → average → L2 normalize → upload
```

Ini menghasilkan template yang lebih robust tanpa mengubah jumlah data (tetap 5 pose, tetap 192-d per pose).

---

## 5. Confidence Score Tidak Disimpan di Log

**File:** Cari `confidenceScore = 1.0f` di ScannerViewModel (kemungkinan di `android/kiosk-scanner/src/main/java/com/facegate/kioskscanner/scanner/ScannerViewModel.kt`)

### Masalah:

```kotlin
AttendanceLogEntity(
    confidenceScore = 1.0f  // HARDCODED!
)
```

### Dampak:

Tidak mungkin audit:
- Apakah match benar-benar yakin (>0.9) atau cuma pas-pasan (0.71)?
- Tidak bisa hitung false positive / false negative rate
- Tidak bisa evaluasi efektivitas perubahan threshold

### Solusi:

Ganti dengan confidence asli dari `MatchResult.confidence` yang sudah di-passing oleh `matchEngine.matchAfterDetection()`.

---

## 6. Bonus: Domain Gap Admin ↔ Kiosk

### Masalah:

Enrollment dilakukan di **admin app** (HP admin — kamera depan biasanya lebih bagus, pencahayaan ruangan).
Matching dilakukan di **kiosk** (HP khusus di gerbang — kamera bisa berbeda, pencahayaan luar ruangan).

### Dampak:

Embedding dari kamera admin bisa berbeda secara sistematis dari embedding kamera kiosk untuk wajah yang sama. Ini masalah umum di face recognition yang disebut *domain gap*.

### Solusi Long Term:

- Kalibrasi: enroll ulang menggunakan kamera kiosk
- Atau gunakan data augmentation saat enrollment (brightness/contrast jitter) untuk simulasi kondisi kiosk

---

## Prioritas Implementasi

| # | Item | Impact | Effort |
|---|------|--------|--------|
| 1 | Turunkan `AMBIGUITY_RATIO` → `0.08` | **High** (false rejection turun drastis) | Rendah (1 baris) |
| 2 | Samakan anti-spoof threshold → `0.3` | **High** (konsistensi logika) | Rendah (2 baris) |
| 3 | Lunakkan quality gate (LV→50, brightness→25) | **Medium** (lebih banyak frame lolos) | Rendah (2 baris) |
| 4 | Average 2 frame per enrollment | **Medium** (template lebih robust) | Sedang (ubah logika enrollment) |
| 5 | Simpan confidence asli di log | **Low** (hanya untuk audit) | Rendah (1 baris) |
| 6 | Domain gap calibration | **Medium** (long term) | Tinggi (riset + testing) |

---

## Cara Verifikasi

Setelah implementasi, ukur:

1. **False Reject Rate (FRR)**: jumlah scan siswa asli yang ditolak / total scan
2. **False Accept Rate (FAR)**: jumlah scan orang asing yang diterima / total scan
3. **Average confidence**: rata-rata confidence score untuk match yang sukses
4. **Distribution plot**: histogram confidence scores untuk match vs no-match

Target: FRR < 5%, FAR < 0.1%.

---

## Referensi

- `FaceMatcher.kt` — Ambiguity penalty logic
- `LivenessDetector.kt` — Anti-spoof threshold
- `QualityAnalyzer.kt` — Quality gate thresholds
- `FaceRegisterViewModel.kt` — Enrollment pipeline (frame selection)
- `FaceEmbedder.kt` — `averageEmbeddings()` (unused)
- `ScannerViewModel.kt` — Confidence log (hardcoded)
- `docs/planning.md` — Dokumen perencanaan proyek
