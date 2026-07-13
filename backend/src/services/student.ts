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
  vector: t.Array(t.Number())
});

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
      { nim: { contains: params.search } }
    ];
  }
  if (params.studyProgram) where.studyProgram = params.studyProgram;
  if (params.academicYear) where.academicYear = params.academicYear;

  const [data, total, faceStudentIds] = await Promise.all([
    prisma.student.findMany({ where, skip, take: pageSize, orderBy: { name: "asc" } }),
    prisma.student.count({ where }),
    prisma.faceVector.findMany({ select: { studentId: true } })
  ]);

  const faceSet = new Set(faceStudentIds.map(f => f.studentId));
  const enriched = data.map(s => ({
    ...s,
    faceRegistered: faceSet.has(s.id)
  }));

  return { data: enriched, total, page, pageSize };
}

export async function getStudent(id: string) {
  const student = await prisma.student.findUnique({ where: { id } });
  if (!student) return null;
  const [faceCount, faceVectors] = await Promise.all([
    prisma.faceVector.count({ where: { studentId: id } }),
    prisma.$queryRawUnsafe<Array<{ student_id: string; updated_at: Date }>>(
      `SELECT student_id, updated_at FROM face_vectors WHERE student_id = $1`,
      id
    )
  ]);
  const enrichedVectors = faceVectors.map(fv => ({
    studentId: fv.student_id,
    updatedAt: fv.updated_at.toISOString()
  }));
  return { ...student, faceRegistered: faceCount > 0, faceVectors: enrichedVectors };
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
    // Silently fail — trigger is best-effort, doesn't block the main operation
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
  await prisma.faceVector.deleteMany({ where: { studentId: id } });
  const student = await prisma.student.delete({ where: { id } });
  await triggerSyncForAllDevices();
  return student;
}

export async function uploadFace(studentId: string, vector: number[]) {
  // Validate student exists
  const student = await prisma.student.findUnique({ where: { id: studentId } });
  if (!student) {
    throw new Error("STUDENT_NOT_FOUND");
  }

  // Validate vector dimension (model produces 192-d embedding)
  if (vector.length !== 192) {
    throw new Error(`VECTOR_DIMENSION_MISMATCH: expected 192, got ${vector.length}`);
  }

  const vectorStr = `[${vector.join(",")}]`;
  try {
    const result = await prisma.$executeRawUnsafe(
      `INSERT INTO face_vectors (student_id, vector, updated_at) VALUES ($1, $2::vector, NOW())
       ON CONFLICT (student_id) DO UPDATE SET vector = $2::vector, updated_at = NOW()`,
      studentId,
      vectorStr
    );
    // Trigger sync so kiosks download the new face vector
    await triggerSyncForAllDevices();
    return result;
  } catch (error: any) {
    // Catch pgvector-specific errors
    if (error.message?.includes("vector")) {
      throw new Error(`PGVECTOR_ERROR: ${error.message}`);
    }
    throw error;
  }
}
