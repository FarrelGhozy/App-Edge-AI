import { Elysia } from "elysia";
import { listHolidays, getHoliday, createHoliday, updateHoliday, deleteHoliday, isTodayHoliday } from "../services/holiday";
import { authGuard } from "../guards/auth";

export const holidayRoutes = new Elysia()
  .use(authGuard)
  .get("/api/holidays", async ({ query }) => {
    const year = query.year ? parseInt(query.year as string) : undefined;
    return await listHolidays(year);
  })
  .post("/api/holidays", async ({ body }) => {
    const data = body as { name: string; date: string; type?: string; description?: string };
    return { success: true, data: await createHoliday(data) };
  })
  .put("/api/holidays/:id", async ({ params: { id }, body }) => {
    const data = body as { name?: string; date?: string; type?: string; description?: string };
    return { success: true, data: await updateHoliday(id, data) };
  })
  .delete("/api/holidays/:id", async ({ params: { id } }) => {
    await deleteHoliday(id);
    return { success: true };
  })
  .get("/api/holidays/today", async () => {
    return await isTodayHoliday();
  });
