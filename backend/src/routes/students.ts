import { Elysia } from "elysia";
import {
  createStudentSchema,
  updateStudentSchema,
  uploadFaceSchema,
  listStudents,
  getStudent,
  createStudent,
  updateStudent,
  deleteStudent,
  uploadFace
} from "../services/student";
import { authGuard } from "../guards/auth";

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
  .post("/api/students/:id/face", async ({ params: { id }, body }) => {
    try {
      await uploadFace(id, body.vector);
      return { success: true };
    } catch (error: any) {
      if (error.code === "23502" || error.message?.includes("null")) {
        return new Response(
          JSON.stringify({ success: false, error: "Gagal menyimpan vector wajah, kolom tidak boleh kosong" }),
          { status: 500, headers: { "Content-Type": "application/json" } }
        );
      }
      return new Response(
        JSON.stringify({ success: false, error: "Gagal mengunggah wajah" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }
  }, { body: uploadFaceSchema });
