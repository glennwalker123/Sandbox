import type { ArtContext } from "./prompt.ts";

/**
 * Offline placeholder "art": a layered SVG landscape tinted by season and time
 * of day. Used in mock mode, and as a last resort when no image API is set up.
 */
const PALETTES: Record<string, [string, string, string, string]> = {
  dawn: ["#f6c6a8", "#e98f8f", "#7a6a9a", "#3c3a5e"],
  morning: ["#cfe6f5", "#9cc6e0", "#6b9a7a", "#3f5f4a"],
  afternoon: ["#a9d3f0", "#e7f1f7", "#7fa36a", "#4b6b3f"],
  "golden hour": ["#ffcf8a", "#f08a5d", "#9a5a6e", "#3d2f4a"],
  evening: ["#5a6fa8", "#c58fb0", "#3f4a72", "#1f2440"],
  night: ["#0e1430", "#25305e", "#151b38", "#070a1a"],
};

const SEASON_TINT: Record<string, string> = {
  spring: "#9fd49a",
  summer: "#f2d16b",
  autumn: "#d9773f",
  winter: "#dfe8f2",
};

function rng(seed: number) {
  let s = seed >>> 0 || 1;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 2 ** 32);
}

function ridge(rand: () => number, baseY: number, amp: number, w: number, h: number): string {
  const pts: string[] = [`M0,${h}`, `L0,${baseY}`];
  const steps = 12;
  for (let i = 1; i <= steps; i++) {
    const x = (w / steps) * i;
    const y = baseY - amp * (0.4 + rand() * 0.6) * Math.sin((i / steps) * Math.PI * (1 + rand()));
    const cx = x - w / steps / 2;
    pts.push(`Q${cx.toFixed(0)},${(y - amp * rand() * 0.3).toFixed(0)} ${x.toFixed(0)},${y.toFixed(0)}`);
  }
  pts.push(`L${w},${h}Z`);
  return pts.join(" ");
}

export function mockArtSvg(ctx: ArtContext, seed = Date.now()): string {
  const w = 1920;
  const h = 1080;
  const rand = rng(seed);
  const [sky1, sky2, land1, land2] = PALETTES[ctx.dayPart] ?? PALETTES.afternoon;
  const tint = SEASON_TINT[ctx.season];
  const night = ctx.dayPart === "night" || ctx.dayPart === "evening";
  const sunX = 300 + rand() * 1300;
  const sunY = night ? 180 + rand() * 120 : ctx.dayPart === "golden hour" || ctx.dayPart === "dawn" ? 620 : 260;
  const stars = night
    ? Array.from({ length: 90 }, () => `<circle cx="${(rand() * w).toFixed(0)}" cy="${(rand() * h * 0.55).toFixed(0)}" r="${(rand() * 1.6 + 0.3).toFixed(1)}" fill="#fff" opacity="${(rand() * 0.7 + 0.2).toFixed(2)}"/>`).join("")
    : "";
  const rain = /rain|drizzle|shower/.test(ctx.weather ?? "")
    ? Array.from({ length: 160 }, () => {
        const x = rand() * w;
        const y = rand() * h;
        return `<line x1="${x.toFixed(0)}" y1="${y.toFixed(0)}" x2="${(x - 12).toFixed(0)}" y2="${(y + 38).toFixed(0)}" stroke="#fff" stroke-opacity="0.18" stroke-width="1.5"/>`;
      }).join("")
    : "";

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">
<defs>
  <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${sky1}"/><stop offset="1" stop-color="${sky2}"/></linearGradient>
  <radialGradient id="sun" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="${night ? "#f4f1e0" : "#fff6d8"}"/><stop offset="0.35" stop-color="${night ? "#e8e4cc" : "#ffd98a"}" stop-opacity="0.9"/><stop offset="1" stop-color="${sky2}" stop-opacity="0"/></radialGradient>
  <filter id="grain"><feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="${Math.floor(rand() * 100)}"/><feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 0.09 0"/><feComposite in2="SourceGraphic" operator="in"/></filter>
</defs>
<rect width="${w}" height="${h}" fill="url(#sky)"/>
${stars}
<circle cx="${sunX.toFixed(0)}" cy="${sunY.toFixed(0)}" r="${night ? 160 : 260}" fill="url(#sun)"/>
<path d="${ridge(rand, h * 0.62, 180, w, h)}" fill="${land1}" opacity="0.55"/>
<path d="${ridge(rand, h * 0.72, 140, w, h)}" fill="${tint}" opacity="0.35"/>
<path d="${ridge(rand, h * 0.8, 120, w, h)}" fill="${land1}" opacity="0.85"/>
<path d="${ridge(rand, h * 0.9, 90, w, h)}" fill="${land2}"/>
${rain}
<rect width="${w}" height="${h}" filter="url(#grain)"/>
</svg>`;
}
