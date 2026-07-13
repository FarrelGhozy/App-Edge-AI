import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const auditRoutes = new Elysia()
  .use(authGuard)
  .get("/api/audit", async ({ query }) => {
    const page = query.page ? parseInt(query.page as string) : 1;
    const pageSize = query.pageSize ? parseInt(query.pageSize as string) : 50;
    const skip = (page - 1) * pageSize;

    const adminId = query.adminId as string | undefined;
    const action = query.action as string | undefined;
    const startDate = query.startDate as string | undefined;
    const endDate = query.endDate as string | undefined;

    const where: Record<string, unknown> = {};
    if (adminId) where.adminId = adminId;
    if (action) where.action = action;
    if (startDate || endDate) {
      where.createdAt = {};
      if (startDate) (where.createdAt as Record<string, unknown>).gte = new Date(startDate);
      if (endDate) (where.createdAt as Record<string, unknown>).lte = new Date(endDate);
    }

    const [data, total] = await Promise.all([
      prisma.auditLog.findMany({ where, skip, take: pageSize, orderBy: { createdAt: "desc" } }),
      prisma.auditLog.count({ where })
    ]);

    return { success: true, data, total, page, pageSize };
  });
