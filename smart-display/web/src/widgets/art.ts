import type { ArtPiece } from "../../../shared/types.ts";
import { esc } from "../util.ts";

/** Two stacked <img> layers; the new piece loads in the hidden one, then crossfades. */
export class ArtView {
  private layers: HTMLImageElement[];
  private front = 0;
  private currentId: string | null = null;

  constructor(
    private readonly root: HTMLElement,
    private readonly caption: HTMLElement,
  ) {
    this.layers = [...root.querySelectorAll<HTMLImageElement>(".art-layer")];
  }

  async show(piece: ArtPiece | null) {
    if (!piece || piece.id === this.currentId) return;
    this.currentId = piece.id;
    const back = this.layers[1 - this.front];
    const front = this.layers[this.front];
    const isPhoto = piece.source === "photos";

    try {
      back.src = piece.url;
      await back.decode();
    } catch {
      return; // keep the current image if the new one fails to load
    }
    if (this.currentId !== piece.id) return; // a newer piece arrived while loading

    back.classList.toggle("photo", isPhoto);
    // Photos are letterboxed over a blurred copy of themselves rather than cropped.
    this.root.classList.toggle("photo-backdrop", isPhoto);
    this.root.style.backgroundImage = isPhoto ? `url("${piece.url}")` : "";
    back.classList.add("visible");
    front.classList.remove("visible");
    this.front = 1 - this.front;

    this.caption.innerHTML =
      piece.source === "photos"
        ? piece.createdAt
          ? `<div>${new Date(piece.createdAt).toLocaleDateString(undefined, { month: "long", year: "numeric" })}</div>`
          : ""
        : piece.title
          ? `<div class="title">${esc(piece.title)}</div>`
          : "";
  }
}
