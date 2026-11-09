import ical, { type VEvent, type CalendarResponse } from "node-ical";
import type { CalendarEvent, CalendarData } from "../../shared/types.ts";
import type { CalendarSource, Config } from "./config.ts";


const DAY = 86_400_000;

function text(v: unknown): string {
  if (typeof v === "string") return v;
  if (v && typeof v === "object" && "val" in v) return String((v as { val: unknown }).val);
  return "";
}

function startOfLocalDay(t: number): number {
  const d = new Date(t);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/**
 * Turns parsed iCal data into concrete event instances overlapping
 * [windowStart, windowEnd). node-ical expands RRULEs and applies EXDATEs and
 * modified single occurrences (RECURRENCE-ID overrides) for us.
 */
export function expandEvents(
  data: CalendarResponse,
  windowStart: number,
  windowEnd: number,
  calendarName?: string,
): CalendarEvent[] {
  const out: CalendarEvent[] = [];
  const vcal = data.vcalendar as Record<string, unknown> | undefined;
  const calName = calendarName ?? (text(vcal?.["WR-CALNAME"]) || undefined);

  for (const comp of Object.values(data)) {
    if (!comp || (comp as { type?: string }).type !== "VEVENT") continue;
    const ev = comp as VEvent;
    if (!ev.start || ev.status === "CANCELLED") continue;
    // Overrides are applied through their parent series.
    if (ev.recurrenceid && !ev.rrule) continue;

    const instances = ical.expandRecurringEvent(ev, {
      from: new Date(windowStart),
      to: new Date(windowEnd),
      expandOngoing: true,
    });
    for (const inst of instances) {
      if (inst.event.status === "CANCELLED") continue;
      const start = new Date(inst.start).getTime();
      let end = new Date(inst.end).getTime();
      if (inst.isFullDay && end <= start) end = start + DAY;
      if (end <= windowStart || start >= windowEnd) continue;
      out.push({
        id: inst.isRecurring ? `${ev.uid}@${start}` : String(ev.uid),
        title: text(inst.summary) || "(no title)",
        start,
        end,
        allDay: inst.isFullDay,
        location: text(inst.event.location) || undefined,
        calendar: calName,
      });
    }
  }

  return out.sort((a, b) => a.start - b.start || Number(b.allDay) - Number(a.allDay));
}

async function fetchSource(src: CalendarSource, windowStart: number, windowEnd: number) {
  const res = await fetch(src.url, { signal: AbortSignal.timeout(20_000) });
  if (!res.ok) throw new Error(`Calendar ${src.name ?? "feed"} returned ${res.status}`);
  const data = await ical.async.parseICS(await res.text());
  return expandEvents(data, windowStart, windowEnd, src.name);
}

export async function fetchCalendar(cfg: Config, now = Date.now()): Promise<CalendarData> {
  const windowStart = startOfLocalDay(now);
  const windowEnd = windowStart + cfg.calendarDays * DAY;
  const results = await Promise.allSettled(cfg.calendars.map((c) => fetchSource(c, windowStart, windowEnd)));
  const failures = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
  if (failures.length && failures.length === results.length) throw failures[0].reason;
  for (const f of failures) console.warn("[calendar]", f.reason?.message ?? f.reason);

  const events = results
    .flatMap((r) => (r.status === "fulfilled" ? r.value : []))
    .filter((e) => e.end > now || (e.allDay && e.end > windowStart))
    .sort((a, b) => a.start - b.start);
  return { updatedAt: now, events };
}
