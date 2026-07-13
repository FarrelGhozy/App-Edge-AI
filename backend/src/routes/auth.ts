import { Elysia } from "elysia";
import { loginSchema, loginUser } from "../services/auth";
import prisma from "../services/prisma";

export const authRoutes = new Elysia()
  .post("/api/auth/login", async ({ body, jwt }) => {
    const { username, password } = body;
    const admin = await loginUser(username, password);
    if (!admin) {
      return new Response(JSON.stringify({ success: false, error: "Invalid credentials" }), {
        status: 401,
        headers: { "Content-Type": "application/json" }
      });
    }

    const token = await jwt.sign({
      id: admin.id,
      username: admin.username,
      role: admin.role
    });

    return {
      success: true,
      data: {
        token,
        admin: {
          id: admin.id,
          username: admin.username,
          displayName: admin.displayName,
          role: admin.role
        }
      }
    };
  }, { body: loginSchema })
  .post("/api/auth/device-register", async ({ body }) => {
    // Admin-only: register a new device account for kiosk
    const { adminUsername, adminPassword, deviceUsername, devicePassword } = body as {
      adminUsername: string;
      adminPassword: string;
      deviceUsername: string;
      devicePassword: string;
    };
    const admin = await loginUser(adminUsername, adminPassword);
    if (!admin || (admin.role !== "admin" && admin.role !== "superadmin")) {
      return new Response(JSON.stringify({ success: false, error: "Admin access required" }), {
        status: 403,
        headers: { "Content-Type": "application/json" }
      });
    }
    const exists = await prisma.admin.findUnique({ where: { username: deviceUsername } });
    if (exists) {
      return { success: true, message: "Device account already exists", username: deviceUsername };
    }
    const bcrypt = await import("bcryptjs");
    const hash = await bcrypt.hash(devicePassword, 10);
    await prisma.admin.create({
      data: {
        username: deviceUsername,
        passwordHash: hash,
        displayName: deviceUsername,
        role: "device"
      }
    });
    return { success: true, message: "Device account created", username: deviceUsername };
  })
  .post("/api/auth/refresh", async ({ body, jwt }) => {
    const { token } = body as { token: string };
    const payload = await jwt.verify(token);
    if (!payload) {
      return new Response(JSON.stringify({ success: false, error: "Invalid token" }), {
        status: 401,
        headers: { "Content-Type": "application/json" }
      });
    }

    const newToken = await jwt.sign({
      id: payload.id,
      username: payload.username,
      role: payload.role
    });

    return { success: true, data: { token: newToken } };
  });
