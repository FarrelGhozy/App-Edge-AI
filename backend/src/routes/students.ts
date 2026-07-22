import { Elysia } from "elysia";
import {
  createStudentSchema,
  updateStudentSchema,
  uploadFaceSchema,
  batchUploadFacesSchema,
  listStudents,
  getStudent,
  createStudent,
  updateStudent,
  deleteStudent,
  uploadFace,
  batchUploadFaces,
  deleteFace
} from "../services/student";
import { authGuard } from "../guards/auth";
import prisma from "../services/prisma";

export const studentRoutes = new Elysia()
  .use(authGuard)
  .get("/api/students", async ({ query }) => {
    const params = {
      page: query.page ? parseInt(query.page as string) : 1,
      pageSize: query.pageSize ? parseInt(query.pageSize as string) : 20,
      search: query.search as string | undefined,
      studyProgram: query.studyProgram as string | undefined,
      academicYear: query.academicYear as string | undefined
    };
    return await listStudents(params);
  })
  .get("/api/students/:id", async ({ params: { id } }) => {
    const student = await getStudent(id);
    if (!student) {
      return new Response(JSON.stringify({ success: false, error: "Student not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }
    return student;
  })
  .post("/api/students", async ({ body }) => {
    try {
      const student = await createStudent(body);
      return student;
    } catch (error: any) {
      if (error.code === "P2002") {
        return new Response(
          JSON.stringify({ success: false, error: "NIM sudah terdaftar" }),
          { status: 409, headers: { "Content-Type": "application/json" } }
        );
      }
      return new Response(
        JSON.stringify({ success: false, error: "Gagal menyimpan mahasiswa" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }
  }, { body: createStudentSchema })
  .put("/api/students/:id", async ({ params: { id }, body }) => {
    const student = await updateStudent(id, body as Record<string, unknown>);
    return student;
  }, { body: updateStudentSchema })
  .delete("/api/students/:id", async ({ params: { id } }) => {
    await deleteStudent(id);
    return { success: true };
  })
  // ─── Upload single pose vector ───
  .post("/api/students/:id/face", async ({ params: { id }, body }) => {
    try {
      await uploadFace(id, body.pose, body.vector);
      return { success: true };
    } catch (error: any) {
      if (error.message === "STUDENT_NOT_FOUND") {
        return new Response(
          JSON.stringify({ success: false, error: "Mahasiswa tidak ditemukan" }),
          { status: 404, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.message === "INVALID_POSE") {
        return new Response(
          JSON.stringify({ success: false, error: "Pose tidak valid" }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.message?.startsWith("VECTOR_DIMENSION_MISMATCH")) {
        return new Response(
          JSON.stringify({ success: false, error: `Dimensi vector tidak sesuai: ${error.message}` }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.code === "23502" || error.message?.includes("null")) {
        return new Response(
          JSON.stringify({ success: false, error: "Gagal menyimpan vector wajah, kolom tidak boleh kosong" }),
          { status: 500, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.message?.startsWith("PGVECTOR_ERROR")) {
        return new Response(
          JSON.stringify({ success: false, error: "Gagal menyimpan ke database vector: pastikan ekstensi pgvector sudah aktif" }),
          { status: 500, headers: { "Content-Type": "application/json" } }
        );
      }
      return new Response(
        JSON.stringify({ success: false, error: "Gagal mengunggah wajah" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }
  }, { body: uploadFaceSchema })
  // ─── Upload all 5 pose vectors in batch ───
  .post("/api/students/:id/faces", async ({ params: { id }, body }) => {
    try {
      const result = await batchUploadFaces(id, body.vectors);
      return { success: true, ...result };
    } catch (error: any) {
      if (error.message === "STUDENT_NOT_FOUND") {
        return new Response(
          JSON.stringify({ success: false, error: "Mahasiswa tidak ditemukan" }),
          { status: 404, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.message === "EMPTY_VECTORS") {
        return new Response(
          JSON.stringify({ success: false, error: "Setidaknya satu pose vector diperlukan" }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.message?.startsWith("INVALID_POSE")) {
        return new Response(
          JSON.stringify({ success: false, error: "Pose tidak valid" }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.message?.startsWith("VECTOR_DIMENSION_MISMATCH")) {
        return new Response(
          JSON.stringify({ success: false, error: `Dimensi vector tidak sesuai: ${error.message}` }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        );
      }
      if (error.message?.startsWith("PGVECTOR_ERROR")) {
        return new Response(
          JSON.stringify({ success: false, error: "Gagal menyimpan ke database vector" }),
          { status: 500, headers: { "Content-Type": "application/json" } }
        );
      }
      return new Response(
        JSON.stringify({ success: false, error: "Gagal mengunggah wajah" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }
  }, { body: batchUploadFacesSchema })
  .delete("/api/students/:id/face", async ({ params: { id } }) => {
    const result = await deleteFace(id);
    if (!result.deleted) {
      return new Response(
        JSON.stringify({ success: false, error: "Wajah tidak ditemukan" }),
        { status: 404, headers: { "Content-Type": "application/json" } }
      );
    }
    return { success: true };
  })
  // ─── Get schedules for a student ───
  .get("/api/students/:id/schedules", async ({ params: { id } }) => {
    const schedules = await prisma.courseSchedule.findMany({
      where: { studentId: id, isActive: true },
      orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }]
    });
    return { success: true, data: schedules };
  })
  // ─── Get student status (toggle + violations) ───
  .get("/api/students/:id/status", async ({ params: { id } }) => {
    const student = await prisma.student.findUnique({ where: { id } });
    if (!student) {
      return new Response(JSON.stringify({ success: false, error: "Student not found" }), { status: 404, headers: { "Content-Type": "application/json" } });
    }
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const todayLogs = await prisma.attendanceLog.findMany({
      where: { studentId: id, timestamp: { gte: today } },
      orderBy: { timestamp: "desc" }
    });
    const lastAction = todayLogs.length > 0 ? todayLogs[0].action : null;
    const violations = await prisma.violation.findMany({
      where: { studentId: id, isResolved: false },
      orderBy: { timestamp: "desc" },
      take: 10
    });
    return {
      success: true,
      data: {
        studentId: id,
        studentName: student.name,
        nim: student.nim,
        currentStatus: lastAction === "keluar" ? "outside" : "inside",
        lastAction,
        lastTimestamp: todayLogs[0]?.timestamp?.toISOString() || null,
        totalScansToday: todayLogs.length,
        activeViolations: violations.length,
        violations
      }
    };
  })
  // ─── Import students (CSV/JSON) ───
  .post("/api/students/import", async ({ body }) => {
    const { rows } = body as { rows: Array<{ nim: string; name: string; studyProgram: string; academicYear: string; phone?: string; email?: string }> };
    const results = { success: 0, failed: 0, errors: [] as string[] };
    for (let i = 0; i < rows.length; i++) {
      try {
        await prisma.student.create({
          data: {
            nim: rows[i].nim,
            name: rows[i].name,
            studyProgram: rows[i].studyProgram,
            academicYear: rows[i].academicYear,
            phone: rows[i].phone || null,
            email: rows[i].email || null
          }
        });
        results.success++;
      } catch (e: any) {
        results.failed++;
        results.errors.push(`Row ${i + 1}: ${e.message || "Unknown error"}`);
      }
    }
    // Log import batch
    if (results.success > 0) {
      await prisma.importBatch.create({
        data: {
          filename: `import-${Date.now()}`,
          totalRows: rows.length,
          successRows: results.success,
          failedRows: results.failed,
          errors: results.errors.length > 0 ? results.errors.join("; ") : null
        }
      });
    }
    return { success: true, data: results };
  });
