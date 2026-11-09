import Anthropic from "@anthropic-ai/sdk";
import type { CalendarEvent, Weather } from "../../../shared/types.ts";
import type { Config } from "../config.ts";

export type DayPart = "dawn" | "morning" | "afternoon" | "golden hour" | "evening" | "night";

export interface ArtContext {
  date: string; // e.g. "Monday 9 November"
  season: "spring" | "summer" | "autumn" | "winter";
  dayPart: DayPart;
  weather?: string; // "light rain, 9°C"
  events: string[]; // today's event titles (if enabled)
}

const HOUR = 3_600_000;

export function seasonFor(date: Date, latitude: number): ArtContext["season"] {
  const m = date.getMonth(); // 0 = Jan
  const north = m >= 2 && m <= 4 ? "spring" : m >= 5 && m <= 7 ? "summer" : m >= 8 && m <= 10 ? "autumn" : "winter";
  if (latitude >= 0) return north;
  return ({ spring: "autumn", summer: "winter", autumn: "spring", winter: "summer" } as const)[north];
}

export function dayPartFor(now: number, sunrise?: number, sunset?: number): DayPart {
  if (sunrise !== undefined && sunset !== undefined) {
    if (now >= sunrise - HOUR && now < sunrise + HOUR) return "dawn";
    if (now >= sunset - HOUR && now < sunset + 0.5 * HOUR) return "golden hour";
    if (now < sunrise - HOUR || now >= sunset + 3 * HOUR) return "night";
    if (now >= sunset + 0.5 * HOUR) return "evening";
  }
  const h = new Date(now).getHours();
  if (h < 5 || h >= 22) return "night";
  if (h < 7) return "dawn";
  if (h < 12) return "morning";
  if (h < 17) return "afternoon";
  if (h < 19) return "golden hour";
  return "evening";
}

export function buildContext(opts: {
  now: number;
  latitude: number;
  weather: Weather | null;
  events: CalendarEvent[];
  useCalendar: boolean;
}): ArtContext {
  const { now, weather } = opts;
  const date = new Date(now);
  const today = weather?.daily[0];
  const endOfDay = new Date(now);
  endOfDay.setHours(23, 59, 59, 999);
  const unit = weather?.units === "imperial" ? "°F" : "°C";

  return {
    date: date.toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" }),
    season: seasonFor(date, opts.latitude),
    dayPart: dayPartFor(now, today?.sunrise, today?.sunset),
    weather: weather ? `${weather.current.label.toLowerCase()}, ${weather.current.temp}${unit}` : undefined,
    events: opts.useCalendar
      ? opts.events
          .filter((e) => e.start <= endOfDay.getTime() && e.end > now - HOUR)
          .slice(0, 5)
          .map((e) => e.title)
      : [],
  };
}

/** Deterministic prompt used when Claude isn't configured or the call fails. */
export function fallbackPrompt(ctx: ArtContext, style: string): string {
  const parts = [
    `A ${ctx.season} landscape at ${ctx.dayPart}`,
    ctx.weather ? `with ${ctx.weather.replace(/,.*$/, "")} weather` : null,
    style,
    "no text, no lettering, no logos",
  ];
  return parts.filter(Boolean).join(", ");
}

const SYSTEM = `You write prompts for an image model that paints artwork for a living-room wall display.
Given today's context, write ONE vivid image prompt (40-80 words) that captures the mood of the season, time of day and weather.
If calendar events are given, you may weave in a subtle, abstract nod to the day's theme, but never depict text, names, screens or recognisable people.
Vary subject matter and medium across days: landscapes, still lifes, abstract compositions, architecture, nature close-ups.
The image must contain no text, letters, numbers, signatures or logos.
Respond with JSON only: {"title": "<2-5 word evocative title>", "prompt": "<the prompt>"}`;

export interface WrittenPrompt {
  title?: string;
  prompt: string;
}

export function parsePromptJson(raw: string): WrittenPrompt | null {
  const match = raw.match(/\{[\s\S]*\}/);
  if (!match) return null;
  try {
    const obj = JSON.parse(match[0]) as { title?: unknown; prompt?: unknown };
    if (typeof obj.prompt !== "string" || obj.prompt.trim().length < 10) return null;
    return { title: typeof obj.title === "string" ? obj.title.trim() : undefined, prompt: obj.prompt.trim() };
  } catch {
    return null;
  }
}

let client: Anthropic | null = null;

export async function writePrompt(ctx: ArtContext, cfg: Config, recentTitles: string[] = []): Promise<WrittenPrompt> {
  const fallback = { prompt: fallbackPrompt(ctx, cfg.art.style) };
  if (!cfg.keys.anthropic) return fallback;
  client ??= new Anthropic({ apiKey: cfg.keys.anthropic });

  const user = [
    `Date: ${ctx.date}`,
    `Season: ${ctx.season}`,
    `Time of day: ${ctx.dayPart}`,
    ctx.weather ? `Weather: ${ctx.weather}` : null,
    ctx.events.length ? `Today's calendar: ${ctx.events.join("; ")}` : null,
    `House style: ${cfg.art.style}`,
    recentTitles.length ? `Recent pieces (avoid repeating these): ${recentTitles.join("; ")}` : null,
  ]
    .filter(Boolean)
    .join("\n");

  try {
    const res = await client.messages.create({
      model: cfg.models.prompt,
      max_tokens: 2000,
      output_config: { effort: "low" },
      system: SYSTEM,
      messages: [{ role: "user", content: user }],
    });
    if (res.stop_reason === "refusal") return fallback;
    const textOut = res.content.flatMap((b) => (b.type === "text" ? [b.text] : [])).join("");
    return parsePromptJson(textOut) ?? fallback;
  } catch (err) {
    console.warn("[art] prompt writing failed, using fallback:", (err as Error).message);
    return fallback;
  }
}
