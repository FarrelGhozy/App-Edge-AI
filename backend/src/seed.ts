import { PrismaClient } from "@prisma/client";
import bcrypt from "bcryptjs";

const prisma = new PrismaClient();

async function main() {
  console.log("🌱 Seeding database...");

  const admin = await prisma.admin.upsert({
    where: { username: "admin" },
    update: {},
    create: {
      username: "admin",
      passwordHash: await bcrypt.hash("admin123", 10),
      displayName: "Admin FaceGate",
      role: "superadmin"
    }
  });
  console.log(`   Admin: ${admin.username} (password: admin123)`);

  const deviceAccount = await prisma.admin.upsert({
    where: { username: "kiosk-gate1" },
    update: {},
    create: {
      username: "kiosk-gate1",
      passwordHash: await bcrypt.hash("facegate-kiosk-2024", 10),
      displayName: "Kiosk Gate 1",
      role: "device"
    }
  });
  console.log(`   Kiosk Admin: ${deviceAccount.username} (password: facegate-kiosk-2024)`);

  await prisma.device.upsert({
    where: { deviceId: "kiosk-gate1" },
    update: {},
    create: {
      deviceId: "kiosk-gate1",
      username: "kiosk-gate1",
      passwordHash: await bcrypt.hash("facegate-kiosk-2024", 10),
      name: "Kiosk Gate 1",
      location: "Main Gate",
      isActive: true
    }
  });
  console.log(`   Device: kiosk-gate1 (Kiosk Gate 1)`);

  const rules = [
    { dayOfWeek: 0, startTime: "22:00", endTime: "05:00", label: "Minggu Malam" },
    { dayOfWeek: 1, startTime: "22:00", endTime: "05:00", label: "Senin Malam" },
    { dayOfWeek: 2, startTime: "22:00", endTime: "05:00", label: "Selasa Malam" },
    { dayOfWeek: 3, startTime: "22:00", endTime: "05:00", label: "Rabu Malam" },
    { dayOfWeek: 4, startTime: "22:00", endTime: "05:00", label: "Kamis Malam" },
    { dayOfWeek: 5, startTime: "22:00", endTime: "05:00", label: "Jumat Malam" },
    { dayOfWeek: 6, startTime: "22:00", endTime: "05:00", label: "Sabtu Malam" },
  ];

  for (const rule of rules) {
    await prisma.campusRule.upsert({
      where: { id: `default-${rule.dayOfWeek}` },
      update: {},
      create: {
        id: `default-${rule.dayOfWeek}`,
        dayOfWeek: rule.dayOfWeek,
        startTime: rule.startTime,
        endTime: rule.endTime,
        isRestricted: true,
        appliesToAll: true,
        priority: 0
      }
    });
  }
  console.log(`   Created ${rules.length} campus rules`);

  const settings = [
    { key: "face_match_threshold", value: "0.80", description: "Adaptive threshold (min) — CONFIDENT ≥ 0.85, MEDIUM ≥ 0.80, WEAK ≥ 0.70" },
    { key: "confident_threshold", value: "0.85", description: "CONFIDENT match threshold" },
    { key: "medium_threshold", value: "0.80", description: "MEDIUM match threshold" },
    { key: "weak_threshold", value: "0.70", description: "WEAK match threshold" },
    { key: "confident_gap", value: "0.05", description: "Minimum gap for CONFIDENT decision" },
    { key: "medium_gap", value: "0.03", description: "Minimum gap for MEDIUM decision" },
    { key: "max_daily_duration_ms", value: "28800000", description: "Max 8 jam di luar per hari" },
    { key: "max_permit_per_month", value: "10", description: "Maks izin harian per bulan" },
    { key: "auto_permit_hours", value: "4", description: "Maks jam izin harian" },
    { key: "kiosk_poll_interval_minutes", value: "10", description: "Polling interval kiosk" },
    { key: "operational_start", value: "06:00", description: "Jam buka kiosk" },
    { key: "operational_end", value: "21:00", description: "Jam tutup kiosk" },
    { key: "sync_poll_interval_seconds", value: "10", description: "Polling sync request interval" },
  ];

  for (const setting of settings) {
    await prisma.globalSetting.upsert({
      where: { key: setting.key },
      update: {},
      create: setting
    });
  }
  console.log(`   Created ${settings.length} global settings`);

  console.log("✅ Seed complete!");
}

main()
  .catch((e) => {
    console.error("❌ Seed failed:", e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
