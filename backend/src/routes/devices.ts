import { Elysia } from "elysia";
import { registerDevice, pingDevice, listDevices } from "../services/device";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const deviceRoutes = new Elysia()
  .use(authGuard)
  .post("/api/devices/register", async ({ body }) => {
    const device = await registerDevice(body as { name: string; location?: string });
    return { success: true, data: device };
  })
  .get("/api/devices", async () => {
    return await listDevices();
  })
  .put("/api/devices/:deviceId/ping", async ({ params: { deviceId }, body }) => {
    const { batteryLevel } = body as { batteryLevel?: number };
    await pingDevice(deviceId, batteryLevel);
    return { success: true };
  })
  .post("/api/sync/request/:deviceId", async ({ params: { deviceId } }) => {
    const request = await prisma.syncRequest.create({
      data: { deviceId }
    });
    return { success: true, data: request };
  })
  .get("/api/sync/logs", async ({ query }) => {
    const where = query.deviceId ? { deviceId: query.deviceId as string } : {};
    return await prisma.syncLog.findMany({
      where,
      orderBy: { createdAt: "desc" },
      take: 50
    });
  });
