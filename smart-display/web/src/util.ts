export const esc = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

/** 12h clocks for US/imperial setups, 24h otherwise, unless the browser locale says so. */
export function uses12h(units: "metric" | "imperial"): boolean {
  const override = new URLSearchParams(location.search).get("clock");
  if (override === "12") return true;
  if (override === "24") return false;
  return units === "imperial";
}

export function fmtTime(t: number, h12: boolean): string {
  return new Date(t).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit", hour12: h12 });
}

export function startOfDay(t: number): number {
  const d = new Date(t);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function dayLabel(t: number, now = Date.now()): string {
  const diff = Math.round((startOfDay(t) - startOfDay(now)) / 86_400_000);
  if (diff === 0) return "Today";
  if (diff === 1) return "Tomorrow";
  return new Date(t).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "short" });
}
