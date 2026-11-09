import { describe, expect, it } from "vitest";
import { isQuietHour, loadConfig, parseCalendars, parseQuietHours } from "../src/config.ts";

describe("config", () => {
  it("parses labelled and webcal calendar URLs", () => {
    expect(
      parseCalendars("Family|webcal://calendar.google.com/a.ics, https://calendar.google.com/b.ics?x=1|2"),
    ).toEqual([
      { name: "Family", url: "https://calendar.google.com/a.ics" },
      { url: "https://calendar.google.com/b.ics?x=1|2" },
    ]);
  });

  it("handles quiet hours across midnight", () => {
    const q = parseQuietHours("23-6");
    expect(q).toEqual([23, 6]);
    expect(isQuietHour(23, q)).toBe(true);
    expect(isQuietHour(3, q)).toBe(true);
    expect(isQuietHour(6, q)).toBe(false);
    expect(isQuietHour(12, parseQuietHours("off"))).toBe(false);
  });

  it("mock mode forces mock art", () => {
    expect(loadConfig({ MOCK: "1", ART_SOURCE: "ai" }).art.mix).toBe("mock");
    expect(loadConfig({ ART_SOURCE: "alternate", IMAGE_PROVIDER: "openai" }).art).toMatchObject({ mix: "alternate", provider: "openai" });
  });

  it("parses orientation and UI scale with sane bounds", () => {
    expect(loadConfig({}).art.orientation).toBe("auto");
    expect(loadConfig({ ART_ORIENTATION: "portrait" }).art.orientation).toBe("portrait");
    expect(loadConfig({ ART_ORIENTATION: "sideways" }).art.orientation).toBe("auto");
    expect(loadConfig({ UI_SCALE: "1.4" }).uiScale).toBe(1.4);
    expect(loadConfig({ UI_SCALE: "9" }).uiScale).toBe(3);
    expect(loadConfig({ UI_SCALE: "abc" }).uiScale).toBe(1);
  });
});
