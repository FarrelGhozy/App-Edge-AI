import { Elysia } from "elysia";

export const authGuard = new Elysia()
  .derive({ as: "scoped" }, async ({ jwt, request }) => {
    const authHeader = request.headers.get("authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return { admin: null };
    }
    const token = authHeader.slice(7);
    const payload = await jwt.verify(token);
    if (!payload) {
      return { admin: null };
    }
    return { admin: { id: payload.id, username: payload.username, role: payload.role } };
  })
  .onBeforeHandle({ as: "scoped" }, ({ admin }) => {
    if (!admin) {
      return new Response(JSON.stringify({ success: false, error: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json" }
      });
    }
  });
