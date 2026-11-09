/**
 * Wraps an async loader with a TTL cache. If a refresh fails, the last good
 * value is kept (a wall display should show slightly stale data rather than
 * nothing when the Wi-Fi blips).
 */
export function cached<T>(load: () => Promise<T>, ttlMs: number, now: () => number = Date.now) {
  let value: T | undefined;
  let fetchedAt = -Infinity;
  let inflight: Promise<T> | null = null;
  let lastError: unknown = null;

  async function get(force = false): Promise<T> {
    if (!force && value !== undefined && now() - fetchedAt < ttlMs) return value;
    if (inflight) return inflight;
    inflight = (async () => {
      try {
        const v = await load();
        value = v;
        fetchedAt = now();
        lastError = null;
        return v;
      } catch (err) {
        lastError = err;
        if (value !== undefined) return value;
        throw err;
      } finally {
        inflight = null;
      }
    })();
    return inflight;
  }

  return {
    get,
    peek: () => value,
    get lastError() {
      return lastError;
    },
  };
}
