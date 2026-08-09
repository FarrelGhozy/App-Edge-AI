import { Elysia } from "elysia";
import { batchSyncSchema, recordScan } from "../services/attendance";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";
import { notifyDevicesChange } from "../services/events";
import { computeFacesWatermark } from "../services/syncWatermark";
import { verifyPermitScan, studentBriefSelect } from "../services/permit";

export const syncRoutes = new Elysia()
  .use(authGuard())
  .get("/api/sync/faces", async ({ query }) => {
    const since = query.since as string | undefined;

    const rows = await prisma.$queryRawUnsafe<Array<{
      student_id: string;
      pose: string;
      vector: string;
      updated_at: Date;
      name: string;
      nim: string;
      study_program: string;
      academic_year: string;
    }>>(
      `SELECT
        fv.student_id,
        fv.pose,
        fv.vector::text,
        fv.updated_at,
        s.name,
        s.nim,
        s.study_program,
        s.academic_year
      FROM face_vectors fv
      JOIN students s ON s.id = fv.student_id
      WHERE $1::timestamptz IS NULL OR fv.updated_at > $1::timestamptz
      ORDER BY fv.id`,
      since ? new Date(since) : null
    );

    const data = rows.map((r) => {
      const vectorStr = r.vector?.replace(/[\[\]]/g, "") || "";
      const vector = vectorStr ? vectorStr.split(",").map(Number) : [];

      return {
        studentId: r.student_id,
        pose: r.pose,
        studentName: r.name,
        nim: r.nim,
        studyProgram: r.study_program,
        academicYear: r.academic_year,
        vector,
        updatedAt: r.updated_at.toISOString()
      };
    });

    // Watermark = server-side max(updated_at) of returned rows, NOT an echo of
    // the client's `since`. Clients persist this as their next `since` so the
    // following sync only fetches the delta (issue #78).
    const watermark = computeFacesWatermark(rows, since);

    return { data, since: watermark };
  })
  .post("/api/sync/attendance", async ({ body, admin }) => {
    const created = [];
    const skipped = [];
    // #84: batch fetch semua student sekali (hilangkan N+1 loop findUnique)
    const ids = (body.logs as { studentId: string }[]).map(l => l.studentId);
    const students = await prisma.student.findMany({ where: { id: { in: ids } } });
    const studentMap = new Map(students.map(s => [s.id, s]));

    for (const log of body.logs) {
      const student = studentMap.get(log.studentId);
      if (!student) {
        // #84: JANGAN silent skip — laporkan supaya kiosk tahu & antrean tidak
        // hilang tanpa jejak (data loss offline).
        skipped.push({ studentId: log.studentId, reason: "student_not_found" });
        continue;
      }
      const record = await recordScan({
        studentId: log.studentId,
        studentName: student.name,
        action: log.action,
        confidenceScore: log.confidenceScore,
        isViolation: log.isViolation,
        violationType: log.violationType,
        deviceId: log.deviceId,
        photoCapture: log.photoCapture,
        clientId: log.clientId, // #119: idempotency key (uuid per log offline)
        timestamp: log.timestamp
      });
      created.push(record);
    }
    return {
      success: true,
      data: {
        synced: created.length,
        skipped: skipped.length,
        skippedLogs: skipped // #84: eksplisit, tidak silent
      }
    };
  }, { body: batchSyncSchema })
  .get("/api/sync/rules", async () => {
    const rules = await prisma.campusRule.findMany();
    return rules;
  })
  .get("/api/sync/permits", async () => {
    // #135: full-replace — kiosk men-download semua izin mandiri/kelompok
    // (pending/approved/rejected, 7 hari terakhir) + anggota + student info.
    const permits = await prisma.permit.findMany({
      where: {
        type: { in: ["izin_mandiri", "izin_kelompok"] },
        status: { in: ["pending", "approved", "rejected"] },
        endDate: { gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }
      },
      orderBy: { createdAt: "desc" },
      take: 200,
      include: {
        members: { include: { student: { select: studentBriefSelect } } }
      }
    });
    return { data: permits };
  })
  .post("/api/sync/permits-verifications", async ({ body }) => {
    // #135: batch upload verifikasi izin offline (sama pola dgn sync attendance).
    const logs = (body as { logs: Array<{
      permitId: string;
      studentId: string;
      confidenceScore?: number;
      deviceId?: string;
      timestamp?: number;
      clientId?: string;
    }> }).logs;
    const created = [];
    const skipped = [];
    for (const log of logs) {
      try {
        const result = await verifyPermitScan(log);
        created.push({ studentId: log.studentId, permitId: log.permitId, action: result.log.action, idempotent: !!result.idempotent });
      } catch (e) {
        skipped.push({
          permitId: log.permitId,
          studentId: log.studentId,
          reason: e instanceof Error ? e.message : "UNKNOWN"
        });
      }
    }
    return {
      success: true,
      data: { synced: created.length, skipped: skipped.length, skippedLogs: skipped, syncedLogs: created }
    };
  })
  .get("/api/sync/requested", async ({ query }) => {
    const deviceId = query.deviceId as string | undefined;
    const where: Record<string, unknown> = { isProcessed: false };
    if (deviceId) where.deviceId = deviceId;

    const request = await prisma.syncRequest.findFirst({
      where,
      orderBy: { requestedAt: "desc" }
    });

    return {
      requested: !!request,
      requestedAt: request?.requestedAt?.toISOString() || null,
      deviceId: request?.deviceId || null
    };
  })
  .get("/api/sync/status/:deviceId", async ({ params: { deviceId } }) => {
    const device = await prisma.device.findUnique({ where: { deviceId } });
    if (!device) {
      return new Response(JSON.stringify({ success: false, error: "Device not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }

    const pendingRequest = await prisma.syncRequest.findFirst({
      where: { deviceId, isProcessed: false },
      orderBy: { requestedAt: "desc" }
    });

    const lastSync = await prisma.syncLog.findFirst({
      where: { deviceId },
      orderBy: { createdAt: "desc" }
    });

    // #131: unprocessedLogs sebelumnya dihitung dari sync_log status="pending"
    // yang TIDAK PERNAH ada (sync_log hanya success/failed) → selalu 0 dan
    // menyesatkan. Makna sebenarnya: berapa log offline device yang BELUM
    // ter-upload ke server. Sumber kebenaran = attendance_logs isSynced=false.
    const unprocessedLogs = await prisma.attendanceLog.count({
      where: { deviceId, isSynced: false }
    });

    return {
      success: true,
      data: {
        deviceId,
        deviceName: device.name,
        isOnline: device.lastPingAt ? (Date.now() - device.lastPingAt.getTime()) < 300000 : false,
        lastPingAt: device.lastPingAt?.toISOString() || null,
        batteryLevel: device.batteryLevel,
        hasPendingSync: !!pendingRequest,
        pendingSyncRequestedAt: pendingRequest?.requestedAt?.toISOString() || null,
        lastSyncAt: lastSync?.createdAt?.toISOString() || null,
        lastSyncStatus: lastSync?.status || null,
        unprocessedLogs
      }
    };
  })
  .get("/api/sync/logs", async ({ query }) => {
    const where = query.deviceId ? { deviceId: query.deviceId as string } : {};
    return await prisma.syncLog.findMany({
      where,
      orderBy: { createdAt: "desc" },
      take: 50
    });
  })
  .post("/api/events/trigger-change", async ({ body }) => {
    const requestedBy = (body as { requestedBy?: string }).requestedBy || undefined;
    await notifyDevicesChange(requestedBy);
    return { success: true, data: { triggeredDevices: true } };
  })
  .post("/api/sync/complete", async ({ body, admin }) => {
    // #86: deviceId WAJIB dari identitas JWT (admin.id = deviceId dari
    // loginDevice), BUKAN dari body. Token device lain tidak bisa menandai
    // SyncRequest / menulis SyncLog milik device lain.
    const deviceId = admin?.id;
    if (!deviceId) {
      return new Response(
        JSON.stringify({ success: false, error: "Unauthorized: device identity required" }),
        { status: 401, headers: { "Content-Type": "application/json" } }
      );
    }
    const data = body as { syncType?: string; status?: string; logsCount?: number };
    await prisma.syncLog.create({
      data: {
        deviceId,
        syncType: data.syncType || "manual",
        status: data.status || "success",
        logsCount: data.logsCount || 0
      }
    });

    await prisma.syncRequest.updateMany({
      where: { deviceId, isProcessed: false },
      data: { isProcessed: true, processedAt: new Date() }
    });

    // #81: SyncRequest lama yang sudah processed dihapus (tabel tak tumbuh tanpa batas)
    const cutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);
    await prisma.syncRequest.deleteMany({
      where: { isProcessed: true, processedAt: { lte: cutoff } }
    });

    return { success: true, deviceId };
  });
