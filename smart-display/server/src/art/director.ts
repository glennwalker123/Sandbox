import { orientationOf, type ArtPiece, type Orientation, type Shape } from "../../../shared/types.ts";
import { isQuietHour, type ArtSchedule, type Config } from "../config.ts";
import { createGenerator, type ImageGenerator } from "./generators.ts";
import { mockArtSvg } from "./mockArt.ts";
import { buildContext, writePrompt, type ArtContext } from "./prompt.ts";
import type { ArtStore } from "./store.ts";
import { UsageLog } from "./usage.ts";

export interface DirectorDeps {
  cfg: Config;
  stores: { ai: ArtStore; photos: ArtStore; mock: ArtStore };
  context: () => ArtContext;
  onChange: (piece: ArtPiece) => void;
  generator?: ImageGenerator | null;
  now?: () => number;
  usage?: UsageLog;
}

const LANDSCAPE: Shape = { width: 16, height: 9 };
const PORTRAIT: Shape = { width: 3, height: 4 };
/** After a rotation with no matching AI art, wait at least this long since the last attempt before generating. */
const RESHAPE_MIN_GAP = 5 * 60_000;
/** After a failed attempt, wait this long before trying again. */
const RETRY_GAP = 30 * 60_000;

/** Is a scheduled generation due, given when the last piece was successfully made? */
export function isDue(schedule: ArtSchedule, now: number, lastGeneratedAt: number): boolean {
  if (schedule.kind === "every") return now - lastGeneratedAt >= schedule.minutes * 60_000;
  const slot = new Date(now);
  slot.setHours(schedule.hour, schedule.minute, 0, 0);
  if (slot.getTime() > now) slot.setDate(slot.getDate() - 1); // most recent slot that has passed
  return lastGeneratedAt < slot.getTime();
}

/**
 * Decides what's on the wall: generates a new AI piece every
 * the configured schedule (outside quiet hours, within the spend limits), and in between rotates through the
 * archive and/or the picked Google Photos every `rotateMinutes`.
 */
export class ArtDirector {
  private current: ArtPiece | null = null;
  private lastGeneratedAt = 0; // last success
  private lastAttemptAt = 0;
  readonly usage: UsageLog;
  private lastRotatedAt = 0;
  private generating: Promise<ArtPiece | null> | null = null;
  private lastPool: "ai" | "photos" = "photos";
  private cursor = new Map<string, number>();
  private timer: NodeJS.Timeout | null = null;
  private shape: Shape;
  readonly generator: ImageGenerator | null;
  lastError: string | null = null;

  constructor(private readonly d: DirectorDeps) {
    this.generator = d.generator === undefined ? createGenerator(d.cfg) : d.generator;
    this.usage = d.usage ?? new UsageLog(null);
    const fixed = d.cfg.art.orientation;
    this.shape = fixed === "portrait" ? PORTRAIT : LANDSCAPE;
  }

  get orientation(): Orientation {
    return orientationOf(this.shape);
  }

  /**
   * The display reports its viewport. With ART_ORIENTATION=auto the art follows
   * it; on a rotation we switch to a matching piece straight away.
   */
  async setShape(shape: Shape) {
    if (this.d.cfg.art.orientation !== "auto" || !(shape.width > 0 && shape.height > 0)) return;
    const before = this.orientation;
    this.shape = { width: shape.width, height: shape.height };
    if (orientationOf(shape) !== before) await this.onReshape();
  }

  private async onReshape() {
    if (this.d.cfg.art.mix === "mock") {
      await this.generateMock();
      return;
    }
    const now = this.now();
    const quiet = isQuietHour(new Date(now).getHours(), this.d.cfg.art.quietHours);
    const hasMatchingAi = this.d.stores.ai.list().some((p) => this.matches(p));
    if (this.usesAi && this.generator && !hasMatchingAi && !quiet && now - this.lastAttemptAt >= RESHAPE_MIN_GAP) {
      if (await this.generateNow()) return;
    }
    await this.next();
  }

  private matches(p: ArtPiece): boolean {
    // Photos are letterboxed, so they suit any orientation. Older pieces predate orientation tracking and are landscape.
    return p.source === "photos" || (p.orientation ?? "landscape") === this.orientation;
  }

  private now() {
    return this.d.now?.() ?? Date.now();
  }

  get piece(): ArtPiece | null {
    return this.current;
  }

  get usesAi() {
    return this.d.cfg.art.mix === "ai" || this.d.cfg.art.mix === "alternate";
  }

  get usesPhotos() {
    return this.d.cfg.art.mix === "photos" || this.d.cfg.art.mix === "alternate";
  }

  async start() {
    await Promise.all(Object.values(this.d.stores).map((s) => s.load()));
    await this.usage.load();
    this.lastGeneratedAt = this.d.stores.ai.list()[0]?.createdAt ?? 0;
    await this.tick();
    this.timer = setInterval(() => void this.tick(), 60_000);
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
  }

  /** Called every minute. */
  async tick() {
    const now = this.now();
    const { art } = this.d.cfg;
    const quiet = isQuietHour(new Date(now).getHours(), art.quietHours);

    if (art.mix === "mock") {
      if (!this.current || now - this.lastRotatedAt >= art.rotateMinutes * 60_000) await this.generateMock();
      return;
    }

    const due = isDue(art.schedule, now, this.lastGeneratedAt) && now - this.lastAttemptAt >= RETRY_GAP;
    if (this.usesAi && this.generator && due && !quiet) {
      const piece = await this.generateNow();
      if (piece) return;
    }

    if (!this.current || now - this.lastRotatedAt >= art.rotateMinutes * 60_000) await this.next();
  }

  /** Advance to the next piece from the configured pool(s). */
  async next(): Promise<ArtPiece | null> {
    const pick = this.pickFromPools();
    if (pick) {
      this.show(pick);
      return pick;
    }
    // Nothing in any pool yet (fresh install, no keys): show a placeholder.
    return this.generateMock();
  }

  private pickFromPools(): ArtPiece | null {
    const pools: ("ai" | "photos")[] = [];
    if (this.usesAi) pools.push("ai");
    if (this.usesPhotos) pools.push("photos");
    if (this.d.cfg.art.mix === "alternate") {
      // Alternate between pools, falling back to whichever has pieces.
      pools.sort((a) => (a === this.lastPool ? 1 : -1));
    }
    // First pass: only pieces in the screen's orientation; second pass: anything.
    for (const strict of [true, false]) {
      for (const pool of pools) {
        const all = this.d.stores[pool].list();
        const list = strict ? all.filter((p) => this.matches(p)) : all;
        if (!list.length) continue;
        const key = `${pool}:${strict ? this.orientation : "any"}`;
        const i = ((this.cursor.get(key) ?? -1) + 1) % list.length;
        const candidate = list[i].id === this.current?.id && list.length > 1 ? list[(i + 1) % list.length] : list[i];
        this.cursor.set(key, list.indexOf(candidate));
        this.lastPool = pool;
        return candidate;
      }
    }
    return null;
  }

  /** Generate a fresh AI piece right now (deduplicates concurrent calls). */
  /** Why a new (paid) piece can't be made right now, or null. */
  blockedReason(): string | null {
    const { maxPerDay, monthlyBudget, costPerImage } = this.d.cfg.art;
    return this.usage.blockedReason(this.now(), { maxPerDay, monthlyBudget, costPerImage });
  }

  generateNow(): Promise<ArtPiece | null> {
    if (!this.generator) return Promise.resolve(null);
    this.generating ??= (async () => {
      const startedAt = this.now();
      try {
        const blocked = this.blockedReason();
        if (blocked) {
          this.lastError = blocked;
          return null;
        }
        const ctx = this.d.context();
        const recent = this.d.stores.ai.list().slice(0, 8).flatMap((p) => (p.title ? [p.title] : []));
        const { prompt, title } = await writePrompt(ctx, this.d.cfg, recent);
        console.log(`[art] generating with ${this.generator!.name}: ${title ?? ""} — ${prompt}`);
        const shape = this.shape;
        const img = await this.generator!.generate(prompt, shape);
        const piece = await this.d.stores.ai.save(img.data, img.ext, { prompt, title, orientation: orientationOf(shape) });
        await this.usage.record({ at: startedAt, model: this.generator!.name, cost: this.d.cfg.art.costPerImage });
        this.lastGeneratedAt = startedAt;
        this.lastError = null;
        this.cursor.set(`ai:${orientationOf(shape)}`, 0);
        this.lastPool = "ai";
        this.show(piece);
        return piece;
      } catch (err) {
        this.lastError = (err as Error).message;
        console.warn("[art] generation failed:", this.lastError);
        return null;
      } finally {
        // Successes and failures both count as attempts, so a broken key or model is retried only every 30 min.
        this.lastAttemptAt = startedAt;
        this.generating = null;
      }
    })();
    return this.generating;
  }

  private async generateMock(): Promise<ArtPiece> {
    const ctx = this.d.context();
    const piece = await this.d.stores.mock.save(mockArtSvg(ctx, this.now(), this.shape), "svg", {
      title: `${ctx.season[0].toUpperCase()}${ctx.season.slice(1)} ${ctx.dayPart}`,
      prompt: "Placeholder art (mock mode)",
      orientation: this.orientation,
    });
    this.show(piece);
    return piece;
  }

  private show(piece: ArtPiece) {
    this.current = piece;
    this.lastRotatedAt = this.now();
    this.d.onChange(piece);
  }
}

export function contextFactory(
  cfg: Config,
  getWeather: () => import("../../../shared/types.ts").Weather | null,
  getEvents: () => import("../../../shared/types.ts").CalendarEvent[],
) {
  return () =>
    buildContext({
      now: Date.now(),
      latitude: cfg.location.latitude,
      weather: getWeather(),
      events: getEvents(),
      useCalendar: cfg.art.useCalendar,
    });
}
