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
export type OpenAIQuality = "low" | "medium" | "high";
export type ArtSchedule = { kind: "daily"; hour: number; minute: number } | { kind: "every"; minutes: number };

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
    schedule: ArtSchedule;
    rotateMinutes: number;
    maxPerDay: number;
    monthlyBudget: number | null; // estimated USD; null = no cap
    costPerImage: number; // estimated USD, for tracking only
    keep: number;
    quietHours: [number, number] | null;
  };
  keys: {
    anthropic?: string;
    openai?: string;
    gemini?: string;
  };
  models: { prompt: string; openaiImage: string; openaiQuality: OpenAIQuality; geminiImage: string };
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

/** "daily@07:00" | "every:180"; falls back to the legacy ART_INTERVAL_MINUTES, then daily at 07:00. */
export function parseSchedule(v: string | undefined, legacyInterval?: string): ArtSchedule {
  const daily = v?.match(/^\s*daily@(\d{1,2}):(\d{2})\s*$/i);
  if (daily && Number(daily[1]) < 24 && Number(daily[2]) < 60) {
    return { kind: "daily", hour: Number(daily[1]), minute: Number(daily[2]) };
  }
  const every = v?.match(/^\s*every:(\d+)\s*$/i);
  if (every) return { kind: "every", minutes: Math.max(15, Number(every[1])) };
  if (!v && legacyInterval && Number(legacyInterval) > 0) {
    return { kind: "every", minutes: Math.max(15, Number(legacyInterval)) };
  }
  return { kind: "daily", hour: 7, minute: 0 };
}

export function describeSchedule(s: ArtSchedule): string {
  if (s.kind === "every") return `every ${s.minutes} min`;
  return `daily at ${String(s.hour).padStart(2, "0")}:${String(s.minute).padStart(2, "0")}`;
}

/** Rough per-image prices (USD) from published price lists, Nov 2026. Override with ART_COST_PER_IMAGE. */
export function defaultCostPerImage(provider: ImageProvider, quality: OpenAIQuality): number {
  if (provider === "gemini") return 0.04;
  return { low: 0.01, medium: 0.05, high: 0.17 }[quality];
}

export function isQuietHour(hour: number, quiet: [number, number] | null): boolean {
  if (!quiet) return false;
  const [start, end] = quiet;
  return start < end ? hour >= start && hour < end : hour >= start || hour < end;
}

export function loadConfig(env: Env = process.env): Config {
  const mock = bool(env.MOCK);
  const mix = (env.ART_SOURCE ?? "ai") as ArtMix;
  const provider: ImageProvider = env.IMAGE_PROVIDER === "openai" ? "openai" : "gemini";
  const quality: OpenAIQuality =
    env.OPENAI_IMAGE_QUALITY === "low" || env.OPENAI_IMAGE_QUALITY === "high" ? env.OPENAI_IMAGE_QUALITY : "medium";
  const budget = num(env.ART_MONTHLY_BUDGET, 0);
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
      provider,
      orientation: env.ART_ORIENTATION === "landscape" || env.ART_ORIENTATION === "portrait" ? env.ART_ORIENTATION : "auto",
      style:
        env.ART_STYLE ??
        "museum-quality fine art, painterly, rich texture, calm and contemplative, suitable for a living-room wall",
      useCalendar: bool(env.ART_USE_CALENDAR, true),
      schedule: parseSchedule(env.ART_SCHEDULE, env.ART_INTERVAL_MINUTES),
      rotateMinutes: Math.max(1, num(env.ART_ROTATE_MINUTES, 15)),
      maxPerDay: Math.max(0, num(env.ART_MAX_PER_DAY, 3)),
      monthlyBudget: budget > 0 ? budget : null,
      costPerImage: num(env.ART_COST_PER_IMAGE, defaultCostPerImage(provider, quality)),
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
      // Image models get retired often; override these if the setup page reports "model unavailable".
      openaiImage: env.OPENAI_IMAGE_MODEL ?? "gpt-image-2",
      openaiQuality: quality,
      geminiImage: env.GEMINI_IMAGE_MODEL ?? "gemini-2.5-flash-image",
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
