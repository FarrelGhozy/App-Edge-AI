/**
 * WIB timezone helpers (Asia/Jakarta, UTC+7, tanpa DST).
 *
 * #110: seluruh logika waktu/hari/rule sebelumnya dievaluasi dalam timezone
 * proses (container tanpa TZ -> Etc/UTC), sehingga jam malam, holiday, dan
 * batas "hari ini" salah untuk domain WIB. Helper ini menghitung day-of-week,
 * HH:MM, dan batas hari secara EKSPLISIT berbasis offset Asia/Jakarta sehingga
 * hasilnya deterministik TANPA bergantung pada timezone environment.
 *
 * Catatan: ini TIDAK mengganti nilai timestamp yang tersimpan (tetap UTC di
 * DB) — hanya memastikan interpretasi "jam berapa / hari apa sekarang" benar
 * menurut WIB.
 */

const WIB_OFFSET_MS = 7 * 60 * 60 * 1000; // UTC+7

/** Geser Date ke "jam dinding" (wall-clock) WIB. */
function toWIB(d: Date): Date {
  return new Date(d.getTime() + WIB_OFFSET_MS);
}

/** Day of week menurut WIB. 0 = Minggu ... 6 = Sabtu (konsisten getDay()). */
export function wibDayOfWeek(d: Date): number {
  return toWIB(d).getUTCDay();
}

/** "HH:MM" menurut WIB (jam lokal operator). */
export function wibTimeHMM(d: Date): string {
  const w = toWIB(d);
  const hh = String(w.getUTCHours()).padStart(2, "0");
  const mm = String(w.getUTCMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

/** Batas awal hari (00:00 WIB) untuk tanggal dari timestamp (UTC storage). */
export function wibDayStart(d: Date): Date {
  const w = toWIB(d);
  // Normalisasi ke tengah malam WIB lalu geser balik ke UTC storage.
  const startUtc = Date.UTC(w.getUTCFullYear(), w.getUTCMonth(), w.getUTCDate()) - WIB_OFFSET_MS;
  return new Date(startUtc);
}

/** Batas akhir hari (24:00 WIB) — exclusive upper bound. */
export function wibDayEndExclusive(d: Date): Date {
  const start = wibDayStart(d);
  return new Date(start.getTime() + 24 * 60 * 60 * 1000);
}

/** Tanggal "YYYY-MM-DD" menurut WIB. */
export function wibDateString(d: Date): string {
  const w = toWIB(d);
  const mm = String(w.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(w.getUTCDate()).padStart(2, "0");
  return `${w.getUTCFullYear()}-${mm}-${dd}`;
}

/** Deteksi tanggal yang sama menurut WIB. */
export function isSameWibDate(a: Date, b: Date): boolean {
  return wibDateString(a) === wibDateString(b);
}

/** Awal bulan (hari ke-1 00:00 WIB) utk tahun/bulan WIB. */
export function wibMonthStart(year: number, month: number): Date {
  return new Date(Date.UTC(year, month - 1, 1) - WIB_OFFSET_MS);
}

/** Akhir bulan (exclusive — hari pertama bulan berikutnya 00:00 WIB). */
export function wibMonthEndExclusive(year: number, month: number): Date {
  return new Date(Date.UTC(year, month, 1) - WIB_OFFSET_MS);
}

/** "YYYY-MM-DD" utk tanggal dari string, diinterpretasikan sebagai hari WIB
 *  (00:00 WIB). Dipakai report harian supaya deterministik tanpa TZ proses. */
export function wibParseDate(dateStr: string): { start: Date; endExclusive: Date } {
  const [y, m, d] = dateStr.split("-").map(Number);
  const start = new Date(Date.UTC(y, m - 1, d) - WIB_OFFSET_MS);
  return { start, endExclusive: new Date(start.getTime() + 24 * 60 * 60 * 1000) };
}