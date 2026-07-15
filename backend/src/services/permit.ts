import prisma from "./prisma";

export async function createPermit(data: {
  studentId: string;
  reason: string;
  type?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
}) {
  return prisma.permit.create({
    data: {
      studentId: data.studentId,
      reason: data.reason,
      type: data.type || "izin_harian",
      startDate: data.startDate ? new Date(data.startDate) : new Date(),
      endDate: data.endDate ? new Date(data.endDate) : new Date(),
      description: data.description,
      status: "pending"
    }
  });
}

export async function getPermit(id: string) {
  return prisma.permit.findUnique({ where: { id } });
}

export async function updatePermitStatus(id: string, status: string) {
  return prisma.permit.update({
    where: { id },
    data: { status }
  });
}

export async function getPermitQuota(studentId: string) {
  const now = new Date();
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const permitsUsed = await prisma.permit.count({
    where: {
      studentId,
      createdAt: { gte: startOfMonth }
    }
  });
  return { permitsUsed, maxPermits: 10 };
}

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
