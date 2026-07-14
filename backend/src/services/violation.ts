import prisma from "./prisma";

export async function listViolations(params: {
  page?: number;
  pageSize?: number;
  type?: string;
  studentId?: string;
}) {
  const page = params.page || 1;
  const pageSize = params.pageSize || 20;
  const skip = (page - 1) * pageSize;

  const where: Record<string, unknown> = {};
  if (params.type) where.type = params.type;
  if (params.studentId) where.studentId = params.studentId;

  const [data, total] = await Promise.all([
    prisma.violation.findMany({
      where,
      skip,
      take: pageSize,
      orderBy: { timestamp: "desc" },
      include: { student: { select: { name: true } } }
    }),
    prisma.violation.count({ where })
  ]);

  const enriched = data.map(v => ({
    ...v,
    studentName: v.student.name
  }));

  return { data: enriched, total, page, pageSize };
}
