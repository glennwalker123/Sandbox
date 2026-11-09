import { describe, expect, it } from "vitest";
import { localToEpoch, mapWeather, type OpenMeteoResponse } from "../src/weather.ts";

const sample: OpenMeteoResponse = {
  utc_offset_seconds: 3600,
  current: { time: "2026-11-09T14:00", temperature_2m: 9.4, apparent_temperature: 6.6, weather_code: 61, is_day: 1, wind_speed_10m: 13.8, relative_humidity_2m: 81 },
  hourly: {
    time: ["2026-11-09T13:00", "2026-11-09T14:00", "2026-11-09T15:00"],
    temperature_2m: [9, 9.4, 8.6],
    weather_code: [3, 61, 63],
    precipitation_probability: [20, 60, null],
  },
  daily: {
    time: ["2026-11-09"],
    weather_code: [63],
    temperature_2m_max: [10.6],
    temperature_2m_min: [4.2],
    sunrise: ["2026-11-09T07:12"],
    sunset: ["2026-11-09T16:21"],
    precipitation_probability_max: [85],
  },
};

describe("weather mapping", () => {
  it("converts local times using the UTC offset", () => {
    expect(new Date(localToEpoch("2026-11-09T14:00", 3600)).toISOString()).toBe("2026-11-09T13:00:00.000Z");
  });

  it("maps Open-Meteo into display data", () => {
    const now = Date.parse("2026-11-09T13:30:00Z"); // 14:30 local
    const w = mapWeather(sample, { locationName: "Home", units: "metric" }, now);
    expect(w.current).toMatchObject({ temp: 9, feelsLike: 7, label: "Light rain", icon: "rain", isDay: true });
    // 13:00 local has fully passed; 14:00 local is in progress
    expect(w.hourly.map((h) => h.temp)).toEqual([9, 9]);
    expect(w.hourly[1].precipChance).toBe(0);
    expect(w.daily[0]).toMatchObject({ high: 11, low: 4, icon: "rain", precipChance: 85 });
    expect(new Date(w.daily[0].sunset).toISOString()).toBe("2026-11-09T15:21:00.000Z");
  });
});
