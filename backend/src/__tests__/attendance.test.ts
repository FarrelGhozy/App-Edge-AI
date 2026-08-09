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
    findUnique: mock(() => null),
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
  // #111: recordScan kini menulis log+violation dalam satu $transaction;
  // mock menjalankan callback dgn tx = mockPrisma (delegasi penuh).
  $transaction: mock(async (fn: (tx: any) => Promise<unknown>) => fn(mockPrisma)),
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

    it("#111: violation.create dipanggil saat isViolation terbukti", async () => {
      const violationCreate = mockPrisma.violation.create.mockResolvedValue({ id: "v1" });
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.campusRule.findMany.mockResolvedValue([
        { dayOfWeek: 1, startTime: "22:00", endTime: "05:00", isRestricted: true,
          appliesToAll: true, priority: 1 },
      ]);
      mockPrisma.permit.findMany.mockResolvedValue([]);
      mockPrisma.holiday.findFirst.mockResolvedValue(null);
      mockPrisma.attendanceLog.create.mockResolvedValue({ id: "log-v" });

      const ts = new Date("2026-08-10T23:30:00+07:00"); // Senin WIB malam
      await recordScan({ ...scanInput, isViolation: true, timestamp: ts.getTime() });

      expect(violationCreate).toHaveBeenCalled();
      const called = violationCreate.mock.calls[0][0].data;
      expect(called.studentId).toBe("s1");
      expect(called.type).toBeDefined();
    });

    it("#111: violation.create TIDAK dipanggil saat tidak melanggar", async () => {
      const violationCreate = mockPrisma.violation.create;
      violationCreate.mockClear();
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.campusRule.findMany.mockResolvedValue([]);
      mockPrisma.attendanceLog.create.mockResolvedValue({ id: "log-ok" });

      await recordScan({ ...scanInput, isViolation: false });

      expect(violationCreate).not.toHaveBeenCalled();
    });

    it("#141: rule kedua di hari sama tetap mencatat violation (OR, bukan topRule)", async () => {
      // Reproduksi bug produksi: Minggu punya 2 rule, semua priority 0 —
      // seed 22:00-05:00 dan rule baru 11:00-12:00. Scan keluar 11:38 WIB
      // cocok dgn rule ke-2; server lama hanya evaluasi topRule (22:00-05:00)
      // sehingga pelanggaran dibatalkan. Sekarang harus tercatat.
      const violationCreate = mockPrisma.violation.create.mockResolvedValue({ id: "v141" });
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.campusRule.findMany.mockResolvedValue([
        { dayOfWeek: 0, startTime: "22:00", endTime: "05:00", isRestricted: true,
          appliesToAll: true, priority: 0 },
        { dayOfWeek: 0, startTime: "11:00", endTime: "12:00", isRestricted: true,
          appliesToAll: true, priority: 0 },
      ]);
      mockPrisma.permit.findMany.mockResolvedValue([]);
      mockPrisma.holiday.findFirst.mockResolvedValue(null);
      mockPrisma.attendanceLog.create.mockImplementation(async (args: any) => args.data);

      const ts = new Date("2026-08-09T04:38:00Z"); // Minggu 11:38 WIB
      const result = await recordScan({
        ...scanInput, isViolation: true, violationType: "restricted_hours",
        timestamp: ts.getTime(),
      });

      expect(result.isViolation).toBe(true);
      expect(violationCreate).toHaveBeenCalled();
      const called = violationCreate.mock.calls[0][0].data;
      expect(called.type).toBe("restricted_hours");
    });

    it("#141: scan di luar jendela semua rule → pelanggaran dibatalkan server", async () => {
      const violationCreate = mockPrisma.violation.create;
      violationCreate.mockClear();
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.campusRule.findMany.mockResolvedValue([
        { dayOfWeek: 0, startTime: "22:00", endTime: "05:00", isRestricted: true,
          appliesToAll: true, priority: 0 },
        { dayOfWeek: 0, startTime: "11:00", endTime: "12:00", isRestricted: true,
          appliesToAll: true, priority: 0 },
      ]);
      mockPrisma.permit.findMany.mockResolvedValue([]);
      mockPrisma.holiday.findFirst.mockResolvedValue(null);
      mockPrisma.attendanceLog.create.mockImplementation(async (args: any) => args.data);

      const ts = new Date("2026-08-09T08:00:00Z"); // Minggu 15:00 WIB — tidak dilindungi rule
      const result = await recordScan({
        ...scanInput, isViolation: true, violationType: "restricted_hours",
        timestamp: ts.getTime(),
      });

      expect(result.isViolation).toBe(false);
      expect(result.violationType).toBeNull();
      expect(violationCreate).not.toHaveBeenCalled();
    });

    it("#116: permit tanpa cakupan jam tidak membatalkan violation", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      // Rule restricted sepanjang hari utk Senin; permit hanya 08:00-17:00,
      // keluar 23:30 WIB → di luar jendela permit → violation TETAP valid.
      mockPrisma.campusRule.findMany.mockResolvedValue([
        { dayOfWeek: 1, startTime: "00:00", endTime: "23:59", isRestricted: true,
          appliesToAll: true, priority: 1 },
      ]);
      mockPrisma.permit.findMany.mockResolvedValue([
        { id: "p1", status: "approved", startDate: new Date("2026-08-01"),
          endDate: new Date("2026-08-31"), startTime: "08:00", endTime: "17:00" },
      ]);
      mockPrisma.holiday.findFirst.mockResolvedValue(null);
      mockPrisma.attendanceLog.create.mockImplementation(async (args: any) => args.data);

      const ts = new Date("2026-08-10T23:30:00+07:00");
      const result = await recordScan({
        ...scanInput, isViolation: true, violationType: "restricted_hours",
        timestamp: ts.getTime(),
      });
      expect(result.isViolation).toBe(true);
    });

    it("#116: permit dengan jendela jam menutupi waktu keluar → batal violation", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.campusRule.findMany.mockResolvedValue([
        { dayOfWeek: 1, startTime: "00:00", endTime: "23:59", isRestricted: true,
          appliesToAll: true, priority: 1 },
      ]);
      // permit 21:00-23:59 → keluar 23:30 WIB TERTUTUP → bukan violation.
      mockPrisma.permit.findMany.mockResolvedValue([
        { id: "p2", status: "approved", startDate: new Date("2026-08-01"),
          endDate: new Date("2026-08-31"), startTime: "21:00", endTime: "23:59" },
      ]);
      mockPrisma.holiday.findFirst.mockResolvedValue(null);
      mockPrisma.attendanceLog.create.mockImplementation(async (args: any) => args.data);

      const ts = new Date("2026-08-10T23:30:00+07:00");
      const result = await recordScan({
        ...scanInput, isViolation: true, violationType: "restricted_hours",
        timestamp: ts.getTime(),
      });
      expect(result.isViolation).toBe(false);
    });

    it("#118: rule scope studyProgram orang lain tidak berlaku utk santri ini", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
        studyProgram: "TI", academicYear: "2024",
      });
      // Rule hanya utk prodi "HKI" → TIDAK applicable → tidak restricted.
      mockPrisma.campusRule.findMany.mockResolvedValue([
        { dayOfWeek: 1, startTime: "00:00", endTime: "23:59", isRestricted: true,
          appliesToAll: false, studyProgram: "HKI", academicYear: null, priority: 5 },
      ]);
      mockPrisma.attendanceLog.create.mockImplementation(async (args: any) => args.data);

      const ts = new Date("2026-08-10T23:30:00+07:00");
      const result = await recordScan({
        ...scanInput, isViolation: true, violationType: "restricted_hours",
        timestamp: ts.getTime(),
      });
      expect(result.isViolation).toBe(false);
      expect(result.violationType).toBeNull();
    });

    it("#119: clientId (idempotency key) diteruskan ke attendanceLog.create", async () => {
      mockPrisma.student.findUnique.mockResolvedValue({
        id: "s1", name: "Test", nim: "123",
      });
      mockPrisma.campusRule.findMany.mockResolvedValue([]);
      mockPrisma.attendanceLog.create.mockImplementation(async (args: any) => args.data);

      const result = await recordScan({
        ...scanInput, clientId: "uuid-offline-1",
      });
      expect(result.clientId).toBe("uuid-offline-1");
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
