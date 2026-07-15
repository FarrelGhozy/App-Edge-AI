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
  const student = await prisma.student.findUnique({ where: { id: data.studentId } });
  if (!student) throw new Error("STUDENT_NOT_FOUND");

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
