import type { Config } from "./config.ts";
import type { Weather, WeatherDay, WeatherHour } from "../../shared/types.ts";
import { describeWeather } from "../../shared/weatherCodes.ts";

export interface OpenMeteoResponse {
  utc_offset_seconds: number;
  current: {
    time: string;
    temperature_2m: number;
    apparent_temperature: number;
    weather_code: number;
    is_day: number;
    wind_speed_10m: number;
    relative_humidity_2m: number;
  };
  hourly: {
    time: string[];
    temperature_2m: number[];
    weather_code: number[];
    precipitation_probability: (number | null)[];
  };
  daily: {
    time: string[];
    weather_code: number[];
    temperature_2m_max: number[];
    temperature_2m_min: number[];
    sunrise: string[];
    sunset: string[];
    precipitation_probability_max: (number | null)[];
  };
}

export function buildWeatherUrl(cfg: Config): string {
  const p = new URLSearchParams({
    latitude: String(cfg.location.latitude),
    longitude: String(cfg.location.longitude),
    current:
      "temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,relative_humidity_2m",
    hourly: "temperature_2m,weather_code,precipitation_probability",
    daily:
      "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max",
    timezone: "auto",
    forecast_days: "6",
  });
  if (cfg.units === "imperial") {
    p.set("temperature_unit", "fahrenheit");
    p.set("wind_speed_unit", "mph");
  }
  return `https://api.open-meteo.com/v1/forecast?${p}`;
}

/** Open-Meteo returns local wall-clock times without an offset; convert to epoch ms. */
export function localToEpoch(local: string, offsetSeconds: number): number {
  const iso = local.length === 10 ? `${local}T00:00` : local;
  return Date.parse(`${iso}:00Z`) - offsetSeconds * 1000;
}

export function mapWeather(
  r: OpenMeteoResponse,
  meta: { locationName: string; units: Config["units"] },
  now = Date.now(),
): Weather {
  const off = r.utc_offset_seconds;
  const c = describeWeather(r.current.weather_code);

  const hourly: WeatherHour[] = [];
  for (let i = 0; i < r.hourly.time.length && hourly.length < 12; i++) {
    const time = localToEpoch(r.hourly.time[i], off);
    if (time + 3600_000 <= now) continue; // skip hours already over
    const code = r.hourly.weather_code[i];
    hourly.push({
      time,
      temp: Math.round(r.hourly.temperature_2m[i]),
      code,
      icon: describeWeather(code).icon,
      precipChance: r.hourly.precipitation_probability[i] ?? 0,
    });
  }

  const daily: WeatherDay[] = r.daily.time.map((date, i) => {
    const code = r.daily.weather_code[i];
    const d = describeWeather(code);
    return {
      date,
      high: Math.round(r.daily.temperature_2m_max[i]),
      low: Math.round(r.daily.temperature_2m_min[i]),
      code,
      label: d.label,
      icon: d.icon,
      precipChance: r.daily.precipitation_probability_max[i] ?? 0,
      sunrise: localToEpoch(r.daily.sunrise[i], off),
      sunset: localToEpoch(r.daily.sunset[i], off),
    };
  });

  return {
    locationName: meta.locationName,
    units: meta.units,
    updatedAt: now,
    current: {
      temp: Math.round(r.current.temperature_2m),
      feelsLike: Math.round(r.current.apparent_temperature),
      code: r.current.weather_code,
      label: c.label,
      icon: c.icon,
      isDay: r.current.is_day === 1,
      wind: Math.round(r.current.wind_speed_10m),
      humidity: Math.round(r.current.relative_humidity_2m),
    },
    hourly,
    daily,
  };
}

export async function fetchWeather(cfg: Config): Promise<Weather> {
  const res = await fetch(buildWeatherUrl(cfg), { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Open-Meteo ${res.status}: ${await res.text()}`);
  const body = (await res.json()) as OpenMeteoResponse;
  return mapWeather(body, { locationName: cfg.location.name, units: cfg.units });
}
