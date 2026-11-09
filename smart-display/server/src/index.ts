import fs from "node:fs";
import path from "node:path";
import Fastify from "fastify";
import fastifyStatic from "@fastify/static";
import QRCode from "qrcode";
import type { DisplayState } from "../../shared/types.ts";
import { DATA_DIR, WEB_DIST, describeSchedule, loadConfig, loadEnvFile } from "./config.ts";
import { cached } from "./cache.ts";
import { fetchWeather } from "./weather.ts";
import { fetchCalendar } from "./calendar.ts";
import { mockCalendar, mockWeather } from "./mock.ts";
import { EventHub } from "./events.ts";
import { ArtStore } from "./art/store.ts";
import { ArtDirector, contextFactory } from "./art/director.ts";
import { GooglePhotos } from "./art/googlePhotos.ts";
import { UsageLog } from "./art/usage.ts";

loadEnvFile();
const cfg = loadConfig();
const hub = new EventHub();
const app = Fastify({ logger: { level: process.env.LOG_LEVEL ?? "info" } });

// --- data sources -----------------------------------------------------------
const weather = cached(
  async () => (cfg.mock ? mockWeather(cfg.location.name, cfg.units) : fetchWeather(cfg)),
  10 * 60_000,
);
const calendar = cached(
  async () => (cfg.mock || !cfg.calendars.length ? (cfg.mock ? mockCalendar() : { updatedAt: Date.now(), events: [] }) : fetchCalendar(cfg)),
  5 * 60_000,
);

// --- art ----------------------------------------------------------------------
const artDir = path.join(DATA_DIR, "art");
const stores = {
  ai: new ArtStore(path.join(artDir, "ai"), "ai", cfg.art.keep),
  photos: new ArtStore(path.join(artDir, "photos"), "photos", 2000),
  mock: new ArtStore(path.join(artDir, "mock"), "mock", 10),
};
const director = new ArtDirector({
  cfg,
  stores,
  context: contextFactory(cfg, () => weather.peek() ?? null, () => calendar.peek()?.events ?? []),
  onChange: (piece) => hub.broadcast({ type: "art", data: piece }),
  usage: new UsageLog(path.join(DATA_DIR, "usage.json")),
});
const photos = new GooglePhotos(cfg, stores.photos, path.join(DATA_DIR, "google-tokens.json"), (n) => {
  app.log.info(`[photos] imported ${n} photos`);
  if (n > 0 && director.usesPhotos) void director.next();
});

function state(): DisplayState {
  return {
    config: { locationName: cfg.location.name, units: cfg.units, quietHours: cfg.art.quietHours, mock: cfg.mock, uiScale: cfg.uiScale },
    weather: weather.peek() ?? null,
    calendar: calendar.peek() ?? null,
    art: director.piece,
  };
}

async function refreshWeather(force = false) {
  try {
    const before = weather.peek();
    const w = await weather.get(force);
    if (w !== before) hub.broadcast({ type: "weather", data: w });
  } catch (err) {
    app.log.warn(`[weather] ${(err as Error).message}`);
  }
}

async function refreshCalendar(force = false) {
  try {
    const before = calendar.peek();
    const c = await calendar.get(force);
    if (c !== before) hub.broadcast({ type: "calendar", data: c });
  } catch (err) {
    app.log.warn(`[calendar] ${(err as Error).message}`);
  }
}

// --- routes ---------------------------------------------------------------------
fs.mkdirSync(artDir, { recursive: true });
await app.register(fastifyStatic, { root: artDir, prefix: "/art/", decorateReply: false, maxAge: "7d", immutable: true });
if (fs.existsSync(WEB_DIST)) {
  await app.register(fastifyStatic, { root: WEB_DIST, prefix: "/" });
}

app.get("/api/state", async () => state());

app.get<{ Querystring: { w?: string; h?: string } }>("/api/stream", (req, reply) => {
  // The display tells us its size so the art can match its orientation.
  // Not awaited: a rotation may trigger a slow generation, which arrives later as an "art" event.
  void director.setShape({ width: Number(req.query.w), height: Number(req.query.h) });
  reply.hijack();
  hub.add(reply.raw, { type: "state", data: state() });
});

app.post<{ Body: { w?: number; h?: number } }>("/api/display/shape", async (req) => {
  void director.setShape({ width: Number(req.body?.w), height: Number(req.body?.h) });
  return { ok: true };
});

app.post("/api/art/next", async () => ({ art: await director.next() }));

app.post("/api/art/generate", async (_req, reply) => {
  if (!director.generator) return reply.code(400).send({ error: "No image API key configured (GEMINI_API_KEY or OPENAI_API_KEY)" });
  const blocked = director.blockedReason();
  if (blocked) return reply.code(429).send({ error: blocked });
  const art = await director.generateNow();
  if (!art) return reply.code(502).send({ error: director.lastError ?? "Generation failed" });
  return { art };
});

app.post("/api/refresh", async () => {
  await Promise.all([refreshWeather(true), refreshCalendar(true)]);
  return state();
});

app.get("/api/status", async () => ({
  mock: cfg.mock,
  location: cfg.location,
  units: cfg.units,
  calendars: cfg.calendars.map((c) => ({ name: c.name ?? new URL(c.url).hostname })),
  weather: { ok: Boolean(weather.peek()), updatedAt: weather.peek()?.updatedAt, error: errMsg(weather.lastError) },
  calendar: { ok: Boolean(calendar.peek()), events: calendar.peek()?.events.length ?? 0, error: errMsg(calendar.lastError) },
  art: {
    mix: cfg.art.mix,
    orientation: `${director.orientation}${cfg.art.orientation === "auto" ? " (follows screen)" : " (fixed)"}`,
    generator: director.generator?.name ?? null,
    promptWriter: cfg.keys.anthropic ? cfg.models.prompt : "built-in template",
    schedule: describeSchedule(cfg.art.schedule),
    limits: { maxPerDay: cfg.art.maxPerDay, monthlyBudget: cfg.art.monthlyBudget, costPerImage: cfg.art.costPerImage },
    usage: director.usage.summary(Date.now()),
    rotateMinutes: cfg.art.rotateMinutes,
    quietHours: cfg.art.quietHours,
    counts: { ai: stores.ai.list().length, photos: stores.photos.list().length },
    current: director.piece,
    lastError: director.lastError,
  },
  photos: { configured: photos.configured, connected: photos.connected, status: photos.getStatus() },
  displays: hub.size,
}));

app.get("/api/photos/auth", async (_req, reply) => {
  if (!photos.configured) return reply.code(400).send({ error: "Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET first" });
  return reply.redirect(photos.authUrl());
});

app.get<{ Querystring: { code?: string; error?: string } }>("/api/photos/callback", async (req, reply) => {
  if (req.query.error || !req.query.code) return reply.code(400).send(`Google sign-in failed: ${req.query.error ?? "no code"}`);
  await photos.handleCallback(req.query.code);
  return reply.redirect("/setup.html?photos=connected");
});

app.post("/api/photos/pick", async (_req, reply) => {
  if (!photos.connected) return reply.code(400).send({ error: "Connect Google Photos first" });
  const pickerUri = await photos.startPicking();
  return { pickerUri, qr: await QRCode.toDataURL(pickerUri, { margin: 1, width: 320 }) };
});

function errMsg(e: unknown) {
  return e ? (e as Error).message ?? String(e) : null;
}

// --- start ------------------------------------------------------------------------
await photos.init();
await Promise.all([refreshWeather(), refreshCalendar()]);
setInterval(() => void refreshWeather(), 10 * 60_000);
setInterval(() => void refreshCalendar(), 5 * 60_000);
void director.start();

await app.listen({ port: cfg.port, host: process.env.HOST ?? "0.0.0.0" });
app.log.info(`Smart display running on http://localhost:${cfg.port} (${cfg.mock ? "mock data" : "live data"}, art: ${cfg.art.mix})`);
