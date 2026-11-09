import fs from "node:fs/promises";
import path from "node:path";
import type { ArtPiece, ArtSourceKind } from "../../../shared/types.ts";

/** A folder of images plus an index.json of their metadata, newest first. */
export class ArtStore {
  private pieces: ArtPiece[] = [];
  private loaded = false;

  constructor(
    readonly dir: string,
    readonly source: ArtSourceKind,
    private readonly keep: number,
    private readonly urlPrefix = `/art/${source}`,
  ) {}

  private get indexPath() {
    return path.join(this.dir, "index.json");
  }

  async load(): Promise<ArtPiece[]> {
    if (this.loaded) return this.pieces;
    await fs.mkdir(this.dir, { recursive: true });
    try {
      const all = JSON.parse(await fs.readFile(this.indexPath, "utf8")) as ArtPiece[];
      const present = await Promise.all(
        all.map(async (p) => ((await exists(path.join(this.dir, path.basename(p.url)))) ? p : null)),
      );
      this.pieces = present.filter((p): p is ArtPiece => p !== null);
    } catch {
      this.pieces = [];
    }
    this.loaded = true;
    return this.pieces;
  }

  list(): ArtPiece[] {
    return this.pieces;
  }

  has(id: string): boolean {
    return this.pieces.some((p) => p.id === id);
  }

  async save(
    data: Buffer | string,
    ext: string,
    meta: Omit<ArtPiece, "id" | "url" | "source" | "createdAt"> & { id?: string; createdAt?: number },
  ): Promise<ArtPiece> {
    await this.load();
    const createdAt = meta.createdAt ?? Date.now();
    const id = meta.id ?? `${createdAt}-${Math.random().toString(36).slice(2, 8)}`;
    const file = `${id.replace(/[^\w.-]/g, "_")}.${ext}`;
    await fs.writeFile(path.join(this.dir, file), data);
    const piece: ArtPiece = { ...meta, id, createdAt, source: this.source, url: `${this.urlPrefix}/${file}` };
    this.pieces = [piece, ...this.pieces.filter((p) => p.id !== id)];
    await this.prune();
    await this.writeIndex();
    return piece;
  }

  private async prune() {
    const drop = this.pieces.slice(this.keep);
    this.pieces = this.pieces.slice(0, this.keep);
    await Promise.all(drop.map((p) => fs.rm(path.join(this.dir, path.basename(p.url)), { force: true })));
  }

  private async writeIndex() {
    const tmp = `${this.indexPath}.tmp`;
    await fs.writeFile(tmp, JSON.stringify(this.pieces, null, 2));
    await fs.rename(tmp, this.indexPath);
  }
}

async function exists(p: string) {
  try {
    await fs.access(p);
    return true;
  } catch {
    return false;
  }
}
