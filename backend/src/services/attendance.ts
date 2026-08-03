import { t } from "elysia";
import prisma from "./prisma";
import { emitToAdmins } from "./events";
import { wibDayOfWeek, wibTimeHMM, wibDayStart, wibDayEndExclusive } from "./wib";

export const scanSchema = t.Object({
  studentId: t.String(),
  action: t.String(),
  confidenceScore: t.Number(),
  isViolation: t.Optional(t.Boolean()),
  violationType: t.Optional(t.String()),
  deviceId: t.Optional(t.String()),
  photoCapture: t.Optional(t.String()),
  clientId: t.Optional(t.String()),
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
  clientId?: string;
  timestamp?: number;
}) {
  const student = await prisma.student.findUnique({ where: { id: data.studentId } });
  if (!student) throw new Error("STUDENT_NOT_FOUND");

  // #119: idempotency — bila client mengirim clientId (uuid per log offline),
  // log yang sama (deviceId+clientId) TIDAK boleh dibuat ulang saat retry.
  if (data.clientId && data.deviceId) {
    const existing = await prisma.attendanceLog.findUnique({
      where: {
        deviceId_clientId: { deviceId: data.deviceId, clientId: data.clientId }
      }
    });
    if (existing) return existing;
  }

  const ts = data.timestamp ? new Date(data.timestamp) : new Date();

  // #107: normalisasi action ke lowercase SAAT WRITE. Kiosk kirim "keluar"/
  // "kembali" (lowercase, ScannerViewModel:283) dan SEMUA query backend
  // (dashboard, attendance.ts, report.ts) membandingkan dengan literal
  // "keluar"/"kembali" (lowercase). Tanpa normalisasi, data uppercase (mis.
  // payload lama/API langsung) TIDAK pernah cocok — statistik & revalidasi
  // pelanggaran jadi salah. Normalisasi di sini = solusi tunggal yg benar.
  const action = (data.action || "").trim().toLowerCase();
  const isOutAction = action === "keluar";

  // #64: re-validasi violation di SERVER. Kiosk menetapkan isViolation secara
    // lokal tanpa data permit/holiday; server punya data itu dan bisa membatalkan
    // false positive. Aturan: pelanggaran hanya valid jika action=keluar, masuk
    // restricted hour, DAN tidak punya permit aktif DAN hari ini bukan libur.
    let isViolation = data.isViolation || false;
    let violationType: string | null | undefined = data.violationType;

    // #110: nilai WIB utk konteks & description violation (diisi bila keluar).
    let dayOfWeek: number | null = null;
    let time = "";

    if (isOutAction) {
      // #110: hitung hari & jam menurut WIB (Asia/Jakarta), bukan timezone proses.
      dayOfWeek = wibDayOfWeek(ts);
      time = wibTimeHMM(ts);

      // #118: rule harus relevan dgn santri — scope studyProgram/academicYear/
      // appliesToAll + prioritas rule. Ambil SEMUA rule hari itu, lalu pilih yang
      // paling relevan & berprioritas tertinggi untuk santri ini.
      const allRules = await prisma.campusRule.findMany({ where: { dayOfWeek } });

      const applicable = allRules.filter((r) =>
        r.appliesToAll ||
        (r.studyProgram && r.studyProgram === student.studyProgram) ||
        (r.academicYear && r.academicYear === student.academicYear)
      );

      // Rule berprioritas tertinggi yang relevan menentukan status restricted.
      const topRule = applicable
        .sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0))[0];

      const restricted = (() => {
        if (!topRule || !topRule.isRestricted) return false;
        const overnight = topRule.endTime < topRule.startTime;
        if (overnight) return time >= topRule.startTime || time <= topRule.endTime;
        return time >= topRule.startTime && time <= topRule.endTime;
      })();

      if (!restricted) {
        isViolation = false;
        violationType = null;
      } else {
        // Restricted hours — tapi boleh dibatalkan oleh permit aktif atau holiday.
        // #116: cek permit dgn window waktu (startTime/endTime) bila diisi,
        //   bukan hanya rentang tanggal.
        const permits = await prisma.permit.findMany({
          where: {
            studentId: data.studentId,
            status: "approved",
            startDate: { lte: ts },
            endDate: { gte: ts }
          }
        });
        const inWindow = (start: string, end: string, t: string) => {
          const overnight = end < start;
          return overnight ? (t >= start || t <= end) : (t >= start && t <= end);
        };
        const hasActivePermit = permits.some((p) =>
          !p.startTime || !p.endTime || inWindow(p.startTime, p.endTime, time)
        );
        // #110: batas hari menurut WIB, bukan setHours() timezone proses.
        const dayStart = wibDayStart(ts);
        const dayEnd = wibDayEndExclusive(ts);
        const isHoliday = await prisma.holiday.findFirst({
          where: { date: { gte: dayStart, lt: dayEnd } }
        });

        if (hasActivePermit || isHoliday) {
          isViolation = false;
          violationType = null;
        }
      }
    }

    // #111: buat baris Violation bila scan terbukti melanggar — sebelumnya
        //   isViolation hanya disimpan di attendance_logs, tabel violations
        //   tidak pernah diisi (fitur pelanggaran buntung). Ditulis dalam satu
        //   transaction bersamaan dgn log agar konsisten.
        let log: Awaited<ReturnType<typeof prisma.attendanceLog.create>>;
        try {
          log = await prisma.$transaction(async (tx) => {
            const created = await tx.attendanceLog.create({
              data: {
                studentId: data.studentId,
                studentName: data.studentName,
                action, // #107: tersimpan ternormalisasi (lowercase)
                timestamp: ts,
                confidenceScore: data.confidenceScore,
                isViolation,
                violationType,
                deviceId: data.deviceId,
                photoCapture: data.photoCapture,
                clientId: data.clientId,
                isSynced: true
              }
            });

            if (isViolation) {
              await tx.violation.create({
                data: {
                  studentId: data.studentId,
                  type: violationType || "keluar_jam_terlarang",
                  description: `Keluar pada jam terlarang (${time} WIB, hari ${dayOfWeek})`,
                  action,
                  timestamp: ts
                }
              });
            }

            return created;
          });
        } catch (e: unknown) {
          // #119: unique (deviceId, clientId) — request duplikat konkuren yang
          //   sama-sama lolos pre-check akan salah satunya kena P2002. Kembalikan
          //   log yang sudah ada, bukan gagal/duplikat.
          if (
            data.clientId &&
            data.deviceId &&
            e instanceof Error &&
            "code" in e &&
            (e as { code: string }).code === "P2002"
          ) {
            const existing = await prisma.attendanceLog.findUnique({
              where: {
                deviceId_clientId: { deviceId: data.deviceId, clientId: data.clientId }
              }
            });
            if (existing) {
              log = existing;
            } else {
              throw e;
            }
          } else {
            throw e;
          }
        }

        try {
          emitToAdmins("scan_realtime", log);
        } catch (e) {
          // #102: jangan telan error broadcast — log supaya debug kiosk tak dapat update.
          console.error("[attendance] emitToAdmins(scan_realtime) gagal:", e);
        }

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
  // #110: "hari ini" menurut WIB (Asia/Jakarta), bukan setHours() timezone proses.
  const today = new Date();
  const start = wibDayStart(today);
  const end = wibDayEndExclusive(today);

  return prisma.attendanceLog.findMany({
    where: {
      timestamp: {
        gte: start,
        lt: end
      }
    },
    orderBy: { timestamp: "desc" }
  });
}
