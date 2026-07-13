import { Elysia } from "elysia";
import { listAuditLogs } from "../services/audit";
import { authGuard } from "../guards/auth";

export const auditRoutes = new Elysia()
  .use(authGuard)
  .get("/api/audit", async ({ query }) => {
    const page = query.page ? parseInt(query.page as string) : 1;
    const pageSize = query.pageSize ? parseInt(query.pageSize as string) : 50;
    return await listAuditLogs(page, pageSize);
  });
