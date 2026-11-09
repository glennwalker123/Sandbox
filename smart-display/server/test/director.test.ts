import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { loadConfig } from "../src/config.ts";
import { ArtDirector } from "../src/art/director.ts";
import { ArtStore } from "../src/art/store.ts";
import type { ImageGenerator } from "../src/art/generators.ts";
import type { ArtPiece } from "../../shared/types.ts";

const ctx = () => ({ date: "Monday", season: "autumn" as const, dayPart: "afternoon" as const, weather: "rain, 9°C", events: [] });

async function setup(env: Record<string, string>, generator: ImageGenerator | null, startAt: number) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "art-"));
  const cfg = loadConfig({ QUIET_HOURS: "off", ...env });
  const stores = {
    ai: new ArtStore(path.join(dir, "ai"), "ai", 5),
    photos: new ArtStore(path.join(dir, "photos"), "photos", 50),
    mock: new ArtStore(path.join(dir, "mock"), "mock", 3),
  };
  const shown: ArtPiece[] = [];
  let t = startAt;
  const director = new ArtDirector({ cfg, stores, context: ctx, onChange: (p) => shown.push(p), generator, now: () => t });
  return { director, stores, shown, advance: (ms: number) => (t += ms) };
}

const okGen = (): ImageGenerator & { calls: number } => ({
  name: "fake",
  calls: 0,
  async generate() {
    this.calls++;
    return { data: Buffer.from("png"), ext: "png" };
  },
});

describe("ArtDirector", () => {
  it("falls back to placeholder art when generation fails and nothing is archived", async () => {
    const failing: ImageGenerator = { name: "broken", generate: async () => { throw new Error("offline"); } };
    const { director, shown } = await setup({ ART_SOURCE: "ai" }, failing, Date.now());
    await director.start();
    director.stop();
    expect(shown.at(-1)?.source).toBe("mock");
    expect(director.lastError).toBe("offline");
  });

  it("generates on the interval and rotates the archive in between", async () => {
    const gen = okGen();
    const { director, shown, advance } = await setup(
      { ART_SOURCE: "ai", ART_INTERVAL_MINUTES: "60", ART_ROTATE_MINUTES: "10" },
      gen,
      Date.now(),
    );
    await director.start();
    director.stop();
    expect(gen.calls).toBe(1);
    expect(shown.at(-1)?.source).toBe("ai");

    advance(11 * 60_000);
    await director.tick();
    expect(gen.calls).toBe(1); // rotated, not regenerated

    advance(50 * 60_000);
    await director.tick();
    expect(gen.calls).toBe(2);
  });

  it("alternates between AI art and photos", async () => {
    const { director, stores, shown, advance } = await setup(
      { ART_SOURCE: "alternate", ART_INTERVAL_MINUTES: "600", ART_ROTATE_MINUTES: "1" },
      null,
      Date.now(),
    );
    await Promise.all([stores.ai.load(), stores.photos.load()]);
    await stores.ai.save(Buffer.from("a"), "png", { title: "ai-1" });
    await stores.photos.save(Buffer.from("p"), "jpg", { title: "photo-1" });
    await director.start();
    director.stop();
    for (let i = 0; i < 3; i++) {
      advance(61_000);
      await director.tick();
    }
    const sources = shown.map((p) => p.source);
    expect(new Set(sources)).toEqual(new Set(["ai", "photos"]));
    for (let i = 1; i < sources.length; i++) expect(sources[i]).not.toBe(sources[i - 1]);
  });
});

describe("ArtDirector orientation", () => {
  it("generates in the screen's shape and prefers matching pieces after a rotation", async () => {
    const shapes: string[] = [];
    const gen: ImageGenerator = {
      name: "fake",
      async generate(_p, shape) {
        shapes.push(shape.width >= shape.height ? "landscape" : "portrait");
        return { data: Buffer.from("png"), ext: "png" };
      },
    };
    const { director, shown, advance } = await setup(
      { ART_SOURCE: "ai", ART_INTERVAL_MINUTES: "600", ART_ROTATE_MINUTES: "10" },
      gen,
      Date.now(),
    );
    await director.start();
    director.stop();
    expect(shown.at(-1)?.orientation).toBe("landscape");

    // Rotating soon after a generation waits for the minimum gap, showing the landscape piece meanwhile.
    advance(60_000);
    await director.setShape({ width: 1504, height: 2256 });
    expect(shapes).toEqual(["landscape"]);

    // Rotating back and forth after the gap generates a portrait piece once, then reuses it.
    await director.setShape({ width: 2256, height: 1504 });
    advance(6 * 60_000);
    await director.setShape({ width: 1504, height: 2256 });
    expect(shapes).toEqual(["landscape", "portrait"]);
    expect(shown.at(-1)?.orientation).toBe("portrait");

    await director.setShape({ width: 2256, height: 1504 });
    expect(shown.at(-1)?.orientation).toBe("landscape");
    advance(1_000);
    await director.setShape({ width: 1504, height: 2256 });
    expect(shapes).toHaveLength(2);
    expect(shown.at(-1)?.orientation).toBe("portrait");
  });

  it("ignores the screen when orientation is fixed", async () => {
    const { director, shown } = await setup({ ART_SOURCE: "mock", MOCK: "1", ART_ORIENTATION: "portrait" }, null, Date.now());
    await director.start();
    director.stop();
    await director.setShape({ width: 1920, height: 1080 });
    expect(director.orientation).toBe("portrait");
    expect(shown.every((p) => p.orientation === "portrait")).toBe(true);
  });
});
