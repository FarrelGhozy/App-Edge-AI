import { Elysia, t } from "elysia";
import { createGroupPermit, listKioskPermits, verifyPermitScan } from "../services/permit";
import { authGuard } from "../guards/auth";

/**
 * #135: Route kiosk untuk alur izin mandiri & kelompok.
 * Role device -> kiosk; admin/superadmin juga boleh untuk testing/dukungan.
 * - POST   /api/kiosk/permits         : pengajuan izin (memberIds >= 1, auto-type)
 * - GET    /api/kiosk/permits         : list izin mandiri/kelompok + anggota
 * - POST   /api/kiosk/permits/:id/verify : verifikasi scan keluar/kembali per anggota
 */
const kioskCreatePermitSchema = t.Object({
  memberIds: t.Array(t.String(), { minItems: 1 }),
  startDate: t.String(),
  endDate: t.String(),
  startTime: t.Optional(t.String()),
  endTime: t.Optional(t.String()),
  reason: t.Optional(t.String()),
  clientId: t.Optional(t.String())
});

const verifySchema = t.Object({
  studentId: t.String(),
  confidenceScore: t.Optional(t.Number()),
  deviceId: t.Optional(t.String()),
  timestamp: t.Optional(t.Number()),
  clientId: t.Optional(t.String())
});

export const kioskRoutes = new Elysia()
  .use(authGuard("admin", "superadmin", "device"))
  .post("/api/kiosk/permits", async ({ body }) => {
    const data = body as {
      memberIds: string[];
      startDate: string;
      endDate: string;
      startTime?: string;
      endTime?: string;
      reason?: string;
      clientId?: string;
    };
    try {
      const permit = await createGroupPermit(data);
      return { success: true, data: permit };
    } catch (e) {
      if (e instanceof Error && e.message === "STUDENT_NOT_FOUND") {
        return new Response(JSON.stringify({ success: false, error: "Salah satu mahasiswa tidak ditemukan / tidak aktif" }), {
          status: 400,
          headers: { "Content-Type": "application/json" }
        });
      }
      if (e instanceof Error && e.message === "NO_MEMBERS") {
        return new Response(JSON.stringify({ success: false, error: "Minimal 1 anggota" }), {
          status: 400,
          headers: { "Content-Type": "application/json" }
        });
      }
      console.error("[kiosk] create permit gagal:", e);
      return new Response(JSON.stringify({ success: false, error: "Internal server error" }), {
        status: 500,
        headers: { "Content-Type": "application/json" }
      });
    }
  }, { body: kioskCreatePermitSchema })
  .get("/api/kiosk/permits", async ({ query }) => {
    const page = query.page ? parseInt(query.page as string) : 1;
    const pageSize = query.pageSize ? parseInt(query.pageSize as string) : 50;
    return await listKioskPermits({ page, pageSize });
  })
  .post("/api/kiosk/permits/:permitId/verify", async ({ params: { permitId }, body }) => {
    const data = body as {
      studentId: string;
      confidenceScore?: number;
      deviceId?: string;
      timestamp?: number;
      clientId?: string;
    };
    try {
      const result = await verifyPermitScan({ permitId, ...data });
      return { success: true, data: result };
    } catch (e) {
      const message = e instanceof Error ? e.message : "UNKNOWN";
      const statusMap: Record<string, number> = {
        PERMIT_NOT_FOUND: 404,
        PERMIT_NOT_APPROVED: 403,
        NOT_A_MEMBER: 403,
        PERMIT_EXPIRED: 400,
        PERMIT_TIME_WINDOW: 400,
        ALREADY_VERIFIED: 409,
        STUDENT_NOT_FOUND: 404
      };
      const human: Record<string, string> = {
        PERMIT_NOT_FOUND: "Izin tidak ditemukan",
        PERMIT_NOT_APPROVED: "Izin belum disetujui admin",
        NOT_A_MEMBER: "Mahasiswa bukan anggota izin ini",
        PERMIT_EXPIRED: "Masa izin sudah berakhir",
        PERMIT_TIME_WINDOW: "Di luar jam izin yang disetujui",
        ALREADY_VERIFIED: "Anggota ini sudah selesai verifikasi keluar & kembali",
        STUDENT_NOT_FOUND: "Mahasiswa tidak ditemukan"
      };
      const status = statusMap[message] || 500;
      if (status === 500) console.error("[kiosk] verify permit gagal:", e);
      return new Response(JSON.stringify({ success: false, error: human[message] || "Terjadi kesalahan" }), {
        status,
        headers: { "Content-Type": "application/json" }
      });
    }
  }, { body: verifySchema });