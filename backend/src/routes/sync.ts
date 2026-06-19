import { Elysia } from "elysia";
import { batchSyncSchema, recordScan } from "../services/attendance";
import prisma from "../services/prisma";

export const syncRoutes = new Elysia()
  .get("/api/sync/faces", async ({ query }) => {
    const since = query.since as string | undefined;
    let faces;

    if (since) {
      const sinceDate = new Date(since);
      faces = await prisma.faceVector.findMany({
        where: { updatedAt: { gte: sinceDate } },
        include: { student: { select: { name: true, nim: true, studyProgram: true, academicYear: true } } }
      });
    } else {
      faces = await prisma.faceVector.findMany({
        include: { student: { select: { name: true, nim: true, studyProgram: true, academicYear: true } } }
      });
    }

    const data = [];
    for (const f of faces) {
      const raw = await prisma.$queryRawUnsafe<Array<{ vector: string }>>(
        `SELECT vector::text FROM face_vectors WHERE student_id = $1`,
        f.studentId
      );
      const vectorStr = raw[0]?.vector?.replace(/[\[\]]/g, "") || "";
      const vector = vectorStr ? vectorStr.split(",").map(Number) : [];

      data.push({
        studentId: f.studentId,
        studentName: f.student.name,
        nim: f.student.nim,
        studyProgram: f.student.studyProgram,
        academicYear: f.student.academicYear,
        vector,
        updatedAt: f.updatedAt.toISOString()
      });
    }

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
  .get("/api/sync/logs", async ({ query }) => {
    const where = query.deviceId ? { deviceId: query.deviceId as string } : {};
    return await prisma.syncLog.findMany({
      where,
      orderBy: { createdAt: "desc" },
      take: 50
    });
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
