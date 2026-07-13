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
import prisma from "../services/prisma";
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
    const student = await createStudent(body);
    return student;
  }, { body: createStudentSchema })
  .put("/api/students/:id", async ({ params: { id }, body }) => {
    const student = await updateStudent(id, body as Record<string, unknown>);
    return student;
  }, { body: updateStudentSchema })
  .delete("/api/students/:id", async ({ params: { id } }) => {
    await deleteStudent(id);
    return new Response(null, { status: 204 });
  })
  .post("/api/students/:id/face", async ({ params: { id }, body }) => {
    await uploadFace(id, body.vector);
    return new Response(null, { status: 204 });
  }, { body: uploadFaceSchema })
  .get("/api/students/:id/schedules", async ({ params: { id } }) => {
    const schedules = await prisma.courseSchedule.findMany({
      where: { studentId: id },
      orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }]
    });
    return schedules;
  })
  .get("/api/students/:id/permits", async ({ params: { id } }) => {
    const permits = await prisma.permit.findMany({
      where: { studentId: id },
      orderBy: { createdAt: "desc" }
    });
    return permits;
  })
  .get("/api/students/:id/violations", async ({ params: { id } }) => {
    const violations = await prisma.violation.findMany({
      where: { studentId: id },
      orderBy: { timestamp: "desc" }
    });
    return violations;
  })
  .get("/api/students/:id/status", async ({ params: { id } }) => {
    const student = await getStudent(id);
    if (!student) {
      return new Response(JSON.stringify({ success: false, error: "Student not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }

    const latestLog = await prisma.attendanceLog.findFirst({
      where: { studentId: id },
      orderBy: { timestamp: "desc" }
    });

    const todayActivePermit = await prisma.permit.findFirst({
      where: {
        studentId: id,
        status: "approved",
        startDate: { lte: new Date() },
        endDate: { gte: new Date() },
      }
    });

    return {
      ...student,
      currentStatus: latestLog?.action === "keluar" ? "outside" : "inside",
      lastScan: latestLog ? {
        action: latestLog.action,
        timestamp: latestLog.timestamp.toISOString(),
      } : null,
      activePermit: todayActivePermit || null,
    };
  })
  .post("/api/students/import", async ({ body }) => {
    const { students } = body as { students: Array<{ nim: string; name: string; studyProgram: string; academicYear: string; phone?: string; email?: string }> };
    let successRows = 0;
    let failedRows = 0;
    const errors: string[] = [];

    for (const s of students) {
      try {
        await createStudent(s as any);
        successRows++;
      } catch (err: any) {
        failedRows++;
        errors.push(`Student ${s.nim}: ${err.message}`);
      }
    }

    await prisma.importBatch.create({
      data: {
        filename: "import-api",
        totalRows: students.length,
        successRows,
        failedRows,
        errors: errors.length > 0 ? JSON.stringify(errors) : null
      }
    });

    return { success: true, data: { totalRows: students.length, successRows, failedRows } };
  });
