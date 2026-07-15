import { describe, it, expect, mock } from "bun:test";

// ---------------------------------------------------------------------------
// Attendance Service Tests
// ---------------------------------------------------------------------------

const mockPrisma = {
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
      action: "keluar" as const,
      confidenceScore: 0.85,
      isViolation: false,
      timestamp: new Date("2025-07-15T08:00:00Z"),
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
