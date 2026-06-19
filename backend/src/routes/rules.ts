import { Elysia } from "elysia";
import { listRules, getSettings } from "../services/rule";

export const ruleRoutes = new Elysia()
  .get("/api/rules", async () => {
    return await listRules();
  })
  .get("/api/settings", async () => {
    return await getSettings();
  });
