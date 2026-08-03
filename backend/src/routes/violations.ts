import { Elysia, t } from "elysia";
import { listViolations } from "../services/violation";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

// #123: Zod schema — body divalidasi, bukan di-cast mentah.
const createViolationSchema = t.Object({
  studentId: t.String(),
  type: t.String({ minLength: 1 }),
  description: t.Optional(t.String()),
  action: t.Optional(t.String())
});

const notFound = (msg: string) =>
  new Response(JSON.stringify({ success: false, error: msg }), {
    status: 404,
    headers: { "Content-Type": "application/json" }
  });

const isP2025 = (e: unknown): boolean =>
  e instanceof Error &&
  "code" in e &&
  (e as { code: string }).code === "P2025";

export const violationRoutes = new Elysia()
  .use(authGuard("admin", "superadmin"))
  .get("/api/violations", async ({ query }) => {
    const params = {
      page: query.page ? parseInt(query.page as string) : 1,
      pageSize: query.pageSize ? parseInt(query.pageSize as string) : 20,
      type: query.type as string | undefined,
      studentId: query.studentId as string | undefined
    };
    return await listViolations(params);
  })
  .get("/api/violations/:id", async ({ params: { id } }) => {
    const violation = await prisma.violation.findUnique({
      where: { id },
      include: { student: { select: { name: true } } }
    });
    if (!violation) {
      return new Response(JSON.stringify({ success: false, error: "Violation not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }
    const { student, ...rest } = violation;
    return { success: true, data: { ...rest, studentName: student.name } };
  })
  .post("/api/violations", async ({ body }) => {
    const data = body as typeof createViolationSchema.static;
    // #123: cek student ada dulu → 400, bukan 500 FK error.
    const student = await prisma.student.findUnique({ where: { id: data.studentId } });
    if (!student) {
      return new Response(JSON.stringify({ success: false, error: "Student tidak ditemukan" }), {
        status: 400,
        headers: { "Content-Type": "application/json" }
      });
    }
    const violation = await prisma.violation.create({
      data: {
        studentId: data.studentId,
        type: data.type,
        description: data.description || null,
        action: data.action || null,
        timestamp: new Date()
      }
    });
    return { success: true, data: violation };
  }, { body: createViolationSchema })
  .put("/api/violations/:id/resolve", async ({ params: { id }, body }) => {
    const { resolvedNote } = body as { resolvedNote?: string };
    try {
      const violation = await prisma.violation.update({
        where: { id },
        data: { isResolved: true, resolvedAt: new Date(), resolvedNote: resolvedNote || null }
      });
      return { success: true, data: violation };
    } catch (e) {
      if (isP2025(e)) return notFound("Violation tidak ditemukan");
      throw e;
    }
  });
