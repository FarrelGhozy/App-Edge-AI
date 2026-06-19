import { Elysia } from "elysia";
import prisma from "../services/prisma";

export const settingRoutes = new Elysia()
  .get("/api/settings", async () => {
    const settings = await prisma.globalSetting.findMany();
    const map: Record<string, string> = {};
    for (const s of settings) map[s.key] = s.value;
    return map;
  })
  .put("/api/settings", async ({ body }) => {
    const entries = body as Record<string, string>;
    for (const [key, value] of Object.entries(entries)) {
      await prisma.globalSetting.upsert({
        where: { key },
        update: { value },
        create: { key, value }
      });
    }
    return { success: true };
  });
