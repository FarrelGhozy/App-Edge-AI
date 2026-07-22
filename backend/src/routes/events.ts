import { Elysia } from "elysia";

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
  .get("/api/events/stream", ({ set }) => {
    set.headers["Content-Type"] = "text/event-stream";
    set.headers["Cache-Control"] = "no-cache";
    set.headers["Connection"] = "keep-alive";

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      start(controller) {
        // Send initial connection event
        controller.enqueue(encoder.encode("event: connected\ndata: {}\n\n"));

        const client = {
          send: (event: string, data: unknown) => {
            const msg = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
            controller.enqueue(encoder.encode(msg));
          },
          close: () => {
            controller.close();
          }
        };

        sseClients.add(client);

        // Heartbeat every 30 seconds
        const heartbeat = setInterval(() => {
          try {
            controller.enqueue(encoder.encode(": heartbeat\n\n"));
          } catch {
            clearInterval(heartbeat);
          }
        }, 30000);

        // Cleanup on client disconnect
        const reader = stream.getReader();
        reader.closed.then(() => {
          sseClients.delete(client);
          clearInterval(heartbeat);
        });
      }
    });

    return stream;
  })
  .post("/api/events/trigger-change", ({ body }) => {
    const { deviceId, type } = body as { deviceId?: string; type: string };
    broadcastEvent("data-change", { deviceId, type, timestamp: new Date().toISOString() });
    return { success: true };
  });
