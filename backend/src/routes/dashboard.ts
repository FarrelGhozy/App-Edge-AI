import { Elysia } from "elysia";
import { dashboardSummary } from "../services/dashboard";

export const dashboardRoutes = new Elysia().get(
  "/api/dashboard/summary",
  async () => {
    return await dashboardSummary();
  }
);
