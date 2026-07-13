import { Elysia } from "elysia";
import { scanSchema, batchSyncSchema, recordScan, listAttendance, getTodayAttendance, getAttendanceStatus, getAttendanceStatistics } from "../services/attendance";
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
    return await getTodayAttendance();
  })
  .get("/api/attendance/status/:studentId", async ({ params: { studentId } }) => {
    return await getAttendanceStatus(studentId);
  })
  .get("/api/attendance/statistics", async () => {
    return await getAttendanceStatistics();
  });
