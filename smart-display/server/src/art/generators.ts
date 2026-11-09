import type { Config } from "../config.ts";

export interface GeneratedImage {
  data: Buffer;
  ext: "png" | "jpg" | "webp";
}

export interface ImageGenerator {
  name: string;
  generate(prompt: string): Promise<GeneratedImage>;
}

async function postJson(url: string, body: unknown, headers: Record<string, string>) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(180_000),
  });
  const textBody = await res.text();
  if (!res.ok) throw new Error(`${new URL(url).host} ${res.status}: ${textBody.slice(0, 500)}`);
  return JSON.parse(textBody) as unknown;
}

/** OpenAI Images API (gpt-image-1 and successors). */
export function openaiGenerator(apiKey: string, model: string): ImageGenerator {
  return {
    name: `openai:${model}`,
    async generate(prompt) {
      const body = (await postJson(
        "https://api.openai.com/v1/images/generations",
        { model, prompt, size: "1536x1024", n: 1 },
        { authorization: `Bearer ${apiKey}` },
      )) as { data?: { b64_json?: string }[] };
      const b64 = body.data?.[0]?.b64_json;
      if (!b64) throw new Error("OpenAI returned no image");
      return { data: Buffer.from(b64, "base64"), ext: "png" };
    },
  };
}

/** Google Imagen through the Gemini API. */
export function geminiGenerator(apiKey: string, model: string): ImageGenerator {
  return {
    name: `gemini:${model}`,
    async generate(prompt) {
      const body = (await postJson(
        `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:predict`,
        { instances: [{ prompt }], parameters: { sampleCount: 1, aspectRatio: "16:9" } },
        { "x-goog-api-key": apiKey },
      )) as { predictions?: { bytesBase64Encoded?: string; mimeType?: string }[] };
      const p = body.predictions?.[0];
      if (!p?.bytesBase64Encoded) throw new Error("Gemini returned no image (it may have been filtered)");
      return { data: Buffer.from(p.bytesBase64Encoded, "base64"), ext: p.mimeType === "image/jpeg" ? "jpg" : "png" };
    },
  };
}

export function createGenerator(cfg: Config): ImageGenerator | null {
  if (cfg.art.provider === "openai" && cfg.keys.openai) return openaiGenerator(cfg.keys.openai, cfg.models.openaiImage);
  if (cfg.art.provider === "gemini" && cfg.keys.gemini) return geminiGenerator(cfg.keys.gemini, cfg.models.geminiImage);
  // Fall back to whichever key exists.
  if (cfg.keys.gemini) return geminiGenerator(cfg.keys.gemini, cfg.models.geminiImage);
  if (cfg.keys.openai) return openaiGenerator(cfg.keys.openai, cfg.models.openaiImage);
  return null;
}
