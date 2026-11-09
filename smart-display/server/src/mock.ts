import type { CalendarData, Weather } from "../../shared/types.ts";
import { describeWeather } from "../../shared/weatherCodes.ts";

const HOUR = 3_600_000;

function at(dayOffset: number, h: number, m = 0) {
  const d = new Date();
  d.setDate(d.getDate() + dayOffset);
  d.setHours(h, m, 0, 0);
  return d.getTime();
}

export function mockWeather(locationName: string, units: Weather["units"]): Weather {
  const now = Date.now();
  const f = (c: number) => (units === "imperial" ? Math.round(c * 1.8 + 32) : c);
  const codes = [61, 3, 2, 0, 80, 71];
  const today = new Date();
  return {
    locationName,
    units,
    updatedAt: now,
    current: { temp: f(9), feelsLike: f(6), code: 61, ...describeWeather(61), isDay: true, wind: 14, humidity: 82 },
    hourly: Array.from({ length: 12 }, (_, i) => {
      const code = codes[Math.floor(i / 3) % codes.length];
      return { time: Math.floor(now / HOUR) * HOUR + (i + 1) * HOUR, temp: f(9 - Math.round(i / 3)), code, icon: describeWeather(code).icon, precipChance: Math.max(0, 70 - i * 6) };
    }),
    daily: codes.map((code, i) => {
      const d = new Date(today);
      d.setDate(d.getDate() + i);
      const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      return { date: iso, high: f(11 - i), low: f(4 - (i % 3)), code, ...describeWeather(code), precipChance: [80, 30, 10, 0, 60, 40][i], sunrise: at(i, 7, 12), sunset: at(i, 16, 22) };
    }),
  };
}

export function mockCalendar(): CalendarData {
  const ev = (id: string, title: string, start: number, end: number, extra: Partial<CalendarData["events"][0]> = {}) => ({ id, title, start, end, allDay: false, calendar: "Family", ...extra });
  return {
    updatedAt: Date.now(),
    events: [
      ev("1", "Bin day", at(0, 0), at(1, 0), { allDay: true }),
      ev("2", "School run", at(0, 8, 15), at(0, 8, 45)),
      ev("3", "Dentist — Sam", at(0, 15, 30), at(0, 16, 15), { location: "High St Dental" }),
      ev("4", "Family dinner", at(0, 19), at(0, 21)),
      ev("5", "Football practice", at(1, 17, 30), at(1, 19)),
      ev("6", "Grandma's birthday", at(2, 0), at(3, 0), { allDay: true }),
      ev("7", "Yoga", at(3, 18), at(3, 19), { calendar: "Glenn" }),
    ].filter((e) => e.end > Date.now()),
  };
}
