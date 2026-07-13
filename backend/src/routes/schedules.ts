import { Elysia } from "elysia";
import { listSchedules, createSchedule, batchCreateSchedules, updateSchedule, deleteSchedule } from "../services/schedule";
import { authGuard } from "../guards/auth";

export const scheduleRoutes = new Elysia()
  .use(authGuard)
  .get("/api/schedules", async ({ query }) => {
    const studentId = query.studentId as string | undefined;
    return await listSchedules(studentId);
  })
  .get("/api/schedules/student/:studentId", async ({ params: { studentId } }) => {
    return await listSchedules(studentId);
  })
  .post("/api/schedules", async ({ body }) => {
    const data = body as {
      studentId: string;
      courseName: string;
      dayOfWeek: number;
      startTime: string;
      endTime: string;
      room?: string;
      lecturer?: string;
    };
    return await createSchedule(data);
  })
  .post("/api/schedules/batch", async ({ body }) => {
    const { items } = body as {
      items: Array<{
        studentId: string;
        courseName: string;
        dayOfWeek: number;
        startTime: string;
        endTime: string;
        room?: string;
        lecturer?: string;
      }>
    };
    return await batchCreateSchedules(items);
  })
  .put("/api/schedules/:id", async ({ params, body }) => {
    const data = body as {
      courseName?: string;
      dayOfWeek?: number;
      startTime?: string;
      endTime?: string;
      room?: string;
      lecturer?: string;
      isActive?: boolean;
    };
    return await updateSchedule(params.id, data);
  })
  .delete("/api/schedules/:id", async ({ params }) => {
    return await deleteSchedule(params.id);
  });
