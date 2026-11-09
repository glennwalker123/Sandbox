import { describe, expect, it } from "vitest";
import { cached } from "../src/cache.ts";

describe("cached", () => {
  it("serves from cache within the TTL and keeps the last good value on failure", async () => {
    let t = 0;
    let calls = 0;
    let fail = false;
    const c = cached(async () => {
      calls++;
      if (fail) throw new Error("offline");
      return calls;
    }, 1000, () => t);

    expect(await c.get()).toBe(1);
    t = 500;
    expect(await c.get()).toBe(1);
    expect(calls).toBe(1);

    t = 1500;
    fail = true;
    expect(await c.get()).toBe(1); // stale but still shown
    expect((c.lastError as Error).message).toBe("offline");

    fail = false;
    expect(await c.get(true)).toBe(3);
    expect(c.lastError).toBeNull();
  });

  it("throws when there is no previous value", async () => {
    const c = cached(async () => {
      throw new Error("nope");
    }, 1000);
    await expect(c.get()).rejects.toThrow("nope");
  });
});
