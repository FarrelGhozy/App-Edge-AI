import prisma from "./prisma";

interface SseClient {
  id: string;
  send: (event: string, data: unknown) => void;
  close: () => void;
}

const clients = new Map<string, SseClient>();
let clientIdCounter = 0;
const encoder = new TextEncoder();

export function addClient(controller: ReadableStreamDefaultController): SseClient {
  const id = `client_${++clientIdCounter}`;

  const client: SseClient = {
    id,
    send(event: string, data: unknown) {
      try {
        const msg = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
        controller.enqueue(encoder.encode(msg));
      } catch {
        clients.delete(id);
      }
    },
    close() {
      clients.delete(id);
    }
  };

  clients.set(id, client);
  client.send("connected", { id });

  return client;
}

export function emitToAdmins(event: string, data: unknown) {
  for (const [, client] of clients) {
    client.send(event, data);
  }
}

export async function notifyDevicesChange(requestedBy?: string) {
  const activeDevices = await prisma.device.findMany({
    where: { isActive: true },
    select: { deviceId: true }
  });
  for (const device of activeDevices) {
    await prisma.syncRequest.create({
      data: {
        deviceId: device.deviceId,
        requestedById: requestedBy ?? null
      }
    });
  }
}
