import prisma from "./prisma";
import bcrypt from "bcryptjs";
import crypto from "crypto";

export async function registerDevice(data: { deviceId?: string; name: string; location?: string }, authenticatedDeviceId?: string) {
  // Use the authenticated device ID (from JWT) if available, otherwise fallback to provided or generated
  const deviceId = authenticatedDeviceId || data.deviceId || crypto.randomUUID().slice(0, 8);

  // Upsert by deviceId — existing device (from seed) gets updated with name/location
  return prisma.device.upsert({
    where: { deviceId },
    update: { name: data.name, location: data.location || null, lastPingAt: new Date() },
    create: {
      deviceId,
      // For dynamically created devices (not pre-seeded), generate a random username
      username: data.deviceId || `device-${crypto.randomUUID().slice(0, 8)}`,
      passwordHash: "", // Dynamic devices can't auth; they need to be seeded
      name: data.name,
      location: data.location || null
    }
  });
}

// #61: buat device dengan credential UNIK per-perangkat (bukan satu password
// bersama). Password di-generate random, di-return SEKALI (tidak bisa dibaca lagi).
export async function createDeviceWithCredentials(data: { name: string; location?: string }) {
  const username = `kiosk-${crypto.randomUUID().slice(0, 8)}`;
  const plainPassword = crypto.randomBytes(9).toString("base64url").slice(0, 12);
  const passwordHash = await bcrypt.hash(plainPassword, 10);
  const device = await prisma.device.create({
    data: {
      deviceId: username,
      username,
      passwordHash,
      name: data.name,
      location: data.location || null
    }
  });
  return { device, username, password: plainPassword };
}

export async function pingDevice(deviceId: string, batteryLevel?: number) {
  return prisma.device.update({
    where: { deviceId },
    data: { lastPingAt: new Date(), batteryLevel: batteryLevel ?? null }
  });
}

export async function listDevices() {
  return prisma.device.findMany({ orderBy: { createdAt: "desc" } });
}
