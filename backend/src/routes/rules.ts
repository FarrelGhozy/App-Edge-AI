import { Elysia, t } from "elysia";
import prisma from "../services/prisma";
import { listRules } from "../services/rule";
import { authGuard } from "../guards/auth";
import { notifyDevicesChange } from "../services/events";
import { audit } from "../services/audit";
import { wibTimeHMM, wibDayOfWeek } from "../services/wib";

// #123: Zod schema — body divalidasi, bukan di-cast mentah. dayOfWeek dibatasi
// 0-6 (Minggu-Sabtu), waktu format HH:MM, priority >= 0.
const ruleSchema = t.Object({
  dayOfWeek: t.Integer({ minimum: 0, maximum: 6 }),
  startTime: t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/),
  endTime: t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/),
  isRestricted: t.Optional(t.Boolean()),
  appliesToAll: t.Optional(t.Boolean()),
  studyProgram: t.Optional(t.String()),
  academicYear: t.Optional(t.String()),
  priority: t.Optional(t.Integer({ minimum: 0 }))
});

const ruleUpdateSchema = t.Object({
  dayOfWeek: t.Optional(t.Integer({ minimum: 0, maximum: 6 })),
  startTime: t.Optional(t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/)),
  endTime: t.Optional(t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/)),
  isRestricted: t.Optional(t.Boolean()),
  appliesToAll: t.Optional(t.Boolean()),
  studyProgram: t.Optional(t.String()),
  academicYear: t.Optional(t.String()),
  priority: t.Optional(t.Integer({ minimum: 0 }))
});

const notFound = (msg: string) =>
  new Response(JSON.stringify({ success: false, error: msg }), {
    status: 404,
    headers: { "Content-Type": "application/json" }
  });

/** Deteksi Prisma error "record not found" (P2025) → 404 bersih, bukan 500. */
const isP2025 = (e: unknown): boolean =>
  e instanceof Error &&
  "code" in e &&
  (e as { code: string }).code === "P2025";

export const ruleRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
  .get("/api/rules", async () => {
    return await listRules();
  })
  .post("/api/rules", async ({ body, admin, set }) => {
    // #123: body divalidasi Zod (handler menerima data yang sudah bersih).
    const data = body as typeof ruleSchema.static;
    const rule = await prisma.campusRule.create({ data });
    notifyDevicesChange();
    await audit(admin, { action: "CREATE", entityType: "RULES", entityId: rule.id, details: `${data.startTime}-${data.endTime} day=${data.dayOfWeek}` });
    return { success: true, data: rule };
  }, { body: ruleSchema })
  .put("/api/rules/:id", async ({ params, body, admin }) => {
    const data = body as Partial<typeof ruleSchema.static>;
    try {
      const rule = await prisma.campusRule.update({ where: { id: params.id }, data });
      notifyDevicesChange();
      await audit(admin, { action: "UPDATE", entityType: "RULES", entityId: params.id });
      return { success: true, data: rule };
    } catch (e) {
      if (isP2025(e)) return notFound("Rule tidak ditemukan");
      throw e;
    }
  }, { body: ruleUpdateSchema })
  .delete("/api/rules/:id", async ({ params, admin }) => {
    try {
      await prisma.campusRule.delete({ where: { id: params.id } });
      notifyDevicesChange();
      await audit(admin, { action: "DELETE", entityType: "RULES", entityId: params.id });
      return { success: true };
    } catch (e) {
      if (isP2025(e)) return notFound("Rule tidak ditemukan");
      throw e;
    }
  })
  .get("/api/rules/effective", async ({ query }) => {
    // #110: default "sekarang" menurut WIB, bukan timezone proses (UTC).
    const now = new Date();
    const time = (query.time as string) || wibTimeHMM(now);
    const day = query.day !== undefined ? parseInt(query.day as string) : wibDayOfWeek(now);
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
