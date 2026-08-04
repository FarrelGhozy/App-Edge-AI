import { t } from "elysia";
import prisma from "./prisma";

export const createStudentSchema = t.Object({
  nim: t.String(),
  name: t.String(),
  studyProgram: t.String(),
  academicYear: t.String(),
  phone: t.Optional(t.String()),
  email: t.Optional(t.String())
});

export const updateStudentSchema = t.Object({
  nim: t.Optional(t.String()),
  name: t.Optional(t.String()),
  studyProgram: t.Optional(t.String()),
  academicYear: t.Optional(t.String()),
  phone: t.Optional(t.String()),
  email: t.Optional(t.String()),
  isActive: t.Optional(t.Boolean())
});

export const uploadFaceSchema = t.Object({
  pose: t.String(),       // FRONT_1 .. FRONT_N (registrasi video 10 detik, #132)
  vector: t.Array(t.Number())
});

export const batchUploadFacesSchema = t.Object({
  vectors: t.Array(
    t.Object({
      pose: t.String(),
      vector: t.Array(t.Number())
    })
  )
});

// Pose/frame labels untuk registrasi video 10 detik (#132):
// FRONT_1 .. FRONT_MAX_FRAMES (frame berkualitas terbaik dari video).
// Menggantikan VALID_POSES (CENTER/LEFT/RIGHT/UP/DOWN) — wajah depan only.
const MAX_FRAMES = 10;
const FRONT_POSE_RE = /^FRONT_(\d+)$/;

export function isValidPose(pose: string): boolean {
  const m = FRONT_POSE_RE.exec(pose);
  if (!m) return false;
  const n = parseInt(m[1], 10);
  return n >= 1 && n <= MAX_FRAMES;
}

export async function listStudents(params: {
  page?: number;
  pageSize?: number;
  search?: string;
  studyProgram?: string;
  academicYear?: string;
}) {
  const page = params.page || 1;
  const pageSize = params.pageSize || 20;
  const skip = (page - 1) * pageSize;

  const where: Record<string, unknown> = {};
  if (params.search) {
    where.OR = [
      { name: { contains: params.search, mode: "insensitive" } },
      { nim: { contains: params.search } },
      { studyProgram: { contains: params.search, mode: "insensitive" } },
      { phone: { contains: params.search } },
      { email: { contains: params.search, mode: "insensitive" } }
    ];
  }
  if (params.studyProgram) where.studyProgram = params.studyProgram;
  if (params.academicYear) where.academicYear = params.academicYear;

  const [data, total, faceStudents] = await Promise.all([
    prisma.student.findMany({ where, skip, take: pageSize, orderBy: { name: "asc" } }),
    prisma.student.count({ where }),
    prisma.$queryRawUnsafe<Array<{ student_id: string }>>(
      `SELECT DISTINCT student_id FROM face_vectors`
    )
  ]);

  const faceSet = new Set(faceStudents.map(f => f.student_id));
  const enriched = data.map(s => ({
    ...s,
    faceRegistered: faceSet.has(s.id)
  }));

  return { data: enriched, total, page, pageSize };
}

export async function getStudent(id: string) {
  const student = await prisma.student.findUnique({ where: { id } });
  if (!student) return null;
  const faceVectors = await prisma.$queryRawUnsafe<Array<{
    student_id: string;
    pose: string;
    vector: string;
    updated_at: Date;
  }>>(
    `SELECT student_id, pose, vector::text, updated_at FROM face_vectors WHERE student_id = $1 ORDER BY id`,
    id
  );
  const enrichedVectors = faceVectors.map(fv => {
    const vectorStr = fv.vector?.replace(/[\[\]]/g, "") || "";
    const vector = vectorStr ? vectorStr.split(",").map(Number) : [];
    return {
      studentId: fv.student_id,
      pose: fv.pose,
      vector,
      updatedAt: fv.updated_at.toISOString()
    };
  });
  return {
    ...student,
    faceRegistered: faceVectors.length > 0,
    faceVectors: enrichedVectors,
    posesCompleted: faceVectors.length
  };
}

export async function createStudent(data: {
  nim: string;
  name: string;
  studyProgram: string;
  academicYear: string;
  phone?: string;
  email?: string;
}) {
  const student = await prisma.student.create({ data });
  return student;
}

export async function updateStudent(id: string, data: Record<string, unknown>) {
  const student = await prisma.student.update({ where: { id }, data });
  return student;
}

export async function deleteStudent(id: string) {
  // #72: bungkus DELETE bertahap dalam $transaction — kalau salah satu gagal,
  // seluruh operasi rollback (tidak ada state parsial).
  return prisma.$transaction(async (tx) => {
    await tx.attendanceLog.deleteMany({ where: { studentId: id } });
    await tx.permit.deleteMany({ where: { studentId: id } });
    await tx.faceVector.deleteMany({ where: { studentId: id } });
    return tx.student.delete({ where: { id } });
  });
}

export async function deleteFace(studentId: string) {
  const result = await prisma.faceVector.deleteMany({ where: { studentId } });
  return { deleted: result.count > 0 };
}

export async function uploadFace(studentId: string, pose: string, vector: number[]) {
  // Validate student exists
  const student = await prisma.student.findUnique({ where: { id: studentId } });
  if (!student) {
    throw new Error("STUDENT_NOT_FOUND");
  }

  // Validate pose (FRONT_1 .. FRONT_N, #132)
  if (!isValidPose(pose)) {
    throw new Error(`INVALID_POSE: expected FRONT_1..FRONT_${MAX_FRAMES}, got ${pose}`);
  }

  // Validate vector dimension (InsightFace w600k_mbf = 512-d)
  if (vector.length !== 512) {
    throw new Error(`VECTOR_DIMENSION_MISMATCH: expected 512 (InsightFace), got ${vector.length}`);
  }

  const vectorStr = `[${vector.join(",")}]`;
  try {
    // PK baru = id auto (#133): tidak bisa ON CONFLICT (student_id, pose).
    // Delete-then-insert dalam transaksi agar pose lama tidak menumpuk.
    return await prisma.$transaction(async (tx) => {
      await tx.$executeRawUnsafe(
        `DELETE FROM face_vectors WHERE student_id = $1 AND pose = $2`,
        studentId,
        pose
      );
      return tx.$executeRawUnsafe(
        `INSERT INTO face_vectors (student_id, pose, vector, updated_at) VALUES ($1, $2, $3::vector, NOW())`,
        studentId,
        pose,
        vectorStr
      );
    });
  } catch (error: any) {
    if (error.message?.includes("vector")) {
      throw new Error(`PGVECTOR_ERROR: ${error.message}`);
    }
    throw error;
  }
}

export async function batchUploadFaces(studentId: string, vectors: { pose: string; vector: number[] }[]) {
  // Validate student exists
  const student = await prisma.student.findUnique({ where: { id: studentId } });
  if (!student) {
    throw new Error("STUDENT_NOT_FOUND");
  }

  if (vectors.length === 0) {
    throw new Error("EMPTY_VECTORS: at least one face vector is required");
  }

  // Validate all poses and vectors
  for (const v of vectors) {
    if (!isValidPose(v.pose)) {
      throw new Error(`INVALID_POSE: expected FRONT_1..FRONT_${MAX_FRAMES}, got ${v.pose}`);
    }
    if (v.vector.length !== 512) {
      throw new Error(`VECTOR_DIMENSION_MISMATCH: expected 512 (InsightFace), got ${v.vector.length} for pose ${v.pose}`);
    }
  }

  try {
    // PK baru = id auto (#133): replace set vektor per santri dalam transaksi —
    // delete semua vektor lama santri, lalu insert set baru (registrasi ulang
    // tidak menumpuk vektor lama).
    await prisma.$transaction(async (tx) => {
      await tx.$executeRawUnsafe(
        `DELETE FROM face_vectors WHERE student_id = $1`,
        studentId
      );
      for (const v of vectors) {
        const vectorStr = `[${v.vector.join(",")}]`;
        await tx.$executeRawUnsafe(
          `INSERT INTO face_vectors (student_id, pose, vector, updated_at) VALUES ($1, $2, $3::vector, NOW())`,
          studentId,
          v.pose,
          vectorStr
        );
      }
    });
    return { uploaded: vectors.length };
  } catch (error: any) {
    if (error.message?.includes("vector")) {
      throw new Error(`PGVECTOR_ERROR: ${error.message}`);
    }
    throw error;
  }
}
