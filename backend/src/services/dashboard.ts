import prisma from "./prisma";

export async function dashboardSummary() {
  const totalStudents = await prisma.student.count({ where: { isActive: true } });

  const latestActions = await prisma.$queryRawUnsafe<
    Array<{ student_id: string; action: string }>
  >(
    `SELECT DISTINCT ON (student_id) student_id, action
     FROM attendance_logs
     ORDER BY student_id, timestamp DESC`
  );

  const currentlyOutside = latestActions.filter((r) => r.action === "keluar").length;

  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const todayEnd = new Date(todayStart.getTime() + 86400000);

  const [violationsToday, recentScans] = await Promise.all([
    prisma.violation.count({
      where: { timestamp: { gte: todayStart, lt: todayEnd } },
    }),
    prisma.attendanceLog.findMany({
      orderBy: { timestamp: "desc" },
      take: 10,
    }),
  ]);

  return {
    totalStudents,
    currentlyOutside,
    violationsToday,
    recentScans,
  };
}

export async function dashboardWeekly() {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const monday = new Date(now);
  monday.setDate(now.getDate() - ((dayOfWeek + 6) % 7));
  monday.setHours(0, 0, 0, 0);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 7);

  const dailyStats = [];
  for (let d = new Date(monday); d < sunday; d.setDate(d.getDate() + 1)) {
    const dayStart = new Date(d);
    const dayEnd = new Date(d);
    dayEnd.setHours(23, 59, 59, 999);

    const dayLogs = await prisma.attendanceLog.findMany({
      where: { timestamp: { gte: dayStart, lte: dayEnd } }
    });

    dailyStats.push({
      date: dayStart.toISOString().split("T")[0],
      keluar: dayLogs.filter(l => l.action === "keluar").length,
      kembali: dayLogs.filter(l => l.action === "kembali").length,
    });
  }

  return { weekStart: monday.toISOString().split("T")[0], dailyStats };
}

export async function dashboardOutsideNow() {
  const latestActions = await prisma.$queryRawUnsafe<
    Array<{ student_id: string; student_name: string; action: string; timestamp: Date }>
  >(
    `SELECT DISTINCT ON (al.student_id) al.student_id, al.student_name, al.action, al.timestamp
     FROM attendance_logs al
     ORDER BY al.student_id, al.timestamp DESC`
  );

  const outsideStudents = latestActions.filter(r => r.action === "keluar");
  return {
    count: outsideStudents.length,
    students: outsideStudents.map(s => ({
      studentId: s.student_id,
      studentName: s.student_name,
      keluarSince: s.timestamp,
    }))
  };
}

export async function dashboardViolationSummary() {
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  const [totalViolations, todayViolations, byType] = await Promise.all([
    prisma.violation.count(),
    prisma.violation.count({ where: { timestamp: { gte: todayStart } } }),
    prisma.violation.groupBy({ by: ["type"], _count: true }),
  ]);

  return { totalViolations, todayViolations, byType };
}

export async function dashboardRecentScans(limit = 20) {
  return prisma.attendanceLog.findMany({
    orderBy: { timestamp: "desc" },
    take: limit,
  });
}
