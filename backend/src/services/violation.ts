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
    prisma.violation.findMany({ where, skip, take: pageSize, orderBy: { timestamp: "desc" } }),
    prisma.violation.count({ where })
  ]);

  return { data, total, page, pageSize };
}
