import { jwt } from "@elysiajs/jwt";

/**
 * Plugin JWT singleton — dipakai index.ts DAN route module standalone
 * (auth, guards) supaya TypeScript tahu `jwt` ada di context (#62 CI).
 * Bun load .env sebelum eksekusi, jadi JWT_SECRET sudah tersedia saat
 * import; index.ts tetap fail-fast jika <16 char.
 */
export const jwtPlugin = jwt({
  name: "jwt",
  secret: process.env.JWT_SECRET as string,
  exp: "24h"
});
