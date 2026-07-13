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
