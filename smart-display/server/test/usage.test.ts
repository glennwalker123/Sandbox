import { describe, expect, it } from "vitest";
import { UsageLog } from "../src/art/usage.ts";

const at = (y: number, m: number, d: number, h = 12) => new Date(y, m - 1, d, h).getTime();
const limits = { maxPerDay: 2, monthlyBudget: 0.1 as number | null, costPerImage: 0.04 };

describe("UsageLog", () => {
  it("enforces the daily count and resets the next day", async () => {
    const log = new UsageLog(null);
    const noBudget = { ...limits, monthlyBudget: null };
    await log.record({ at: at(2026, 11, 9, 7), model: "m", cost: 0.04 });
    expect(log.blockedReason(at(2026, 11, 9), noBudget)).toBeNull();
    await log.record({ at: at(2026, 11, 9, 9), model: "m", cost: 0.04 });
    expect(log.blockedReason(at(2026, 11, 9), noBudget)).toMatch(/Daily limit/);
    expect(log.blockedReason(at(2026, 11, 10), noBudget)).toBeNull();
  });

  it("stops before the next image would exceed the monthly budget, and resets next month", async () => {
    const log = new UsageLog(null);
    await log.record({ at: at(2026, 11, 1), model: "m", cost: 0.04 });
    expect(log.blockedReason(at(2026, 11, 2), limits)).toBeNull(); // 0.04 + 0.04 <= 0.10
    await log.record({ at: at(2026, 11, 2), model: "m", cost: 0.04 });
    expect(log.blockedReason(at(2026, 11, 3), limits)).toMatch(/Monthly budget/); // 0.08 + 0.04 > 0.10
    expect(log.blockedReason(at(2026, 12, 1), limits)).toBeNull();
    expect(log.summary(at(2026, 11, 3))).toEqual({ today: { count: 0, cost: 0 }, month: { count: 2, cost: 0.08 } });
  });
});
