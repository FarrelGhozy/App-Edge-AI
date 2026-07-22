import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

export const scanMetricRoutes = new Elysia()
  .use(authGuard)
  // POST /api/sync/scan-metrics — kiosk upload batch scan metrics
  .post("/api/sync/scan-metrics", async ({ body }) => {
    const metrics = body as Array<{
      studentId: string;
      predictedStudentId?: string;
      deviceId?: string;
      matchingTimeMs: number;
      totalTimeMs: number;
      decision: string;
      topSimilarity: number;
      gap?: number;
      runnerUpSimilarity?: number;
      timestamp: string;
    }>;

    const count = await prisma.scanMetric.createMany({
      data: metrics.map(m => ({
        studentId: m.studentId,
        predictedStudentId: m.predictedStudentId || null,
        deviceId: m.deviceId || null,
        matchingTimeMs: m.matchingTimeMs || 0,
        totalTimeMs: m.totalTimeMs || 0,
        decision: m.decision,
        topSimilarity: m.topSimilarity,
        gap: m.gap || 0,
        runnerUpSimilarity: m.runnerUpSimilarity || 0,
        timestamp: new Date(m.timestamp)
      }))
    });

    return { success: true, count: count.count };
  })
  // GET /api/scan-metrics — admin view metrics with pagination + filters
  .get("/api/scan-metrics", async ({ query }) => {
    const page = query.page ? parseInt(query.page as string) : 1;
    const pageSize = query.pageSize ? parseInt(query.pageSize as string) : 20;
    const decision = query.decision as string | undefined;
    const startDate = query.startDate as string | undefined;
    const endDate = query.endDate as string | undefined;
    const deviceId = query.deviceId as string | undefined;

    const where: Record<string, unknown> = {};
    if (decision) where.decision = decision;
    if (deviceId) where.deviceId = deviceId;
    if (startDate || endDate) {
      where.timestamp = {};
      if (startDate) (where.timestamp as Record<string, unknown>).gte = new Date(startDate);
      if (endDate) (where.timestamp as Record<string, unknown>).lte = new Date(endDate);
    }

    const [data, total] = await Promise.all([
      prisma.scanMetric.findMany({
        where,
        skip: (page - 1) * pageSize,
        take: pageSize,
        orderBy: { timestamp: "desc" }
      }),
      prisma.scanMetric.count({ where })
    ]);

    return { success: true, data, total, page, pageSize };
  })
  // PATCH /api/scan-metrics/:id/review — admin review a weak match
  .patch("/api/scan-metrics/:id/review", async ({ params: { id }, body }) => {
    const { isCorrect, note, reviewedBy } = body as {
      isCorrect: boolean;
      note?: string;
      reviewedBy?: string;
    };

    const metric = await prisma.scanMetric.update({
      where: { id },
      data: {
        isCorrect,
        note: note || null,
        reviewedById: reviewedBy || null,
        reviewedAt: new Date()
      }
    });

    return { success: true, data: metric };
  });
