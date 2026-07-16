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
  pose: t.String(),       // CENTER, LEFT, RIGHT, UP, DOWN
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

// Valid pose names
const VALID_POSES = new Set(["CENTER", "LEFT", "RIGHT", "UP", "DOWN"]);

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
    `SELECT student_id, pose, vector::text, updated_at FROM face_vectors WHERE student_id = $1 ORDER BY pose`,
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

async function triggerSyncForAllDevices() {
  try {
    const activeDevices = await prisma.device.findMany({
      where: { isActive: true },
      select: { deviceId: true }
    });
    for (const device of activeDevices) {
      await prisma.syncRequest.create({
        data: { deviceId: device.deviceId }
      });
    }
  } catch (_) {
    // Silently fail
  }
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
  await triggerSyncForAllDevices();
  return student;
}

export async function updateStudent(id: string, data: Record<string, unknown>) {
  const student = await prisma.student.update({ where: { id }, data });
  await triggerSyncForAllDevices();
  return student;
}

export async function deleteStudent(id: string) {
  await prisma.attendanceLog.deleteMany({ where: { studentId: id } });
  await prisma.permit.deleteMany({ where: { studentId: id } });
  await prisma.faceVector.deleteMany({ where: { studentId: id } });
  const student = await prisma.student.delete({ where: { id } });
  await triggerSyncForAllDevices();
  return student;
}

export async function deleteFace(studentId: string) {
  const result = await prisma.faceVector.deleteMany({ where: { studentId } });
  await triggerSyncForAllDevices();
  return { deleted: result.count > 0 };
}

export async function uploadFace(studentId: string, pose: string, vector: number[]) {
  // Validate student exists
  const student = await prisma.student.findUnique({ where: { id: studentId } });
  if (!student) {
    throw new Error("STUDENT_NOT_FOUND");
  }

  // Validate pose
  if (!VALID_POSES.has(pose)) {
    throw new Error(`INVALID_POSE: expected one of ${[...VALID_POSES].join(", ")}, got ${pose}`);
  }

  // Validate vector dimension
  if (vector.length !== 192 && vector.length !== 512) {
    throw new Error(`VECTOR_DIMENSION_MISMATCH: expected 192 or 512, got ${vector.length}`);
  }

  const vectorStr = `[${vector.join(",")}]`;
  try {
    const result = await prisma.$executeRawUnsafe(
      `INSERT INTO face_vectors (student_id, pose, vector, updated_at) VALUES ($1, $2, $3::vector, NOW())
       ON CONFLICT (student_id, pose) DO UPDATE SET vector = $3::vector, updated_at = NOW()`,
      studentId,
      pose,
      vectorStr
    );
    await triggerSyncForAllDevices();
    return result;
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
    throw new Error("EMPTY_VECTORS: at least one pose vector is required");
  }

  // Validate all poses and vectors
  for (const v of vectors) {
    if (!VALID_POSES.has(v.pose)) {
      throw new Error(`INVALID_POSE: expected one of ${[...VALID_POSES].join(", ")}, got ${v.pose}`);
    }
    if (v.vector.length !== 512) {
      throw new Error(`VECTOR_DIMENSION_MISMATCH: expected 512, got ${v.vector.length} for pose ${v.pose}`);
    }
  }

  try {
    // Use a transaction for atomicity
    await prisma.$transaction(
      vectors.map(v => {
        const vectorStr = `[${v.vector.join(",")}]`;
        return prisma.$executeRawUnsafe(
          `INSERT INTO face_vectors (student_id, pose, vector, updated_at) VALUES ($1, $2, $3::vector, NOW())
           ON CONFLICT (student_id, pose) DO UPDATE SET vector = $3::vector, updated_at = NOW()`,
          studentId,
          v.pose,
          vectorStr
        );
      })
    );
    await triggerSyncForAllDevices();
    return { uploaded: vectors.length };
  } catch (error: any) {
    if (error.message?.includes("vector")) {
      throw new Error(`PGVECTOR_ERROR: ${error.message}`);
    }
    throw error;
  }
}
