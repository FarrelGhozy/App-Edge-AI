import { Elysia } from "elysia";
import { batchSyncSchema, recordScan } from "../services/attendance";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const syncRoutes = new Elysia()
  .use(authGuard)
  .get("/api/sync/faces", async ({ query }) => {
    const since = query.since as string | undefined;

    const rows = await prisma.$queryRawUnsafe<Array<{
      student_id: string;
      vector: string;
      updated_at: Date;
      name: string;
      nim: string;
      study_program: string;
      academic_year: string;
    }>>(
      `SELECT
        fv.student_id,
        fv.vector::text,
        fv.updated_at,
        s.name,
        s.nim,
        s.study_program,
        s.academic_year
      FROM face_vectors fv
      JOIN students s ON s.id = fv.student_id
      WHERE $1::timestamptz IS NULL OR fv.updated_at >= $1::timestamptz`,
      since ? new Date(since) : null
    );

    const data = rows.map((r) => {
      const vectorStr = r.vector?.replace(/[\[\]]/g, "") || "";
      const vector = vectorStr ? vectorStr.split(",").map(Number) : [];

      return {
        studentId: r.student_id,
        studentName: r.name,
        nim: r.nim,
        studyProgram: r.study_program,
        academicYear: r.academic_year,
        vector,
        updatedAt: r.updated_at.toISOString()
      };
    });

    return { data, since: since || null };
  })
  .post("/api/sync/attendance", async ({ body }) => {
    const created = [];
    for (const log of body.logs) {
      const student = await prisma.student.findUnique({ where: { id: log.studentId } });
      if (student) {
        const record = await recordScan({
          studentId: log.studentId,
          studentName: student.name,
          action: log.action,
          confidenceScore: log.confidenceScore,
          isViolation: log.isViolation,
          violationType: log.violationType,
          deviceId: log.deviceId,
          photoCapture: log.photoCapture,
          timestamp: log.timestamp
        });
        created.push(record);
      }
    }
    return { success: true, data: { synced: created.length } };
  }, { body: batchSyncSchema })
  .get("/api/sync/rules", async () => {
    const rules = await prisma.campusRule.findMany();
    return rules;
  })
  .get("/api/sync/requested", async ({ query }) => {
    const deviceId = query.deviceId as string | undefined;
    const where: Record<string, unknown> = { isProcessed: false };
    if (deviceId) where.deviceId = deviceId;

    const request = await prisma.syncRequest.findFirst({
      where,
      orderBy: { requestedAt: "desc" }
    });

    return {
      requested: !!request,
      requestedAt: request?.requestedAt?.toISOString() || null,
      deviceId: request?.deviceId || null
    };
  })
  .get("/api/sync/status/:deviceId", async ({ params: { deviceId } }) => {
    const device = await prisma.device.findUnique({ where: { deviceId } });
    if (!device) {
      return new Response(JSON.stringify({ success: false, error: "Device not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }

    const pendingRequest = await prisma.syncRequest.findFirst({
      where: { deviceId, isProcessed: false },
      orderBy: { requestedAt: "desc" }
    });

    const lastSync = await prisma.syncLog.findFirst({
      where: { deviceId },
      orderBy: { createdAt: "desc" }
    });

    const unprocessedLogs = await prisma.syncLog.count({
      where: { deviceId, status: "pending" }
    });

    return {
      success: true,
      data: {
        deviceId,
        deviceName: device.name,
        isOnline: device.lastPingAt ? (Date.now() - device.lastPingAt.getTime()) < 300000 : false,
        lastPingAt: device.lastPingAt?.toISOString() || null,
        batteryLevel: device.batteryLevel,
        hasPendingSync: !!pendingRequest,
        pendingSyncRequestedAt: pendingRequest?.requestedAt?.toISOString() || null,
        lastSyncAt: lastSync?.createdAt?.toISOString() || null,
        lastSyncStatus: lastSync?.status || null,
        unprocessedLogs
      }
    };
  })
  .get("/api/sync/logs", async ({ query }) => {
    const where = query.deviceId ? { deviceId: query.deviceId as string } : {};
    return await prisma.syncLog.findMany({
      where,
      orderBy: { createdAt: "desc" },
      take: 50
    });
  })
  .post("/api/events/trigger-change", async ({ body }) => {
    // Called by service layer when DB changes (student CRUD, face upload, permit approve, rules change, etc.)
    // Sets syncRequested=true for ALL active devices so kiosks fetch latest data
    const activeDevices = await prisma.device.findMany({
      where: { isActive: true },
      select: { deviceId: true }
    });

    const requestedBy = (body as { requestedBy?: string }).requestedBy || null;

    for (const device of activeDevices) {
      await prisma.syncRequest.create({
        data: {
          deviceId: device.deviceId,
          requestedById: requestedBy
        }
      });
    }

    return {
      success: true,
      data: { triggeredDevices: activeDevices.length }
    };
  })
  .post("/api/sync/complete", async ({ body }) => {
    const data = body as { deviceId: string; syncType?: string; status?: string; logsCount?: number };
    await prisma.syncLog.create({
      data: {
        deviceId: data.deviceId,
        syncType: data.syncType || "manual",
        status: data.status || "success",
        logsCount: data.logsCount || 0
      }
    });

    await prisma.syncRequest.updateMany({
      where: { deviceId: data.deviceId, isProcessed: false },
      data: { isProcessed: true, processedAt: new Date() }
    });

    return { success: true };
  });
