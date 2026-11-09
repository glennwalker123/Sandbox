import type { ArtPiece } from "../../shared/types.ts";
import { esc } from "./util.ts";

interface Status {
  mock: boolean;
  location: { name: string; latitude: number; longitude: number };
  calendars: { name: string }[];
  weather: { ok: boolean; updatedAt?: number; error: string | null };
  calendar: { ok: boolean; events: number; error: string | null };
  art: {
    mix: string;
    orientation: string;
    generator: string | null;
    promptWriter: string;
    schedule: string;
    rotateMinutes: number;
    limits: { maxPerDay: number; monthlyBudget: number | null; costPerImage: number };
    usage: { today: { count: number; cost: number }; month: { count: number; cost: number } };
    quietHours: [number, number] | null;
    counts: { ai: number; photos: number };
    current: ArtPiece | null;
    lastError: string | null;
  };
  photos: { configured: boolean; connected: boolean; status: { state: string; [k: string]: unknown } };
  displays: number;
}

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const dl = (rows: [string, string][]) => rows.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${v}</dd>`).join("");
const usd = (n: number) => `$${n.toFixed(2)}`;

function spendText(usage: Status["art"]["usage"], limits: Status["art"]["limits"]): string {
  const plural = (n: number) => `${n} piece${n === 1 ? "" : "s"}`;
  const caps = [`~${usd(limits.costPerImage)} each`, `max ${limits.maxPerDay}/day`];
  if (limits.monthlyBudget !== null) caps.push(`budget ${usd(limits.monthlyBudget)}/month`);
  return `today ${plural(usage.today.count)} · this month ${plural(usage.month.count)} ≈ ${usd(usage.month.cost)} <span class="muted">(${caps.join(", ")})</span>`;
}

const ago = (t?: number) => (t ? `${Math.max(0, Math.round((Date.now() - t) / 60_000))} min ago` : "never");

async function load() {
  const s = (await (await fetch("/api/status")).json()) as Status;
  $("summary").textContent = `${s.location.name} · ${s.displays} screen${s.displays === 1 ? "" : "s"} connected${s.mock ? " · mock data" : ""}`;

  const cur = s.art.current;
  $("art-now").style.backgroundImage = cur ? `url("${cur.url}")` : "";
  $("art-info").innerHTML = dl([
    ["Showing", cur ? esc(cur.title ?? cur.source) : "—"],
    ...(cur?.prompt ? ([["Prompt", esc(cur.prompt)]] as [string, string][]) : []),
    ["Source", esc(s.art.mix)],
    ["Orientation", esc(s.art.orientation)],
    ["Image model", esc(s.art.generator ?? "not configured")],
    ["Prompt writer", esc(s.art.promptWriter)],
    ["Schedule", `new piece ${esc(s.art.schedule)}; saved art rotates every ${s.art.rotateMinutes} min`],
    ["Spend (est.)", spendText(s.art.usage, s.art.limits)],
    ["Quiet hours", s.art.quietHours ? `${s.art.quietHours[0]}:00–${s.art.quietHours[1]}:00` : "off"],
    ["Archive", `${s.art.counts.ai} AI pieces, ${s.art.counts.photos} photos`],
    ...(s.art.lastError ? ([["Last error", `<span class="error">${esc(s.art.lastError)}</span>`]] as [string, string][]) : []),
  ]);
  $<HTMLButtonElement>("btn-generate").disabled = !s.art.generator;

  const ps = s.photos.status;
  $("photos-info").innerHTML = !s.photos.configured
    ? "Add GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to .env to enable."
    : s.photos.connected
      ? ps.state === "done"
        ? `Connected. Imported ${ps.imported} photos last time.`
        : ps.state === "importing"
          ? `Importing ${ps.done}/${ps.total}…`
          : ps.state === "waiting"
            ? "Waiting for you to finish picking in Google Photos…"
            : ps.state === "error"
              ? `<span class="error">${esc(String(ps.message))}</span>`
              : "Connected."
      : "Not connected.";
  $("btn-connect").style.display = s.photos.configured ? "" : "none";
  $("btn-connect").textContent = s.photos.connected ? "Reconnect" : "Connect Google account";
  $<HTMLButtonElement>("btn-pick").disabled = !s.photos.connected;
  if (ps.state !== "waiting" && ps.state !== "importing") $("picker").innerHTML = "";

  $("data-info").innerHTML = dl([
    ["Weather", s.weather.ok ? `Open-Meteo, updated ${ago(s.weather.updatedAt)}` : `<span class="error">${esc(s.weather.error ?? "unavailable")}</span>`],
    [
      "Calendars",
      s.calendars.length
        ? `${esc(s.calendars.map((c) => c.name).join(", "))} — ${s.calendar.events} upcoming`
        : "none configured (CALENDAR_ICS_URLS)",
    ],
    ...(s.calendar.error ? ([["Calendar error", `<span class="error">${esc(s.calendar.error)}</span>`]] as [string, string][]) : []),
  ]);
}

async function action(btn: HTMLButtonElement, url: string, okMsg: string) {
  const msg = $("art-msg");
  btn.disabled = true;
  msg.className = "msg";
  msg.textContent = "Working…";
  try {
    const res = await fetch(url, { method: "POST" });
    const body = (await res.json()) as { error?: string };
    if (!res.ok) throw new Error(body.error ?? res.statusText);
    msg.textContent = okMsg;
  } catch (err) {
    msg.className = "msg error";
    msg.textContent = (err as Error).message;
  } finally {
    btn.disabled = false;
    void load();
  }
}

$("btn-generate").addEventListener("click", (e) => action(e.currentTarget as HTMLButtonElement, "/api/art/generate", "New piece is on the wall."));
$("btn-next").addEventListener("click", (e) => action(e.currentTarget as HTMLButtonElement, "/api/art/next", "Skipped."));
$("btn-refresh").addEventListener("click", async () => {
  await fetch("/api/refresh", { method: "POST" });
  void load();
});
$("btn-pick").addEventListener("click", async () => {
  const res = await fetch("/api/photos/pick", { method: "POST" });
  const body = (await res.json()) as { pickerUri?: string; qr?: string; error?: string };
  $("picker").innerHTML = body.pickerUri
    ? `<a class="button" href="${esc(body.pickerUri)}" target="_blank" rel="noopener">Open Google Photos picker</a>
       <span class="muted">Or scan with your phone:</span><img src="${body.qr}" alt="QR code for the Google Photos picker" />`
    : `<span class="error">${esc(body.error ?? "Could not start picker")}</span>`;
  void load();
});

void load();
setInterval(() => void load(), 5000);
