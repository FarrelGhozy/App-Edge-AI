import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { listNotifications, markRead, markAllRead } from "../services/notification";
import { authGuard } from "../guards/auth";

export const notificationRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
  .get("/api/notifications", async ({ query, admin }) => {
    const page = query.page ? parseInt(query.page as string) : 1;
    const pageSize = query.pageSize ? parseInt(query.pageSize as string) : 20;
    // #122: scope per admin (admin.id); null untuk notifikasi global device.
    return await listNotifications(admin?.role === "device" ? null : admin?.id ?? null, page, pageSize);
  })
  .put("/api/notifications/:id/read", async ({ params: { id }, admin }) => {
    await markRead(id, admin?.role === "device" ? null : admin?.id ?? null);
    return { success: true };
  })
  .put("/api/notifications/read-all", async ({ admin }) => {
    await markAllRead(admin?.role === "device" ? null : admin?.id ?? null);
    return { success: true };
  })
  .delete("/api/notifications/:id", async ({ params: { id }, admin }) => {
    const result = await prisma.notification.deleteMany({
      where: {
        id,
        ...(admin?.role === "device" ? {} : admin ? { adminId: admin.id } : {})
      }
    });
    // #123: id tidak ditemukan (atau bukan milik admin ini) → 404, bukan 500.
    if (result.count === 0) {
      return new Response(JSON.stringify({ success: false, error: "Notification tidak ditemukan" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }
    return { success: true };
  });
