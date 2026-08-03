import { Elysia } from "elysia";
import { registerDevice, pingDevice, listDevices } from "../services/device";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const deviceRoutes = new Elysia()
  .use(authGuard())
  // Device self-register + ping: boleh dari token device (#70 — device
  // hanya bisa daftar/mem-ping dirinya sendiri, bukan kelola device lain)
  .post("/api/devices/register", async ({ body, store }) => {
    const admin = store?.admin as { id: string; role: string } | undefined;
    const req = body as { deviceId?: string; name: string; location?: string };
    const authenticatedDeviceId = admin?.role === "device" ? admin.id : undefined;
    const device = await registerDevice(req, authenticatedDeviceId);
    return device;
  })
  .put("/api/devices/:deviceId/ping", async ({ params: { deviceId }, body }) => {
    const { batteryLevel } = body as { batteryLevel?: number };
    await pingDevice(deviceId, batteryLevel);
    return { success: true };
  })
  // Semua route admin-only di bawah — guard factory kedua dengan RBAC (#70)
  .use(authGuard("admin", "superadmin"))
  .get("/api/devices", async () => {
    return await listDevices();
  })
  .get("/api/devices/:deviceId", async ({ params: { deviceId } }) => {
    const device = await prisma.device.findUnique({ where: { deviceId } });
    if (!device) {
      return new Response(JSON.stringify({ success: false, error: "Device not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }
    return { success: true, data: device };
  })
  .put("/api/devices/:deviceId", async ({ params: { deviceId }, body }) => {
    const data = body as { name?: string; location?: string; isActive?: boolean };
    const device = await prisma.device.update({ where: { deviceId }, data });
    return { success: true, data: device };
  })
  .post("/api/sync/request/:deviceId", async ({ params: { deviceId }, store }) => {
    const admin = store?.admin as { id: string } | undefined;
    const request = await prisma.syncRequest.create({
      data: {
        deviceId,
        requestedById: admin?.id || null
      }
    });
    return { success: true, data: request };
  });
