# InsightFace + Video Embedding — Migration Plan

> **Branch:** `insightface`
>
> **Tujuan:** Migrasi face detection & recognition dari ML Kit + TFLite ke InsightFace ONNX (buffalo_sc) dan mengimplementasikan video-based multi-frame embedding untuk matching scan di gate.
>
> **Target:** Meningkatkan akurasi matching, menurunkan false reject rate, dan membuat sistem lebih robust terhadap variasi pose/lighting.

---

## Daftar Isi

1. [Ringkasan Perubahan](#1-ringkasan-perubahan)
2. [InsightFace buffalo_sc — Model Detail](#2-insightface-buffalo_sc--model-detail)
3. [Video Embedding — Strategi Matching](#3-video-embedding--strategi-matching)
4. [Arsitektur Baru](#4-arsitektur-baru)
5. [File yang Berubah / Ditambah](#5-file-yang-berubah--ditambah)
6. [Dependencies Baru](#6-dependencies-baru)
7. [Implementasi: Face Detection (RetinaFace)](#7-implementasi-face-detection-retinaface)
8. [Implementasi: Face Embedding (MBF@WebFace600K)](#8-implementasi-face-embedding-mbfwebface600k)
9. [Implementasi: Video Matching Engine](#9-implementasi-video-matching-engine)
10. [Implementasi: Dukungan Dual-Runtime (TFLite + ONNX)](#10-implementasi-dukungan-dual-runtime-tflite--onnx)
11. [Fallback Strategy](#11-fallback-strategy)
12. [Testing Plan](#12-testing-plan)
13. [Timeline / Prioritas](#13-timeline--prioritas)

---

## 1. Ringkasan Perubahan

### Saat Ini (`yolo-v8-face` / `main`)

| Layer | Teknologi | Format |
|-------|-----------|--------|
| Face Detection | ML Kit Face Detection (MediaPipe) | Internal Google Play Services |
| Face Recognition | MobileFaceNet TFLite | `.tflite` (192-d, 112×112) |
| Matching | Single frame → 1 embedding → cosine match | 1× inferensi per scan |
| Anti-spoofing | EAR blink + Deep learning model | Hybrid |
| Runtime | TFLite Interpreter | TensorFlow Lite |

### Target (`insightface`)

| Layer | Teknologi | Format |
|-------|-----------|--------|
| Face Detection | **RetinaFace-500MF** (buffalo_sc) | `.onnx` via ONNX Runtime |
| Face Recognition | **MBF@WebFace600K** (buffalo_sc) | `.onnx` via ONNX Runtime |
| Matching | **Multi-frame video** → weight fusion → cosine match | 5-10× inferensi + average |
| Anti-spoofing | EAR blink + Deep learning (dipertahankan) | Tidak berubah |
| Runtime | **ONNX Runtime Mobile** + optional TFLite fallback | `onnxruntime-android` |

### Perbandingan Key Metrics

| Metric | Sebelum | Sesudah | Dampak |
|--------|---------|---------|--------|
| False Reject Rate | ~5-8% | ~2-3% | ⬇️ 60% lebih jarang gagal scan |
| False Accept Rate | ~0.1% | ~0.05% | ⬇️ 50% lebih aman |
| Confidence rata-rata | 0.75-0.85 | 0.85-0.93 | ⬆️ 10% lebih yakin |
| Latency per scan | ~2 detik | ~3-4 detik | ⬆️ +1-2 detik (acceptable) |
| Model size (total) | ~10-15 MB | ~18 MB (16 MB ONNX + 2 MB fallback) | 📈 sedikit lebih besar |
| Google Services dependency | ✅ Wajib | ❌ Opsional (fallback) | ✅ Bisa offline penuh |

---

## 2. InsightFace buffalo_sc — Model Detail

### Model Pack: buffalo_sc

| File | Ukuran | Arsitektur | Fungsi | Input Shape | Output |
|------|--------|------------|--------|-------------|--------|
| `det_500m.onnx` | ~2.4 MB | RetinaFace-500MF (MobileNet0.25 backbone) | Face detection + bounding box + 5 landmarks | `[1,3,H,W]` | Boxes, scores, landmarks |
| `w600k_mbf.onnx` | ~13.6 MB | MobileFaceNet @ WebFace600K | Face embedding (192-d) | `[1,3,112,112]` | `[1,192]` L2-normalized |

**Total: ~16 MB**

### Akurasi Recognition (MBF@WebFace600K)

| Benchmark | Skor |
|-----------|------|
| LFW | 99.70% |
| CFP-FP | 98.00% |
| AgeDB-30 | 96.58% |
| IJB-C (E-4) | 95.02% |
| MR-ALL | 71.87% |

*(Akurasi sama persis dengan buffalo_s, hanya tanpa alignment & attributes)*

### Keunggulan RetinaFace-500MF vs ML Kit

| Aspek | ML Kit Face Detection | RetinaFace-500MF |
|-------|----------------------|------------------|
| Akurasi WIDER FACE Hard | ~79% | **~81-83%** |
| Keypoints | 6 contour + landmarks | **5 landmark** (mata, hidung, mulut) |
| Ukuran | Bundled di Play Services | **2.4 MB ONNX** |
| Google Services | **Wajib** | **Tidak perlu** |
| Kecepatan | Hardware accelerated | CPU via ONNX Runtime (~5-10ms) |
| Kustomisasi | Terbatas | **Bisa fine-tune / ganti model** |

---

## 3. Video Embedding — Strategi Matching

### Masalah dengan Single Image Matching

1. **1 frame tentukan nasib** — frame blur, silau, atau momen mata terpejam = gagal scan
2. **Tidak representatif** — Ekspresi wajah momentary bisa beda jauh dari registered embedding
3. **Anti-spoof lemah** — Single frame lebih mudah ditipu foto/dim
4. **Variance tinggi** — Confidence score naik-turun antar frame

### Solusi: Video Multi-Frame dengan Quality-Weighted Fusion

```
Liveness Pass
    │
    ├── Collect phase (1-1.5 detik):
    │   Capture ~10-15 frame berturut-turut dari live feed
    │       │
    │       ├── Frame 1 → Detect → Quality Score → Embed (192-d)
    │       ├── Frame 2 → Detect → Quality Score → Embed (192-d)
    │       ├── Frame 3 → Detect → Quality Score → Embed (192-d)
    │       ├── ... (skip: blur, no face, low quality)
    │       └── Frame N → Detect → Quality Score → Embed (192-d)
    │
    ├── Selection phase:
    │   Pilih K frame terbaik berdasarkan quality score (K=3-5)
    │
    ├── Fusion phase:
    │   Weighted average: ∑(embedding_i × quality_i) / ∑quality_i
    │   L2 Normalize hasil fusion
    │
    └── Match → Cosine similarity → Toggle
```

### Adaptive Threshold Strategy

Dengan video matching, confidence lebih stabil. Bisa gunakan threshold adaptif:

| Kondisi | Threshold | Keterangan |
|---------|-----------|------------|
| Match dengan gap > 0.15 | 0.70 | CONFIDENT — langsung accept |
| Match dengan gap 0.08-0.15 | 0.75 | MEDIUM — butuh confidence lebih tinggi |
| Match dengan gap < 0.08 | 0.85 | WEAK — banyak mirip, perlu yakin banget |
| Frame kualitas rata-rata rendah | +0.05 boost | Kompensasi noise |

### Selection Algorithm: Per-Pose Awareness (Optional)

Karena selama registrasi ada 5 pose (CENTER, LEFT, RIGHT, UP, DOWN), idealnya matching juga collect frame dari berbagai sudut. Tapi untuk gate yang mengharuskan face lurus, cukup prioritaskan frame dengan yaw < 15°.

---

## 4. Arsitektur Baru

### Diagram Alur Scan (Gate/Kiosk)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CAMERA STREAM                               │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    LIVE ANALYZER THREAD                              │
│                                                                      │
│  ┌────────────────┐    ┌────────────────┐    ┌──────────────┐      │
│  │  RetinaFace    │───▶│  Quality Gate  │───▶│  EAR Blink   │      │
│  │  Detection     │    │  (center,      │    │  (temporal)  │      │
│  │  (ONNX)        │    │   yaw≤25°)    │    │              │      │
│  └───────┬────────┘    └────────────────┘    └──────┬───────┘      │
│          │                                          │               │
│          │  ┌────────────────────────────────────┐  │               │
│          ├──│  Face Rect → UI Overlay (setiap     │  │               │
│          │  │  frame, real-time)                  │  │               │
│          │  │  - Gambar kotak bounding box        │  │               │
│          │  │  - Warna: hijau (quality OK) /      │  │               │
│          │  │    kuning (sedang) / merah (no good) │  │               │
│          │  │  - Label status: "Lurus", "Kiri",   │  │               │
│          │  │    "Kanan", "Mundur", etc.          │  │               │
│          │  └────────────────────────────────────┘  │               │
│          │                                          │               │
│          │                                   [Blink detected]       │
│          │                                          │               │
│          │                                   ┌──────▼──────┐       │
│          │                                   │ Frame       │       │
│          │                                   │ Collector   │       │
│          │                                   │ (1-1.5s)   │       │
│          │                                   └──────┬──────┘       │
│          │                                          │               │
│          │                                   10-15 raw frames       │
└──────────┼──────────────────────────────────────────────────────────┘
           │
           │ FaceRect juga tetap dikirim ke UI selama frame collection
           ▼

### Face Bounding Box Overlay (UI Wajib)

Selama proses scan, **kotak pembatas wajah harus selalu terlihat** di layar kiosk sebagai panduan visual real-time. Ini krusial untuk pengalaman pengguna.

#### Spesifikasi Overlay

| Aspek | Detail |
|-------|--------|
| **Bentuk** | Rectangle (persegi panjang) mengikuti bounding box dari RetinaFace |
| **Update rate** | **Setiap frame** (real-time, 30 FPS) — sinkron dengan live analyzer |
| **Posisi** | Langsung di atas CameraX Preview, koordinat dikonversi dari koordinat gambar ke koordinat Canvas Composable |
| **Animasi** | Smooth transition — bounding box tidak boleh "melompat" antar frame (gunakan lerp/interpolasi) |

#### Warna Kotak Berdasarkan State

| State Live Analyzer | Warna Kotak | Label |
|--------------------|-------------|-------|
| ❌ Tidak ada wajah | Transparan / hilang | "Arahkan wajah ke kamera" |
| ⚠️ Wajah terdeteksi tapi belum centered / miring | **Kuning** `#FFC107` | "Posisikan di tengah" / "Hadap lurus" |
| ✅ Wajah di tengah + quality OK | **Hijau** `#4CAF50` | "Tahan pose..." |
| 🔵 Sedang collect frame (video mode) | **Biru** `#2196F3` + progress indicator | "Mengambil gambar... 5/10" |
| 🟢 Scan sukses | **Hijau solid** (bisa flash) | Nama + "KELUAR ✅" / "KEMBALI ✅" |
| ❌ Scan gagal | **Merah** `#E53935` (bisa animasi shake) | Pesan error |

#### Informasi di Dalam / Sekitar Kotak

```
┌─────────────────────────────────────────────┐
│                                             │
│     ┌─────────────────────┐                │
│     │                     │                │
│     │    [WAJAH USER]     │  ← Kotak       │
│     │                     │     bounding    │
│     │                     │     box hijau   │
│     └─────────────────────┘                │
│              ↑                              │
│         "Farrel Ghozy"  ← Nama (kalau match)│
│         "KELUAR ✅"                         │
│                                             │
│  [⚪⚪⚪⚪⚪] ← Progress collect frame       │
│                                             │
│  Status: "Tahan pose..."                    │
└─────────────────────────────────────────────┘
```

#### Sumber Data untuk Overlay

`ScannerViewModel` sudah punya StateFlow yang bisa di-expand:

```kotlin
// Existing — sudah ada
val isFaceDetected: StateFlow<Boolean>
val isFaceCentered: StateFlow<Boolean>
val statusMessage: StateFlow<String>

// Baru — untuk bounding box overlay
data class FaceOverlayState(
    val faceRect: Rect? = null,           // Bounding box di koordinat gambar asli
    val canvasRect: Rect? = null,         // Bounding box di koordinat Canvas (setelah transformasi)
    val qualityColor: Color = Color.Transparent,
    val label: String = "",
    val progress: Float = 0f,             // 0.0 - 1.0 selama frame collection
    val userName: String? = null,          // Muncul kalau match sukses
    val actionLabel: String? = null       // "KELUAR ✅" / "KEMBALI ✅"
)

val faceOverlay: StateFlow<FaceOverlayState>
```

#### Implementasi di ScannerScreen

```kotlin
// ScannerScreen.kt — Canvas overlay di atas CameraX Preview
Box(modifier = Modifier.fillMaxSize()) {
    // CameraX preview (existing)
    AndroidView(factory = { ... })
    
    // Face bounding box overlay (BARU)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val state = viewModel.faceOverlay.value
        val rect = state.canvasRect ?: return@Canvas
        
        // Gambar bounding box
        drawRect(
            color = state.qualityColor,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width.toFloat(), rect.height.toFloat()),
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Label di atas box
        drawContext.canvas.nativeCanvas.drawText(...)
    }
    
    // Status message (existing)
    Text(state.statusMessage, ...)
    
    // Progress indicator (BARU — selama frame collection)
    if (isCollecting) {
        LinearProgressIndicator(progress = state.progress, ...)
    }
}
```

#### Transformasi Koordinat (Image → Canvas)

Penting: bounding box dari RetinaFace dalam koordinat gambar asli (misal 1920×1080). Harus di-transform ke koordinat Canvas yang ukurannya beda (sesuai layout).

```kotlin
fun transformRect(
    imageRect: Rect,
    imageWidth: Int, imageHeight: Int,
    canvasWidth: Float, canvasHeight: Float
): Rect {
    val scaleX = canvasWidth / imageWidth
    val scaleY = canvasHeight / imageHeight
    return Rect(
        (imageRect.left * scaleX).toInt(),
        (imageRect.top * scaleY).toInt(),
        (imageRect.right * scaleX).toInt(),
        (imageRect.bottom * scaleY).toInt()
    )
}
```

#### Edge Cases yang Harus Ditangani

| Skenario | Perilaku Overlay |
|----------|-----------------|
| CameraX preview mirror (front camera) | Balik koordinat X: `canvasWidth - x` |
| Face keluar frame | Kotak menghilang, warna transparan |
| Scale type FIT_CENTER vs FILL | Hitung offset agar kotak tetap presisi |
| Multi-face terdeteksi | Tampilkan kotak untuk face terbesar saja (mirip `maxByOrNull`) |
| Screen rotation | Hitung ulang transformasi |

#### File yang Terkait

| File | Perubahan |
|------|-----------|
| `ScannerViewModel.kt` | Tambah `_faceOverlay: MutableStateFlow<FaceOverlayState>` |
| `ScannerScreen.kt` | Tambah Canvas overlay + progress indicator |
| `ScannerScreen.kt` | Hapus `_debugDetection` (digantikan overlay proper) |
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MATCHING WORKER (Coroutine)                   │
│                                                                  │
│  10-15 frames                                                    │
│       │                                                          │
│       ▼                                                          │
│  ┌──────────────┐   Process tiap frame:                         │
│  │ Anti-spoof   │   ┌──────────┐  ┌─────────┐  ┌──────────┐   │
│  │ (deep model) │   │ Retina   │  │ Face    │  │ Quality  │   │
│  │              │   │ Face     │  │ Embed   │  │ Weighter │   │
│  └──────────────┘   └──────────┘  └─────────┘  └──────────┘   │
│       │                                                          │
│       ▼                                                          │
│  ┌─────────────────────────────────────────────────┐            │
│  │ Filter: skip frame kualitas rendah, no face,    │            │
│  │ blur, pose ekstrem                               │            │
│  └─────────────────────────────────────────────────┘            │
│       │                                                          │
│       ▼                                                          │
│  ┌─────────────────────────────────────────────────┐            │
│  │ K-best selection: ambil 3-5 frame terbaik       │            │
│  └─────────────────────────────────────────────────┘            │
│       │                                                          │
│       ▼                                                          │
│  ┌─────────────────────────────────────────────────┐            │
│  │ Weighted Average: ∑(emb_i × quality_i) / ∑q     │            │
│  │ L2 Normalize → Fusion Embedding                 │            │
│  └─────────────────────────────────────────────────┘            │
│       │                                                          │
│       ▼                                                          │
│  ┌─────────────────────────────────────────────────┐            │
│  │ Cosine Matching vs Index                         │            │
│  │ + gap analysis (second-best)                     │            │
│  │ + adaptive threshold                              │            │
│  └─────────────────────────────────────────────────┘            │
│       │                                                          │
│       ▼                                                          │
│  MatchResult → ToggleEngine → ViolationCheck → Log              │
└─────────────────────────────────────────────────────────────────┘
```

### Layer Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     KIOSK-SCANNER MODULE                  │
│  ┌────────────────┐  ┌──────────────────┐                │
│  │ ScannerScreen  │  │ ScannerViewModel │                │
│  │ (CameraX UI)   │  │ (state machine)  │                │
│  └────────────────┘  └────────┬─────────┘                │
│                               │                           │
│  ┌────────────────────────────▼─────────────────────────┐│
│  │              VideoMatchEngine (NEW)                   ││
│  │  - collectFrames()  - aggregateEmbeddings()          ││
│  │  - weightedFusion() - matchFused()                   ││
│  └────────────────────────────┬─────────────────────────┘│
└───────────────────────────────┼───────────────────────────┘
                                │
┌───────────────────────────────┼───────────────────────────┐
│                    CORE MODULE                             │
│                                │                           │
│  ┌────────────────────────────▼─────────────────────────┐ │
│  │              face/ (PAKET BARU)                       │ │
│  │  ┌──────────────────┐  ┌──────────────────┐          │ │
│  │  │ RetinaFaceDetector│  │ ONNXFaceEmbedder │          │ │
│  │  │ (ONNX Runtime)    │  │ (ONNX Runtime)   │          │ │
│  │  └──────────────────┘  └──────────────────┘          │ │
│  │  ┌──────────────────┐  ┌──────────────────┐          │ │
│  │  │ FaceMatcher (sama)│  │ VideoFrameBuffer  │          │ │
│  │  │ (tidak berubah)   │  │ (NEW)             │          │ │
│  │  └──────────────────┘  └──────────────────┘          │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌──────────────────────────────────────────────────────┐ │
│  │              face/ (LEGACY — fallback)                │ │
│  │  FaceDetectorWrapper.kt  (ML Kit)                     │ │
│  │  FaceEmbedder.kt          (TFLite)                    │ │
│  └──────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────┘
```

---

## 5. File yang Berubah / Ditambah

### File Baru

| File | Lokasi | Fungsi |
|------|--------|--------|
| `OnnxRuntimeManager.kt` | `android/core/src/.../face/` | Singleton ONNX Runtime session manager |
| `RetinaFaceDetector.kt` | `android/core/src/.../face/` | Face detection dengan RetinaFace-500MF |
| `OnnxFaceEmbedder.kt` | `android/core/src/.../face/` | Face embedding dengan MBF@WebFace600K |
| `VideoFrameBuffer.kt` | `android/core/src/.../face/` | Ring buffer untuk collect N frame dengan metadata |
| `QualityWeightedFusion.kt` | `android/core/src/.../face/` | K-best selection + weighted average fusion |
| `VideoMatchEngine.kt` | `android/kiosk-scanner/.../matching/` | New matching engine untuk video-based scan |

### File Diubah

| File | Lokasi | Perubahan |
|------|--------|-----------|
| `build.gradle.kts` (core) | `android/core/` | Tambah ONNX Runtime dependencies |
| `build.gradle.kts` (kiosk) | `android/kiosk-scanner/` | Tambah ONNX Runtime dependencies |
| `libs.versions.toml` | `android/gradle/` | Tambah version + library ONNX Runtime |
| `ScannerViewModel.kt` | `android/kiosk-scanner/.../scanner/` | Ganti `MatchEngine` → `VideoMatchEngine` + tambah `FaceOverlayState` |
| `ScannerScreen.kt` | `android/kiosk-scanner/.../scanner/` | Tambah Canvas bounding box overlay + progress indicator |
| `MatchEngine.kt` | `android/kiosk-scanner/.../matching/` | Tambah method untuk video-based pipeline |
| `KioskModule.kt` | `android/kiosk-scanner/` | Binding dependency baru (DI) |
| `FaceMatcher.kt` | `android/core/.../face/` | Tambah adaptive threshold |
| `FaceIndex.kt` | `android/core/.../face/` | Tidak berubah (interface stabil) |
| `MatchResult.kt` | `android/core/.../face/` | Tambah `decision` enum (CONFIDENT/MEDIUM/WEAK/NO_MATCH) |

### File Tidak Berubah (Dipertahankan untuk Fallback)

| File | Alasan |
|------|--------|
| `FaceDetectorWrapper.kt` (ML Kit) | Fallback jika ONNX Runtime gagal load |
| `FaceEmbedder.kt` (TFLite) | Fallback untuk compatibilitas model lama |
| `LivenessDetector.kt` | Tidak kena dampak — tetap EAR + anti-spoof |
| `AntiSpoofDetector.kt` | Tetap dipakai di pipeline |
| `QualityAnalyzer.kt` | Tetap dipakai untuk quality scoring |

### Asset Model Baru (`android/.../assets/`)

| File | Ukuran | Sumber |
|------|--------|--------|
| `det_500m.onnx` | ~2.4 MB | [WePrompt/buffalo_sc](https://huggingface.co/WePrompt/buffalo_sc) |
| `w600k_mbf.onnx` | ~13.6 MB | [WePrompt/buffalo_sc](https://huggingface.co/WePrompt/buffalo_sc) |

---

## 6. Dependencies Baru

### `android/gradle/libs.versions.toml`

```toml
[versions]
onnxruntime = "1.20.0"       # atau versi stabil terbaru

[libraries]
onnxruntime-android = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnxruntime" }
```

### `android/core/build.gradle.kts`

```kotlin
dependencies {
    // InsightFace via ONNX Runtime
    implementation(libs.onnxruntime.android)
    
    // TFLite tetap untuk fallback
    implementation(libs.tensorflow.lite)
    
    // ML Kit tetap untuk fallback detection
    implementation(libs.mlkit.facedetection)
}
```

### Kelebihan ONNX Runtime

| Fitur | ONNX Runtime | TFLite |
|-------|-------------|--------|
| Format model | ONNX | TFLite |
| Hardware acceleration | NNAPI, GPU (OpenCL) | NNAPI, GPU (OpenGL) |
| Custom ops | Support | Terbatas |
| Model converter | Semua framework → ONNX | TensorFlow → TFLite |
| Ukuran library (AAR) | ~5 MB | ~3 MB |
| Maturity | Sangat mature (Microsoft) | Sangat mature (Google) |

---

## 7. Implementasi: Face Detection (RetinaFace)

### RetinaFaceDetector.kt — Desain

```kotlin
class RetinaFaceDetector(private val context: Context) {
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    
    fun init(): Boolean {
        env = OrtEnvironment.getEnvironment()
        // Load det_500m.onnx dari assets
        val modelBytes = context.assets.open("det_500m.onnx").readBytes()
        session = env?.createSession(modelBytes, OrtSession.SessionOptions())
        return session != null
    }
    
    data class RetinaFaceResult(
        val boundingBox: Rect,
        val confidence: Float,
        val landmarks: List<PointF>  // 5 points: leftEye, rightEye, nose, leftMouth, rightMouth
    )
    
    fun detect(image: Bitmap, threshold: Float = 0.5f): List<RetinaFaceResult> {
        // 1. Preprocess: resize ke input model, normalize [0,1] atau [0,255]
        // 2. Run ONNX inference
        // 3. Postprocess: decode output → NMS → box scaling
        // 4. Return face list
    }
    
    fun detectFromImage(mediaImage: Image, rotation: Int): List<RetinaFaceResult> {
        // Convert Image → Bitmap → detect
    }
}
```

### Output RetinaFace

Model `det_500m.onnx` punya multiple output heads:
- **Bounding boxes**: `[N, 4]` — (x1, y1, x2, y2) relative to grid
- **Confidence scores**: `[N, 1]` — face probability
- **Landmarks**: `[N, 10]` — 5 pasang (x,y) untuk mata kiri, mata kanan, hidung, mulut kiri, mulut kanan

Post-processing butuh:
1. **Decode** — konversi dari grid/anchor space ke gambar
2. **NMS** (Non-Maximum Suppression) — filter bounding box overlap, threshold IoU ~0.5
3. **Scale** — scaling ke ukuran gambar asli
4. Pilih box dengan confidence tertinggi (mirip `maxByOrNull` di implementasi ML Kit sekarang)

---

## 8. Implementasi: Face Embedding (MBF@WebFace600K)

### OnnxFaceEmbedder.kt — Desain

```kotlin
class OnnxFaceEmbedder(private val context: Context) {
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    
    companion object {
        const val INPUT_SIZE = 112     // Model input: 112×112
        const val EMBEDDING_DIM = 192  // Output: 192-d
        const val INPUT_NAME = "input"
        const val OUTPUT_NAME = "output"
    }
    
    fun init(): Boolean {
        env = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open("w600k_mbf.onnx").readBytes()
        session = env?.createSession(modelBytes, OrtSession.SessionOptions())
        return session != null
    }
    
    fun embed(faceCrop: Bitmap): FloatArray {
        // 1. Resize bilinear ke 112×112
        // 2. Normalize: pixel / 255.0 → [0,1]
        // 3. Convert ke float array [1,3,112,112] (CHW format khas ONNX)
        // 4. Run ONNX inference
        // 5. Output sudah L2-normalized (langsung bisa dipakai cosine dot product)
    }
    
    fun embedBatch(bitmaps: List<Bitmap>): List<FloatArray> {
        // Batch inference: [N,3,112,112] → [N,192]
    }
}
```

### Perbedaan Preprocessing dengan TFLite FaceEmbedder

| Aspek | TFLite (existing) | ONNX (InsightFace) |
|-------|-------------------|-------------------|
| Format input | NHWC `[1,112,112,3]` | **NCHW** `[1,3,112,112]` |
| Normalisasi | [0,1] ÷ 255 | [0,1] ÷ 255 (sama) |
| Output | L2-normalized (dilakukan manual di kotlin) | **Sudah L2-normalized** dari model |
| Layout pixel | R, G, B interleaved | **R,R,R... G,G,G... B,B,B...** (channel-separated) |

⚠️ **Penting:** ONNX InsightFace pakai layout **NCHW** (Channel-first). Perlu konversi dari NHWC yang biasa dipakai TFLite.

---

## 9. Implementasi: Video Matching Engine

### VideoFrameBuffer.kt — Ring Buffer Collector

```kotlin
class VideoFrameBuffer(
    private val maxFrames: Int = 15,   // Max collect
    private val collectDurationMs: Long = 1500L  // Max duration
) {
    data class FrameEntry(
        val bitmap: Bitmap,
        val timestamp: Long,
        val detection: FaceDetectionResult? = null,
        val qualityScore: Float = 0f,
        val embedding: FloatArray? = null
    )
    
    private val frames = mutableListOf<FrameEntry>()
    
    fun add(frame: Bitmap, detection: FaceDetectionResult) { ... }
    fun isFull(): Boolean = frames.size >= maxFrames
    fun isExpired(now: Long): Boolean = now - startTime > collectDurationMs
    fun clear() { ... }
    fun getFrames(): List<FrameEntry> = frames.toList()
}
```

### QualityWeightedFusion.kt — Fusion Engine

```kotlin
class QualityWeightedFusion {
    companion object {
        const val MIN_QUALITY = 0.4f      // Skip frame di bawah ini
        const val K_BEST = 5               // Ambil K frame terbaik
        const val MIN_FRAMES = 2           // Minimal frame untuk fusion
        
        fun qualityScore(detection: FaceDetectionResult): Float {
            // Kombinasi: yaw, pitch, eye openness, face ratio
            // normalized [0, 1]
        }
    }
    
    fun fuse(entries: List<FrameEntry>): FloatArray? {
        // 1. Filter: buang frame quality < MIN_QUALITY
        // 2. K-best: ambil K terbaik
        // 3. Weighted average: ∑(emb_i × q_i) / ∑q_i
        // 4. L2 normalize hasil fusion
        // 5. Return fused embedding
    }
}
```

### VideoMatchEngine.kt — Pipeline Orchestrator

```kotlin
class VideoMatchEngine @Inject constructor(
    private val faceDetector: RetinaFaceDetector,
    private val faceEmbedder: OnnxFaceEmbedder,
    private val faceMatcher: FaceMatcher,
    private val antiSpoofDetector: AntiSpoofDetector,
    private val toggleEngine: ToggleEngine,
    private val violationDetector: ViolationDetector,
    private val sessionTracker: SessionTracker
) {
    
    // ─── Live analyzer: tiap frame dari kamera ───
    fun onPreviewFrame(image: Image, rotation: Int): LiveAnalysisResult {
        val faces = faceDetector.detectFromImage(image, rotation)
        val face = faces.maxByOrNull { it.confidence } ?: return NoFace
        return LiveAnalysisResult(face.boundingBox, face.confidence, ...)
    }
    
    // ─── Collect phase: ambil N frame setelah liveness ───
    suspend fun collectFrames(durationMs: Long = 1500): List<FrameEntry> {
        // Kumpulkan frame dari kamera selama ~1.5 detik
        // Simpan bitmap kecil + metadata di ring buffer
    }
    
    // ─── Match phase: fusion + matching ───
    suspend fun matchVideoFrames(frames: List<FrameEntry>): MatchEngineResult {
        // 1. Anti-spoof check (deep learning) pada frame terbaik
        // 2. Embed semua frame yang lolos quality
        // 3. Quality-weighted fusion
        // 4. Cosine match + adaptive threshold
        // 5. Toggle + Violation + Session
    }
}
```

---

## 10. Implementasi: Dukungan Dual-Runtime (TFLite + ONNX)

### OnnxRuntimeManager.kt

```kotlin
class OnnxRuntimeManager private constructor(private val context: Context) {
    
    enum class RuntimeState {
        ONNX_READY,      // ONNX Runtime aktif dan model loaded
        FALLBACK_TFLITE, // ONNX gagal, pakai TFLite
        FALLBACK_MLKIT   // Semua gagal, pakai ML Kit
    }
    
    val state: RuntimeState
    
    fun init(): Boolean {
        return try {
            // Coba load ONNX Runtime + model
            OrtEnvironment.getEnvironment()
            loadModel("det_500m.onnx")
            loadModel("w600k_mbf.onnx")
            RuntimeState.ONNX_READY
        } catch (e: UnsatisfiedLinkError) {
            // ONNX Runtime native library tidak ada
            RuntimeState.FALLBACK_TFLITE
        }
    }
}
```

### Dual Provider Interface

```kotlin
interface FaceDetectorProvider {
    fun detect(image: Image, rotation: Int): List<FaceDetectionBox>
    fun init(): Boolean
}

interface FaceEmbedderProvider {
    fun embed(faceCrop: Bitmap): FloatArray
    val embeddingDim: Int  // 192
    val inputSize: Int     // 112
    fun init(): Boolean
}
```

Implementasi:
- `RetinaFaceDetector` : `FaceDetectorProvider` (ONNX)
- `FaceDetectorWrapper` : `FaceDetectorProvider` (ML Kit — legacy)
- `OnnxFaceEmbedder` : `FaceEmbedderProvider` (ONNX)
- `FaceEmbedder` : `FaceEmbedderProvider` (TFLite — legacy)

---

## 11. Fallback Strategy

### Skenario Fallback

| Kondisi | Deteksi | Embedding | Matching |
|---------|---------|-----------|----------|
| ONNX Runtime normal ✅ | RetinaFace-500MF | MBF@WebFace600K | Video fusion |
| ONNX Runtime gagal load ⚠️ | ML Kit (Google Play) | TFLite MobileFaceNet | Single image |
| Device tanpa Google Play ❌ | RetinaFace-500MF | MBF@WebFace600K | Video fusion |
| Semua gagal 💀 | Tampilkan error | — | — |

### Graceful Degradation

```
Inisialisasi
    │
    ├── Coba load RetinaFace ONNX ✔
    │   └── Coba load MBF ONNX ✔
    │       └── [MODE: Video ONNX] ✅ Optimal
    │
    ├── Coba load RetinaFace ONNX ❌
    │   └── Coba ML Kit Detection ✔
    │       └── Coba TFLite Embedder ✔
    │           └── [MODE: Single Image Legacy] ⚠️
    │
    └── Semua gagal ❌
        └── [MODE: Error — minta update]
```

---

## 12. Testing Plan

### Unit Tests

| Test | File | Coverage |
|------|------|----------|
| RetinaFace output decoding | `RetinaFaceDetectorTest.kt` | Box format, NMS, confidence threshold |
| ONNX embedder preprocessing | `OnnxFaceEmbedderTest.kt` | NCHW layout, normalization [0,1] |
| QualityWeightedFusion | `QualityWeightedFusionTest.kt` | K-best selection, weighted average, L2 norm |
| VideoFrameBuffer | `VideoFrameBufferTest.kt` | Ring buffer overflow, timeout, clear |
| VideoMatchEngine | `VideoMatchEngineTest.kt` | End-to-end pipeline mock |

### Integration Tests

| Test | Skenario |
|------|----------|
| ONNX Runtime load model | Di device Android real |
| RetinaFace detection accuracy | 10 sample face images, measure IoR |
| Video collect + fusion time | Measure latency budget |
| Fallback ML Kit saat ONNX crash | Simulasikan corrupt model |

### Benchmark (Target)

| Metric | Target | Device |
|--------|--------|--------|
| RetinaFace inference time | < 15ms per frame | Xiaomi Redmi / Samsung A series |
| MBF embedding inference | < 30ms per face crop | Xiaomi Redmi / Samsung A series |
| Video collect duration | 1000-1500ms | — |
| Fusion + match time | < 50ms | — |
| Total scan time | < 4000ms | End-to-end |

---

## 13. Timeline / Prioritas

### Phase 1: Foundation ⚡ (Prioritas Tertinggi)

| # | Task | File | Estimated |
|---|------|------|-----------|
| 1 | Tambah ONNX Runtime dependency + download model | `libs.versions.toml`, `assets/` | 15 menit |
| 2 | `OnnxRuntimeManager.kt` — Singleton session manager | File baru | 30 menit |
| 3 | `RetinaFaceDetector.kt` — Implementasi dasar (preprocess + inference + NMS) | File baru | 2 jam |
| 4 | `OnnxFaceEmbedder.kt` — Implementasi dasar (NCHW preprocessing + inference) | File baru | 1 jam |
| 5 | Integration test: ONNX Runtime bisa load & inference di Android | — | 30 menit |

### Phase 2: Video Pipeline 🔄

| # | Task | File | Estimated |
|---|------|------|-----------|
| 6 | `VideoFrameBuffer.kt` — Ring buffer collector | File baru | 30 menit |
| 7 | `QualityWeightedFusion.kt` — K-best + weighted average | File baru | 45 menit |
| 8 | `VideoMatchEngine.kt` — Pipeline orchestrator | File baru | 2 jam |
| 9 | `MatchResult.kt` — Tambah `decision` + adaptive threshold | File diubah | 15 menit |
| 10 | `FaceMatcher.kt` — Adaptive threshold logic | File diubah | 30 menit |

### Phase 3: Integration 🔌

| # | Task | File | Estimated |
|---|------|------|-----------|
| 11 | Update DI module (`KioskModule.kt`) — bind new providers | File diubah | 15 menit |
| 12 | Update `ScannerViewModel.kt` — call VideoMatchEngine | File diubah | 1 jam |
| 13 | Fallback chain: ONNX → TFLite → ML Kit | `OnnxRuntimeManager.kt` | 45 menit |
| 14 | Registrasi tetap pakai existing (compatible 192-d) | Tidak berubah | — |

### Phase 4: Testing & Tuning 🎯

| # | Task | Estimated |
|---|------|-----------|
| 15 | Unit test untuk tiap class baru | 2 jam |
| 16 | Integration test di device real | 3 jam |
| 17 | Benchmark latency + accuracy tuning | 2 jam |
| 18 | Threshold tuning (adaptive threshold parameters) | 1 jam |
| 19 | Fallback verification test | 30 menit |

**Total estimasi: ~18 jam kerja**

---

## Lampiran

### A. Cara Download Model

```bash
# Download buffalo_sc dari HuggingFace
cd App-Edge-AI/android/core/src/main/assets/

# RetinaFace-500MF (detection)
curl -L -o det_500m.onnx \
  "https://huggingface.co/WePrompt/buffalo_sc/resolve/main/det_500m.onnx?download=true"

# MBF@WebFace600K (recognition)
curl -L -o w600k_mbf.onnx \
  "https://huggingface.co/WePrompt/buffalo_sc/resolve/main/w600k_mbf.onnx?download=true"

# Cek ukuran
ls -lh det_500m.onnx w600k_mbf.onnx
```

### B. Referensi

| Resource | Link |
|----------|------|
| InsightFace Model Zoo | https://github.com/deepinsight/insightface/blob/master/model_zoo/README.md |
| buffalo_sc HuggingFace | https://huggingface.co/WePrompt/buffalo_sc |
| ONNX Runtime Android | https://onnxruntime.ai/docs/tutorials/mobile/ |
| RetinaFace Paper | https://arxiv.org/abs/1905.00641 |
| Video FR Survey | https://arxiv.org/abs/2211.02952 |
| Quality-aware fusion | https://peerj.com/articles/cs-391/ |

### C. ONNX Model Input/Output Details

Untuk referensi implementasi post-processing RetinaFace:

> **RetinaFace-500MF (`det_500m.onnx`)**
> - Input: `[1, 3, H, W]` — float32, normalized [0,1]
> - Output 1: bboxes — `[N, 4]` (x1, y1, x2, y2) di grid scale
> - Output 2: scores — `[N, 1]` face confidence
> - Output 3: landmarks — `[N, 10]` (5 keypoints × 2)
> - NMS threshold: IoU 0.5, Confidence threshold: 0.5

> **MBF@WebFace600K (`w600k_mbf.onnx`)**
> - Input: `[1, 3, 112, 112]` — float32, normalized [0,1]
> - Output: `[1, 192]` — float32, sudah L2-normalized
> - Tidak perlu L2-normalize lagi di Kotlin
