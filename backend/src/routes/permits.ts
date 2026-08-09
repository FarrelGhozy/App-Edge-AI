import { Elysia, t } from "elysia";
import { listPermits, approvePermit, rejectPermit, createGroupPermit } from "../services/permit";import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";
import { notifyDevicesChange } from "../services/events";
import { audit } from "../services/audit";

// #85: Zod schema — body divalidasi, bukan di-cast mentah.
// #135: memberIds opsional — bila diisi, type otomatis mandiri (1) / kelompok (>1).
const createPermitSchema = t.Object({
  studentId: t.String(),
  type: t.Union([t.Literal("izin_harian"), t.Literal("pengajuan_izin"), t.Literal("izin_mandiri"), t.Literal("izin_kelompok")]),
  startDate: t.String(),
  endDate: t.String(),
  startTime: t.Optional(t.String()),
  endTime: t.Optional(t.String()),
  reason: t.Optional(t.String()),
  memberIds: t.Optional(t.Array(t.String()))
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
    const permit = await prisma.permit.findUnique({
      where: { id },
      include: { members: { include: { student: { select: { id: true, name: true, nim: true } } } } }
    });
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
      memberIds?: string[];
    };

    // #85: cek student ada — 400 (bukan 500 FK error)
    const student = await prisma.student.findUnique({ where: { id: data.studentId } });
    if (!student) {
      return new Response(JSON.stringify({
        success: false, error: "Student tidak ditemukan"
      }), { status: 400, headers: { "Content-Type": "application/json" } });
    }

    // #135: izin mandiri/kelompok dari admin — sama dgn flow kiosk, pending.
    if (data.type === "izin_mandiri" || data.type === "izin_kelompok" || (data.memberIds?.length ?? 0) > 0) {
      const memberIds = data.memberIds && data.memberIds.length > 0
        ? data.memberIds
        : [data.studentId];
      // gunakan layanan yang sama dgn kiosk supaya konsisten (auto-type)
      const permit = await createGroupPermit({
        memberIds,
        startDate: data.startDate,
        endDate: data.endDate,
        startTime: data.startTime,
        endTime: data.endTime,
        reason: data.reason
      });
      return { success: true, data: permit };
    }

    if (data.type === "izin_harian") {
      const now = new Date();
      const month = now.getMonth() + 1;
      const year = now.getFullYear();

      const setting = await prisma.globalSetting.findUnique({ where: { key: "max_permit_per_month" } });
      const maxPermits = setting ? parseInt(setting.value) : 10;

      // #85 + #128: cek quota + create + increment DALAM SATU transaction.
      // #128: isolasi default READ COMMITTED membuat check-then-act bisa tembus
      // saat 2 request paralel (keduanya baca permitsUsed < max lalu keduanya
      // create+increment → kuota jadi max+1). Solusi: row lock SELECT ... FOR
      // UPDATE pada permit_quotas — transaksi kedua menunggu commit yang pertama,
      // lalu membaca nilai yang sudah di-increment → kuota dijaga atomik.
      const permit = await prisma.$transaction(async (tx) => {
        const quota = await tx.$queryRawUnsafe<Array<{ permits_used: number; max_permits: number }>>(
          `SELECT permits_used, max_permits
             FROM permit_quotas
            WHERE student_id = $1 AND month = $2 AND year = $3
            FOR UPDATE`,
          data.studentId, month, year
        );

        const used = quota[0]?.permits_used ?? 0;
        const quotaMax = quota[0]?.max_permits ?? maxPermits;

        if (quota[0] && used >= quotaMax) {
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
        if (
          e instanceof Error &&
          "code" in e &&
          (e as { code: string }).code === "P2034"
        ) {
          // #128: deadlock serializable jarang terjadi — retry sempat gagal,
          // kembalikan null (konsumen melihat kuota habis/perlu coba lagi).
          console.error("[permits] deadlock serialization di kuota:", e);
          return null;
        }
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
    const data = body as {
      status: string;
      note?: string;
      rejectionReason?: string;
      startDate?: string;
      endDate?: string;
      startTime?: string;
      endTime?: string;
    };
    const { status } = data;
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
      // #135: admin boleh mengoreksi waktu izin (startDate/endDate/startTime/
      // endTime) + menulis pesan (note) utk santri sebelum menyetujui.
      const permit = await approvePermit(id, adminId, {
        note: data.note,
        startDate: data.startDate,
        endDate: data.endDate,
        startTime: data.startTime,
        endTime: data.endTime
      });
      await prisma.notification.create({
        data: {
          type: "izin_approved",
          title: "Izin Disetujui",
          message: `Izin disetujui${data.note ? `: ${data.note}` : ""}`,
          linkTo: `/permits/${id}`
        }
      });
      notifyDevicesChange();
      await audit(admin, { action: "APPROVE", entityType: "PERMITS", entityId: id, details: `status -> approved${data.note ? `, note: ${data.note}` : ""}` });
      return { success: true, data: permit };
    } else if (status === "rejected") {
      const permit = await rejectPermit(id, adminId, data.rejectionReason);
      await prisma.notification.create({
        data: {
          type: "izin_rejected",
          title: "Izin Ditolak",
          message: data.rejectionReason ? `Izin ditolak: ${data.rejectionReason}` : "Pengajuan izin ditolak",
          linkTo: `/permits/${id}`
        }
      });
      notifyDevicesChange();
      await audit(admin, { action: "REJECT", entityType: "PERMITS", entityId: id, details: `status -> rejected${data.rejectionReason ? `, reason: ${data.rejectionReason}` : ""}` });
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
