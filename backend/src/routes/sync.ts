import { Elysia } from "elysia";
import { batchSyncSchema, recordScan } from "../services/attendance";
import prisma from "../services/prisma";

export const syncRoutes = new Elysia()
  .get("/api/sync/faces", async ({ query }) => {
    const faces = await prisma.faceVector.findMany({
      include: { student: { select: { name: true } } }
    });

    const data = faces.map((f) => ({
      studentId: f.studentId,
      vector: [],
      updatedAt: f.updatedAt.toISOString()
    }));

    return { data, since: query.since || null };
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
    const request = await prisma.syncRequest.findFirst({
      where: { deviceId: query.deviceId as string, isProcessed: false },
      orderBy: { requestedAt: "desc" }
    });
    return { requested: !!request, requestedAt: request?.requestedAt?.toISOString() || null };
  })
  .post("/api/sync/complete", async ({ body }) => {
    await prisma.syncLog.create({
      data: {
        deviceId: body.deviceId,
        syncType: body.syncType || "manual",
        status: body.status || "success",
        logsCount: body.logsCount || 0
      }
    });

    await prisma.syncRequest.updateMany({
      where: { deviceId: body.deviceId, isProcessed: false },
      data: { isProcessed: true, processedAt: new Date() }
    });

    return { success: true };
  });
