-- AlterTable
-- #132/#133: FaceVector PK [student_id, pose] → id auto-increment.
-- Registrasi wajah berubah dari 5 pose (CENTER..DOWN) menjadi N frame
-- front (FRONT_1..FRONT_N) sehingga PK lama tidak bisa menampung banyak
-- baris dengan pose berbeda untuk santri yang sama.

-- Buat tabel baru dengan struktur baru (PK id serial)
CREATE TABLE "face_vectors_new" (
    "id" SERIAL NOT NULL,
    "student_id" TEXT NOT NULL,
    "pose" TEXT NOT NULL,
    "vector" vector(512) NOT NULL,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "face_vectors_new_pkey" PRIMARY KEY ("id")
);

-- Salin data lama (pose CENTER..DOWN tetap dipertahankan sebagai label;
-- kiosk tetap memuat semua vektor tanpa filter pose)
INSERT INTO "face_vectors_new" ("student_id", "pose", "vector", "updated_at")
SELECT "student_id", "pose", "vector", "updated_at" FROM "face_vectors";

-- Ganti tabel lama
DROP TABLE "face_vectors";
ALTER TABLE "face_vectors_new" RENAME TO "face_vectors";

-- Index student_id (pengganti FK lookup & deleteByStudentId)
CREATE INDEX "face_vectors_student_id_idx" ON "face_vectors"("student_id");

-- Foreign key ke students (cascade) — sama dengan constraint lama
ALTER TABLE "face_vectors" ADD CONSTRAINT "face_vectors_student_id_fkey" FOREIGN KEY ("student_id") REFERENCES "students"("id") ON DELETE CASCADE ON UPDATE CASCADE;
