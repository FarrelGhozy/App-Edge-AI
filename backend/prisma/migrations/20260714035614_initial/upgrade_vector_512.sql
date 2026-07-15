-- Migration: Upgrade face vector dimension from 192 to 512
-- 
-- Before running: Drop the old vector column and recreate with new dimension.
-- WARNING: This drops ALL existing face vectors! Use only when ready to re-upload.
--
-- Run: prisma migrate dev --name upgrade_vector_512
-- Or: psql -d yourdb -f migration.sql

-- 1. Drop the existing extension-based constraint and recreate the column
ALTER TABLE face_vectors DROP COLUMN vector;
ALTER TABLE face_vectors ADD COLUMN vector vector(512);

-- 2. (Optional) Add an IVFFlat index for faster ANN search on 512-d vectors
-- CREATE INDEX idx_face_vectors_512 ON face_vectors USING ivfflat (vector vector_cosine_ops) WITH (lists = 100);
