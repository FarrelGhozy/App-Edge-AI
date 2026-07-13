import { Elysia } from "elysia";
import { listSchedules, createScheduleBatch, updateSchedule, deleteSchedule, getStudentSchedules } from "../services/schedule";
import { authGuard } from "../guards/auth";

export const scheduleRoutes = new Elysia()
  .use(authGuard)
  .get("/api/schedules", async ({ query }) => {
    const page = query.page ? parseInt(query.page as string) : 1;
    const pageSize = query.pageSize ? parseInt(query.pageSize as string) : 20;
    return await listSchedules(page, pageSize);
  })
  .post("/api/schedules/batch", async ({ body }) => {
    const { entries } = body as { entries: Array<{ courseName: string; dayOfWeek: number; startTime: string; endTime: string; room?: string; studentId?: string }> };
    return { success: true, data: await createScheduleBatch(entries) };
  })
  .put("/api/schedules/:id", async ({ params: { id }, body }) => {
    const data = body as { courseName?: string; dayOfWeek?: number; startTime?: string; endTime?: string; room?: string };
    return { success: true, data: await updateSchedule(id, data) };
  })
  .delete("/api/schedules/:id", async ({ params: { id } }) => {
    await deleteSchedule(id);
    return { success: true };
  })
  .get("/api/schedules/student/:studentId", async ({ params: { studentId } }) => {
    return await getStudentSchedules(studentId);
  });
