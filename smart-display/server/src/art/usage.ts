import fs from "node:fs/promises";
import path from "node:path";

export interface UsageEntry {
  at: number;
  model: string;
  cost: number; // estimated USD
}

export interface UsagePeriod {
  count: number;
  cost: number;
}

export interface UsageLimits {
  maxPerDay: number;
  monthlyBudget: number | null;
  costPerImage: number;
}

const KEEP_MS = 400 * 86_400_000;

const sameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
const sameMonth = (a: Date, b: Date) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth();

/** Records every paid image generation, and enforces the daily count and monthly budget. */
export class UsageLog {
  private entries: UsageEntry[] = [];

  constructor(private readonly file: string | null) {}

  async load() {
    if (!this.file) return;
    try {
      this.entries = JSON.parse(await fs.readFile(this.file, "utf8")) as UsageEntry[];
    } catch {
      this.entries = [];
    }
  }

  async record(entry: UsageEntry) {
    this.entries = [...this.entries.filter((e) => entry.at - e.at < KEEP_MS), entry];
    if (!this.file) return;
    await fs.mkdir(path.dirname(this.file), { recursive: true });
    const tmp = `${this.file}.tmp`;
    await fs.writeFile(tmp, JSON.stringify(this.entries));
    await fs.rename(tmp, this.file);
  }

  summary(now: number): { today: UsagePeriod; month: UsagePeriod } {
    const d = new Date(now);
    const sum = (pred: (e: Date) => boolean) => {
      const hits = this.entries.filter((e) => pred(new Date(e.at)));
      return { count: hits.length, cost: Math.round(hits.reduce((s, e) => s + e.cost, 0) * 100) / 100 };
    };
    return { today: sum((e) => sameDay(e, d)), month: sum((e) => sameMonth(e, d)) };
  }

  /** Returns why another generation isn't allowed right now, or null if it is. */
  blockedReason(now: number, limits: UsageLimits): string | null {
    const { today, month } = this.summary(now);
    if (today.count >= limits.maxPerDay) {
      return `Daily limit reached (${limits.maxPerDay} new pieces today). Showing saved art until tomorrow.`;
    }
    if (limits.monthlyBudget !== null && month.cost + limits.costPerImage > limits.monthlyBudget + 1e-9) {
      return `Monthly budget reached (~$${month.cost.toFixed(2)} of $${limits.monthlyBudget.toFixed(2)}). Showing saved art until next month.`;
    }
    return null;
  }
}
