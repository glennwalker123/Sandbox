import { uses12h } from "../util.ts";

export function renderClock(timeEl: HTMLElement, dateEl: HTMLElement, units: "metric" | "imperial") {
  const now = new Date();
  const h12 = uses12h(units);
  const parts = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit", hour12: h12 }).formatToParts(now);
  const main = parts.filter((p) => p.type !== "dayPeriod").map((p) => p.value).join("").trim();
  const period = parts.find((p) => p.type === "dayPeriod")?.value;
  timeEl.innerHTML = period ? `${main}<span class="ampm">${period}</span>` : main;
  dateEl.textContent = now.toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" });
}
