import { Elysia } from "elysia";
import { listPermits, approvePermit, rejectPermit } from "../services/permit";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const permitRoutes = new Elysia()
  .use(authGuard)
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
  .get("/api/permits/pending", async ({ query }) => {
    const params = {
      page: query.page ? parseInt(query.page as string) : 1,
      pageSize: query.pageSize ? parseInt(query.pageSize as string) : 20,
      status: "pending",
    };
    return await listPermits(params);
  })
  .get("/api/permits/active/:studentId", async ({ params: { studentId } }) => {
    const now = new Date();
    const permits = await prisma.permit.findMany({
      where: {
        studentId,
        status: "approved",
        startDate: { lte: now },
        endDate: { gte: now },
      },
      orderBy: { endDate: "asc" }
    });
    return { success: true, data: permits };
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
  .put("/api/permits/:id/approve", async ({ params: { id }, body }) => {
    const { adminId } = body as { adminId: string };
    const permit = await approvePermit(id, adminId);
    return { success: true, data: permit };
  })
  .put("/api/permits/:id/reject", async ({ params: { id }, body }) => {
    const { adminId } = body as { adminId: string };
    const permit = await rejectPermit(id, adminId);
    return { success: true, data: permit };
  })
  .put("/api/permits/:id/status", async ({ params: { id }, body }) => {
    const { status, adminId } = body as { status: string; adminId: string };

    if (status === "approved") {
      const permit = await approvePermit(id, adminId);
      return { success: true, data: permit };
    } else if (status === "rejected") {
      const permit = await rejectPermit(id, adminId);
      return { success: true, data: permit };
    }
    return { success: false, error: "Invalid status" };
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

    if (data.type === "izin_harian") {
      const now = new Date();
      const month = now.getMonth() + 1;
      const year = now.getFullYear();

      const setting = await prisma.globalSetting.findUnique({ where: { key: "max_permit_per_month" } });
      const maxPermits = setting ? parseInt(setting.value) : 10;

      const quota = await prisma.permitQuota.findUnique({
        where: { studentId_month_year: { studentId: data.studentId, month, year } }
      });

      if (quota && quota.permitsUsed >= (quota.maxPermits ?? maxPermits)) {
        return new Response(JSON.stringify({
          success: false, error: "Kuota izin bulan ini sudah habis"
        }), { status: 400, headers: { "Content-Type": "application/json" } });
      }

      const permit = await prisma.permit.create({
        data: {
          studentId: data.studentId,
          type: "izin_harian",
          startDate: new Date(data.startDate),
          endDate: new Date(data.endDate),
          startTime: data.startTime || null,
          endTime: data.endTime || null,
          reason: data.reason || null,
          status: "approved"
        }
      });

      await prisma.permitQuota.upsert({
        where: { studentId_month_year: { studentId: data.studentId, month, year } },
        update: { permitsUsed: { increment: 1 } },
        create: { studentId: data.studentId, month, year, permitsUsed: 1, maxPermits }
      });

      return { success: true, data: permit };
    }

    const permit = await prisma.permit.create({
      data: {
        studentId: data.studentId,
        type: "pengajuan_izin",
        startDate: new Date(data.startDate),
        endDate: new Date(data.endDate),
        startTime: data.startTime || null,
        endTime: data.endTime || null,
        reason: data.reason || null,
        status: "pending"
      }
    });

    await prisma.notification.create({
      data: {
        type: "pengajuan_izin",
        title: "Pengajuan Izin Baru",
        message: `Mahasiswa mengajukan izin baru`
      }
    });

    return { success: true, data: permit };
  })
  .get("/api/permits/quota", async ({ query }) => {
    const studentId = query.studentId as string;
    if (!studentId) return { success: false, error: "studentId required" };

    const now = new Date();
    const quota = await prisma.permitQuota.findUnique({
      where: { studentId_month_year: { studentId, month: now.getMonth() + 1, year: now.getFullYear() } }
    });

    return {
      success: true,
      data: {
        permitsUsed: quota?.permitsUsed ?? 0,
        maxPermits: quota?.maxPermits ?? 10
      }
    };
  });
