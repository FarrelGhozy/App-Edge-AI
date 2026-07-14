import prisma from "./prisma";

export async function dailyReport(date: string) {
  const startDate = new Date(date);
  startDate.setHours(0, 0, 0, 0);
  const endDate = new Date(date);
  endDate.setHours(23, 59, 59, 999);

  const logs = await prisma.attendanceLog.findMany({
    where: { timestamp: { gte: startDate, lte: endDate } },
    orderBy: [{ studentName: "asc" }, { timestamp: "asc" }]
  });

  const studentCount = new Set(logs.map(l => l.studentId)).size;
  const keluarCount = logs.filter(l => l.action === "keluar").length;
  const kembaliCount = logs.filter(l => l.action === "kembali").length;

  const stillOutside = new Set(
    logs.filter(l => l.action === "keluar").map(l => l.studentId)
  );
  for (const l of logs) {
    if (l.action === "kembali") stillOutside.delete(l.studentId);
  }

  return {
    date,
    totalLogs: logs.length,
    studentCount,
    keluarCount,
    kembaliCount,
    stillOutsideCount: stillOutside.size,
    logs
  };
}

export async function monthlyReport(month: number, year: number) {
  const startDate = new Date(year, month - 1, 1);
  const endDate = new Date(year, month, 0, 23, 59, 59, 999);

  const students = await prisma.student.findMany({
    where: { isActive: true }
  });

  const logs = await prisma.attendanceLog.findMany({
    where: { timestamp: { gte: startDate, lte: endDate } },
    orderBy: { timestamp: "asc" }
  });

  const violations = await prisma.violation.findMany({
    where: { timestamp: { gte: startDate, lte: endDate } }
  });

  const permits = await prisma.permit.findMany({
    where: { createdAt: { gte: startDate, lte: endDate } }
  });

  const logsByStudent: Record<string, typeof logs> = {};
  for (const l of logs) {
    if (!logsByStudent[l.studentId]) logsByStudent[l.studentId] = [];
    logsByStudent[l.studentId].push(l);
  }

  const stats = students.map(s => {
    const studentLogs = logsByStudent[s.id] || [];
    const keluarCount = studentLogs.filter(l => l.action === "keluar").length;
    const studentViolations = violations.filter(v => v.studentId === s.id).length;
    const studentPermits = permits.filter(p => p.studentId === s.id).length;

    let totalDurationMs = 0;
    let keluarTime: number | null = null;
    for (const l of studentLogs) {
      const t = l.timestamp.getTime();
      if (l.action === "keluar") {
        keluarTime = t;
      } else if (l.action === "kembali" && keluarTime) {
        totalDurationMs += t - keluarTime;
        keluarTime = null;
      }
    }

    return {
      studentId: s.id,
      nim: s.nim,
      name: s.name,
      studyProgram: s.studyProgram,
      keluarCount,
      totalDurationHours: +(totalDurationMs / 3600000).toFixed(1),
      violationCount: studentViolations,
      permitCount: studentPermits
    };
  });

  return {
    month,
    year,
    totalStudents: students.length,
    stats
  };
}

export async function violationReport(from: string, to: string) {
  const fromDate = new Date(from);
  const toDate = new Date(to);
  toDate.setHours(23, 59, 59, 999);

  const violations = await prisma.violation.findMany({
    where: { timestamp: { gte: fromDate, lte: toDate } },
    orderBy: { timestamp: "desc" },
    include: { student: { select: { name: true } } }
  });

  const enriched = violations.map(v => ({
    ...v,
    studentName: v.student.name
  }));

  return {
    from,
    to,
    total: violations.length,
    violations: enriched
  };
}

export async function outsideNow() {
  const todayStart = new Date();
  todayStart.setHours(0, 0, 0, 0);

  const logs = await prisma.attendanceLog.findMany({
    where: { timestamp: { gte: todayStart } },
    orderBy: { timestamp: "desc" }
  });

  const outsideIds = new Set<string>();
  for (const l of logs) {
    if (l.action === "keluar") outsideIds.add(l.studentId);
    else if (l.action === "kembali") outsideIds.delete(l.studentId);
  }

  const students = await prisma.student.findMany({
    where: { id: { in: Array.from(outsideIds) }, isActive: true }
  });

  const lastKeluarTimes: Record<string, Date> = {};
  for (const l of logs) {
    if (l.action === "keluar" && !lastKeluarTimes[l.studentId]) {
      lastKeluarTimes[l.studentId] = l.timestamp;
    }
  }

  return {
    count: students.length,
    students: students.map(s => ({
      id: s.id,
      nim: s.nim,
      name: s.name,
      studyProgram: s.studyProgram,
      keluarSince: lastKeluarTimes[s.id]?.toISOString() || null
    }))
  };
}
