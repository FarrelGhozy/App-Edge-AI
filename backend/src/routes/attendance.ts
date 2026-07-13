import { Elysia } from "elysia";
import { scanSchema, batchSyncSchema, recordScan, listAttendance } from "../services/attendance";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const attendanceRoutes = new Elysia()
  .use(authGuard)
  .post("/api/attendance/scan", async ({ body }) => {
    const student = await prisma.student.findUnique({ where: { id: body.studentId } });
    if (!student) {
      return new Response(JSON.stringify({ success: false, error: "Student not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }

    const log = await recordScan({
      studentId: body.studentId,
      studentName: student.name,
      action: body.action,
      confidenceScore: body.confidenceScore,
      isViolation: body.isViolation,
      violationType: body.violationType,
      deviceId: body.deviceId,
      photoCapture: body.photoCapture,
      timestamp: body.timestamp
    });

    return { success: true, data: log };
  }, { body: scanSchema })
  .get("/api/attendance", async ({ query }) => {
    const params = {
      page: query.page ? parseInt(query.page as string) : 1,
      pageSize: query.pageSize ? parseInt(query.pageSize as string) : 20,
      studentId: query.studentId as string | undefined,
      startDate: query.startDate as string | undefined,
      endDate: query.endDate as string | undefined
    };
    return await listAttendance(params);
  })
  .get("/api/attendance/today", async () => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    const logs = await prisma.attendanceLog.findMany({
      where: { timestamp: { gte: today, lt: tomorrow } },
      orderBy: { timestamp: "desc" }
    });

    const outsideIds = new Set<string>();
    for (const l of logs) {
      if (l.action === "keluar") outsideIds.add(l.studentId);
      else if (l.action === "kembali") outsideIds.delete(l.studentId);
    }

    return {
      success: true,
      data: {
        total: logs.length,
        keluarCount: logs.filter(l => l.action === "keluar").length,
        kembaliCount: logs.filter(l => l.action === "kembali").length,
        stillOutside: outsideIds.size,
        logs
      }
    };
  })
  .get("/api/attendance/status/:studentId", async ({ params: { studentId } }) => {
    const student = await prisma.student.findUnique({ where: { id: studentId } });
    if (!student) {
      return new Response(JSON.stringify({ success: false, error: "Student not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    const todayLogs = await prisma.attendanceLog.findMany({
      where: { studentId, timestamp: { gte: today, lt: tomorrow } },
      orderBy: { timestamp: "desc" }
    });

    const lastAction = todayLogs.length > 0 ? todayLogs[0].action : null;

    const now = new Date();
    const month = now.getMonth() + 1;
    const year = now.getFullYear();
    const quota = await prisma.permitQuota.findUnique({
      where: { studentId_month_year: { studentId, month, year } }
    });

    return {
      success: true,
      data: {
        studentId,
        studentName: student.name,
        nim: student.nim,
        currentStatus: lastAction === "keluar" ? "outside" : lastAction === "kembali" ? "inside" : "unknown",
        lastAction,
        lastTimestamp: todayLogs[0]?.timestamp?.toISOString() || null,
        totalScansToday: todayLogs.length,
        permitsUsed: quota?.permitsUsed ?? 0,
        maxPermits: quota?.maxPermits ?? 10
      }
    };
  })
  .get("/api/attendance/statistics", async ({ query }) => {
    const startDate = query.startDate ? new Date(query.startDate as string) : new Date(new Date().setDate(new Date().getDate() - 30));
    const endDate = query.endDate ? new Date(query.endDate as string) : new Date();
    endDate.setHours(23, 59, 59, 999);

    const totalScans = await prisma.attendanceLog.count({
      where: { timestamp: { gte: startDate, lte: endDate } }
    });

    const keluarCount = await prisma.attendanceLog.count({
      where: { timestamp: { gte: startDate, lte: endDate }, action: "keluar" }
    });

    const kembaliCount = await prisma.attendanceLog.count({
      where: { timestamp: { gte: startDate, lte: endDate }, action: "kembali" }
    });

    const uniqueStudents = await prisma.attendanceLog.groupBy({
      by: ["studentId"],
      where: { timestamp: { gte: startDate, lte: endDate } }
    });

    return {
      success: true,
      data: {
        totalScans,
        keluarCount,
        kembaliCount,
        uniqueStudentCount: uniqueStudents.length,
        startDate: startDate.toISOString(),
        endDate: endDate.toISOString()
      }
    };
  });
