import { t } from "elysia";
import prisma from "./prisma";

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
  return prisma.attendanceLog.create({
    data: {
      studentId: data.studentId,
      studentName: data.studentName,
      action: data.action,
      timestamp: data.timestamp ? new Date(data.timestamp) : new Date(),
      confidenceScore: data.confidenceScore,
      isViolation: data.isViolation || false,
      violationType: data.violationType,
      deviceId: data.deviceId,
      photoCapture: data.photoCapture,
      isSynced: true
    }
  });
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
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const todayEnd = new Date(todayStart.getTime() + 86400000);

  const logs = await prisma.attendanceLog.findMany({
    where: { timestamp: { gte: todayStart, lt: todayEnd } },
    orderBy: { timestamp: "desc" }
  });

  const keluarCount = logs.filter(l => l.action === "keluar").length;
  const kembaliCount = logs.filter(l => l.action === "kembali").length;

  return {
    date: todayStart.toISOString().split("T")[0],
    totalLogs: logs.length,
    keluarCount,
    kembaliCount,
    logs,
  };
}

export async function getAttendanceStatus(studentId: string) {
  const latestLog = await prisma.attendanceLog.findFirst({
    where: { studentId },
    orderBy: { timestamp: "desc" }
  });

  return {
    studentId,
    currentStatus: latestLog?.action === "keluar" ? "outside" : "inside",
    lastAction: latestLog?.action || null,
    lastTimestamp: latestLog?.timestamp?.toISOString() || null,
  };
}

export async function getAttendanceStatistics() {
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
  const yearStart = new Date(now.getFullYear(), 0, 1);

  const [todayCount, monthCount, yearCount, totalStudents] = await Promise.all([
    prisma.attendanceLog.count({ where: { timestamp: { gte: todayStart } } }),
    prisma.attendanceLog.count({ where: { timestamp: { gte: monthStart } } }),
    prisma.attendanceLog.count({ where: { timestamp: { gte: yearStart } } }),
    prisma.student.count({ where: { isActive: true } }),
  ]);

  return {
    today: todayCount,
    thisMonth: monthCount,
    thisYear: yearCount,
    totalStudents,
  };
}
