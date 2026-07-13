import { Elysia } from "elysia";
import { dailyReport, monthlyReport, violationReport, outsideNow } from "../services/report";

export const reportRoutes = new Elysia()
  .get("/api/reports/daily", async ({ query }) => {
    const date = (query.date as string) || new Date().toISOString().split("T")[0];
    return await dailyReport(date);
  })
  .get("/api/reports/monthly", async ({ query }) => {
    const now = new Date();
    const month = query.month ? parseInt(query.month as string) : now.getMonth() + 1;
    const year = query.year ? parseInt(query.year as string) : now.getFullYear();
    return await monthlyReport(month, year);
  })
  .get("/api/reports/violations", async ({ query }) => {
    const from = (query.from as string) || new Date().toISOString().split("T")[0];
    const to = (query.to as string) || new Date().toISOString().split("T")[0];
    return await violationReport(from, to);
  })
  .get("/api/reports/outside-now", async () => {
    return await outsideNow();
  });
