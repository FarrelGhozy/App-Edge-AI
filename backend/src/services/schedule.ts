import prisma from "./prisma";

export async function listSchedules(studentId?: string) {
  const where = studentId ? { studentId } : {};
  return prisma.courseSchedule.findMany({
    where,
    orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }],
    include: { student: { select: { id: true, nim: true, name: true } } }
  });
}

export async function createSchedule(data: {
  studentId: string;
  courseName: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  room?: string;
  lecturer?: string;
}) {
  return prisma.courseSchedule.create({ data });
}

export async function batchCreateSchedules(items: Array<{
  studentId: string;
  courseName: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  room?: string;
  lecturer?: string;
}>) {
  return prisma.courseSchedule.createMany({ data: items });
}

export async function updateSchedule(id: string, data: {
  courseName?: string;
  dayOfWeek?: number;
  startTime?: string;
  endTime?: string;
  room?: string;
  lecturer?: string;
  isActive?: boolean;
}) {
  return prisma.courseSchedule.update({ where: { id }, data });
}

export async function deleteSchedule(id: string) {
  return prisma.courseSchedule.delete({ where: { id } });
}
