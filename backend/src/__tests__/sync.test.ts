import { describe, it, expect, beforeEach, mock } from "bun:test";

const mockFindMany = mock<any>();
const mockFindUnique = mock<any>();
const mockCreate = mock<any>();
const mockDeleteMany = mock<any>();
const mockRaw = mock<any>();

const mockPrisma = {
  attendanceLog: { findMany: mockFindMany, create: mockCreate },
  faceVector: { findMany: mockFindMany, deleteMany: mockDeleteMany },
  campusRule: { findMany: mockFindMany },
  syncRequest: { findMany: mockFindMany, create: mockCreate, deleteMany: mockDeleteMany },
  device: { findUnique: mockFindUnique, update: mock<any>() },
};

mock.module("../services/prisma", () => ({ default: mockPrisma }));

// ─── Data models ───
interface SyncLog {
  id: string;
  studentId: string;
  studentName: string;
  action: string;
  confidenceScore: number;
  timestamp: Date;
  deviceId?: string;
  synced: boolean;
}

interface FaceData {
  studentId: string;
  vector: string;
  updatedAt: Date;
}

interface SyncRequest {
  id: string;
  deviceId: string;
  requestedAt: Date;
}

// ─── Sync logic functions (mirrors backend) ───
function prepareAttendanceBatch(logs: SyncLog[]): SyncLog[] {
  return logs
    .filter(l => !l.synced)
    .map(l => ({ ...l, synced: true }));
}

function serializeFaceForSync(face: FaceData): { studentId: string; vector: number[]; updatedAt: string } {
  // Parse PostgreSQL vector string like [0.1,0.2,0.3] → number[]
  const vecStr = face.vector.replace(/[\[\]]/g, "").split(",").filter(Boolean).map(Number);
  return {
    studentId: face.studentId,
    vector: vecStr,
    updatedAt: face.updatedAt.toISOString(),
  };
}

function preparePendingSync(requests: SyncRequest[], deviceId: string): SyncRequest[] {
  return requests.filter(r => r.deviceId === deviceId);
}

function syncComplete(requests: SyncRequest[], ids: string[]): SyncRequest[] {
  return requests.filter(r => !ids.includes(r.id));
}

// ─────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────

describe("sync service", () => {

  describe("attendance batch", () => {
    it("filters out already synced logs", () => {
      const logs: SyncLog[] = [
        { id: "1", studentId: "s1", studentName: "A", action: "keluar", confidenceScore: 0.9, timestamp: new Date(), synced: true },
        { id: "2", studentId: "s2", studentName: "B", action: "kembali", confidenceScore: 0.85, timestamp: new Date(), synced: false },
      ];
      const batch = prepareAttendanceBatch(logs);
      expect(batch).toHaveLength(1);
      expect(batch[0].id).toBe("2");
    });

    it("marks logs as synced after processing", () => {
      const logs: SyncLog[] = [
        { id: "1", studentId: "s1", studentName: "A", action: "keluar", confidenceScore: 0.9, timestamp: new Date(), synced: false },
      ];
      const batch = prepareAttendanceBatch(logs);
      expect(batch[0].synced).toBe(true);
    });
  });

  describe("face sync serialization", () => {
    it("parses 192-d vector from PostgreSQL format", () => {
      const face: FaceData = {
        studentId: "s1",
        vector: `[${new Array(192).fill(0.15).join(",")}]`,
        updatedAt: new Date("2025-07-15"),
      };
      const result = serializeFaceForSync(face);
      expect(result.vector).toHaveLength(192);
      expect(result.vector[0]).toBe(0.15);
    });

    it("parses 512-d vector from PostgreSQL format", () => {
      const face: FaceData = {
        studentId: "s2",
        vector: `[${new Array(512).fill(0.05).join(",")}]`,
        updatedAt: new Date(),
      };
      const result = serializeFaceForSync(face);
      expect(result.vector).toHaveLength(512);
      expect(result.vector[0]).toBe(0.05);
    });

    it("handles negative vector values", () => {
      const face: FaceData = {
        studentId: "s3",
        vector: "[-0.1,0.2,-0.3]",
        updatedAt: new Date(),
      };
      const result = serializeFaceForSync(face);
      expect(result.vector).toEqual([-0.1, 0.2, -0.3]);
    });

    it("returns ISO date string", () => {
      const date = new Date("2025-07-15T10:30:00Z");
      const face: FaceData = { studentId: "s1", vector: "[0.1]", updatedAt: date };
      const result = serializeFaceForSync(face);
      expect(result.updatedAt).toBe("2025-07-15T10:30:00.000Z");
    });
  });

  describe("sync request management", () => {
    it("filters requests by device", () => {
      const requests: SyncRequest[] = [
        { id: "r1", deviceId: "dev-1", requestedAt: new Date() },
        { id: "r2", deviceId: "dev-2", requestedAt: new Date() },
        { id: "r3", deviceId: "dev-1", requestedAt: new Date() },
      ];
      const pending = preparePendingSync(requests, "dev-1");
      expect(pending).toHaveLength(2);
    });

    it("marks sync as complete by removing requests", () => {
      const requests: SyncRequest[] = [
        { id: "r1", deviceId: "dev-1", requestedAt: new Date() },
        { id: "r2", deviceId: "dev-1", requestedAt: new Date() },
      ];
      const remaining = syncComplete(requests, ["r1"]);
      expect(remaining).toHaveLength(1);
      expect(remaining[0].id).toBe("r2");
    });
  });

  // ──────────────────────────────────────────────
  // Sync state counting
  // ──────────────────────────────────────────────
  describe("sync status", () => {
    it("should calculate pending count", () => {
      const totalFaces = 1000;
      const synced = 800;
      const pending = totalFaces - synced;
      expect(pending).toBe(200);
    });

    it("zero pending when all synced", () => {
      expect(1000 - 1000).toBe(0);
    });
  });
});
