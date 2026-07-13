import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { listRules } from "../services/rule";
import { authGuard } from "../guards/auth";

export const ruleRoutes = new Elysia()
  .use(authGuard)
  .get("/api/rules", async () => {
    return await listRules();
  })
  .post("/api/rules", async ({ body }) => {
    const data = body as {
      dayOfWeek: number;
      startTime: string;
      endTime: string;
      isRestricted?: boolean;
      appliesToAll?: boolean;
      studyProgram?: string;
      academicYear?: string;
      priority?: number;
    };
    const rule = await prisma.campusRule.create({ data });
    return { success: true, data: rule };
  })
  .put("/api/rules/:id", async ({ params, body }) => {
    const data = body as {
      dayOfWeek?: number;
      startTime?: string;
      endTime?: string;
      isRestricted?: boolean;
      appliesToAll?: boolean;
      studyProgram?: string;
      academicYear?: string;
      priority?: number;
    };
    const rule = await prisma.campusRule.update({ where: { id: params.id }, data });
    return { success: true, data: rule };
  })
  .delete("/api/rules/:id", async ({ params }) => {
    await prisma.campusRule.delete({ where: { id: params.id } });
    return { success: true };
  })
  .get("/api/rules/effective", async ({ query }) => {
    const time = (query.time as string) || new Date().toTimeString().slice(0, 5);
    const day = query.day !== undefined ? parseInt(query.day as string) : new Date().getDay();
    const rules = await prisma.campusRule.findMany({
      where: { dayOfWeek: day, isRestricted: true, startTime: { lte: time }, endTime: { gte: time } },
      orderBy: { priority: "desc" }
    });
    return { success: true, data: rules };
  });
