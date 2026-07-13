import prisma from "./prisma";

export async function listSchedules() {
  return prisma.courseSchedule.findMany({ orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }] });
}

export async function createSchedule(data: {
  courseName: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  room?: string;
}) {
  return prisma.courseSchedule.create({ data });
}

export async function batchCreateSchedules(items: Array<{
  courseName: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  room?: string;
}>) {
  return prisma.courseSchedule.createMany({ data: items });
}

export async function updateSchedule(id: string, data: {
  courseName?: string;
  dayOfWeek?: number;
  startTime?: string;
  endTime?: string;
  room?: string;
}) {
  return prisma.courseSchedule.update({ where: { id }, data });
}

export async function deleteSchedule(id: string) {
  return prisma.courseSchedule.delete({ where: { id } });
}
