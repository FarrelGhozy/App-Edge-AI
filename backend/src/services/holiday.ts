import prisma from "./prisma";
import { wibDayStart, wibDayEndExclusive } from "./wib";

export async function listHolidays(year?: number) {
  const where: Record<string, unknown> = {};
  if (year) {
    const start = new Date(year, 0, 1);
    const end = new Date(year, 11, 31, 23, 59, 59, 999);
    where.date = { gte: start, lte: end };
  }
  return prisma.holiday.findMany({ where, orderBy: { date: "asc" } });
}

export async function getHoliday(id: string) {
  return prisma.holiday.findUnique({ where: { id } });
}

export async function createHoliday(data: { name: string; date: string; type?: string; description?: string }) {
  return prisma.holiday.create({
    data: {
      name: data.name,
      date: new Date(data.date),
      type: data.type || "national",
      description: data.description || null,
    }
  });
}

export async function updateHoliday(id: string, data: { name?: string; date?: string; type?: string; description?: string }) {
  const updateData: Record<string, unknown> = {};
  if (data.name) updateData.name = data.name;
  if (data.date) updateData.date = new Date(data.date);
  if (data.type) updateData.type = data.type;
  if (data.description !== undefined) updateData.description = data.description;
  return prisma.holiday.update({ where: { id }, data: updateData });
}

export async function deleteHoliday(id: string) {
  return prisma.holiday.delete({ where: { id } });
}

export async function isTodayHoliday() {
  // #110: batas hari menurut WIB, bukan setHours() timezone proses.
  const today = new Date();
  const dayStart = wibDayStart(today);
  const dayEnd = wibDayEndExclusive(today);
  const holiday = await prisma.holiday.findFirst({
    where: { date: { gte: dayStart, lt: dayEnd } }
  });
  return { isHoliday: !!holiday, holiday: holiday || null };
}
