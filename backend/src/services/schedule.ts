import prisma from "./prisma";

export async function listSchedules(page = 1, pageSize = 20) {
  const skip = (page - 1) * pageSize;
  const [data, total] = await Promise.all([
    prisma.courseSchedule.findMany({ skip, take: pageSize, orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }] }),
    prisma.courseSchedule.count()
  ]);
  return { data, total, page, pageSize };
}

export async function createScheduleBatch(entries: Array<{ courseName: string; dayOfWeek: number; startTime: string; endTime: string; room?: string; studentId?: string }>) {
  const created = await prisma.courseSchedule.createMany({
    data: entries.map(e => ({
      courseName: e.courseName,
      dayOfWeek: e.dayOfWeek,
      startTime: e.startTime,
      endTime: e.endTime,
      room: e.room || null,
      studentId: e.studentId || null,
    }))
  });
  return { count: created.count };
}

export async function updateSchedule(id: string, data: { courseName?: string; dayOfWeek?: number; startTime?: string; endTime?: string; room?: string }) {
  return prisma.courseSchedule.update({ where: { id }, data });
}

export async function deleteSchedule(id: string) {
  return prisma.courseSchedule.delete({ where: { id } });
}

export async function getStudentSchedules(studentId: string) {
  return prisma.courseSchedule.findMany({
    where: { studentId },
    orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }]
  });
}
