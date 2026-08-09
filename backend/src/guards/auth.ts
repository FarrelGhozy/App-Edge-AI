import { Elysia } from "elysia";
import { jwtPlugin } from "../plugins/jwt";

/**
 * Auth guard — factory: `authGuard()` cek token valid (401),
 * `authGuard(["admin","superadmin"])` juga cek role (403).
 * Dipakai di semua route; role ditaruh DI GUARD INI supaya tidak
 * bergantung pada store sharing antar plugin (#70 RBAC).
 */
export const authGuard = (...allowedRoles: string[]) =>
  new Elysia()
    .use(jwtPlugin)
    .derive({ as: "scoped" }, async ({ jwt, request }) => {
      const authHeader = request.headers.get("authorization");
      if (!authHeader || !authHeader.startsWith("Bearer ")) {
        return { admin: null };
      }
      const token = authHeader.slice(7);
      // #62: jwt.verify ber-return AllowClaimValue — cast ke bentuk payload
      // aplikasi agar admin ter-typed string (bukan union).
      const payload = (await jwt.verify(token)) as
        | { id: string; username: string; role: string }
        | null;
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
      // `authGuard()` = tanpa role check; `authGuard("admin","superadmin")` = RBAC (#70)
      if (allowedRoles.length > 0 && !allowedRoles.includes(admin.role)) {
        return new Response(
          JSON.stringify({ success: false, error: "Forbidden: insufficient role" }),
          { status: 403, headers: { "Content-Type": "application/json" } }
        );
      }
    });
