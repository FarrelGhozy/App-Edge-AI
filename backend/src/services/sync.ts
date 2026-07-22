import prisma from "./prisma";

export async function getSyncRequestStatus(deviceId?: string) {
  if (deviceId) {
    const request = await prisma.syncRequest.findFirst({
      where: { deviceId, isProcessed: false },
      orderBy: { requestedAt: "desc" }
    });
    return { syncRequested: request != null, request };
  }

  const hasUnprocessed = await prisma.syncRequest.count({
    where: { isProcessed: false }
  });
  return { syncRequested: hasUnprocessed > 0 };
}

export async function markSyncProcessed(deviceId: string) {
  await prisma.syncRequest.updateMany({
    where: { deviceId, isProcessed: false },
    data: { isProcessed: true, processedAt: new Date() }
  });

  // Log sync completion
  await prisma.syncLog.create({
    data: {
      deviceId,
      syncType: "full",
      status: "completed"
    }
  });
}

export async function getSyncStatus(deviceId: string) {
  const device = await prisma.device.findUnique({ where: { deviceId } });
  const lastSync = await prisma.syncLog.findFirst({
    where: { deviceId },
    orderBy: { createdAt: "desc" }
  });
  const pendingRequests = await prisma.syncRequest.count({
    where: { deviceId, isProcessed: false }
  });

  return {
    deviceId,
    isActive: device?.isActive ?? false,
    lastPingAt: device?.lastPingAt,
    lastSyncAt: lastSync?.createdAt ?? null,
    lastSyncStatus: lastSync?.status ?? null,
    pendingSyncRequests: pendingRequests
  };
}

export async function listSyncLogs(deviceId: string, page = 1, pageSize = 20) {
  const where = deviceId ? { deviceId } : {};
  const [logs, total] = await Promise.all([
    prisma.syncLog.findMany({
      where,
      skip: (page - 1) * pageSize,
      take: pageSize,
      orderBy: { createdAt: "desc" }
    }),
    prisma.syncLog.count({ where })
  ]);
  return { data: logs, total, page, pageSize };
}

export async function getSyncSettings() {
  const settings = await prisma.globalSetting.findMany({
    where: {
      key: { in: ["sync_poll_interval_seconds", "sync_batch_size"] }
    }
  });
  const map: Record<string, string> = {};
  for (const s of settings) map[s.key] = s.value;
  return map;
}
