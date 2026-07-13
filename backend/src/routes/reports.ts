import { Elysia } from "elysia";
import { dailyReport, monthlyReport, violationReport, outsideNow } from "../services/report";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const reportRoutes = new Elysia()
  .use(authGuard)
  .get("/api/reports/daily", async ({ query }) => {
    const date = (query.date as string) || new Date().toISOString().split("T")[0];
    return await dailyReport(date);
  })
  .get("/api/reports/daily/export", async ({ query }) => {
    const date = (query.date as string) || new Date().toISOString().split("T")[0];
    const format = (query.format as string) || "csv";
    const report = await dailyReport(date);

    if (format === "csv") {
      const header = "studentId,studentName,action,timestamp,confidenceScore\n";
      const rows = report.logs.map(l =>
        `${l.studentId},"${l.studentName}",${l.action},${l.timestamp.toISOString()},${l.confidenceScore}`
      ).join("\n");
      return new Response(header + rows, {
        headers: { "Content-Type": "text/csv", "Content-Disposition": `attachment; filename="daily-report-${date}.csv"` }
      });
    }

    return report;
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
  .get("/api/reports/permits", async ({ query }) => {
    const from = (query.from as string) || new Date().toISOString().split("T")[0];
    const to = (query.to as string) || new Date().toISOString().split("T")[0];
    const permits = await prisma.permit.findMany({
      where: { createdAt: { gte: new Date(from), lte: new Date(to + "T23:59:59.999Z") } },
      orderBy: { createdAt: "desc" }
    });
    return { from, to, total: permits.length, permits };
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

    const toggleData: Record<string, Array<{ action: string; timestamp: string }>> = {};
    for (const l of logs) {
      if (!toggleData[l.studentId]) toggleData[l.studentId] = [];
      toggleData[l.studentId].push({ action: l.action, timestamp: l.timestamp.toISOString() });
    }

    const students = await prisma.student.findMany({
      where: { id: { in: Object.keys(toggleData) } },
      select: { id: true, nim: true, name: true }
    });
    const studentMap = new Map(students.map(s => [s.id, s]));

    const result = Object.entries(toggleData).map(([sid, toggles]) => ({
      student: studentMap.get(sid) || { id: sid, nim: "", name: "" },
      toggles
    }));

    return { date, total: result.length, data: result };
  })
  .get("/api/reports/outside-hours", async ({ query }) => {
    const date = (query.date as string) || new Date().toISOString().split("T")[0];
    const startDate = new Date(date);
    startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(date);
    endDate.setHours(23, 59, 59, 999);

    const logs = await prisma.attendanceLog.findMany({
      where: { timestamp: { gte: startDate, lte: endDate } },
      orderBy: { timestamp: "asc" }
    });

    const studentDurations: Record<string, { name: string; totalMs: number; keluarCount: number }> = {};
    const studentNames = new Map<string, string>();
    const activeTimers: Record<string, number> = {};

    for (const l of logs) {
      studentNames.set(l.studentId, l.studentName);
      if (!studentDurations[l.studentId]) {
        studentDurations[l.studentId] = { name: l.studentName, totalMs: 0, keluarCount: 0 };
      }
      if (l.action === "keluar") {
        activeTimers[l.studentId] = l.timestamp.getTime();
        studentDurations[l.studentId].keluarCount++;
      } else if (l.action === "kembali" && activeTimers[l.studentId]) {
        studentDurations[l.studentId].totalMs += l.timestamp.getTime() - activeTimers[l.studentId];
        delete activeTimers[l.studentId];
      }
    }

    for (const [sid, startTime] of Object.entries(activeTimers)) {
      studentDurations[sid].totalMs += endDate.getTime() - startTime;
    }

    const result = Object.entries(studentDurations).map(([studentId, d]) => ({
      studentId,
      name: d.name,
      keluarCount: d.keluarCount,
      totalHours: +(d.totalMs / 3600000).toFixed(1),
    }));

    return { date, total: result.length, data: result };
  });
