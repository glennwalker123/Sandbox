import type { ArtPiece, CalendarData, DisplayState, Weather } from "../../shared/types.ts";
import { renderAgenda } from "./widgets/agenda.ts";
import { ArtView } from "./widgets/art.ts";
import { renderClock } from "./widgets/clock.ts";
import { renderWeather } from "./widgets/weather.ts";

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const body = document.body;
const art = new ArtView($("art"), $("caption"));

let state: DisplayState = {
  config: { locationName: "", units: "metric", quietHours: null, mock: false },
  weather: null,
  calendar: null,
  art: null,
};

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

function renderAll() {
  renderClock($("time"), $("date"), state.config.units);
  renderWeather($("weather"), state.weather);
  renderAgenda($("agenda"), state.calendar, state.config.units);
  void art.show(state.art);
  updateModes();
}

// Clock ticks on the minute; agenda re-renders so "Now" markers move.
function scheduleTick() {
  const ms = 60_000 - (Date.now() % 60_000) + 50;
  setTimeout(() => {
    renderClock($("time"), $("date"), state.config.units);
    renderAgenda($("agenda"), state.calendar, state.config.units);
    renderWeather($("weather"), state.weather);
    updateModes();
    scheduleTick();
  }, ms);
}

function connect() {
  const es = new EventSource("/api/stream");
  es.addEventListener("open", () => body.classList.remove("disconnected"));
  es.addEventListener("error", () => body.classList.add("disconnected"));
  es.addEventListener("state", (e) => {
    state = JSON.parse((e as MessageEvent).data) as DisplayState;
    renderAll();
  });
  es.addEventListener("weather", (e) => {
    state.weather = JSON.parse((e as MessageEvent).data) as Weather;
    renderWeather($("weather"), state.weather);
    updateModes();
  });
  es.addEventListener("calendar", (e) => {
    state.calendar = JSON.parse((e as MessageEvent).data) as CalendarData;
    renderAgenda($("agenda"), state.calendar, state.config.units);
  });
  es.addEventListener("art", (e) => {
    state.art = JSON.parse((e as MessageEvent).data) as ArtPiece;
    void art.show(state.art);
  });
}

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

renderClock($("time"), $("date"), state.config.units);
scheduleTick();
connect();
