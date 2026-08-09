import { Elysia, t } from "elysia";
import { listSchedules, createSchedule, batchCreateSchedules, updateSchedule, deleteSchedule } from "../services/schedule";
import { authGuard } from "../guards/auth";
import prisma from "../services/prisma";

// #123: Zod schema — body divalidasi, bukan di-cast mentah.
const scheduleSchema = t.Object({
  studentId: t.String(),
  courseName: t.String({ minLength: 1 }),
  dayOfWeek: t.Integer({ minimum: 0, maximum: 6 }),
  startTime: t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/),
  endTime: t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/),
  room: t.Optional(t.String()),
  lecturer: t.Optional(t.String())
});

const scheduleUpdateSchema = t.Object({
  courseName: t.Optional(t.String({ minLength: 1 })),
  dayOfWeek: t.Optional(t.Integer({ minimum: 0, maximum: 6 })),
  startTime: t.Optional(t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/)),
  endTime: t.Optional(t.RegExp(/^([01]\d|2[0-3]):[0-5]\d$/)),
  room: t.Optional(t.String()),
  lecturer: t.Optional(t.String()),
  isActive: t.Optional(t.Boolean())
});

const notFound = (msg: string) =>
  new Response(JSON.stringify({ success: false, error: msg }), {
    status: 404,
    headers: { "Content-Type": "application/json" }
  });

const isP2025 = (e: unknown): boolean =>
  e instanceof Error &&
  "code" in e &&
  (e as { code: string }).code === "P2025";

export const scheduleRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
  .get("/api/schedules", async ({ query }) => {
    const studentId = query.studentId as string | undefined;
    return await listSchedules(studentId);
  })
  .get("/api/schedules/student/:studentId", async ({ params: { studentId } }) => {
    return await listSchedules(studentId);
  })
  .post("/api/schedules", async ({ body }) => {
    const data = body as typeof scheduleSchema.static;
    // #123: cek student ada → 400, bukan 500 FK error.
    const student = await prisma.student.findUnique({ where: { id: data.studentId } });
    if (!student) {
      return new Response(JSON.stringify({ success: false, error: "Student tidak ditemukan" }), {
        status: 400,
        headers: { "Content-Type": "application/json" }
      });
    }
    return await createSchedule(data);
  }, { body: scheduleSchema })
  .post("/api/schedules/batch", async ({ body }) => {
    const { items } = body as { items: typeof scheduleSchema.static[] };
    // #123: validasi semua student di batch → 400 cepat sebelum insert apa pun.
    const ids = [...new Set(items.map((i) => i.studentId))];
    const students = await prisma.student.findMany({ where: { id: { in: ids } } });
    const found = new Set(students.map((s) => s.id));
    const missing = ids.filter((id) => !found.has(id));
    if (missing.length > 0) {
      return new Response(
        JSON.stringify({ success: false, error: `Student tidak ditemukan: ${missing.join(", ")}` }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }
    return await batchCreateSchedules(items);
  }, { body: t.Object({ items: t.Array(scheduleSchema) }) })
  .put("/api/schedules/:id", async ({ params, body }) => {
    const data = body as Partial<typeof scheduleSchema.static>;
    try {
      return await updateSchedule(params.id, data);
    } catch (e) {
      if (isP2025(e)) return notFound("Schedule tidak ditemukan");
      throw e;
    }
  }, { body: scheduleUpdateSchema })
  .delete("/api/schedules/:id", async ({ params }) => {
    try {
      return await deleteSchedule(params.id);
    } catch (e) {
      if (isP2025(e)) return notFound("Schedule tidak ditemukan");
      throw e;
    }
  });
