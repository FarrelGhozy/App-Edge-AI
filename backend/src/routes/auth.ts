import { Elysia } from "elysia";
import { loginSchema, loginUser, loginDevice } from "../services/auth";
import prisma from "../services/prisma";
import bcrypt from "bcryptjs";

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

  .post("/api/auth/device-login", async ({ body, jwt }) => {
    const { username, password } = body;
    const device = await loginDevice(username, password);
    if (!device) {
      return new Response(JSON.stringify({ success: false, error: "Invalid credentials" }), {
        status: 401,
        headers: { "Content-Type": "application/json" }
      });
    }

    const token = await jwt.sign({
      id: device.id,
      username: device.username,
      role: device.role
    });

    return {
      success: true,
      data: {
        token,
        admin: {
          id: device.id,
          username: device.username,
          displayName: device.displayName,
          role: device.role
        }
      }
    };
  }, { body: loginSchema })

  .post("/api/auth/device-register", async ({ body }) => {
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
    const exists = await prisma.device.findUnique({ where: { username: deviceUsername } });
    if (exists) {
      return { success: true, message: "Device account already exists", username: deviceUsername };
    }
    const hash = await bcrypt.hash(devicePassword, 10);
    await prisma.device.create({
      data: {
        deviceId: deviceUsername,
        username: deviceUsername,
        passwordHash: hash,
        name: deviceUsername,
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
