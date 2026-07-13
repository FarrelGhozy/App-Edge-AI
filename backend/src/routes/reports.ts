import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { dailyReport, monthlyReport, violationReport, outsideNow } from "../services/report";
import { authGuard } from "../guards/auth";

export const reportRoutes = new Elysia()
  .use(authGuard)
  .get("/api/reports/daily", async ({ query }) => {
    const date = (query.date as string) || new Date().toISOString().split("T")[0];
    return await dailyReport(date);
  })
  .get("/api/reports/monthly", async ({ query }) => {
    const now = new Date();
    const month = query.month ? parseInt(query.month as string) : now.getMonth() + 1;
    const year = query.year ? parseInt(query.year as string) : now.getFullYear();
    return await monthlyReport(month, year);
  })
  .get("/api/reports/violations", async ({ query }) => {
    const from = (query.from as string) || new Date().toISOString().split("T")[0];
    const to = (query.to as string) || new Date().toISOString().split("T")[0];
    return await violationReport(from, to);
  })
  .get("/api/reports/outside-now", async () => {
    return await outsideNow();
  })
  .get("/api/reports/toggle-history", async ({ query }) => {
    const date = (query.date as string) || new Date().toISOString().split("T")[0];
    const studentId = query.studentId as string | undefined;

    const startDate = new Date(date);
    startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(date);
    endDate.setHours(23, 59, 59, 999);

    const where: Record<string, unknown> = { timestamp: { gte: startDate, lte: endDate } };
    if (studentId) where.studentId = studentId;

    const logs = await prisma.attendanceLog.findMany({
      where,
      orderBy: [{ studentName: "asc" }, { timestamp: "asc" }]
    });

    const historyByStudent: Record<string, typeof logs> = {};
    for (const l of logs) {
      if (!historyByStudent[l.studentId]) historyByStudent[l.studentId] = [];
      historyByStudent[l.studentId].push(l);
    }

    return {
      success: true,
      data: {
        date,
        totalStudents: Object.keys(historyByStudent).length,
        students: Object.entries(historyByStudent).map(([sid, studentLogs]) => ({
          studentId: sid,
          studentName: studentLogs[0].studentName,
          scans: studentLogs.map(l => ({
            action: l.action,
            timestamp: l.timestamp.toISOString(),
            confidenceScore: l.confidenceScore,
            deviceId: l.deviceId
          }))
        }))
      }
    };
  })
  .get("/api/reports/outside-hours", async ({ query }) => {
    const date = (query.date as string) || new Date().toISOString().split("T")[0];

    const startDate = new Date(date);
    startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(date);
    endDate.setHours(23, 59, 59, 999);

    const logs = await prisma.attendanceLog.findMany({
      where: { timestamp: { gte: startDate, lte: endDate } },
      orderBy: [{ studentId: "asc" }, { timestamp: "asc" }]
    });

    const outsidePeriods: Array<{ studentId: string; studentName: string; keluarTime: string; kembaliTime: string | null; durationMinutes: number | null }> = [];
    const state: Record<string, { name: string; keluarTime: Date }> = {};

    for (const l of logs) {
      if (l.action === "keluar") {
        state[l.studentId] = { name: l.studentName, keluarTime: l.timestamp };
      } else if (l.action === "kembali" && state[l.studentId]) {
        const keluarTime = state[l.studentId].keluarTime;
        const durationMs = l.timestamp.getTime() - keluarTime.getTime();
        outsidePeriods.push({
          studentId: l.studentId,
          studentName: state[l.studentId].name,
          keluarTime: keluarTime.toISOString(),
          kembaliTime: l.timestamp.toISOString(),
          durationMinutes: Math.round(durationMs / 60000)
        });
        delete state[l.studentId];
      }
    }

    for (const [sid, s] of Object.entries(state)) {
      outsidePeriods.push({
        studentId: sid,
        studentName: s.name,
        keluarTime: s.keluarTime.toISOString(),
        kembaliTime: null,
        durationMinutes: null
      });
    }

    return { success: true, data: { date, periods: outsidePeriods, totalOutside: outsidePeriods.length } };
  });
