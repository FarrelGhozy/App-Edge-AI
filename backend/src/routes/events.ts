import { Elysia } from "elysia";
import { addClient } from "../services/events";
import { authGuard } from "../guards/auth";

export const eventsRoutes = new Elysia()
  .use(authGuard())
  .get("/api/events/stream", ({ admin }) => {
    let client: ReturnType<typeof addClient> | null = null;
    let heartbeat: ReturnType<typeof setInterval> | null = null;

    // #131: scope koneksi → "admin:<adminId>" atau "device:<deviceId>" agar
    // emitToAdmins bisa mem-filter event yang relevan (kiosk tak menerima
    // data admin, admin tak menerima heartbeat device lain).
    const adminId = admin?.id ?? null;
    const scope = admin?.role === "device"
      ? `device:${adminId}`
      : adminId ? `admin:${adminId}` : undefined;

    const stream = new ReadableStream({
      start(controller) {
        client = addClient(controller, scope);

        heartbeat = setInterval(() => {
          try {
            controller.enqueue(new TextEncoder().encode(":heartbeat\n\n"));
          } catch {
            if (heartbeat) clearInterval(heartbeat);
          }
        }, 30000);
      },
      cancel() {
        if (client) client.close();
        if (heartbeat) clearInterval(heartbeat);
      }
    });

    return new Response(stream, {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "X-Accel-Buffering": "no"
      }
    });
  });
