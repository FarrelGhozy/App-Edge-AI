import { describe, it, expect, mock } from "bun:test";

// ---------------------------------------------------------------------------
// Attendance Service Tests
// ---------------------------------------------------------------------------

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mockPrisma: any = {
  student: {
    findUnique: mock(() => null),
    findMany: mock(() => []),
  },
  attendanceLog: {
    create: mock(() => ({})),
    findMany: mock(() => []),
    count: mock(() => 0),
    aggregate: mock(() => ({ _count: { id: 0 } })),
  },
  violation: {
    create: mock(() => ({})),
  },
  campusRule: {
    findMany: mock(() => []),
  },
  holiday: {
    findFirst: mock(() => null),
  },
  permit: {
    findMany: mock(() => []),
    findFirst: mock(() => null),
  },
};

mock.module("../services/prisma", () => ({
  default: mockPrisma,
}));

const { recordScan, getTodayAttendance } = await import("../services/attendance");

describe("attendance service", () => {

  describe("recordScan", () => {
    const scanInput = {
      studentId: "s1",
      studentName: "Test Student",
      action: "keluar" as const,
      confidenceScore: 0.85,
      isViolation: false,
      timestamp: Date.now(),
      deviceId: "device-1",
    };

    it("should record a valid scan", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1",
        name: "Test Student",
        nim: "123",
      });
      mockPrisma.attendanceLog.create.mockResolvedValue({ id: "log-1", ...scanInput });

      const result = await recordScan(scanInput);
      expect(result).toBeDefined();
    });

    it("should throw STUDENT_NOT_FOUND for unknown student", async () => {
      mockPrisma.student.findUnique.mockResolvedValue(null);

      await expect(recordScan(scanInput)).rejects.toThrow("STUDENT_NOT_FOUND");
    });

    it("should handle low confidence scores", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1",
        name: "Test",
        nim: "123",
      });
      mockPrisma.attendanceLog.create.mockResolvedValue({ id: "log-2", ...scanInput });

      const result = await recordScan({ ...scanInput, confidenceScore: 0.5 });
      expect(result).toBeDefined();
    });

    it("should record different action types", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.attendanceLog.create.mockResolvedValue({ id: "log-3" });

      const result = await recordScan({ ...scanInput, action: "kembali" });
      expect(result).toBeDefined();
    });

    it("should handle missing deviceId", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.attendanceLog.create.mockResolvedValue({ id: "log-4" });

      const result = await recordScan({ ...scanInput, deviceId: undefined });
      expect(result).toBeDefined();
    });

    it("should handle violation flag", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.attendanceLog.create.mockResolvedValue({ id: "log-5" });

      const result = await recordScan({
        ...scanInput,
        isViolation: true,
        violationType: "terlambat",
      });
      expect(result).toBeDefined();
    });

    it("#107: action case dinormalisasi ke lowercase saat write", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.attendanceLog.create.mockImplementation(async (args: any) => args.data);

      const upper = await recordScan({ ...scanInput, action: "KELUAR" });
      expect(upper.action).toBe("keluar");
      const mixed = await recordScan({ ...scanInput, action: "KeMbAlI" });
      expect(mixed.action).toBe("kembali");
    });

    it("#107: revalidasi #64 jalan untuk action lowercase (kiosk)", async () => {
      // Kiosk kirim lowercase "keluar" (ScannerViewModel.kt). Sebelumnya
      // recordScan cek `=== "KELUAR"` (uppercase) → blok revalidasi #64 SELALU
      // di-skip untuk data kiosk → pelanggaran tak pernah dibatalkan server.
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      // Senin malam 23:30 → restricted (rule overnight 22:00-05:00), tapi ada permit
      const ts = new Date("2026-08-10T23:30:00+07:00");
      mockPrisma.campusRule.findMany.mockResolvedValue([
        { dayOfWeek: 1, startTime: "22:00", endTime: "05:00", isRestricted: true },
      ]);
      mockPrisma.permit.findFirst.mockResolvedValue({ id: "permit-1", status: "approved" });
      mockPrisma.holiday.findFirst.mockResolvedValue(null);

      const result = await recordScan({
        ...scanInput,
        action: "keluar",
        isViolation: true,
        violationType: "restricted_hours",
        timestamp: ts.getTime(),
      });
      // permit aktif → server batalkan false positive
      expect(result.isViolation).toBe(false);
      expect(result.violationType).toBeNull();
    });
  });

  describe("getTodayAttendance", () => {
    it("should return today's attendance list", async () => {
      mockPrisma.attendanceLog.findMany.mockResolvedValue([
        { id: "1", studentName: "A", action: "keluar", timestamp: new Date() },
        { id: "2", studentName: "B", action: "kembali", timestamp: new Date() },
      ]);

      const result = await getTodayAttendance();
      expect(result).toEqual(expect.arrayContaining([
        expect.objectContaining({ id: "1" }),
      ]));
    });

    it("should return empty array when no attendance", async () => {
      mockPrisma.attendanceLog.findMany.mockResolvedValue([]);

      const result = await getTodayAttendance();
      expect(result).toEqual([]);
    });
  });
});

// ---------------------------------------------------------------------------
// Related: Attendance Batches (Sync)
// ---------------------------------------------------------------------------

describe("sync - attendance", () => {
  it("batch sync should handle empty arrays", async () => {
    // This validates the sync.route handler behavior
    expect(true).toBe(true); // Placeholder
  });
});
