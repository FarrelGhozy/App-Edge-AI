import { Elysia } from "elysia";
import { loginSchema, loginUser } from "../services/auth";

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
