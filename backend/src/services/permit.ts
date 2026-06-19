import prisma from "./prisma";

export async function listPermits(params: {
  page?: number;
  pageSize?: number;
  status?: string;
  type?: string;
  studentId?: string;
}) {
  const page = params.page || 1;
  const pageSize = params.pageSize || 20;
  const skip = (page - 1) * pageSize;

  const where: Record<string, unknown> = {};
  if (params.status) where.status = params.status;
  if (params.type) where.type = params.type;
  if (params.studentId) where.studentId = params.studentId;

  const [data, total] = await Promise.all([
    prisma.permit.findMany({ where, skip, take: pageSize, orderBy: { createdAt: "desc" } }),
    prisma.permit.count({ where })
  ]);

  return { data, total, page, pageSize };
}

export async function approvePermit(id: string, adminId: string) {
  return prisma.permit.update({
    where: { id },
    data: { status: "approved", approvedById: adminId, approvedAt: new Date() }
  });
}

export async function rejectPermit(id: string, adminId: string) {
  return prisma.permit.update({
    where: { id },
    data: { status: "rejected", approvedById: adminId, approvedAt: new Date() }
  });
}
