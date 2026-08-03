import { Elysia, t } from "elysia";
import { listPermits, approvePermit, rejectPermit } from "../services/permit";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";
import { notifyDevicesChange } from "../services/events";
import { audit } from "../services/audit";

// #85: Zod schema — body divalidasi, bukan di-cast mentah.
const createPermitSchema = t.Object({
  studentId: t.String(),
  type: t.Union([t.Literal("izin_harian"), t.Literal("pengajuan_izin")]),
  startDate: t.String(),
  endDate: t.String(),
  startTime: t.Optional(t.String()),
  endTime: t.Optional(t.String()),
  reason: t.Optional(t.String())
});

export const permitRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
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

    // #85: cek student ada — 400 (bukan 500 FK error)
    const student = await prisma.student.findUnique({ where: { id: data.studentId } });
    if (!student) {
      return new Response(JSON.stringify({
        success: false, error: "Student tidak ditemukan"
      }), { status: 400, headers: { "Content-Type": "application/json" } });
    }

    if (data.type === "izin_harian") {
      const now = new Date();
      const month = now.getMonth() + 1;
      const year = now.getFullYear();

      const setting = await prisma.globalSetting.findUnique({ where: { key: "max_permit_per_month" } });
      const maxPermits = setting ? parseInt(setting.value) : 10;

      // #85: cek quota + create + increment DALAM SATU transaction — 2 request
      // paralel tidak bisa lolos melewati batas kuota.
      const permit = await prisma.$transaction(async (tx) => {
        const quota = await tx.permitQuota.findUnique({
          where: { studentId_month_year: { studentId: data.studentId, month, year } }
        });

        if (quota && quota.permitsUsed >= (quota.maxPermits ?? maxPermits)) {
          throw new Error("QUOTA_EXCEEDED");
        }

        const created = await tx.permit.create({
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

        await tx.permitQuota.upsert({
          where: { studentId_month_year: { studentId: data.studentId, month, year } },
          update: { permitsUsed: { increment: 1 } },
          create: { studentId: data.studentId, month, year, permitsUsed: 1, maxPermits }
        });

        return created;
      }).catch((e: unknown) => {
        if (e instanceof Error && e.message === "QUOTA_EXCEEDED") return null;
        throw e;
      });

      if (!permit) {
        return new Response(JSON.stringify({
          success: false, error: "Kuota izin bulan ini sudah habis"
        }), { status: 400, headers: { "Content-Type": "application/json" } });
      }

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
  }, { body: createPermitSchema })
  .put("/api/permits/:id/status", async ({ params: { id }, body, admin }) => {
    const { status } = body as { status: string };
    // #73: approvedById HARUS dari identitas JWT (admin dari guard derive),
    // bukan body. Klien tidak boleh menentukan siapa yang menyetujui.
    if (!admin?.id) {
      return new Response(
        JSON.stringify({ success: false, error: "Unauthorized: admin identity required" }),
        { status: 401, headers: { "Content-Type": "application/json" } }
      );
    }
    const adminId = admin.id;

    if (status === "approved") {
      const permit = await approvePermit(id, adminId);
      notifyDevicesChange();
      await audit(admin, { action: "APPROVE", entityType: "PERMITS", entityId: id, details: `status -> approved` });
      return { success: true, data: permit };
    } else if (status === "rejected") {
      const permit = await rejectPermit(id, adminId);
      notifyDevicesChange();
      await audit(admin, { action: "REJECT", entityType: "PERMITS", entityId: id, details: `status -> rejected` });
      return { success: true, data: permit };
    }
    return { success: false, error: "Invalid status" };
  })
  .get("/api/permits/active/:studentId", async ({ params: { studentId } }) => {
    const now = new Date();
    const permits = await prisma.permit.findMany({
      where: {
        studentId,
        status: { in: ["approved", "pending"] },
        startDate: { lte: now },
        endDate: { gte: now }
      },
      orderBy: { createdAt: "desc" }
    });
    return { success: true, data: permits };
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
