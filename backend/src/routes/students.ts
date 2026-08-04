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
import { notifyDevicesChange } from "../services/events";
import { audit } from "../services/audit";
import prisma from "../services/prisma";

export const studentRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
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
  .post("/api/students", async ({ body, admin }) => {
    try {
      const student = await createStudent(body);
      notifyDevicesChange();
      await audit(admin, { action: "CREATE", entityType: "STUDENTS", entityId: student.id, details: `nim=${body.nim}` });
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
  // ─── Import CSV/JSON batch (1 request, bukan 1/baris — #99) ───
  .post("/api/students/import", async ({ body, admin }) => {
    const rows = (body as { students: Array<Record<string, unknown>> }).students ?? [];
    if (!Array.isArray(rows) || rows.length === 0) {
      return new Response(
        JSON.stringify({ success: false, error: "Tidak ada data untuk diimpor" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }
    let successRows = 0;
    let failedRows = 0;
    const errors: Array<{ row: number; error: string }> = [];
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      try {
        await createStudent(row as never);
        successRows++;
      } catch (error: any) {
        failedRows++;
        errors.push({
          row: i + 1,
          error: error?.code === "P2002" ? "NIM sudah terdaftar" : (error?.message ?? "gagal")
        });
      }
    }
    // Catat batch import (model ImportBatch selama ini tak terpakai — #99)
    await prisma.importBatch.create({
      data: {
        filename: (body as { filename?: string }).filename ?? "csv",
        totalRows: rows.length,
        successRows,
        failedRows,
        errors: errors.length > 0 ? JSON.stringify(errors) : null
      }
    });
    if (successRows > 0) notifyDevicesChange();
    await audit(admin, {
      action: "CREATE",
      entityType: "STUDENTS",
      entityId: "batch",
      details: `import ${rows.length} baris (ok=${successRows}, gagal=${failedRows})`
    });
    return { success: true, total: rows.length, successRows, failedRows, errors };
  })
  .put("/api/students/:id", async ({ params: { id }, body, admin }) => {
    const student = await updateStudent(id, body as Record<string, unknown>);
    notifyDevicesChange();
    await audit(admin, { action: "UPDATE", entityType: "STUDENTS", entityId: id });
    return student;
  }, { body: updateStudentSchema })
  .delete("/api/students/:id", async ({ params: { id }, admin }) => {
    await deleteStudent(id);
    notifyDevicesChange();
    await audit(admin, { action: "DELETE", entityType: "STUDENTS", entityId: id });
    return { success: true };
  })
  // ─── Upload single face vector (FRONT_N, #132) ───
  .post("/api/students/:id/face", async ({ params: { id }, body, admin }) => {
    try {
      await uploadFace(id, body.pose, body.vector);
      notifyDevicesChange();
      await audit(admin, { action: "UPDATE", entityType: "STUDENTS", entityId: id, details: "face upload (single)" });
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
  // ─── Upload batch face vectors (FRONT_1..FRONT_N, #132) ───
  .post("/api/students/:id/faces", async ({ params: { id }, body, admin }) => {
    try {
      const result = await batchUploadFaces(id, body.vectors);
      notifyDevicesChange();
      await audit(admin, { action: "UPDATE", entityType: "STUDENTS", entityId: id, details: `face batch (${body.vectors.length} pose)` });
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
  .delete("/api/students/:id/face", async ({ params: { id }, admin }) => {
    const result = await deleteFace(id);
    if (!result.deleted) {
      return new Response(
        JSON.stringify({ success: false, error: "Wajah tidak ditemukan" }),
        { status: 404, headers: { "Content-Type": "application/json" } }
      );
    }
    notifyDevicesChange();
    await audit(admin, { action: "UPDATE", entityType: "STUDENTS", entityId: id, details: "face delete" });
    return { success: true };
  });
