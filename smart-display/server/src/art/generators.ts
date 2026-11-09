import type { Config, OpenAIQuality } from "../config.ts";
import { orientationOf, type Shape } from "../../../shared/types.ts";

export interface GeneratedImage {
  data: Buffer;
  ext: "png" | "jpg" | "webp";
}

export interface ImageGenerator {
  name: string;
  generate(prompt: string, shape: Shape): Promise<GeneratedImage>;
}

/** Pick the supported "W:H" ratio closest to the screen's shape. */
export function closestRatio<T extends string>(shape: Shape, ratios: readonly T[]): T {
  const target = Math.log(shape.width / shape.height);
  let best = ratios[0];
  let bestDiff = Infinity;
  for (const r of ratios) {
    const [w, h] = r.split(":").map(Number);
    const diff = Math.abs(Math.log(w / h) - target);
    if (diff < bestDiff) [best, bestDiff] = [r, diff];
  }
  return best;
}

const IMAGEN_RATIOS = ["16:9", "4:3", "1:1", "3:4", "9:16"] as const;
const GEMINI_RATIOS = ["16:9", "3:2", "4:3", "5:4", "1:1", "4:5", "3:4", "2:3", "9:16"] as const;

export const imagenAspect = (shape: Shape) => closestRatio(shape, IMAGEN_RATIOS);
export const geminiAspect = (shape: Shape) => closestRatio(shape, GEMINI_RATIOS);

export function openaiSize(shape: Shape): "1536x1024" | "1024x1536" {
  return orientationOf(shape) === "landscape" ? "1536x1024" : "1024x1536";
}

const extFor = (mime: string | undefined): GeneratedImage["ext"] =>
  mime === "image/jpeg" ? "jpg" : mime === "image/webp" ? "webp" : "png";

export class ModelUnavailableError extends Error {}

async function postJson(url: string, body: unknown, headers: Record<string, string>, model: string, envVar: string) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(180_000),
  });
  const text = await res.text();
  if (!res.ok) {
    // Image models are retired regularly; make that failure obvious on the setup page.
    if (res.status === 404 || /not found|not supported|deprecated|does not exist/i.test(text)) {
      throw new ModelUnavailableError(`Image model "${model}" is unavailable (${res.status}). Set ${envVar} to a current model.`);
    }
    throw new Error(`${new URL(url).host} ${res.status}: ${text.slice(0, 400)}`);
  }
  return JSON.parse(text) as unknown;
}

/** OpenAI Images API (GPT Image models). */
export function openaiGenerator(apiKey: string, model: string, quality: OpenAIQuality): ImageGenerator {
  return {
    name: `openai:${model}`,
    async generate(prompt, shape) {
      const body = (await postJson(
        "https://api.openai.com/v1/images/generations",
        { model, prompt, size: openaiSize(shape), quality, n: 1 },
        { authorization: `Bearer ${apiKey}` },
        model,
        "OPENAI_IMAGE_MODEL",
      )) as { data?: { b64_json?: string }[] };
      const b64 = body.data?.[0]?.b64_json;
      if (!b64) throw new Error("OpenAI returned no image");
      return { data: Buffer.from(b64, "base64"), ext: "png" };
    },
  };
}

type GeminiPart = { text?: string; inlineData?: { mimeType?: string; data?: string }; inline_data?: { mime_type?: string; data?: string } };

/**
 * Google image generation through the Gemini API. Gemini image models use
 * generateContent; older Imagen models (IDs starting "imagen-") use :predict.
 */
export function geminiGenerator(apiKey: string, model: string): ImageGenerator {
  const base = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}`;
  const headers = { "x-goog-api-key": apiKey };
  return {
    name: `gemini:${model}`,
    async generate(prompt, shape) {
      if (model.startsWith("imagen-")) {
        const body = (await postJson(
          `${base}:predict`,
          { instances: [{ prompt }], parameters: { sampleCount: 1, aspectRatio: imagenAspect(shape) } },
          headers,
          model,
          "GEMINI_IMAGE_MODEL",
        )) as { predictions?: { bytesBase64Encoded?: string; mimeType?: string }[] };
        const p = body.predictions?.[0];
        if (!p?.bytesBase64Encoded) throw new Error("Imagen returned no image (it may have been filtered)");
        return { data: Buffer.from(p.bytesBase64Encoded, "base64"), ext: extFor(p.mimeType) };
      }

      const body = (await postJson(
        `${base}:generateContent`,
        {
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { responseModalities: ["IMAGE"], imageConfig: { aspectRatio: geminiAspect(shape) } },
        },
        headers,
        model,
        "GEMINI_IMAGE_MODEL",
      )) as { candidates?: { content?: { parts?: GeminiPart[] }; finishReason?: string }[] };
      const parts = body.candidates?.[0]?.content?.parts ?? [];
      for (const part of parts) {
        const data = part.inlineData?.data ?? part.inline_data?.data;
        if (data) return { data: Buffer.from(data, "base64"), ext: extFor(part.inlineData?.mimeType ?? part.inline_data?.mime_type) };
      }
      const reason = body.candidates?.[0]?.finishReason;
      throw new Error(`Gemini returned no image${reason ? ` (${reason})` : ""}; it may have been filtered`);
    },
  };
}

export function createGenerator(cfg: Config): ImageGenerator | null {
  const openai = () => openaiGenerator(cfg.keys.openai!, cfg.models.openaiImage, cfg.models.openaiQuality);
  const gemini = () => geminiGenerator(cfg.keys.gemini!, cfg.models.geminiImage);
  if (cfg.art.provider === "openai" && cfg.keys.openai) return openai();
  if (cfg.art.provider === "gemini" && cfg.keys.gemini) return gemini();
  // Fall back to whichever key exists.
  if (cfg.keys.gemini) return gemini();
  if (cfg.keys.openai) return openai();
  return null;
}
