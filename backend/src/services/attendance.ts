import { t } from "elysia";
import prisma from "./prisma";
import { emitToAdmins } from "./events";

export const scanSchema = t.Object({
  studentId: t.String(),
  action: t.String(),
  confidenceScore: t.Number(),
  isViolation: t.Optional(t.Boolean()),
  violationType: t.Optional(t.String()),
  deviceId: t.Optional(t.String()),
  photoCapture: t.Optional(t.String()),
  timestamp: t.Optional(t.Number())
});

export const batchSyncSchema = t.Object({
  logs: t.Array(scanSchema)
});

export async function recordScan(data: {
  studentId: string;
  studentName: string;
  action: string;
  confidenceScore: number;
  isViolation?: boolean;
  violationType?: string;
  deviceId?: string;
  photoCapture?: string;
  timestamp?: number;
}) {
  const student = await prisma.student.findUnique({ where: { id: data.studentId } });
  if (!student) throw new Error("STUDENT_NOT_FOUND");

  const ts = data.timestamp ? new Date(data.timestamp) : new Date();

  // #64: re-validasi violation di SERVER. Kiosk menetapkan isViolation secara
  // lokal tanpa data permit/holiday; server punya data itu dan bisa membatalkan
  // false positive. Aturan: pelanggaran hanya valid jika action=KELUAR, masuk
  // restricted hour, DAN tidak punya permit aktif DAN hari ini bukan libur.
  let isViolation = data.isViolation || false;
  let violationType: string | null | undefined = data.violationType;

  if (data.action === "KELUAR") {
    const dayOfWeek = ts.getDay(); // 0 = Minggu
    const time = ts.toTimeString().slice(0, 5); // HH:MM

    // Rules hari itu — re-evaluasi dengan logika overnight (#83)
    const rules = await prisma.campusRule.findMany({
      where: { dayOfWeek, isRestricted: true }
    });

    const restricted = rules.some(r => {
      const overnight = r.endTime < r.startTime;
      if (overnight) return time >= r.startTime || time <= r.endTime;
      return time >= r.startTime && time <= r.endTime;
    });

    if (!restricted) {
      isViolation = false;
      violationType = null;
    } else {
      // Restricted hours — tapi boleh dibatalkan oleh permit aktif atau holiday
      const hasActivePermit = await prisma.permit.findFirst({
        where: {
          studentId: data.studentId,
          status: "approved",
          startDate: { lte: ts },
          endDate: { gte: ts }
        }
      });
      const dayStart = new Date(ts); dayStart.setHours(0, 0, 0, 0);
      const dayEnd = new Date(ts); dayEnd.setHours(23, 59, 59, 999);
      const isHoliday = await prisma.holiday.findFirst({
        where: { date: { gte: dayStart, lte: dayEnd } }
      });

      if (hasActivePermit || isHoliday) {
        isViolation = false;
        violationType = null;
      }
    }
  }

  const log = await prisma.attendanceLog.create({
    data: {
      studentId: data.studentId,
      studentName: data.studentName,
      action: data.action,
      timestamp: ts,
      confidenceScore: data.confidenceScore,
      isViolation,
      violationType,
      deviceId: data.deviceId,
      photoCapture: data.photoCapture,
      isSynced: true
    }
  });

  try {
    emitToAdmins("scan_realtime", log);
  } catch {}

  return log;
}

export async function listAttendance(params: {
  page?: number;
  pageSize?: number;
  studentId?: string;
  startDate?: string;
  endDate?: string;
}) {
  const page = params.page || 1;
  const pageSize = params.pageSize || 20;
  const skip = (page - 1) * pageSize;

  const where: Record<string, unknown> = {};
  if (params.studentId) where.studentId = params.studentId;
  if (params.startDate || params.endDate) {
    where.timestamp = {};
    if (params.startDate) (where.timestamp as Record<string, unknown>).gte = new Date(params.startDate);
    if (params.endDate) (where.timestamp as Record<string, unknown>).lte = new Date(params.endDate);
  }

  const [data, total] = await Promise.all([
    prisma.attendanceLog.findMany({
      where,
      skip,
      take: pageSize,
      orderBy: { timestamp: "desc" }
    }),
    prisma.attendanceLog.count({ where })
  ]);

  return { data, total, page, pageSize };
}

export async function getTodayAttendance() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  return prisma.attendanceLog.findMany({
    where: {
      timestamp: {
        gte: today,
        lt: tomorrow
      }
    },
    orderBy: { timestamp: "desc" }
  });
}
