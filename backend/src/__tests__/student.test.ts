import { describe, it, expect, beforeEach, mock } from "bun:test";

// Use `any` mocks (Bun's mock works without strict typing at runtime)
const mockFindUnique = mock<any>();
const mockFindMany = mock<any>();
const mockCount = mock<any>();
const mockCreate = mock<any>();
const mockUpdate = mock<any>();
const mockDelete = mock<any>();
const mockDeleteMany = mock<any>();
const mockRawQuery = mock<any>();
const mockExecuteRaw = mock<any>();

const mockPrisma = {
  $queryRawUnsafe: mockRawQuery,
  $executeRawUnsafe: mockExecuteRaw,
  $transaction: mock<any>(),
  student: {
    findUnique: mockFindUnique,
    findMany: mockFindMany,
    count: mockCount,
    create: mockCreate,
    update: mockUpdate,
    delete: mockDelete,
  },
  faceVector: {
    deleteMany: mockDeleteMany,
  },
  attendanceLog: { deleteMany: mockDeleteMany },
  permit: { deleteMany: mockDeleteMany },
  device: { findMany: mockFindMany },
  syncRequest: { create: mockCreate },
};

mock.module("../services/prisma", () => ({ default: mockPrisma }));

const { uploadFace, createStudent, listStudents, getStudent, deleteStudent, deleteFace, updateStudent, batchUploadFaces } = await import("../services/student");

// ────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────
function resetMocks() {
  [mockFindUnique, mockFindMany, mockCount, mockCreate, mockUpdate, mockDelete, mockDeleteMany, mockRawQuery, mockExecuteRaw].forEach(m => m.mockReset());
}

function vec192() { return new Array(192).fill(0.1); }
function vec512() { return new Array(512).fill(0.05); }

// ────────────────────────────────────────────────────
// uploadFace (single pose)
// ────────────────────────────────────────────────────
describe("uploadFace", () => {
  beforeEach(resetMocks);

  it("rejects 100-d vector", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    await expect(uploadFace("s1", "CENTER", new Array(100).fill(0.5))).rejects.toThrow("VECTOR_DIMENSION_MISMATCH");
  });

  it("rejects empty vector", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    await expect(uploadFace("s1", "CENTER", [])).rejects.toThrow("VECTOR_DIMENSION_MISMATCH");
  });

  it("rejects invalid pose", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    await expect(uploadFace("s1", "INVALID", vec512())).rejects.toThrow("INVALID_POSE");
  });

  it("accepts 192-d vector with pose", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    mockExecuteRaw.mockResolvedValue({ count: 1 });
    const r = await uploadFace("s1", "CENTER", vec192());
    expect(r).toBeDefined();
    // Verify SQL uses composite key
    expect(mockExecuteRaw.mock.calls[0][0]).toContain("ON CONFLICT (student_id, pose)");
  });

  it("accepts 512-d vector with pose", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    mockExecuteRaw.mockResolvedValue({ count: 1 });
    const r = await uploadFace("s1", "LEFT", vec512());
    expect(r).toBeDefined();
  });

  it("throws STUDENT_NOT_FOUND when missing", async () => {
    mockFindUnique.mockResolvedValue(null);
    await expect(uploadFace("x", "CENTER", vec192())).rejects.toThrow("STUDENT_NOT_FOUND");
  });
});

// ────────────────────────────────────────────────────
// batchUploadFaces
// ────────────────────────────────────────────────────
describe("batchUploadFaces", () => {
  beforeEach(resetMocks);

  it("uploads all 5 poses", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    mockPrisma.$transaction.mockImplementation(async (txn: any[]) => txn);
    mockExecuteRaw.mockResolvedValue({ count: 1 });

    const vectors = [
      { pose: "CENTER", vector: vec512() },
      { pose: "LEFT", vector: vec512() },
      { pose: "RIGHT", vector: vec512() },
      { pose: "UP", vector: vec512() },
      { pose: "DOWN", vector: vec512() },
    ];
    const r = await batchUploadFaces("s1", vectors);
    expect(r).toEqual({ uploaded: 5 });
  });

  it("rejects empty vectors array", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    await expect(batchUploadFaces("s1", [])).rejects.toThrow("EMPTY_VECTORS");
  });

  it("rejects invalid pose", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    await expect(batchUploadFaces("s1", [{ pose: "BAD", vector: vec512() }])).rejects.toThrow("INVALID_POSE");
  });
});

// ────────────────────────────────────────────────────
// createStudent
// ────────────────────────────────────────────────────
describe("createStudent", () => {
  beforeEach(resetMocks);

  it("creates with required fields", async () => {
    mockCreate.mockResolvedValue({ id: "n1", nim: "123", name: "A" });
    mockFindMany.mockResolvedValue([]);
    const r = await createStudent({ nim: "123", name: "A", studyProgram: "TI", academicYear: "2024" });
    expect(r).toBeDefined();
    expect(mockCreate).toHaveBeenCalled();
  });

  it("creates with optional fields", async () => {
    mockCreate.mockResolvedValue({ id: "n2" });
    mockFindMany.mockResolvedValue([]);
    const r = await createStudent({ nim: "456", name: "B", studyProgram: "SI", academicYear: "2023", phone: "081", email: "a@b.c" });
    expect(r).toBeDefined();
  });
});

// ────────────────────────────────────────────────────
// updateStudent
// ────────────────────────────────────────────────────
describe("updateStudent", () => {
  beforeEach(resetMocks);

  it("updates provided fields", async () => {
    mockUpdate.mockResolvedValue({ id: "s1", name: "Updated" });
    const r = await updateStudent("s1", { name: "Updated" });
    expect(r).toBeDefined();
    expect(mockUpdate).toHaveBeenCalled();
  });
});

// ────────────────────────────────────────────────────
// listStudents
// ────────────────────────────────────────────────────
describe("listStudents", () => {
  beforeEach(resetMocks);

  it("returns paginated + faceRegistered flag", async () => {
    mockFindMany.mockResolvedValueOnce([{ id: "s1", nim: "001", name: "A" }]); // student.findMany
    mockCount.mockResolvedValue(1);                                             // student.count
    mockRawQuery.mockResolvedValue([{ student_id: "s1" }]);                     // raw query: DISTINCT student_id

    const r = await listStudents({ page: 1, pageSize: 20 });
    expect(r.data).toHaveLength(1);
    expect(r.total).toBe(1);
    expect(r.data[0].faceRegistered).toBe(true);
  });
});

// ────────────────────────────────────────────────────
// getStudent
// ────────────────────────────────────────────────────
describe("getStudent", () => {
  beforeEach(resetMocks);

  it("returns null for unknown", async () => {
    mockFindUnique.mockResolvedValue(null);
    const r = await getStudent("x");
    expect(r).toBeNull();
  });

  it("returns enriched data for known student", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", nim: "001", name: "Test", studyProgram: "TI", academicYear: "2024" });
    mockRawQuery.mockResolvedValue([]);
    const r = await getStudent("s1");
    expect(r).not.toBeNull();
    expect(r!.faceRegistered).toBe(false);
    expect(r!.posesCompleted).toBe(0);
  });
});

// ────────────────────────────────────────────────────
// deleteStudent
// ────────────────────────────────────────────────────
describe("deleteStudent", () => {
  beforeEach(resetMocks);

  it("deletes related records then student", async () => {
    mockDeleteMany.mockResolvedValue({ count: 1 });
    mockDelete.mockResolvedValue({ id: "s1" });
    mockFindMany.mockResolvedValue([]);
    const r = await deleteStudent("s1");
    expect(r).toBeDefined();
  });
});

// ────────────────────────────────────────────────────
// deleteFace
// ────────────────────────────────────────────────────
describe("deleteFace", () => {
  beforeEach(resetMocks);

  it("returns deleted: false when nothing removed", async () => {
    mockDeleteMany.mockResolvedValue({ count: 0 });
    mockFindMany.mockResolvedValue([]);
    const r = await deleteFace("s1");
    expect(r.deleted).toBe(false);
  });

  it("returns deleted: true when removed", async () => {
    mockDeleteMany.mockResolvedValue({ count: 1 });
    mockFindMany.mockResolvedValue([]);
    const r = await deleteFace("s1");
    expect(r.deleted).toBe(true);
  });
});
