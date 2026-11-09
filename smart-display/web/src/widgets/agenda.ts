import type { CalendarData, CalendarEvent } from "../../../shared/types.ts";
import { dayLabel, esc, fmtTime, startOfDay, uses12h } from "../util.ts";

const MAX_EVENTS = 6;

export function renderAgenda(el: HTMLElement, cal: CalendarData | null, units: "metric" | "imperial") {
  if (!cal) {
    el.innerHTML = "";
    return;
  }
  const now = Date.now();
  const h12 = uses12h(units);
  const upcoming = cal.events.filter((e) => e.end > now).slice(0, MAX_EVENTS);
  if (!upcoming.length) {
    el.innerHTML = `<h3>Coming up</h3><div class="empty">Nothing on the calendar this week</div>`;
    return;
  }

  const groups = new Map<number, CalendarEvent[]>();
  for (const e of upcoming) {
    const day = Math.max(startOfDay(e.start), startOfDay(now)); // multi-day events show under today
    groups.set(day, [...(groups.get(day) ?? []), e]);
  }

  el.innerHTML = [...groups.entries()]
    .map(([day, events]) => {
      const rows = events
        .map((e) => {
          const happening = e.start <= now && e.end > now;
          const when = e.allDay ? "All day" : happening ? "Now" : fmtTime(e.start, h12);
          return `<div class="event${happening && !e.allDay ? " now" : ""}">
            <span class="when">${when}</span>
            <span class="what">${esc(e.title)}${e.location ? `<span class="where">${esc(e.location)}</span>` : ""}</span>
          </div>`;
        })
        .join("");
      return `<h3>${esc(dayLabel(day, now))}</h3>${rows}`;
    })
    .join("");
}
