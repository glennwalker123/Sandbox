import { describe, expect, it } from "vitest";
import { imagenAspect, openaiSize } from "../src/art/generators.ts";

describe("image shape", () => {
  it("picks the closest Imagen ratio", () => {
    expect(imagenAspect({ width: 1920, height: 1080 })).toBe("16:9");
    expect(imagenAspect({ width: 2256, height: 1504 })).toBe("4:3"); // 3:2 panel
    expect(imagenAspect({ width: 1504, height: 2256 })).toBe("3:4");
    expect(imagenAspect({ width: 1080, height: 1920 })).toBe("9:16");
    expect(imagenAspect({ width: 1000, height: 1050 })).toBe("1:1");
  });

  it("picks the OpenAI size by orientation", () => {
    expect(openaiSize({ width: 2256, height: 1504 })).toBe("1536x1024");
    expect(openaiSize({ width: 1504, height: 2256 })).toBe("1024x1536");
  });
});
