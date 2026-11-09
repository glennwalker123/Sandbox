import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";
import type { Units } from "../../shared/types.ts";

const here = path.dirname(fileURLToPath(import.meta.url));
export const ROOT_DIR = path.resolve(here, "../..");
export const DATA_DIR = path.join(ROOT_DIR, "data");
export const WEB_DIST = path.join(ROOT_DIR, "web/dist");

export type ArtMix = "ai" | "photos" | "alternate" | "mock";
export type ImageProvider = "openai" | "gemini";
export type ArtOrientation = "auto" | "landscape" | "portrait";

export interface CalendarSource {
  name?: string;
  url: string;
}

export interface Config {
  port: number;
  mock: boolean;
  location: { name: string; latitude: number; longitude: number };
  units: Units;
  uiScale: number;
  calendars: CalendarSource[];
  calendarDays: number;
  art: {
    mix: ArtMix;
    provider: ImageProvider;
    orientation: ArtOrientation;
    style: string;
    useCalendar: boolean;
    intervalMinutes: number;
    rotateMinutes: number;
    keep: number;
    quietHours: [number, number] | null;
  };
  keys: {
    anthropic?: string;
    openai?: string;
    gemini?: string;
  };
  models: { prompt: string; openaiImage: string; geminiImage: string };
  google: { clientId?: string; clientSecret?: string; redirectUri: string };
}

type Env = Record<string, string | undefined>;

const num = (v: string | undefined, fallback: number) => {
  const n = Number(v);
  return v !== undefined && v !== "" && Number.isFinite(n) ? n : fallback;
};

const bool = (v: string | undefined, fallback = false) =>
  v === undefined || v === "" ? fallback : /^(1|true|yes|on)$/i.test(v);

/** "Family|https://...ics, https://...ics" -> sources. */
export function parseCalendars(v: string | undefined): CalendarSource[] {
  if (!v) return [];
  return v
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((entry) => {
      const bar = entry.indexOf("|");
      if (bar > 0 && !entry.slice(0, bar).includes("://")) {
        return { name: entry.slice(0, bar).trim(), url: entry.slice(bar + 1).trim() };
      }
      return { url: entry };
    })
    // Google shows the secret address as webcal:// in some places.
    .map((c) => ({ ...c, url: c.url.replace(/^webcal:\/\//i, "https://") }));
}

/** "23-6" -> [23, 6]; "" / "off" -> null. */
export function parseQuietHours(v: string | undefined): [number, number] | null {
  if (!v || /^(off|none|no)$/i.test(v)) return null;
  const m = v.match(/^\s*(\d{1,2})\s*-\s*(\d{1,2})\s*$/);
  if (!m) return null;
  const a = Number(m[1]);
  const b = Number(m[2]);
  if (a > 23 || b > 24 || a === b) return null;
  return [a, b];
}

export function isQuietHour(hour: number, quiet: [number, number] | null): boolean {
  if (!quiet) return false;
  const [start, end] = quiet;
  return start < end ? hour >= start && hour < end : hour >= start || hour < end;
}

export function loadConfig(env: Env = process.env): Config {
  const mock = bool(env.MOCK);
  const mix = (env.ART_SOURCE ?? "ai") as ArtMix;
  return {
    port: num(env.PORT, 8080),
    mock,
    location: {
      name: env.LOCATION_NAME ?? "Home",
      latitude: num(env.LATITUDE, 51.5072),
      longitude: num(env.LONGITUDE, -0.1276),
    },
    units: env.UNITS === "imperial" ? "imperial" : "metric",
    uiScale: Math.min(3, Math.max(0.5, num(env.UI_SCALE, 1))),
    calendars: parseCalendars(env.CALENDAR_ICS_URLS),
    calendarDays: num(env.CALENDAR_DAYS, 7),
    art: {
      mix: mock ? "mock" : ["ai", "photos", "alternate", "mock"].includes(mix) ? mix : "ai",
      provider: env.IMAGE_PROVIDER === "openai" ? "openai" : "gemini",
      orientation: env.ART_ORIENTATION === "landscape" || env.ART_ORIENTATION === "portrait" ? env.ART_ORIENTATION : "auto",
      style:
        env.ART_STYLE ??
        "museum-quality fine art, painterly, rich texture, calm and contemplative, suitable for a living-room wall",
      useCalendar: bool(env.ART_USE_CALENDAR, true),
      intervalMinutes: Math.max(15, num(env.ART_INTERVAL_MINUTES, 180)),
      rotateMinutes: Math.max(1, num(env.ART_ROTATE_MINUTES, 15)),
      keep: Math.max(5, num(env.ART_KEEP, 60)),
      quietHours: parseQuietHours(env.QUIET_HOURS ?? "23-6"),
    },
    keys: {
      anthropic: env.ANTHROPIC_API_KEY || undefined,
      openai: env.OPENAI_API_KEY || undefined,
      gemini: env.GEMINI_API_KEY || undefined,
    },
    models: {
      prompt: env.PROMPT_MODEL ?? "claude-sonnet-5-5",
      openaiImage: env.OPENAI_IMAGE_MODEL ?? "gpt-image-1",
      geminiImage: env.GEMINI_IMAGE_MODEL ?? "imagen-4.0-generate-001",
    },
    google: {
      clientId: env.GOOGLE_CLIENT_ID || undefined,
      clientSecret: env.GOOGLE_CLIENT_SECRET || undefined,
      redirectUri: env.GOOGLE_REDIRECT_URI ?? `http://localhost:${num(env.PORT, 8080)}/api/photos/callback`,
    },
  };
}

export function loadEnvFile() {
  dotenv.config({ path: path.join(ROOT_DIR, ".env"), quiet: true });
}
