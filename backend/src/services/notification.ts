import prisma from "./prisma";

export async function listNotifications(page = 1, pageSize = 20) {
  const skip = (page - 1) * pageSize;
  const [data, total] = await Promise.all([
    prisma.notification.findMany({ skip, take: pageSize, orderBy: { createdAt: "desc" } }),
    prisma.notification.count()
  ]);
  return { data, total, page, pageSize };
}

export async function markRead(id: string) {
  return prisma.notification.update({ where: { id }, data: { isRead: true } });
}

export async function markAllRead() {
  await prisma.notification.updateMany({ data: { isRead: true } });
}
