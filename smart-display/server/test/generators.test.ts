import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ModelUnavailableError,
  geminiAspect,
  geminiGenerator,
  imagenAspect,
  openaiGenerator,
  openaiSize,
} from "../src/art/generators.ts";

const IMG = Buffer.from("fake-image").toString("base64");

function stubFetch(status: number, body: unknown) {
  const calls: { url: string; body: any }[] = [];
  vi.stubGlobal("fetch", async (url: string, init: RequestInit) => {
    calls.push({ url, body: JSON.parse(String(init.body)) });
    return new Response(JSON.stringify(body), { status });
  });
  return calls;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("image shape", () => {
  it("picks the closest supported ratio", () => {
    expect(imagenAspect({ width: 1920, height: 1080 })).toBe("16:9");
    expect(imagenAspect({ width: 2256, height: 1504 })).toBe("4:3");
    expect(imagenAspect({ width: 1504, height: 2256 })).toBe("3:4");
    expect(imagenAspect({ width: 1080, height: 1920 })).toBe("9:16");
    expect(imagenAspect({ width: 1000, height: 1050 })).toBe("1:1");
    expect(geminiAspect({ width: 2256, height: 1504 })).toBe("3:2");
    expect(geminiAspect({ width: 1504, height: 2256 })).toBe("2:3");
  });

  it("picks the OpenAI size by orientation", () => {
    expect(openaiSize({ width: 2256, height: 1504 })).toBe("1536x1024");
    expect(openaiSize({ width: 1504, height: 2256 })).toBe("1024x1536");
  });
});

describe("Gemini generator", () => {
  it("uses generateContent for Gemini image models and reads inlineData", async () => {
    const calls = stubFetch(200, {
      candidates: [{ content: { parts: [{ text: "here you go" }, { inlineData: { mimeType: "image/jpeg", data: IMG } }] } }],
    });
    const img = await geminiGenerator("k", "gemini-2.5-flash-image").generate("a lake", { width: 1504, height: 2256 });
    expect(calls[0].url).toMatch(/models\/gemini-2\.5-flash-image:generateContent$/);
    expect(calls[0].body.generationConfig).toEqual({ responseModalities: ["IMAGE"], imageConfig: { aspectRatio: "2:3" } });
    expect(calls[0].body.contents[0].parts[0].text).toBe("a lake");
    expect(img.ext).toBe("jpg");
    expect(img.data.toString()).toBe("fake-image");
  });

  it("keeps the :predict path for Imagen models", async () => {
    const calls = stubFetch(200, { predictions: [{ bytesBase64Encoded: IMG, mimeType: "image/png" }] });
    await geminiGenerator("k", "imagen-4.0-generate-001").generate("x", { width: 16, height: 9 });
    expect(calls[0].url).toMatch(/:predict$/);
    expect(calls[0].body.parameters.aspectRatio).toBe("16:9");
  });

  it("explains a retired model", async () => {
    stubFetch(404, { error: { message: "models/old-model is not found" } });
    const err = await geminiGenerator("k", "old-model").generate("x", { width: 16, height: 9 }).catch((e) => e);
    expect(err).toBeInstanceOf(ModelUnavailableError);
    expect(err.message).toMatch(/old-model.*GEMINI_IMAGE_MODEL/);
  });

  it("reports filtered images", async () => {
    stubFetch(200, { candidates: [{ content: { parts: [] }, finishReason: "IMAGE_SAFETY" }] });
    await expect(geminiGenerator("k", "g").generate("x", { width: 1, height: 1 })).rejects.toThrow(/IMAGE_SAFETY/);
  });
});

describe("OpenAI generator", () => {
  it("sends size and quality, and decodes b64_json", async () => {
    const calls = stubFetch(200, { data: [{ b64_json: IMG }] });
    const img = await openaiGenerator("k", "gpt-image-2", "low").generate("x", { width: 1504, height: 2256 });
    expect(calls[0].body).toMatchObject({ model: "gpt-image-2", size: "1024x1536", quality: "low" });
    expect(img.data.toString()).toBe("fake-image");
  });
});
