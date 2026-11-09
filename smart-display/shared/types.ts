// Types shared by the server and the display frontend.

export type Units = "metric" | "imperial";

export type WeatherIcon =
  | "clear" | "partly" | "cloudy" | "fog" | "drizzle"
  | "rain" | "snow" | "storm";

export interface WeatherNow {
  temp: number;
  feelsLike: number;
  code: number;
  label: string;
  icon: WeatherIcon;
  isDay: boolean;
  wind: number;
  humidity: number;
}

export interface WeatherDay {
  date: string; // YYYY-MM-DD (local to the location)
  high: number;
  low: number;
  code: number;
  label: string;
  icon: WeatherIcon;
  precipChance: number;
  sunrise: number; // epoch ms
  sunset: number; // epoch ms
}

export interface WeatherHour {
  time: number; // epoch ms
  temp: number;
  code: number;
  icon: WeatherIcon;
  precipChance: number;
}

export interface Weather {
  locationName: string;
  units: Units;
  updatedAt: number;
  current: WeatherNow;
  hourly: WeatherHour[]; // next 12 hours
  daily: WeatherDay[]; // today + next days
}

export interface CalendarEvent {
  id: string;
  title: string;
  start: number; // epoch ms (all-day: local midnight)
  end: number;
  allDay: boolean;
  location?: string;
  calendar?: string;
}

export interface CalendarData {
  updatedAt: number;
  events: CalendarEvent[];
}

export type ArtSourceKind = "ai" | "photos" | "mock";

export interface ArtPiece {
  id: string;
  url: string; // served path, e.g. /art/ai/123.png
  source: ArtSourceKind;
  createdAt: number;
  prompt?: string;
  title?: string;
}

export interface DisplayConfig {
  locationName: string;
  units: Units;
  quietHours: [number, number] | null; // [startHour, endHour) local time
  mock: boolean;
}

export interface DisplayState {
  config: DisplayConfig;
  weather: Weather | null;
  calendar: CalendarData | null;
  art: ArtPiece | null;
}

export type StreamEvent =
  | { type: "state"; data: DisplayState }
  | { type: "weather"; data: Weather }
  | { type: "calendar"; data: CalendarData }
  | { type: "art"; data: ArtPiece };
