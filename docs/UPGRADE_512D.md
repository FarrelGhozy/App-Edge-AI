# Model History: 512-d → 192-d

## Current Status (Jul 2026)
**Model aktif: MobileFaceNet 192-d**

Alasan downgrade dari ArcFace 512-d:
- Model `arcface_512.tflite` dihasilkan dari **conversion script yang tidak lengkap** (`scripts/convert_arcface_tflite.py` hanya skeleton demo)
- Arsitektur Inception-ResNet V1 tidak dibangun dengan benar → embedding tidak meaningful
- Semua wajah menghasilkan vektor mirip (false positive 100%)
- MobileFaceNet 192-d dari GitHub release adalah **model proven** dengan akurasi LFW 99.4%

## Spesifikasi MobileFaceNet
| Properti | Nilai |
|---|---|
| Input | 112×112×3 RGB |
| Preprocessing | `pixel / 255.0` → range [0, 1] |
| Output | 192-d L2-normalized float vector |
| Database | `vector(192)` di PostgreSQL |
| Akurasi LFW | ~99.4% |
| Ukuran model | ~5 MB (FP16) |

## Jika ingin kembali ke 512-d di masa depan
1. Siapkan model ArcFace/FaceNet512 yang **proper** (bisa dari PINTO model zoo atau konversi sendiri)
2. Update `FaceEmbedder.init("arcface_512.tflite", embeddingDim = 512, inputSize = 112)`
3. SQL: `ALTER TABLE face_vectors DROP COLUMN vector; ALTER TABLE face_vectors ADD COLUMN vector vector(512);`
4. Update schema.prisma: `vector(192)` → `vector(512)`
5. Re-enroll semua mahasiswa

