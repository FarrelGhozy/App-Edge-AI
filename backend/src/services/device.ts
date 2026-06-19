import prisma from "./prisma";

export async function registerDevice(data: { name: string; location?: string }) {
  const deviceId = crypto.randomUUID().slice(0, 8);
  return prisma.device.create({
    data: { deviceId, name: data.name, location: data.location || null }
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
