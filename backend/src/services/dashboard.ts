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

  const [violationsToday, recentScans, registeredFaces] = await Promise.all([
    prisma.violation.count({
      where: { timestamp: { gte: todayStart, lt: todayEnd } },
    }),
    prisma.attendanceLog.findMany({
      orderBy: { timestamp: "desc" },
      take: 10,
    }),
    // #94: jumlah santri dengan minimal 1 wajah terdaftar (distinct student_id)
    prisma.$queryRawUnsafe<Array<{ count: string }>>(
      `SELECT COUNT(DISTINCT student_id)::text AS count FROM face_vectors`
    ),
  ]);

  return {
    totalStudents,
    currentlyOutside,
    violationsToday,
    recentScans,
    registeredFaces: parseInt(registeredFaces[0]?.count ?? "0", 10),
  };
}
