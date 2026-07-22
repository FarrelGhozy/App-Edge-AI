import { Elysia } from "elysia";
import { authGuard } from "../guards/auth";

// SSE client connections
const sseClients = new Set<{
  send: (event: string, data: unknown) => void;
  close: () => void;
}>();

export function broadcastEvent(event: string, data: unknown) {
  for (const client of sseClients) {
    try {
      client.send(event, data);
    } catch {
      sseClients.delete(client);
    }
  }
}

export const eventRoutes = new Elysia()
  .use(authGuard)
  .get("/api/events/stream", ({ set, admin }) => {
    set.headers["Content-Type"] = "text/event-stream";
    set.headers["Cache-Control"] = "no-cache";
    set.headers["Connection"] = "keep-alive";
    set.headers["X-Accel-Buffering"] = "no";

    const encoder = new TextEncoder();
    let closed = false;
    const stream = new ReadableStream({
      start(controller) {
        // Send initial connection event
        controller.enqueue(encoder.encode("event: connected\ndata: {}\n\n"));

        const client = {
          send: (event: string, data: unknown) => {
            if (closed) return;
            const msg = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
            try {
              controller.enqueue(encoder.encode(msg));
            } catch {
              closed = true;
              sseClients.delete(client);
            }
          },
          close: () => {
            closed = true;
            try { controller.close(); } catch { /* already closed */ }
          }
        };

        sseClients.add(client);

        // Heartbeat every 30 seconds
        const heartbeat = setInterval(() => {
          if (closed) {
            clearInterval(heartbeat);
            return;
          }
          try {
            controller.enqueue(encoder.encode(": heartbeat\n\n"));
          } catch {
            closed = true;
            sseClients.delete(client);
            clearInterval(heartbeat);
          }
        }, 30000);

        // Cleanup on client disconnect
        const reader = stream.getReader();
        reader.closed.then(() => {
          closed = true;
          sseClients.delete(client);
          clearInterval(heartbeat);
        });
      }
    });

    return stream;
  });
