import { describe, expect, it } from "vitest";
import { buildContext, dayPartFor, fallbackPrompt, parsePromptJson, seasonFor } from "../src/art/prompt.ts";

const H = 3_600_000;

describe("seasonFor", () => {
  it("flips for the southern hemisphere", () => {
    const nov = new Date(2026, 10, 9);
    expect(seasonFor(nov, 51.5)).toBe("autumn");
    expect(seasonFor(nov, -33.9)).toBe("spring");
    expect(seasonFor(new Date(2026, 0, 5), 51.5)).toBe("winter");
  });
});

describe("dayPartFor", () => {
  const sunrise = Date.parse("2026-11-09T07:10:00Z");
  const sunset = Date.parse("2026-11-09T16:20:00Z");
  it("uses sunrise/sunset when available", () => {
    expect(dayPartFor(sunrise + 10 * 60_000, sunrise, sunset)).toBe("dawn");
    expect(dayPartFor(sunset - 30 * 60_000, sunrise, sunset)).toBe("golden hour");
    expect(dayPartFor(sunset + 2 * H, sunrise, sunset)).toBe("evening");
    expect(dayPartFor(sunset + 5 * H, sunrise, sunset)).toBe("night");
    expect(dayPartFor(sunrise - 3 * H, sunrise, sunset)).toBe("night");
  });
});

describe("buildContext", () => {
  it("only includes today's events and respects the calendar opt-out", () => {
    const now = new Date(2026, 10, 9, 10).getTime();
    const events = [
      { id: "a", title: "Dinner", start: now + 8 * H, end: now + 10 * H, allDay: false },
      { id: "b", title: "Tomorrow thing", start: now + 30 * H, end: now + 31 * H, allDay: false },
    ];
    const ctx = buildContext({ now, latitude: 51, weather: null, events, useCalendar: true });
    expect(ctx.events).toEqual(["Dinner"]);
    expect(buildContext({ now, latitude: 51, weather: null, events, useCalendar: false }).events).toEqual([]);
  });
});

describe("prompts", () => {
  it("builds a sensible fallback prompt", () => {
    const p = fallbackPrompt({ date: "x", season: "autumn", dayPart: "evening", weather: "light rain, 9°C", events: [] }, "oil painting");
    expect(p).toBe("A autumn landscape at evening, with light rain weather, oil painting, no text, no lettering, no logos");
  });

  it("parses JSON even with surrounding prose", () => {
    expect(parsePromptJson('Here: {"title":"Wet Lanterns","prompt":"A rain-soaked street at dusk, lanterns glowing"}')).toEqual({
      title: "Wet Lanterns",
      prompt: "A rain-soaked street at dusk, lanterns glowing",
    });
    expect(parsePromptJson("no json")).toBeNull();
    expect(parsePromptJson('{"prompt": "short"}')).toBeNull();
  });
});
