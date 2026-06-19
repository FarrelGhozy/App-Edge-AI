import { Elysia } from "elysia";
import { listPermits, approvePermit, rejectPermit } from "../services/permit";
import prisma from "../services/prisma";

export const permitRoutes = new Elysia()
  .get("/api/permits", async ({ query }) => {
    const params = {
      page: query.page ? parseInt(query.page as string) : 1,
      pageSize: query.pageSize ? parseInt(query.pageSize as string) : 20,
      status: query.status as string | undefined,
      type: query.type as string | undefined,
      studentId: query.studentId as string | undefined
    };
    return await listPermits(params);
  })
  .get("/api/permits/:id", async ({ params: { id } }) => {
    const permit = await prisma.permit.findUnique({ where: { id } });
    if (!permit) {
      return new Response(JSON.stringify({ success: false, error: "Permit not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }
    return { success: true, data: permit };
  })
  .post("/api/permits", async ({ body }) => {
    const data = body as {
      studentId: string;
      type: string;
      startDate: string;
      endDate: string;
      startTime?: string;
      endTime?: string;
      reason?: string;
    };

    const permit = await prisma.permit.create({
      data: {
        studentId: data.studentId,
        type: data.type,
        startDate: new Date(data.startDate),
        endDate: new Date(data.endDate),
        startTime: data.startTime || null,
        endTime: data.endTime || null,
        reason: data.reason || null,
        status: data.type === "izin_harian" ? "approved" : "pending"
      }
    });

    return { success: true, data: permit };
  })
  .put("/api/permits/:id/status", async ({ params: { id }, body }) => {
    const { status, adminId } = body as { status: string; adminId: string };

    if (status === "approved") {
      return { success: true, data: await approvePermit(id, adminId) };
    } else if (status === "rejected") {
      return { success: true, data: await rejectPermit(id, adminId) };
    }
    return { success: false, error: "Invalid status" };
  });
