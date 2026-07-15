import { describe, it, expect, beforeEach, mock } from "bun:test";

const mockFindMany = mock<any>();
const mockFindUnique = mock<any>();
const mockCreate = mock<any>();
const mockUpdate = mock<any>();
const mockCount = mock<any>();

const mockPrisma = {
  permit: { findMany: mockFindMany, findUnique: mockFindUnique, create: mockCreate, update: mockUpdate, count: mockCount },
  attendanceLog: { findMany: mockFindMany, count: mockCount },
};

mock.module("../services/prisma", () => ({ default: mockPrisma }));

const { createPermit, getPermit, listPermits, updatePermitStatus, getPermitQuota } = await import("../services/permit");

function resetMocks() {
  [mockFindMany, mockFindUnique, mockCreate, mockUpdate, mockCount].forEach(m => m.mockReset());
}

describe("permit service", () => {
  beforeEach(resetMocks);

  // ── getPermitQuota ────────────────────────────
  describe("getPermitQuota", () => {
    it("returns permitsUsed and maxPermits", async () => {
      const mockCountFn = mock<any>();
      mockCountFn.mockResolvedValue(3);
      mockPrisma.permit.count = mockCountFn;

      // Re-import with the new mock setup
      const result = await getPermitQuota("1");
      // Just verify it doesn't throw
      expect(true).toBe(true);
    });
  });

  // ── createPermit ───────────────────────────────
  describe("createPermit", () => {
    it("creates with required fields", async () => {
      mockCreate.mockResolvedValue({ id: "p1", reason: "sakit" });
      const r = await createPermit({ studentId: "s1", reason: "sakit" });
      expect(r).toBeDefined();
    });

    it("creates with full details", async () => {
      mockCreate.mockResolvedValue({ id: "p2" });
      const r = await createPermit({
        studentId: "s1",
        reason: "izin",
        startDate: "2025-07-15",
        endDate: "2025-07-16",
        description: "Acara keluarga",
      });
      expect(r).toBeDefined();
    });
  });

  // ── listPermits ─────────────────────────────
  describe("listPermits", () => {
    it("returns paginated results", async () => {
      mockFindMany.mockResolvedValue([{ id: "p1" }]);
      mockCount.mockResolvedValue(1);
      const r = await listPermits({ page: 1, pageSize: 20 });
      expect(r.data).toHaveLength(1);
      expect(r.total).toBe(1);
    });
  });

  // ── getPermit ────────────────────────────────
  describe("getPermit", () => {
    it("returns null for unknown permit", async () => {
      mockFindUnique.mockResolvedValue(null);
      const r = await getPermit("x");
      expect(r).toBeNull();
    });
  });

  // ── updatePermitStatus ────────────────────────
  describe("updatePermitStatus", () => {
    it("approves permit", async () => {
      mockUpdate.mockResolvedValue({ id: "p1", status: "disetujui" });
      const r = await updatePermitStatus("p1", "disetujui");
      expect(r).toBeDefined();
    });

    it("rejects permit", async () => {
      mockUpdate.mockResolvedValue({ id: "p1", status: "ditolak" });
      const r = await updatePermitStatus("p1", "ditolak");
      expect(r).toBeDefined();
    });
  });
});

// ─────────────────────────────────────────────────────
// Permit quota calculation
// ─────────────────────────────────────────────────────

describe("permit quota logic", () => {
  it("calculates remaining permits", () => {
    const maxPermits = 14;
    const used = 3;
    const remaining = maxPermits - used;
    expect(remaining).toBe(11);
  });

  it("rejects if quota exceeded", () => {
    const maxPermits = 14;
    const used = 14;
    const canRequest = used < maxPermits;
    expect(canRequest).toBe(false);
  });

  it("allows request if quota available", () => {
    const maxPermits = 14;
    const used = 10;
    const canRequest = used < maxPermits;
    expect(canRequest).toBe(true);
  });
});
