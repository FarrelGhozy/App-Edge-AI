import { Elysia } from "elysia";
import { dashboardSummary } from "../services/dashboard";
import { authGuard } from "../guards/auth";

export const dashboardRoutes = new Elysia()
  .use(authGuard)
  .get(
  "/api/dashboard/summary",
  async () => {
    return await dashboardSummary();
  }
);
