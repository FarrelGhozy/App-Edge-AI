import prisma from "./prisma";

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

export async function pingDevice(deviceId: string, batteryLevel?: number) {
  return prisma.device.update({
    where: { deviceId },
    data: { lastPingAt: new Date(), batteryLevel: batteryLevel ?? null }
  });
}

export async function listDevices() {
  return prisma.device.findMany({ orderBy: { createdAt: "desc" } });
}
