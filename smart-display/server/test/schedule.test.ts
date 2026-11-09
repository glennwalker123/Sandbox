import { describe, expect, it } from "vitest";
import { describeSchedule, loadConfig, parseSchedule } from "../src/config.ts";
import { isDue } from "../src/art/director.ts";

const t = (d: number, h: number, m = 0) => new Date(2026, 10, d, h, m).getTime();

describe("schedule", () => {
  it("parses daily and interval schedules, with the legacy alias and a daily default", () => {
    expect(parseSchedule("daily@07:30")).toEqual({ kind: "daily", hour: 7, minute: 30 });
    expect(parseSchedule("every:180")).toEqual({ kind: "every", minutes: 180 });
    expect(parseSchedule(undefined, "60")).toEqual({ kind: "every", minutes: 60 });
    expect(parseSchedule(undefined)).toEqual({ kind: "daily", hour: 7, minute: 0 });
    expect(parseSchedule("daily@25:00")).toEqual({ kind: "daily", hour: 7, minute: 0 });
    expect(describeSchedule(parseSchedule("daily@7:05"))).toBe("daily at 07:05");
  });

  it("daily: due once after the slot, and catches up a missed slot once", () => {
    const daily = parseSchedule("daily@07:00");
    expect(isDue(daily, t(9, 6, 59), t(8, 7, 1))).toBe(false); // today's slot not reached yet
    expect(isDue(daily, t(9, 7, 0), t(8, 7, 1))).toBe(true);
    expect(isDue(daily, t(9, 15), t(9, 7, 2))).toBe(false); // already made today
    expect(isDue(daily, t(11, 3), t(8, 7))).toBe(true); // Pi was off: catch up...
    expect(isDue(daily, t(11, 4), t(11, 3))).toBe(false); // ...just once
    expect(isDue(daily, t(9, 8), 0)).toBe(true); // fresh install
  });

  it("every: due after the interval", () => {
    const every = parseSchedule("every:180");
    expect(isDue(every, t(9, 12), t(9, 10))).toBe(false);
    expect(isDue(every, t(9, 13), t(9, 10))).toBe(true);
  });

  it("config defaults: daily, 3 a day, no budget, cost by provider and quality", () => {
    const c = loadConfig({});
    expect(c.art.schedule).toEqual({ kind: "daily", hour: 7, minute: 0 });
    expect(c.art).toMatchObject({ maxPerDay: 3, monthlyBudget: null, costPerImage: 0.04 });
    expect(c.models).toMatchObject({ geminiImage: "gemini-2.5-flash-image", openaiImage: "gpt-image-2", openaiQuality: "medium" });
    expect(loadConfig({ IMAGE_PROVIDER: "openai", OPENAI_IMAGE_QUALITY: "high" }).art.costPerImage).toBe(0.17);
    expect(loadConfig({ ART_MONTHLY_BUDGET: "5", ART_COST_PER_IMAGE: "0.03" }).art).toMatchObject({ monthlyBudget: 5, costPerImage: 0.03 });
  });
});
