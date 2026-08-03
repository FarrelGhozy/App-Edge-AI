import prisma from "./prisma";

export interface AuditEntry {
  adminId: string;
  action: string;       // CREATE, UPDATE, DELETE, APPROVE, REJECT, LOGIN...
  entityType: string;   // USERS, STUDENTS, DEVICES, RULES, PERMITS, SETTINGS
  entityId?: string;
  details?: string;
}

/**
 * #63: tulis ke AuditLog. Dipanggil dari route admin setelah mutasi berhasil.
 * Gagal diam-diam (jangan blokir operasi utama hanya karena log gagal).
 */
export async function writeAudit(entry: AuditEntry): Promise<void> {
  try {
    await prisma.auditLog.create({
      data: {
        adminId: entry.adminId,
        action: entry.action,
        entityType: entry.entityType,
        entityId: entry.entityId ?? "",
        details: entry.details ?? null
      }
    });
  } catch (_) {
    // Audit logging tidak boleh menggagalkan operasi utama
  }
}

type Actor = { id?: string; username?: string; role?: string } | null | undefined;

/**
 * Helper yang mengisi adminId dari aktor, lalu menulis audit.
 */
export async function audit(actor: Actor, entry: Omit<AuditEntry, "adminId">): Promise<void> {
  if (!actor?.id) return;
  await writeAudit({ ...entry, adminId: actor.id });
}