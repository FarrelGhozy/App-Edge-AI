import { Elysia } from "elysia";
import { dashboardSummary, dashboardWeekly, dashboardOutsideNow, dashboardViolationSummary, dashboardRecentScans } from "../services/dashboard";
import { authGuard } from "../guards/auth";

export const dashboardRoutes = new Elysia()
  .use(authGuard)
  .get("/api/dashboard/summary", async () => {
    return await dashboardSummary();
  })
  .get("/api/dashboard/weekly", async () => {
    return await dashboardWeekly();
  })
  .get("/api/dashboard/outside-now", async () => {
    return await dashboardOutsideNow();
  })
  .get("/api/dashboard/violation-summary", async () => {
    return await dashboardViolationSummary();
  })
  .get("/api/dashboard/recent-scans", async ({ query }) => {
    const limit = query.limit ? parseInt(query.limit as string) : 20;
    return await dashboardRecentScans(limit);
  });
