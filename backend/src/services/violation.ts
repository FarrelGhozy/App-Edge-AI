import prisma from "./prisma";

export async function listViolations(params: {
  page?: number;
  pageSize?: number;
  type?: string;
  studentId?: string;
  search?: string;
  from?: string;
  to?: string;
}) {
  const page = params.page || 1;
  const pageSize = params.pageSize || 20;
  const skip = (page - 1) * pageSize;

  const where: Record<string, unknown> = {};
  if (params.type) where.type = params.type;
  if (params.studentId) where.studentId = params.studentId;

  // Pencarian nama / NIM mahasiswa
  if (params.search && params.search.trim()) {
    const q = params.search.trim();
    where.student = {
      OR: [
        { name: { contains: q, mode: "insensitive" } },
        { nim: { contains: q } }
      ]
    };
  }

  // Filter rentang tanggal pelanggaran
  if (params.from || params.to) {
    const ts: { gte?: Date; lte?: Date } = {};
    if (params.from) ts.gte = new Date(params.from);
    if (params.to) {
      const toDate = new Date(params.to);
      toDate.setHours(23, 59, 59, 999);
      ts.lte = toDate;
    }
    where.timestamp = ts;
  }

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
