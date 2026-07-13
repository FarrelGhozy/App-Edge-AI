import { Elysia } from "elysia";
import { authGuard } from "../guards/auth";
import {
  listHolidays,
  getHoliday,
  createHoliday,
  updateHoliday,
  deleteHoliday,
  isTodayHoliday,
} from "../services/holiday";

export const holidayRoutes = new Elysia()
  .use(authGuard)
  .get("/api/holidays", async ({ query }) => {
    const year = query.year ? parseInt(query.year as string) : undefined;
    return listHolidays(year);
  })
  .get("/api/holidays/today", async () => {
    return isTodayHoliday();
  })
  .post("/api/holidays", async ({ body }) => {
    const data = body as { name: string; date: string; type?: string; description?: string };
    return createHoliday(data);
  })
  .put("/api/holidays/:id", async ({ params, body }) => {
    const data = body as { name?: string; date?: string; type?: string; description?: string };
    return updateHoliday(params.id, data);
  })
  .delete("/api/holidays/:id", async ({ params }) => {
    return deleteHoliday(params.id);
  });
