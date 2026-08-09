import prisma from "./prisma";

// #122: notifikasi di-scope per admin (adminId diperkenankan). Sebelumnya
// list/markAllRead tanpa filter admin → satu admin bisa menandai semua
// notifikasi semua admin terbaca & melihat notifikasi orang lain.
export async function listNotifications(adminId: string | null, page = 1, pageSize = 20) {
  const skip = (page - 1) * pageSize;
  const where = adminId ? { adminId } : {};
  const [data, total] = await Promise.all([
    prisma.notification.findMany({ where, skip, take: pageSize, orderBy: { createdAt: "desc" } }),
    prisma.notification.count({ where })
  ]);
  return { data, total, page, pageSize };
}

export async function markRead(id: string, adminId: string | null) {
  // #122: hanya admin pemilik notifikasi yang bisa menanda sudah dibaca.
  const where: Record<string, unknown> = { id };
  if (adminId) where.adminId = adminId;
  return prisma.notification.updateMany({ where, data: { isRead: true } });
}

export async function markAllRead(adminId: string | null) {
  await prisma.notification.updateMany({
    where: adminId ? { adminId } : {},
    data: { isRead: true }
  });
}
