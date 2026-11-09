import type { ArtPiece, CalendarData, DisplayState, Weather } from "../../shared/types.ts";
import { renderAgenda } from "./widgets/agenda.ts";
import { ArtView } from "./widgets/art.ts";
import { renderClock } from "./widgets/clock.ts";
import { renderWeather } from "./widgets/weather.ts";

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const body = document.body;
const root = document.documentElement;
const art = new ArtView($("art"), $("caption"));
const params = new URLSearchParams(location.search);

let state: DisplayState = {
  config: { locationName: "", units: "metric", quietHours: null, mock: false, uiScale: 1 },
  weather: null,
  calendar: null,
  art: null,
};

/** How much the panel shows is set in CSS per screen shape (see styles.css :root). */
function layout() {
  const css = getComputedStyle(root);
  const int = (name: string, fallback: number) => {
    const n = parseInt(css.getPropertyValue(name), 10);
    return Number.isFinite(n) ? n : fallback;
  };
  return {
    maxEvents: int("--max-events", 5),
    slots: { hours: int("--forecast-hours", 2), days: int("--forecast-days", 3) },
  };
}

function applyScale() {
  const scale = Number(params.get("scale")) || state.config.uiScale || 1;
  root.style.setProperty("--scale", String(scale));
}

function inQuietHours(): boolean {
  const q = state.config.quietHours;
  if (!q) return false;
  const h = new Date().getHours();
  return q[0] < q[1] ? h >= q[0] && h < q[1] : h >= q[0] || h < q[1];
}

function updateModes() {
  const now = Date.now();
  const today = state.weather?.daily[0];
  const dark = today ? now < today.sunrise - 30 * 60_000 || now > today.sunset + 90 * 60_000 : false;
  body.classList.toggle("night", dark);
  body.classList.toggle("ambient", inQuietHours());
}

const drawClock = () => renderClock($("time"), $("date"), state.config.units);
const drawWeather = () => renderWeather($("weather"), state.weather, layout().slots);
const drawAgenda = () => renderAgenda($("agenda"), state.calendar, state.config.units, layout().maxEvents);

function renderAll() {
  applyScale();
  drawClock();
  drawWeather();
  drawAgenda();
  void art.show(state.art);
  updateModes();
}

// Clock ticks on the minute; agenda re-renders so "Now" markers move.
function scheduleTick() {
  const ms = 60_000 - (Date.now() % 60_000) + 50;
  setTimeout(() => {
    drawClock();
    drawAgenda();
    drawWeather();
    updateModes();
    scheduleTick();
  }, ms);
}

const shapeQuery = () => `w=${window.innerWidth}&h=${window.innerHeight}`;

function connect() {
  const es = new EventSource(`/api/stream?${shapeQuery()}`);
  es.addEventListener("open", () => body.classList.remove("disconnected"));
  es.addEventListener("error", () => body.classList.add("disconnected"));
  es.addEventListener("state", (e) => {
    state = JSON.parse((e as MessageEvent).data) as DisplayState;
    renderAll();
  });
  es.addEventListener("weather", (e) => {
    state.weather = JSON.parse((e as MessageEvent).data) as Weather;
    drawWeather();
    updateModes();
  });
  es.addEventListener("calendar", (e) => {
    state.calendar = JSON.parse((e as MessageEvent).data) as CalendarData;
    drawAgenda();
  });
  es.addEventListener("art", (e) => {
    state.art = JSON.parse((e as MessageEvent).data) as ArtPiece;
    void art.show(state.art);
  });
}

// Screen rotated or resized: re-lay out now, and tell the server so new art matches.
let resizeTimer: ReturnType<typeof setTimeout> | undefined;
let lastOrientation = window.innerWidth >= window.innerHeight;
window.addEventListener("resize", () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    drawWeather();
    drawAgenda();
    const landscape = window.innerWidth >= window.innerHeight;
    if (landscape !== lastOrientation) {
      lastOrientation = landscape;
      void fetch("/api/display/shape", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ w: window.innerWidth, h: window.innerHeight }),
      });
    }
  }, 400);
});

// Touch / click: tap toggles the info panel; double-tap skips to the next piece.
let lastTap = 0;
document.addEventListener("pointerup", () => {
  const now = Date.now();
  if (now - lastTap < 350) {
    void fetch("/api/art/next", { method: "POST" });
    lastTap = 0;
    return;
  }
  lastTap = now;
  setTimeout(() => {
    if (lastTap === now) body.classList.toggle("panel-hidden");
  }, 360);
});
document.addEventListener("keydown", (e) => {
  if (e.key === "ArrowRight" || e.key === " ") void fetch("/api/art/next", { method: "POST" });
  if (e.key === "h") body.classList.toggle("panel-hidden");
});

// Reload once a day so a long-running kiosk picks up new frontend builds and frees memory.
setTimeout(() => location.reload(), 24 * 3_600_000 + Math.random() * 600_000);

applyScale();
drawClock();
scheduleTick();
connect();
