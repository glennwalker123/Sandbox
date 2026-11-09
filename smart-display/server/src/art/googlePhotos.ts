import fs from "node:fs/promises";
import path from "node:path";
import type { Config } from "../config.ts";
import type { ArtStore } from "./store.ts";

/**
 * Google Photos via the Picker API.
 *
 * Since 2025 Google no longer lets third-party apps read a whole library or
 * album. Instead the user picks photos in Google's own picker (on their phone,
 * by scanning a QR code on the display); we then download the chosen photos
 * once and keep them locally in the "photos" art store.
 */

const SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly";
const API = "https://photospicker.googleapis.com/v1";

interface Tokens {
  access_token: string;
  refresh_token?: string;
  expires_at: number;
}

interface PickerSession {
  id: string;
  pickerUri: string;
  mediaItemsSet?: boolean;
  pollingConfig?: { pollInterval?: string; timeoutIn?: string };
  expireTime?: string;
}

interface PickedItem {
  id: string;
  type?: string;
  createTime?: string;
  mediaFile: { baseUrl: string; mimeType?: string; filename?: string };
}

export type PickerStatus =
  | { state: "idle" }
  | { state: "waiting"; pickerUri: string; startedAt: number }
  | { state: "importing"; done: number; total: number }
  | { state: "done"; imported: number; at: number }
  | { state: "error"; message: string };

export class GooglePhotos {
  private tokens: Tokens | null = null;
  private status: PickerStatus = { state: "idle" };
  private pollTimer: NodeJS.Timeout | null = null;

  constructor(
    private readonly cfg: Config,
    private readonly store: ArtStore,
    private readonly tokenPath: string,
    private readonly onImported: (count: number) => void,
  ) {}

  get configured() {
    return Boolean(this.cfg.google.clientId && this.cfg.google.clientSecret);
  }

  get connected() {
    return Boolean(this.tokens?.refresh_token || this.tokens?.access_token);
  }

  getStatus(): PickerStatus {
    return this.status;
  }

  async init() {
    try {
      this.tokens = JSON.parse(await fs.readFile(this.tokenPath, "utf8")) as Tokens;
    } catch {
      this.tokens = null;
    }
  }

  authUrl(): string {
    const p = new URLSearchParams({
      client_id: this.cfg.google.clientId ?? "",
      redirect_uri: this.cfg.google.redirectUri,
      response_type: "code",
      scope: SCOPE,
      access_type: "offline",
      prompt: "consent",
    });
    return `https://accounts.google.com/o/oauth2/v2/auth?${p}`;
  }

  async handleCallback(code: string) {
    await this.tokenRequest({
      code,
      grant_type: "authorization_code",
      redirect_uri: this.cfg.google.redirectUri,
    });
  }

  private async tokenRequest(params: Record<string, string>) {
    const res = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: this.cfg.google.clientId ?? "",
        client_secret: this.cfg.google.clientSecret ?? "",
        ...params,
      }),
    });
    const body = (await res.json()) as { access_token?: string; refresh_token?: string; expires_in?: number; error?: string };
    if (!res.ok || !body.access_token) throw new Error(`Google token error: ${body.error ?? res.status}`);
    this.tokens = {
      access_token: body.access_token,
      refresh_token: body.refresh_token ?? this.tokens?.refresh_token,
      expires_at: Date.now() + (body.expires_in ?? 3600) * 1000 - 60_000,
    };
    await fs.mkdir(path.dirname(this.tokenPath), { recursive: true });
    await fs.writeFile(this.tokenPath, JSON.stringify(this.tokens), { mode: 0o600 });
  }

  private async accessToken(): Promise<string> {
    if (!this.tokens) throw new Error("Google Photos is not connected yet");
    if (Date.now() < this.tokens.expires_at) return this.tokens.access_token;
    if (!this.tokens.refresh_token) throw new Error("Google Photos session expired; reconnect it");
    await this.tokenRequest({ grant_type: "refresh_token", refresh_token: this.tokens.refresh_token });
    return this.tokens!.access_token;
  }

  private async api<T>(method: string, pathname: string, body?: unknown): Promise<T> {
    const res = await fetch(`${API}${pathname}`, {
      method,
      headers: { authorization: `Bearer ${await this.accessToken()}`, "content-type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const textBody = await res.text();
    if (!res.ok) throw new Error(`Photos Picker ${res.status}: ${textBody.slice(0, 300)}`);
    return (textBody ? JSON.parse(textBody) : {}) as T;
  }

  /** Starts a picking session; the returned URI is shown as a QR code on the display. */
  async startPicking(): Promise<string> {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    const session = await this.api<PickerSession>("POST", "/sessions", {});
    this.status = { state: "waiting", pickerUri: session.pickerUri, startedAt: Date.now() };
    this.schedulePoll(session, parseDuration(session.pollingConfig?.pollInterval) ?? 5000);
    return session.pickerUri;
  }

  private schedulePoll(session: PickerSession, intervalMs: number) {
    const deadline = Date.now() + 30 * 60_000;
    const poll = async () => {
      try {
        const s = await this.api<PickerSession>("GET", `/sessions/${session.id}`);
        if (s.mediaItemsSet) {
          await this.importSession(s.id);
          return;
        }
        if (Date.now() > deadline) {
          this.status = { state: "error", message: "Picking timed out — start again from the setup page" };
          await this.api("DELETE", `/sessions/${session.id}`).catch(() => {});
          return;
        }
        this.pollTimer = setTimeout(poll, Math.max(2000, parseDuration(s.pollingConfig?.pollInterval) ?? intervalMs));
      } catch (err) {
        this.status = { state: "error", message: (err as Error).message };
      }
    };
    this.pollTimer = setTimeout(poll, intervalMs);
  }

  private async importSession(sessionId: string) {
    const items: PickedItem[] = [];
    let pageToken: string | undefined;
    do {
      const q = new URLSearchParams({ sessionId, pageSize: "100" });
      if (pageToken) q.set("pageToken", pageToken);
      const page = await this.api<{ mediaItems?: PickedItem[]; nextPageToken?: string }>("GET", `/mediaItems?${q}`);
      items.push(...(page.mediaItems ?? []));
      pageToken = page.nextPageToken;
    } while (pageToken);

    const photos = items.filter((i) => i.type !== "VIDEO" && !i.mediaFile.mimeType?.startsWith("video/"));
    this.status = { state: "importing", done: 0, total: photos.length };
    let imported = 0;
    for (const item of photos) {
      if (this.store.has(item.id)) continue;
      try {
        // Size the download for a 4K-ish screen; baseUrls need the OAuth header.
        const res = await fetch(`${item.mediaFile.baseUrl}=w3840-h2160`, {
          headers: { authorization: `Bearer ${await this.accessToken()}` },
        });
        if (!res.ok) throw new Error(`download ${res.status}`);
        const ext = item.mediaFile.mimeType === "image/png" ? "png" : item.mediaFile.mimeType === "image/webp" ? "webp" : "jpg";
        await this.store.save(Buffer.from(await res.arrayBuffer()), ext, {
          id: item.id,
          title: item.mediaFile.filename,
          createdAt: item.createTime ? Date.parse(item.createTime) : Date.now(),
        });
        imported++;
      } catch (err) {
        console.warn("[photos] skipped item:", (err as Error).message);
      }
      this.status = { state: "importing", done: imported, total: photos.length };
    }
    await this.api("DELETE", `/sessions/${sessionId}`).catch(() => {});
    this.status = { state: "done", imported, at: Date.now() };
    this.onImported(imported);
  }
}

/** "5s" / "3.5s" -> ms */
export function parseDuration(v: string | undefined): number | undefined {
  const m = v?.match(/^([\d.]+)s$/);
  return m ? Math.round(Number(m[1]) * 1000) : undefined;
}
