import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { listNotifications, markRead, markAllRead } from "../services/notification";
import { authGuard } from "../guards/auth";

export const notificationRoutes = new Elysia()
  .use(authGuard)
  .get("/api/notifications", async ({ query }) => {
    const page = query.page ? parseInt(query.page as string) : 1;
    const pageSize = query.pageSize ? parseInt(query.pageSize as string) : 20;
    return await listNotifications(page, pageSize);
  })
  .put("/api/notifications/:id/read", async ({ params: { id } }) => {
    await markRead(id);
    return { success: true };
  })
  .put("/api/notifications/read-all", async () => {
    await markAllRead();
    return { success: true };
  })
  .delete("/api/notifications/:id", async ({ params: { id } }) => {
    await prisma.notification.delete({ where: { id } });
    return { success: true };
  });
