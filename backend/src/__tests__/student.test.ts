import { describe, it, expect, mock } from "bun:test";

// Use `any` mocks (Bun's mock works without strict typing at runtime)
const mockFindUnique = mock<any>();
const mockFindMany = mock<any>();
const mockCount = mock<any>();
const mockCreate = mock<any>();
const mockUpdate = mock<any>();
const mockDelete = mock<any>();
const mockDeleteMany = mock<any>();
const mockRawQuery = mock<any>();

const mockPrisma = {
  $queryRawUnsafe: mockRawQuery,
  $executeRawUnsafe: mock<any>(),
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
    findMany: mockFindMany,
    count: mockCount,
  },
  attendanceLog: { deleteMany: mockDeleteMany },
  permit: { deleteMany: mockDeleteMany },
  device: { findMany: mockFindMany },
  syncRequest: { create: mockCreate },
};

mock.module("../services/prisma", () => ({ default: mockPrisma }));

const { uploadFace, createStudent, listStudents, getStudent, deleteStudent, deleteFace, updateStudent } = await import("../services/student");

// ────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────
function resetMocks() {
  [mockFindUnique, mockFindMany, mockCount, mockCreate, mockUpdate, mockDelete, mockDeleteMany, mockRawQuery].forEach(m => m.mockReset());
}

function vec192() { return new Array(192).fill(0.1); }
function vec512() { return new Array(512).fill(0.05); }

// ────────────────────────────────────────────────────
// uploadFace
// ────────────────────────────────────────────────────
describe("uploadFace", () => {
  beforeEach(resetMocks);

  it("rejects 100-d vector", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    await expect(uploadFace("s1", new Array(100).fill(0.5))).rejects.toThrow("VECTOR_DIMENSION_MISMATCH");
  });

  it("rejects empty vector", async () => {
    await expect(uploadFace("s1", [])).rejects.toThrow("VECTOR_DIMENSION_MISMATCH");
  });

  it("accepts 192-d vector", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    const r = await uploadFace("s1", vec192());
    expect(r).toBeDefined();
  });

  it("accepts 512-d vector", async () => {
    mockFindUnique.mockResolvedValue({ id: "s1", name: "T" });
    const r = await uploadFace("s1", vec512());
    expect(r).toBeDefined();
  });

  it("throws STUDENT_NOT_FOUND when missing", async () => {
    mockFindUnique.mockResolvedValue(null);
    await expect(uploadFace("x", vec192())).rejects.toThrow("STUDENT_NOT_FOUND");
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
    mockFindMany.mockResolvedValue([{ studentId: "s1" }]);                      // faceVector.findMany

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
    mockCount.mockResolvedValue(1);
    mockRawQuery.mockResolvedValue([]);
    const r = await getStudent("s1");
    expect(r).not.toBeNull();
    expect(r!.faceRegistered).toBe(true);
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
