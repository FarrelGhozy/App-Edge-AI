import { Elysia } from "elysia";
import { addClient } from "../services/events";
import { authGuard } from "../guards/auth";

export const eventsRoutes = new Elysia()
  .use(authGuard())
  .get("/api/events/stream", () => {
    let client: ReturnType<typeof addClient> | null = null;
    let heartbeat: ReturnType<typeof setInterval> | null = null;

    const stream = new ReadableStream({
      start(controller) {
        client = addClient(controller);

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
