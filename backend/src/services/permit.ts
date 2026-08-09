import prisma from "./prisma";
import { emitToAdmins } from "./events";
import { wibDayStart, wibDayEndExclusive, wibTimeHMM } from "./wib";

// #135-fix: StudentDto di client Android mewajibkan studyProgram & academicYear.
// Semua select student parsial harus menyertakan keduanya, kalau tidak seluruh
// response gagal di-deserialize (MissingFieldException).
export const studentBriefSelect = {
  id: true,
  name: true,
  nim: true,
  studyProgram: true,
  academicYear: true
} as const;

export async function createPermit(data: {
  studentId: string;
  reason: string;
  type?: string;
  startDate?: string;
  endDate?: string;
}) {
  return prisma.permit.create({
    data: {
      studentId: data.studentId,
      reason: data.reason,
      type: data.type || "izin_harian",
      startDate: data.startDate ? new Date(data.startDate) : new Date(),
      endDate: data.endDate ? new Date(data.endDate) : new Date(),
      status: "pending"
    }
  });
}

export async function getPermit(id: string) {
  // #135: sertakan anggota + nama/NIM (detail admin butuh status verifikasi member).
  return prisma.permit.findUnique({
    where: { id },
    include: {
      student: { select: studentBriefSelect },
      members: { include: { student: { select: studentBriefSelect } } }
    }
  });
}

export async function updatePermitStatus(id: string, status: string) {
  return prisma.permit.update({
    where: { id },
    data: { status }
  });
}

export async function getPermitQuota(studentId: string) {
  const now = new Date();
  const month = now.getMonth() + 1;
  const year = now.getFullYear();

  const existing = await prisma.permitQuota.findUnique({
    where: { studentId_month_year: { studentId, month, year } }
  });

  if (existing) {
    return { permitsUsed: existing.permitsUsed, maxPermits: existing.maxPermits };
  }

  const startOfMonth = new Date(year, month - 1, 1);
  const permitsUsed = await prisma.permit.count({
    where: { studentId, startDate: { gte: startOfMonth } }
  });
  return { permitsUsed, maxPermits: 10 };
}

export async function listPermits(params: {
  page?: number;
  pageSize?: number;
  status?: string;
  type?: string;
  studentId?: string;
}) {
  const page = params.page || 1;
  const pageSize = params.pageSize || 20;
  const skip = (page - 1) * pageSize;

  const where: Record<string, unknown> = {};
  if (params.status) where.status = params.status;
  if (params.type) where.type = params.type;
  if (params.studentId) where.studentId = params.studentId;

  const [data, total] = await Promise.all([
    prisma.permit.findMany({
      where,
      skip,
      take: pageSize,
      orderBy: { createdAt: "desc" },
      include: { members: { include: { student: { select: studentBriefSelect } } } }
    }),
    prisma.permit.count({ where })
  ]);

  return { data, total, page, pageSize };
}

export async function approvePermit(
  id: string,
  adminId: string,
  opts?: { note?: string; startDate?: string; endDate?: string; startTime?: string; endTime?: string }
) {
  const data: Record<string, unknown> = {
    status: "approved",
    approvedById: adminId,
    approvedAt: new Date()
  };
  // #K2: admin boleh mengoreksi waktu izin saat approve bila data yang
  // diajukan salah (startDate/endDate/startTime/endTime) + tulis note utk santri.
  if (opts?.note !== undefined) data.note = opts.note;
  if (opts?.startDate) data.startDate = new Date(opts.startDate);
  if (opts?.endDate) data.endDate = new Date(opts.endDate);
  if (opts?.startTime !== undefined) data.startTime = opts.startTime || null;
  if (opts?.endTime !== undefined) data.endTime = opts.endTime || null;
  return prisma.permit.update({ where: { id }, data });
}

export async function rejectPermit(id: string, adminId: string, rejectionReason?: string) {
  return prisma.permit.update({
    where: { id },
    data: { status: "rejected", approvedById: adminId, approvedAt: new Date(), rejectionReason: rejectionReason || null }
  });
}

/**
 * #135: Buat izin mandiri/kelompok dari kiosk (role device).
 * 1 anggota -> "izin_mandiri", >1 anggota -> "izin_kelompok" (auto-detected).
 * Selalu ber-status "pending" — butuh persetujuan admin di admin-app.
 */
export async function createGroupPermit(data: {
  memberIds: string[];
  startDate: string;
  endDate: string;
  startTime?: string;
  endTime?: string;
  reason?: string;
  clientId?: string;
}) {
  if (!data.memberIds || data.memberIds.length === 0) throw new Error("NO_MEMBERS");

  // #135: idempotency — retry offline (sync worker) dgn clientId yang sama
  // TIDAK boleh membuat izin duplikat. Kembalikan izin yang sudah dibuat.
  if (data.clientId) {
    const existing = await prisma.permit.findUnique({
      where: { kioskClientId: data.clientId },
      include: {
        members: { include: { student: { select: studentBriefSelect } } }
      }
    });
    if (existing) return existing;
  }

  const students = await prisma.student.findMany({
    where: { id: { in: data.memberIds }, isActive: true }
  });
  if (students.length !== data.memberIds.length) throw new Error("STUDENT_NOT_FOUND");

  const type = students.length > 1 ? "izin_kelompok" : "izin_mandiri";

  const permit = await prisma.permit.create({
    data: {
      // studentId = pemohon (anggota pertama) — relasi lama tetap valid,
      // semua anggota tercatat di PermitMember.
      studentId: students[0].id,
      type,
      startDate: new Date(data.startDate),
      endDate: new Date(data.endDate),
      startTime: data.startTime || null,
      endTime: data.endTime || null,
      reason: data.reason || null,
      kioskClientId: data.clientId || null,
      status: "pending",
      members: {
        create: students.map((s) => ({ studentId: s.id }))
      }
    },
    include: {
      members: {
        include: { student: { select: studentBriefSelect } }
      }
    }
  });

  await prisma.notification.create({
    data: {
      type,
      title: type === "izin_mandiri" ? "Pengajuan Izin Mandiri" : "Pengajuan Izin Kelompok",
      message:
        type === "izin_mandiri"
          ? `${students[0].name} mengajukan izin mandiri`
          : `${students.length} mahasiswa mengajukan izin kelompok (${students.map((s) => s.name).join(", ")})`
    }
  });

  return permit;
}

/** List izin untuk kiosk — semua izin mandiri/kelompok lengkap dengan anggota. */
export async function listKioskPermits(params: { page?: number; pageSize?: number }) {
  const page = params.page || 1;
  const pageSize = params.pageSize || 50;
  const skip = (page - 1) * pageSize;

  const now = new Date();
  const where: Record<string, unknown> = {
    type: { in: ["izin_mandiri", "izin_kelompok"] },
    // #135: izin yang status akhir sudah settle (rejected/expired) boleh ikut
    // beberapa hari agar tetap tampil di riwayat kiosk, tapi tidak perlu semua.
    status: { in: ["pending", "approved", "rejected"] },
    endDate: { gte: new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000) }
  };

  const [data, total] = await Promise.all([
    prisma.permit.findMany({
      where,
      skip,
      take: pageSize,
      orderBy: { createdAt: "desc" },
      include: { members: { include: { student: { select: studentBriefSelect } } } }
    }),
    prisma.permit.count({ where })
  ]);

  return { data, total, page, pageSize };
}

/**
 * #135: Verifikasi scan keluar/kembali terhadap izin yang sudah disetujui.
 * Single-trip: tiap anggota keluar 1x + kembali 1x. Verifikasi menulis baris
 * AttendanceLog (dgn permitId/permitMemberId) supaya terpantau di admin.
 */
export async function verifyPermitScan(data: {
  permitId: string;
  studentId: string;
  confidenceScore?: number;
  deviceId?: string;
  timestamp?: number;
  clientId?: string;
}) {
  const permit = await prisma.permit.findUnique({ where: { id: data.permitId }, include: { members: true } });
  if (!permit) throw new Error("PERMIT_NOT_FOUND");
  if (permit.status !== "approved") throw new Error("PERMIT_NOT_APPROVED");

  const member = permit.members.find((m) => m.studentId === data.studentId);
  if (!member) throw new Error("NOT_A_MEMBER");

  const student = await prisma.student.findUnique({ where: { id: data.studentId } });
  if (!student) throw new Error("STUDENT_NOT_FOUND");

  const ts = data.timestamp ? new Date(data.timestamp) : new Date();

  // Rentang tanggal izin (batas hari WIB, bukan midnight timezone proses).
  const dayStart = wibDayStart(ts);
  if (permit.startDate > ts || permit.endDate < dayStart) {
    throw new Error("PERMIT_EXPIRED");
  }

  // Window jam izin (startTime/endTime) bila diisi.
  const time = wibTimeHMM(ts);
  if (permit.startTime && permit.endTime) {
    const overnight = permit.endTime < permit.startTime;
    const inWindow = overnight
      ? time >= permit.startTime || time < permit.endTime
      : time >= permit.startTime && time < permit.endTime;
    if (!inWindow) throw new Error("PERMIT_TIME_WINDOW");
  }

  // #119: idempotency — retry offline tidak boleh menulis log kedua.
  if (data.clientId && data.deviceId) {
    const existing = await prisma.attendanceLog.findUnique({
      where: {
        deviceId_clientId: { deviceId: data.deviceId, clientId: data.clientId }
      }
    });
    if (existing) {
      return { log: existing, member, idempotent: true };
    }
  }

  // Single-trip enforcement.
  let action: string;
  if (!member.keluarVerifiedAt) {
    action = "keluar";
  } else if (!member.kembaliVerifiedAt) {
    action = "kembali";
  } else {
    throw new Error("ALREADY_VERIFIED");
  }

  try {
    const updated = await prisma.$transaction(async (tx) => {
      const updatedMember = await tx.permitMember.update({
        where: { id: member.id },
        data: action === "keluar" ? { keluarVerifiedAt: ts } : { kembaliVerifiedAt: ts }
      });
      const log = await tx.attendanceLog.create({
        data: {
          studentId: student.id,
          studentName: student.name,
          action,
          timestamp: ts,
          confidenceScore: data.confidenceScore ?? 0,
          isViolation: false,
          deviceId: data.deviceId,
          clientId: data.clientId,
          permitId: permit.id,
          permitMemberId: member.id,
          isSynced: true
        }
      });
      return { log, updatedMember };
    });
    emitPermitVerification({
      log: updated.log,
      member: { id: updated.updatedMember.id, keluarVerifiedAt: updated.updatedMember.keluarVerifiedAt, kembaliVerifiedAt: updated.updatedMember.kembaliVerifiedAt },
      permit
    });
    return { log: updated.log, member: updated.updatedMember, idempotent: false };
  } catch (e: unknown) {
    // P2002 (deviceId, clientId) — retry duplikat yang lolos pre-check.
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
      if (existing) return { log: existing, member, idempotent: true };
    }
    throw e;
  }
}

/** #135: SSE ke admin — verifikasi izin terlihat realtime di dashboard. */
export function emitPermitVerification(result: {
  log: { id: string; studentId: string; studentName: string; action: string; timestamp: Date };
  member: { id: string; keluarVerifiedAt: Date | null; kembaliVerifiedAt: Date | null };
  permit: { id: string; type: string };
}) {
  emitToAdmins("scan_realtime", {
    ...result.log,
    permit: result.permit,
    memberVerification: {
      id: result.member.id,
      studentId: result.log.studentId,
      keluarVerifiedAt: result.member.keluarVerifiedAt?.toISOString() || null,
      kembaliVerifiedAt: result.member.kembaliVerifiedAt?.toISOString() || null
    }
  });
}