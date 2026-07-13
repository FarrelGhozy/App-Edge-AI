import { Elysia } from "elysia";

export const authGuard = new Elysia()
  .derive({ as: "scoped" }, async (context: any) => {
    const authHeader = context.request.headers.get("authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return { admin: null };
    }
    const token = authHeader.slice(7);
    const payload = await context.jwt.verify(token);
    if (!payload) {
      return { admin: null };
    }
    return { admin: { id: payload.id as string, username: payload.username as string, role: payload.role as string } };
  })
  .onBeforeHandle({ as: "scoped" }, (context: any) => {
    if (!context.admin) {
      return new Response(JSON.stringify({ success: false, error: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json" }
      });
    }
  });
