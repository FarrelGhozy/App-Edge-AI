import { describe, it, expect } from "bun:test";

// ---------------------------------------------------------------------------
// Day-of-week helpers used in attendance rules / schedules
// ---------------------------------------------------------------------------

function getTodayDayOfWeek(): number {
  return new Date().getDay();
}

function formatDateISO(date: Date): string {
  return date.toISOString().split("T")[0];
}

function isWithinTime(start: string, end: string, check: Date): boolean {
  const [sh, sm] = start.split(":").map(Number);
  const [eh, em] = end.split(":").map(Number);
  const nowH = check.getHours();
  const nowM = check.getMinutes();

  const startMin = sh * 60 + sm;
  const endMin = eh * 60 + em;
  const nowMin = nowH * 60 + nowM;

  return nowMin >= startMin && nowMin <= endMin;
}

describe("time helpers", () => {

  describe("getTodayDayOfWeek", () => {
    it("should return a value between 0 and 6", () => {
      const dow = getTodayDayOfWeek();
      expect(dow).toBeGreaterThanOrEqual(0);
      expect(dow).toBeLessThanOrEqual(6);
    });
  });

  describe("formatDateISO", () => {
    it("should format date as YYYY-MM-DD", () => {
      const d = new Date("2025-07-15T10:30:00Z");
      expect(formatDateISO(d)).toBe("2025-07-15");
    });

    it("should pad month and day", () => {
      const d = new Date("2025-01-05T00:00:00Z");
      expect(formatDateISO(d)).toBe("2025-01-05");
    });
  });

  describe("isWithinTime", () => {
    it("should return true for time within range", () => {
      const check = new Date("2025-07-15T10:30:00Z");
      const result = isWithinTime("08:00", "17:00", check);
      expect(result).toBe(true);
    });

    it("should return false for time before range", () => {
      const check = new Date("2025-07-15T07:00:00Z");
      const result = isWithinTime("08:00", "17:00", check);
      expect(result).toBe(false);
    });

    it("should return false for time after range", () => {
      const check = new Date("2025-07-15T18:00:00Z");
      const result = isWithinTime("08:00", "17:00", check);
      expect(result).toBe(false);
    });

    it("should handle exact start time", () => {
      const check = new Date("2025-07-15T08:00:00Z");
      const result = isWithinTime("08:00", "17:00", check);
      expect(result).toBe(true);
    });

    it("should handle exact end time", () => {
      const check = new Date("2025-07-15T17:00:00Z");
      const result = isWithinTime("08:00", "17:00", check);
      expect(result).toBe(true);
    });
  });
});

// ---------------------------------------------------------------------------
// Vector serialization (used in sync & face upload)
// ---------------------------------------------------------------------------

describe("vector helpers", () => {
  it("should serialize vector array to PostgreSQL format", () => {
    const vec = [0.1, 0.2, 0.3, 0.4, 0.5];
    const vecStr = `[${vec.join(",")}]`;
    expect(vecStr).toBe("[0.1,0.2,0.3,0.4,0.5]");
  });

  it("should handle negative values in vector", () => {
    const vec = [-0.1, 0.5, -0.3];
    const vecStr = `[${vec.join(",")}]`;
    expect(vecStr).toBe("[-0.1,0.5,-0.3]");
  });

  it("should parse vector string back to array", () => {
    const str = "[0.1,0.2,0.3]";
    const parsed = str.replace(/[\[\]]/g, "").split(",").map(Number);
    expect(parsed).toEqual([0.1, 0.2, 0.3]);
  });

  it("should handle 192-dimension string", () => {
    const vec = new Array(192).fill(0.1);
    const str = `[${vec.join(",")}]`;
    expect(str.startsWith("[0.1,0.1,")).toBe(true);
    expect(str.endsWith(",0.1]")).toBe(true);
    expect(str.split(",").length).toBe(192);
  });

  it("should handle 512-dimension string", () => {
    const vec = new Array(512).fill(0.05);
    const str = `[${vec.join(",")}]`;
    const parts = str.split(",");
    expect(parts.length).toBe(512);
  });
});
