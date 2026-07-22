import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { dashboardSummary } from "../services/dashboard";
import { authGuard } from "../guards/auth";

export const dashboardRoutes = new Elysia()
  .use(authGuard)
  .get("/api/dashboard/summary", async () => {
    return await dashboardSummary();
  })
  .get("/api/dashboard/weekly", async () => {
    const now = new Date();
    const dayOfWeek = now.getDay();
    const weekStart = new Date(now);
    weekStart.setDate(now.getDate() - ((dayOfWeek + 6) % 7));
    weekStart.setHours(0, 0, 0, 0);
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 7);

    const dailyStats = [];
    for (let i = 0; i < 7; i++) {
      const dayStart = new Date(weekStart);
      dayStart.setDate(dayStart.getDate() + i);
      const dayEnd = new Date(dayStart);
      dayEnd.setDate(dayEnd.getDate() + 1);

      const count = await prisma.attendanceLog.count({
        where: { timestamp: { gte: dayStart, lt: dayEnd } }
      });

      dailyStats.push({
        date: dayStart.toISOString().split("T")[0],
        dayName: ["Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"][dayStart.getDay()],
        scanCount: count
      });
    }

    return { success: true, data: dailyStats };
  })
  .get("/api/dashboard/violation-summary", async () => {
    const total = await prisma.violation.count();
    const resolved = await prisma.violation.count({ where: { isResolved: true } });
    const unresolved = await prisma.violation.count({ where: { isResolved: false } });
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayViolations = await prisma.violation.count({
      where: { timestamp: { gte: today } }
    });

    const byType = await prisma.violation.groupBy({
      by: ["type"],
      _count: true
    });

    return {
      success: true,
      data: { total, resolved, unresolved, todayViolations, byType: byType.map(b => ({ type: b.type, count: b._count })) }
    };
  })
  .get("/api/dashboard/recent-scans", async () => {
    const scans = await prisma.attendanceLog.findMany({
      orderBy: { timestamp: "desc" },
      take: 20
    });
    return { success: true, data: scans };
  })
  // ─── Dashboard outside-now summary ───
  .get("/api/dashboard/outside-now", async () => {
    const lookback = new Date();
    lookback.setDate(lookback.getDate() - 2);
    lookback.setHours(0, 0, 0, 0);

    const recentLogs = await prisma.attendanceLog.findMany({
      where: { timestamp: { gte: lookback } },
      orderBy: [{ studentId: "asc" }, { timestamp: "desc" }]
    });

    const studentActions = new Map<string, string>();
    for (const l of recentLogs) {
      if (!studentActions.has(l.studentId)) {
        studentActions.set(l.studentId, l.action);
      }
    }

    const count = Array.from(studentActions.values()).filter(a => a === "keluar").length;
    return { success: true, data: { count } };
  });
