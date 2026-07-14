import { Elysia } from "elysia";
import { listViolations } from "../services/violation";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const violationRoutes = new Elysia()
  .use(authGuard)
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
    const data = body as {
      studentId: string;
      type: string;
      description?: string;
      action?: string;
    };
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
  })
  .put("/api/violations/:id/resolve", async ({ params: { id }, body }) => {
    const { resolvedNote } = body as { resolvedNote?: string };
    const violation = await prisma.violation.update({
      where: { id },
      data: { isResolved: true, resolvedAt: new Date(), resolvedNote: resolvedNote || null }
    });
    return { success: true, data: violation };
  });
