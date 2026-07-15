# Upgrade Guide: MobileFaceNet 192-d → ArcFace 512-d

## Kenapa Upgrade?
- 512-d embedding punya **2.7× fitur per wajah**
- Lebih akurat ngebedain 10rb+ mahasiswa
- False Accept Rate turun dari 1:10.000 → 1:1.000.000

## Yang Berubah di Kode
Semua kode **udah siap** untuk 512-d. Tinggal 3 langkah:

## Langkah 1: Konversi Model
Jalankan script konversi di komputer yang ada Python + TensorFlow:

```bash
cd backend
pip install tensorflow
python scripts/convert_arcface_tflite.py
```

Output:
- `android/core/src/main/assets/arcface_512.tflite` (FP16, ~15MB)
- `android/core/src/main/assets/arcface_512_quant.tflite` (INT8, ~8MB)

## Langkah 2: Update Android Init
### Di `FaceRegisterViewModel.kt`:
```kotlin
faceEmbedder.init(
    modelName = "arcface_512.tflite",
    embeddingDim = 512,
    inputSize = 112   // ArcFace pake 112×112
)
```

### Di `MatchEngine.kt`:
faceEmbedder udah auto-init dari `FaceEmbedder.embed()` — 
tinggal ubah default model name jadi `"arcface_512.tflite"` dengan dim 512.

## Langkah 3: Migrasi Database
```bash
cd backend

# SQL langsung di PostgreSQL (bikin kolom baru, HAPUS data lama!)
psql -d facegate -c "
  ALTER TABLE face_vectors DROP COLUMN vector;
  ALTER TABLE face_vectors ADD COLUMN vector vector(512);
  CREATE INDEX idx_face_vectors ON face_vectors USING ivfflat (vector vector_cosine_ops) WITH (lists = 100);
"

# Update Prisma schema
npx prisma db push   # atau: npx prisma migrate dev --name upgrade_vector_512
```

## ⚠️ Peringatan
> **Data lama 192-d TIDAK compatible dengan 512-d.**
> Setelah migrasi, **semua vector wajah harus di re-upload** dari kiosk!
> Format: Mahasiswa harus registrasi ulang atau batch re-process dari server.

## Rollback
Kalau mau balik ke 192-d:
1. Ganti model ke `mobilefacenet.tflite`, embeddingDim = 192
2. SQL: `ALTER TABLE face_vectors DROP COLUMN vector; ALTER TABLE face_vectors ADD COLUMN vector vector(192);`
3. Re-upload semua data 192-d
