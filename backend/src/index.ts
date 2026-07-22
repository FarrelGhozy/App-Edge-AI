import { Elysia } from "elysia";
import { cors } from "@elysiajs/cors";
import { swagger } from "@elysiajs/swagger";
import { jwt } from "@elysiajs/jwt";
import { authRoutes } from "./routes/auth";
import { studentRoutes } from "./routes/students";
import { attendanceRoutes } from "./routes/attendance";
import { syncRoutes } from "./routes/sync";
import { ruleRoutes } from "./routes/rules";
import { deviceRoutes } from "./routes/devices";
import { permitRoutes } from "./routes/permits";
import { violationRoutes } from "./routes/violations";
import { notificationRoutes } from "./routes/notifications";
import { settingRoutes } from "./routes/settings";
import { reportRoutes } from "./routes/reports";
import { dashboardRoutes } from "./routes/dashboard";
import { holidayRoutes } from "./routes/holidays";
import { scheduleRoutes } from "./routes/schedules";
import { auditRoutes } from "./routes/audit";
import { eventRoutes } from "./routes/events";
import { scanMetricRoutes } from "./routes/scanMetrics";
import { metricRoutes } from "./routes/metrics";

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8150;
const JWT_SECRET = process.env.JWT_SECRET || "facegate-jwt-secret";

const app = new Elysia()
  .use(swagger({
    path: "/docs",
    documentation: {
      info: {
        title: "FaceGate API",
        version: "1.0.0",
        description: "Face recognition gate system API"
      }
    }
  }))
  .use(cors())
  .use(
    jwt({
      name: "jwt",
      secret: JWT_SECRET,
      exp: "24h"
    })
  )
  .get("/api/health", () => ({
    status: "ok",
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  }))
  .use(authRoutes)
  .use(studentRoutes)
  .use(attendanceRoutes)
  .use(syncRoutes)
  .use(ruleRoutes)
  .use(deviceRoutes)
  .use(permitRoutes)
  .use(violationRoutes)
  .use(notificationRoutes)
  .use(settingRoutes)
  .use(reportRoutes)
  .use(dashboardRoutes)
  .use(holidayRoutes)
  .use(scheduleRoutes)
  .use(auditRoutes)
  .use(eventRoutes)
  .use(scanMetricRoutes)
  .use(metricRoutes)
  .listen(PORT);

console.log(`🚀 FaceGate API running at http://localhost:${PORT}`);
console.log(`📖 Swagger docs at http://localhost:${PORT}/docs`);

export type App = typeof app;
