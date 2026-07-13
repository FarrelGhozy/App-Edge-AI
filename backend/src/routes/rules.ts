import { Elysia } from "elysia";
import { listRules, getSettings } from "../services/rule";
import { authGuard } from "../guards/auth";

export const ruleRoutes = new Elysia()
  .use(authGuard)
  .get("/api/rules", async () => {
    return await listRules();
  })
  .get("/api/settings", async () => {
    return await getSettings();
  });
