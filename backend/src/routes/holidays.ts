import { Elysia, t } from "elysia";
import { authGuard } from "../guards/auth";
import {
  listHolidays,
  getHoliday,
  createHoliday,
  updateHoliday,
  deleteHoliday,
  isTodayHoliday,
} from "../services/holiday";
import prisma from "../services/prisma";

// #123: Zod schema — body divalidasi, bukan di-cast mentah. date harus
// format YYYY-MM-DD (valid) dan type dibatasi.
const holidaySchema = t.Object({
  name: t.String({ minLength: 1 }),
  date: t.RegExp(/^\d{4}-\d{2}-\d{2}$/),
  type: t.Optional(t.String()),
  description: t.Optional(t.String())
});

const holidayUpdateSchema = t.Object({
  name: t.Optional(t.String({ minLength: 1 })),
  date: t.Optional(t.RegExp(/^\d{4}-\d{2}-\d{2}$/)),
  type: t.Optional(t.String()),
  description: t.Optional(t.String())
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

export const holidayRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
  .get("/api/holidays", async ({ query }) => {
    const year = query.year ? parseInt(query.year as string) : undefined;
    return listHolidays(year);
  })
  .get("/api/holidays/today", async () => {
    return isTodayHoliday();
  })
  .post("/api/holidays", async ({ body }) => {
    const data = body as typeof holidaySchema.static;
    try {
      return await createHoliday(data);
    } catch (e) {
      // P2002 = tanggal sudah ada (unique constraint) → 409, bukan 500.
      if (
        e instanceof Error &&
        "code" in e &&
        (e as { code: string }).code === "P2002"
      ) {
        return new Response(
          JSON.stringify({ success: false, error: "Holiday dengan tanggal tersebut sudah ada" }),
          { status: 409, headers: { "Content-Type": "application/json" } }
        );
      }
      throw e;
    }
  }, { body: holidaySchema })
  .put("/api/holidays/:id", async ({ params, body }) => {
    const data = body as Partial<typeof holidaySchema.static>;
    try {
      return await updateHoliday(params.id, data);
    } catch (e) {
      if (isP2025(e)) return notFound("Holiday tidak ditemukan");
      throw e;
    }
  }, { body: holidayUpdateSchema })
  .delete("/api/holidays/:id", async ({ params }) => {
    try {
      return await deleteHoliday(params.id);
    } catch (e) {
      if (isP2025(e)) return notFound("Holiday tidak ditemukan");
      throw e;
    }
  });
