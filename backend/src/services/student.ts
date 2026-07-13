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
  studentId: t.String(),
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
  const faceCount = await prisma.faceVector.count({ where: { studentId: id } });
  return { ...student, faceRegistered: faceCount > 0 };
}

export async function createStudent(data: {
  nim: string;
  name: string;
  studyProgram: string;
  academicYear: string;
  phone?: string;
  email?: string;
}) {
  return prisma.student.create({ data });
}

export async function updateStudent(id: string, data: Record<string, unknown>) {
  return prisma.student.update({ where: { id }, data });
}

export async function deleteStudent(id: string) {
  await prisma.faceVector.deleteMany({ where: { studentId: id } });
  return prisma.student.delete({ where: { id } });
}

export async function uploadFace(studentId: string, vector: number[]) {
  const vectorStr = `[${vector.join(",")}]`;
  return prisma.$executeRawUnsafe(
    `INSERT INTO face_vectors (student_id, vector) VALUES ($1, $2::vector)
     ON CONFLICT (student_id) DO UPDATE SET vector = $2::vector, updated_at = NOW()`,
    studentId,
    vectorStr
  );
}
