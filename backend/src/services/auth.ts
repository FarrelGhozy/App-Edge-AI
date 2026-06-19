import { t } from "elysia";
import bcrypt from "bcryptjs";
import prisma from "./prisma";

export const loginSchema = t.Object({
  username: t.String(),
  password: t.String()
});

export async function loginUser(username: string, password: string) {
  const admin = await prisma.admin.findUnique({ where: { username } });
  if (!admin) return null;

  const valid = await bcrypt.compare(password, admin.passwordHash);
  if (!valid) return null;

  return {
    id: admin.id,
    username: admin.username,
    displayName: admin.displayName,
    role: admin.role
  };
}
