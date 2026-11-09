import type { ArtPiece } from "../../../shared/types.ts";
import { isQuietHour, type Config } from "../config.ts";
import { createGenerator, type ImageGenerator } from "./generators.ts";
import { mockArtSvg } from "./mockArt.ts";
import { buildContext, writePrompt, type ArtContext } from "./prompt.ts";
import type { ArtStore } from "./store.ts";

export interface DirectorDeps {
  cfg: Config;
  stores: { ai: ArtStore; photos: ArtStore; mock: ArtStore };
  context: () => ArtContext;
  onChange: (piece: ArtPiece) => void;
  generator?: ImageGenerator | null;
  now?: () => number;
}

/**
 * Decides what's on the wall: generates a new AI piece every
 * `intervalMinutes` (outside quiet hours), and in between rotates through the
 * archive and/or the picked Google Photos every `rotateMinutes`.
 */
export class ArtDirector {
  private current: ArtPiece | null = null;
  private lastGeneratedAt = 0;
  private lastRotatedAt = 0;
  private generating: Promise<ArtPiece | null> | null = null;
  private lastPool: "ai" | "photos" = "photos";
  private cursor = new Map<string, number>();
  private timer: NodeJS.Timeout | null = null;
  readonly generator: ImageGenerator | null;
  lastError: string | null = null;

  constructor(private readonly d: DirectorDeps) {
    this.generator = d.generator === undefined ? createGenerator(d.cfg) : d.generator;
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

    const due = now - this.lastGeneratedAt >= art.intervalMinutes * 60_000;
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
    for (const pool of pools) {
      const list = this.d.stores[pool].list();
      if (!list.length) continue;
      const i = ((this.cursor.get(pool) ?? -1) + 1) % list.length;
      const candidate = list[i].id === this.current?.id && list.length > 1 ? list[(i + 1) % list.length] : list[i];
      this.cursor.set(pool, list.indexOf(candidate));
      this.lastPool = pool;
      return candidate;
    }
    return null;
  }

  /** Generate a fresh AI piece right now (deduplicates concurrent calls). */
  generateNow(): Promise<ArtPiece | null> {
    if (!this.generator) return Promise.resolve(null);
    this.generating ??= (async () => {
      const startedAt = this.now();
      try {
        const ctx = this.d.context();
        const recent = this.d.stores.ai.list().slice(0, 8).flatMap((p) => (p.title ? [p.title] : []));
        const { prompt, title } = await writePrompt(ctx, this.d.cfg, recent);
        console.log(`[art] generating with ${this.generator!.name}: ${title ?? ""} — ${prompt}`);
        const img = await this.generator!.generate(prompt);
        const piece = await this.d.stores.ai.save(img.data, img.ext, { prompt, title });
        this.lastError = null;
        this.cursor.set("ai", 0);
        this.lastPool = "ai";
        this.show(piece);
        return piece;
      } catch (err) {
        this.lastError = (err as Error).message;
        console.warn("[art] generation failed:", this.lastError);
        return null;
      } finally {
        // Back off a full interval even on failure, so a bad key can't burn money in a loop.
        this.lastGeneratedAt = startedAt;
        this.generating = null;
      }
    })();
    return this.generating;
  }

  private async generateMock(): Promise<ArtPiece> {
    const ctx = this.d.context();
    const piece = await this.d.stores.mock.save(mockArtSvg(ctx, this.now()), "svg", {
      title: `${ctx.season[0].toUpperCase()}${ctx.season.slice(1)} ${ctx.dayPart}`,
      prompt: "Placeholder art (mock mode)",
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
