import { Elysia } from "elysia";
import { listRules, getSettings, createRule, updateRule, deleteRule, getEffectiveRule } from "../services/rule";
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
    return { success: true, data: await createRule(data) };
  })
  .put("/api/rules/:id", async ({ params: { id }, body }) => {
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
    return { success: true, data: await updateRule(id, data) };
  })
  .delete("/api/rules/:id", async ({ params: { id } }) => {
    await deleteRule(id);
    return { success: true };
  })
  .get("/api/rules/effective", async ({ query }) => {
    const time = (query.time as string) || new Date().toTimeString().slice(0, 5);
    const day = query.day !== undefined ? parseInt(query.day as string) : new Date().getDay();
    const rule = await getEffectiveRule(time, day);
    return { time, day, rule };
  })
  .get("/api/settings", async () => {
    return await getSettings();
  });
