import prisma from "./prisma";

interface SseClient {
  id: string;
  /** Identitas client: "admin:<adminId>" utk admin, "device:<deviceId>" utk kiosk. */
  scope?: string;
  send: (event: string, data: unknown) => void;
  close: () => void;
}

const clients = new Map<string, SseClient>();
let clientIdCounter = 0;
const encoder = new TextEncoder();

export function addClient(controller: ReadableStreamDefaultController, scope?: string): SseClient {
  const id = `client_${++clientIdCounter}`;

  const client: SseClient = {
    id,
    scope,
    send(event: string, data: unknown) {
      try {
        const msg = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
        controller.enqueue(encoder.encode(msg));
      } catch (e) {
        // #102: client terputus — lepas & log ringkas (bukan silent swallow)
        clients.delete(id);
        console.error(`[sse] client ${id} terputus saat kirim event ${event}:`, e);
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

export function emitToAdmins(event: string, data: unknown, targetScope?: string) {
  for (const [, client] of clients) {
    // #131: SSE bocor — event di-broadcast ke SEMUA koneksi termasuk token
    // device. Bila event spesifik admin (violation, permit), kirim hanya ke
    // client dengan scope admin. Bila targetScope diberikan, filter ketat.
    if (targetScope) {
      if (client.scope === targetScope) client.send(event, data);
      continue;
    }
    if (event === "scan_realtime" || event === "notify") {
      // scan & notif relevan utk admin; kiosk tak butuh data admin.
      if (!client.scope || client.scope.startsWith("admin:")) {
        client.send(event, data);
      }
    } else {
      client.send(event, data);
    }
  }
}

export async function notifyDevicesChange(requestedBy?: string) {
  const activeDevices = await prisma.device.findMany({
    where: { isActive: true },
    select: { deviceId: true }
  });
  for (const device of activeDevices) {
    // #131: dedup — maksimal 1 request PENDING per device (partial unique
    // index sync_requests_pending_device menjaminnya di level DB). Mutasi
    // cepat (mis. import 100 santri → 100x notify) sebelumnya menumpuk baris
    // sync_request tak terbatas. Update saja bila sudah ada pending.
    const existing = await prisma.syncRequest.findFirst({
      where: { deviceId: device.deviceId, isProcessed: false }
    });
    if (existing) {
      await prisma.syncRequest.update({
        where: { id: existing.id },
        data: { requestedAt: new Date(), requestedById: requestedBy ?? existing.requestedById }
      });
      continue;
    }
    try {
      await prisma.syncRequest.create({
        data: {
          deviceId: device.deviceId,
          requestedById: requestedBy ?? null
        }
      });
    } catch (e) {
      // P2002 (partial index) = race dengan create paralel — pending sudah ada,
      // abaikan; informasi "ada data baru" tetap tersampaikan oleh baris tsb.
      if (
        e instanceof Error &&
        "code" in e &&
        (e as { code: string }).code === "P2002"
      ) {
        continue;
      }
      throw e;
    }
  }
}
