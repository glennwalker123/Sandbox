import type { WeatherIcon } from "../../shared/types.ts";

// Simple line icons, drawn on a 48×48 grid. currentColor = panel ink.
const sun = `<circle cx="24" cy="24" r="8"/><g stroke-linecap="round">${[0, 45, 90, 135, 180, 225, 270, 315]
  .map((a) => `<line x1="24" y1="7" x2="24" y2="11" transform="rotate(${a} 24 24)"/>`)
  .join("")}</g>`;
const moon = `<path d="M30 9a15 15 0 1 0 9 21A12 12 0 0 1 30 9z"/>`;
const cloud = `<path d="M15 36h19a8 8 0 0 0 0-16 11 11 0 0 0-21 3 7 7 0 0 0 2 13z"/>`;
const smallSun = `<circle cx="17" cy="17" r="6"/><g stroke-linecap="round">${[0, 60, 120, 180, 240, 300]
  .map((a) => `<line x1="17" y1="5" x2="17" y2="7.5" transform="rotate(${a} 17 17)"/>`)
  .join("")}</g>`;
const smallMoon = `<path d="M20 7a10 10 0 1 0 6 14A8 8 0 0 1 20 7z"/>`;
const lowCloud = `<path d="M18 38h17a7 7 0 0 0 0-14 10 10 0 0 0-19 3 6 6 0 0 0 2 11z" fill="var(--cloud-fill, rgba(20,20,28,.35))"/>`;
const drops = (n: number) =>
  Array.from({ length: n }, (_, i) => `<line x1="${17 + i * 7}" y1="40" x2="${15 + i * 7}" y2="45" stroke-linecap="round"/>`).join("");
const flakes = `<g stroke-linecap="round"><path d="M18 41v5M15.5 43.5h5"/><path d="M26 41v5M23.5 43.5h5"/><path d="M34 41v5M31.5 43.5h5"/></g>`;
const bolt = `<path d="M25 37l-4 6h5l-3 5" stroke-linejoin="round" stroke-linecap="round"/>`;
const fog = `<g stroke-linecap="round"><line x1="10" y1="40" x2="38" y2="40"/><line x1="14" y1="45" x2="34" y2="45"/></g>`;
const raisedCloud = `<path d="M15 32h19a8 8 0 0 0 0-16 11 11 0 0 0-21 3 7 7 0 0 0 2 13z"/>`;

export function weatherIcon(icon: WeatherIcon, isDay = true): string {
  const body = (() => {
    switch (icon) {
      case "clear": return isDay ? sun : moon;
      case "partly": return (isDay ? smallSun : smallMoon) + lowCloud;
      case "cloudy": return cloud;
      case "fog": return raisedCloud + fog;
      case "drizzle": return raisedCloud + drops(2);
      case "rain": return raisedCloud + drops(3);
      case "snow": return raisedCloud + flakes;
      case "storm": return raisedCloud + bolt;
    }
  })();
  return `<svg class="icon" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">${body}</svg>`;
}
