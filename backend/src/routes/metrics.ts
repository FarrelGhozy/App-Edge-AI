import { Elysia } from "elysia";
import prisma from "../services/prisma";
import { authGuard } from "../guards/auth";

/**
 * Daily metrics aggregation endpoint.
 * Calculates FPR/FNR from ScanMetrics each day.
 */
function startOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

function endOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(23, 59, 59, 999);
  return d;
}

export const metricRoutes = new Elysia()
  .use(authGuard)
  // GET /api/metrics/daily — get metrics for a specific date range
  .get("/api/metrics/daily", async ({ query }) => {
    const from = (query.from as string) || new Date().toISOString().split("T")[0];
    const to = (query.to as string) || from;

    const startDate = new Date(from);
    startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(to);
    endDate.setHours(23, 59, 59, 999);

    const metrics = await prisma.dailyMetrics.findMany({
      where: {
        date: { gte: startDate, lte: endDate }
      },
      orderBy: { date: "asc" }
    });

    return { success: true, data: metrics };
  })
  // GET /api/metrics/today — quick today's metrics (or compute on the fly)
  .get("/api/metrics/today", async () => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Try to find existing DailyMetrics for today
    let metrics = await prisma.dailyMetrics.findUnique({
      where: { date: today }
    });

    if (!metrics) {
      // Compute from raw scan metrics
      const todayEnd = new Date(today);
      todayEnd.setHours(23, 59, 59, 999);

      const [totalScans, totalConfident, totalMedium, totalWeak, totalNoMatch, avgTime] =
        await Promise.all([
          prisma.scanMetric.count({
            where: { timestamp: { gte: today, lte: todayEnd } }
          }),
          prisma.scanMetric.count({
            where: { timestamp: { gte: today, lte: todayEnd }, decision: "CONFIDENT" }
          }),
          prisma.scanMetric.count({
            where: { timestamp: { gte: today, lte: todayEnd }, decision: "MEDIUM" }
          }),
          prisma.scanMetric.count({
            where: { timestamp: { gte: today, lte: todayEnd }, decision: "WEAK" }
          }),
          prisma.scanMetric.count({
            where: { timestamp: { gte: today, lte: todayEnd }, decision: "NO_MATCH" }
          }),
          prisma.scanMetric.aggregate({
            _avg: { matchingTimeMs: true },
            where: { timestamp: { gte: today, lte: todayEnd } }
          })
        ]);

      const falsePositives = await prisma.scanMetric.count({
        where: {
          timestamp: { gte: today, lte: todayEnd },
          decision: { in: ["CONFIDENT", "MEDIUM"] },
          isCorrect: false
        }
      });

      const falseNegatives = await prisma.scanMetric.count({
        where: {
          timestamp: { gte: today, lte: todayEnd },
          decision: "NO_MATCH",
          isCorrect: false
        }
      });

      const reviewedTotal = await prisma.scanMetric.count({
        where: {
          timestamp: { gte: today, lte: todayEnd },
          isCorrect: { not: null }
        }
      });

      const fpr = reviewedTotal > 0 ? falsePositives / reviewedTotal : 0;
      const fnr = reviewedTotal > 0 ? falseNegatives / reviewedTotal : 0;

      metrics = {
        id: "",
        date: today,
        totalScans,
        totalConfident,
        totalMedium,
        totalWeak,
        totalNoMatch,
        falsePositives,
        falseNegatives,
        avgResponseTimeMs: avgTime._avg.matchingTimeMs || 0,
        fpr,
        fnr,
        createdAt: new Date(),
        updatedAt: new Date()
      };
    }

    return { success: true, data: metrics };
  })
  // POST /api/metrics/aggregate — manually trigger daily aggregation (cron)
  .post("/api/metrics/aggregate", async ({ body }) => {
    const targetDate = (body as { date?: string }).date
      ? new Date((body as { date: string }).date)
      : new Date();
    targetDate.setHours(0, 0, 0, 0);
    const targetEnd = new Date(targetDate);
    targetEnd.setHours(23, 59, 59, 999);

    const [totalScans, totalConfident, totalMedium, totalWeak, totalNoMatch, avgTime] =
      await Promise.all([
        prisma.scanMetric.count({ where: { timestamp: { gte: targetDate, lte: targetEnd } } }),
        prisma.scanMetric.count({ where: { timestamp: { gte: targetDate, lte: targetEnd }, decision: "CONFIDENT" } }),
        prisma.scanMetric.count({ where: { timestamp: { gte: targetDate, lte: targetEnd }, decision: "MEDIUM" } }),
        prisma.scanMetric.count({ where: { timestamp: { gte: targetDate, lte: targetEnd }, decision: "WEAK" } }),
        prisma.scanMetric.count({ where: { timestamp: { gte: targetDate, lte: targetEnd }, decision: "NO_MATCH" } }),
        prisma.scanMetric.aggregate({
          _avg: { matchingTimeMs: true },
          where: { timestamp: { gte: targetDate, lte: targetEnd } }
        })
      ]);

    const falsePositives = await prisma.scanMetric.count({
      where: {
        timestamp: { gte: targetDate, lte: targetEnd },
        decision: { in: ["CONFIDENT", "MEDIUM"] },
        isCorrect: false
      }
    });

    const falseNegatives = await prisma.scanMetric.count({
      where: {
        timestamp: { gte: targetDate, lte: targetEnd },
        decision: "NO_MATCH",
        isCorrect: false
      }
    });

    const reviewedTotal = await prisma.scanMetric.count({
      where: {
        timestamp: { gte: targetDate, lte: targetEnd },
        isCorrect: { not: null }
      }
    });

    const fpr = reviewedTotal > 0 ? falsePositives / reviewedTotal : 0;
    const fnr = reviewedTotal > 0 ? falseNegatives / reviewedTotal : 0;

    const metric = await prisma.dailyMetrics.upsert({
      where: { date: targetDate },
      update: {
        totalScans, totalConfident, totalMedium, totalWeak, totalNoMatch,
        falsePositives, falseNegatives,
        avgResponseTimeMs: avgTime._avg.matchingTimeMs || 0,
        fpr, fnr
      },
      create: {
        date: targetDate,
        totalScans, totalConfident, totalMedium, totalWeak, totalNoMatch,
        falsePositives, falseNegatives,
        avgResponseTimeMs: avgTime._avg.matchingTimeMs || 0,
        fpr, fnr
      }
    });

    return { success: true, data: metric };
  });
