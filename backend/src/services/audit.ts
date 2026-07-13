import prisma from "./prisma";

export async function listAuditLogs(page = 1, pageSize = 50) {
  const skip = (page - 1) * pageSize;
  const [data, total] = await Promise.all([
    prisma.auditLog.findMany({
      skip,
      take: pageSize,
      orderBy: { createdAt: "desc" },
      include: { admin: { select: { username: true, displayName: true } } }
    }),
    prisma.auditLog.count()
  ]);
  return { data, total, page, pageSize };
}
