import { describe, expect, it } from "vitest";
import ical from "node-ical";
import { expandEvents } from "../src/calendar.ts";

const ICS = `BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//test//EN
X-WR-CALNAME:Family
BEGIN:VEVENT
UID:weekly-1
DTSTART:20261102T180000Z
DTEND:20261102T190000Z
RRULE:FREQ=WEEKLY;BYDAY=MO
EXDATE:20261116T180000Z
SUMMARY:Football
END:VEVENT
BEGIN:VEVENT
UID:weekly-1
RECURRENCE-ID:20261109T180000Z
DTSTART:20261109T190000Z
DTEND:20261109T200000Z
SUMMARY:Football (late)
END:VEVENT
BEGIN:VEVENT
UID:allday-1
DTSTART;VALUE=DATE:20261110
DTEND;VALUE=DATE:20261111
SUMMARY:Bin day
END:VEVENT
BEGIN:VEVENT
UID:single-1
DTSTART:20261112T090000Z
DTEND:20261112T100000Z
SUMMARY:Dentist
LOCATION:High St
END:VEVENT
BEGIN:VEVENT
UID:old-1
DTSTART:20261001T090000Z
DTEND:20261001T100000Z
SUMMARY:Long ago
END:VEVENT
BEGIN:VEVENT
UID:cancel-1
DTSTART:20261111T090000Z
DTEND:20261111T100000Z
STATUS:CANCELLED
SUMMARY:Cancelled thing
END:VEVENT
END:VCALENDAR`;

describe("expandEvents", () => {
  const data = ical.sync.parseICS(ICS);
  const from = Date.parse("2026-11-08T00:00:00Z");
  const to = Date.parse("2026-11-24T00:00:00Z");
  const events = expandEvents(data, from, to);
  const titles = events.map((e) => e.title);

  it("expands weekly recurrences, applying overrides and exdates", () => {
    const football = events.filter((e) => e.title.startsWith("Football"));
    expect(football.map((e) => new Date(e.start).toISOString())).toEqual([
      "2026-11-09T19:00:00.000Z", // moved by RECURRENCE-ID override
      "2026-11-23T18:00:00.000Z", // 16th excluded
    ]);
    expect(football[0].title).toBe("Football (late)");
  });

  it("keeps all-day events as all-day", () => {
    const bin = events.find((e) => e.title === "Bin day")!;
    expect(bin.allDay).toBe(true);
    expect(bin.end - bin.start).toBe(86_400_000);
  });

  it("drops events outside the window and cancelled ones", () => {
    expect(titles).not.toContain("Long ago");
    expect(titles).not.toContain("Cancelled thing");
  });

  it("keeps location and calendar name, sorted by start", () => {
    const dentist = events.find((e) => e.title === "Dentist")!;
    expect(dentist.location).toBe("High St");
    expect(dentist.calendar).toBe("Family");
    const starts = events.map((e) => e.start);
    expect([...starts].sort((a, b) => a - b)).toEqual(starts);
  });
});
