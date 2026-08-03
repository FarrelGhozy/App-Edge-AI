import { Elysia } from "elysia";
import { registerDevice, pingDevice, listDevices, createDeviceWithCredentials } from "../services/device";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";
import { audit } from "../services/audit";

export const deviceRoutes = new Elysia()
  .use(authGuard())
  // Device self-register + ping: boleh dari token device (#70 — device
  // hanya bisa daftar/mem-ping dirinya sendiri, bukan kelola device lain)
  .post("/api/devices/register", async ({ body, admin }) => {
    const req = body as { deviceId?: string; name: string; location?: string };
    const authenticatedDeviceId = admin?.role === "device" ? admin.id : undefined;
    const device = await registerDevice(req, authenticatedDeviceId);
    return device;
  })
  .put("/api/devices/:deviceId/ping", async ({ params: { deviceId }, body, admin }) => {
    // #121: IDOR — device hanya boleh mem-ping dirinya sendiri (token device
    // ber-identitas admin.id = deviceId). Admin (bukan device) tidak boleh
    // memakai route ini untuk spoof heartbeat device lain.
    const deviceIdFromToken = admin?.role === "device" ? admin.id : undefined;
    if (deviceIdFromToken && deviceIdFromToken !== deviceId) {
      return new Response(
        JSON.stringify({ success: false, error: "Forbidden: device can only ping itself" }),
        { status: 403, headers: { "Content-Type": "application/json" } }
      );
    }
    if (admin?.role === "device" && !deviceIdFromToken) {
      return new Response(
        JSON.stringify({ success: false, error: "Unauthorized: device identity required" }),
        { status: 401, headers: { "Content-Type": "application/json" } }
      );
    }
    const { batteryLevel } = body as { batteryLevel?: number };
    await pingDevice(deviceId, batteryLevel);
    return { success: true };
  })
  // Semua route admin-only di bawah — guard factory kedua dengan RBAC (#70)
  .use(authGuard("admin", "superadmin"))
  // #61: admin membuat device dgn credential UNIK per-perangkat — password
  // di-generate random & hanya di-return sekali (tidak hardcoded di APK).
  .post("/api/devices", async ({ body, admin }) => {
    const data = body as { name: string; location?: string };
    if (!data.name) {
      return new Response(JSON.stringify({ success: false, error: "Nama device wajib diisi" }), {
        status: 400,
        headers: { "Content-Type": "application/json" }
      });
    }
    const { device, username, password } = await createDeviceWithCredentials(data);
    await audit(admin, { action: "CREATE", entityType: "DEVICES", entityId: device.deviceId, details: `device ${data.name}` });
    return { success: true, data: { ...device, username, password } };
  })
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
    // #97: return raw object (konsisten dengan GET /api/devices) agar
    // client bisa Response<DeviceDto> tanpa wrapper.
    return device;
  })
  .put("/api/devices/:deviceId", async ({ params: { deviceId }, body }) => {
    const data = body as { name?: string; location?: string; isActive?: boolean };
    try {
      const device = await prisma.device.update({ where: { deviceId }, data });
      return { success: true, data: device };
    } catch (e) {
      // #123: P2025 → 404 bersih, bukan 500 mentah.
      if (
        e instanceof Error &&
        "code" in e &&
        (e as { code: string }).code === "P2025"
      ) {
        return new Response(JSON.stringify({ success: false, error: "Device tidak ditemukan" }), {
          status: 404,
          headers: { "Content-Type": "application/json" }
        });
      }
      throw e;
    }
  })
  .post("/api/sync/request/:deviceId", async ({ params: { deviceId }, admin }) => {
    // #131: dedup — 1 pending per device (partial unique index memaksa unik).
    const existing = await prisma.syncRequest.findFirst({
      where: { deviceId, isProcessed: false }
    });
    if (existing) {
      await prisma.syncRequest.update({
        where: { id: existing.id },
        data: { requestedAt: new Date(), requestedById: admin?.id || existing.requestedById }
      });
      return { success: true, data: existing, updated: true };
    }
    try {
      const request = await prisma.syncRequest.create({
        data: { deviceId, requestedById: admin?.id || null }
      });
      return { success: true, data: request };
    } catch (e) {
      // P2002 (partial index) = request pending sudah dibuat paralel → laporkan exist.
      if (
        e instanceof Error &&
        "code" in e &&
        (e as { code: string }).code === "P2002"
      ) {
        const race = await prisma.syncRequest.findFirst({ where: { deviceId, isProcessed: false } });
        return { success: true, data: race, updated: false };
      }
      throw e;
    }
  });
