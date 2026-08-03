import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { listRules } from "../services/rule";
import { authGuard } from "../guards/auth";
import { notifyDevicesChange } from "../services/events";
import { audit } from "../services/audit";

export const ruleRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
  .get("/api/rules", async () => {
    return await listRules();
  })
  .post("/api/rules", async ({ body, admin }) => {
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
    notifyDevicesChange();
    await audit(admin, { action: "CREATE", entityType: "RULES", entityId: rule.id, details: `${data.startTime}-${data.endTime} day=${data.dayOfWeek}` });
    return { success: true, data: rule };
  })
  .put("/api/rules/:id", async ({ params, body, admin }) => {
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
    notifyDevicesChange();
    await audit(admin, { action: "UPDATE", entityType: "RULES", entityId: params.id });
    return { success: true, data: rule };
  })
  .delete("/api/rules/:id", async ({ params, admin }) => {
    await prisma.campusRule.delete({ where: { id: params.id } });
    notifyDevicesChange();
    await audit(admin, { action: "DELETE", entityType: "RULES", entityId: params.id });
    return { success: true };
  })
  .get("/api/rules/effective", async ({ query }) => {
    const time = (query.time as string) || new Date().toTimeString().slice(0, 5);
    const day = query.day !== undefined ? parseInt(query.day as string) : new Date().getDay();
    const rules = await prisma.campusRule.findMany({
      where: { dayOfWeek: day, isRestricted: true },
      orderBy: { priority: "desc" }
    });
    // #83: rule bisa overnight (endTime < startTime, contoh 22:00–05:00).
    // Kondisi `start <= t <= end` mustahil true utk overnight — cek manual:
    // rule normal aktif jika start <= t <= end; rule overnight aktif jika
    // t >= start ATAU t <= end.
    const effective = rules.filter(r => {
      const overnight = r.endTime < r.startTime;
      if (overnight) {
        return time >= r.startTime || time <= r.endTime;
      }
      return time >= r.startTime && time <= r.endTime;
    });
    return { success: true, data: effective };
  });
