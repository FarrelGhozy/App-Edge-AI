import prisma from "./prisma";

export async function listRules() {
  return prisma.campusRule.findMany({ orderBy: [{ dayOfWeek: "asc" }, { priority: "desc" }] });
}

export async function getSettings() {
  const settings = await prisma.globalSetting.findMany();
  const map: Record<string, string> = {};
  for (const s of settings) map[s.key] = s.value;
  return map;
}

export async function createRule(data: {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  isRestricted?: boolean;
  appliesToAll?: boolean;
  studyProgram?: string;
  academicYear?: string;
  priority?: number;
}) {
  return prisma.campusRule.create({ data });
}

export async function updateRule(id: string, data: {
  dayOfWeek?: number;
  startTime?: string;
  endTime?: string;
  isRestricted?: boolean;
  appliesToAll?: boolean;
  studyProgram?: string;
  academicYear?: string;
  priority?: number;
}) {
  return prisma.campusRule.update({ where: { id }, data });
}

export async function deleteRule(id: string) {
  return prisma.campusRule.delete({ where: { id } });
}

export async function getEffectiveRule(time: string, day: number) {
  const rules = await prisma.campusRule.findMany({
    where: { dayOfWeek: day },
    orderBy: { priority: "desc" }
  });

  for (const rule of rules) {
    if (rule.startTime <= time && time < rule.endTime) {
      return rule;
    }
  }
  return null;
}
