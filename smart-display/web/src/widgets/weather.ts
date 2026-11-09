import type { Weather } from "../../../shared/types.ts";
import { weatherIcon } from "../icons.ts";
import { esc, fmtTime, uses12h } from "../util.ts";

export function renderWeather(el: HTMLElement, w: Weather | null, slots = { hours: 2, days: 3 }) {
  if (!w) {
    el.innerHTML = `<div class="empty">Weather unavailable</div>`;
    return;
  }
  const deg = "°";
  const today = w.daily[0];
  const now = Date.now();
  const isDay = today ? now >= today.sunrise && now < today.sunset : w.current.isDay;
  const h12 = uses12h(w.units);

  // Next few hours, then the coming days.
  const hours = w.hourly.filter((_, i) => i % 3 === 0).slice(0, slots.hours);
  const days = w.daily.slice(1, 1 + slots.days);
  const slot = (label: string, icon: string, top: string, rain: number) =>
    `<div class="slot"><span>${esc(label)}</span>${icon}<span class="hi">${top}</span><span class="rain">${rain >= 20 ? `${rain}%` : ""}</span></div>`;

  el.innerHTML = `
    <div class="now">
      ${weatherIcon(w.current.icon, isDay)}
      <div class="temp">${w.current.temp}${deg}</div>
      <div class="desc">
        <strong>${esc(w.current.label)}</strong>
        ${today ? `<span>H ${today.high}${deg} · L ${today.low}${deg}</span>` : ""}
        <span>Feels ${w.current.feelsLike}${deg}${today && today.precipChance >= 20 ? ` · ${today.precipChance}% rain` : ""}</span>
      </div>
    </div>
    <div class="forecast">
      ${hours
        .map((h) => {
          const hDay = w.daily.some((d) => h.time >= d.sunrise && h.time < d.sunset);
          return slot(fmtTime(h.time, h12).replace(":00", ""), weatherIcon(h.icon, hDay), `${h.temp}${deg}`, h.precipChance);
        })
        .join("")}
      ${days
        .map((d) =>
          slot(
            new Date(`${d.date}T12:00`).toLocaleDateString(undefined, { weekday: "short" }),
            weatherIcon(d.icon, true),
            `${d.high}${deg}<span style="opacity:.6"> ${d.low}${deg}</span>`,
            d.precipChance,
          ),
        )
        .join("")}
    </div>`;
}
