# Smart Display

A wall display for the house. It shows full-screen **AI-generated art** or **Google Photos**, with a frosted panel showing the **clock, weather and Google Calendar**. It runs on a Raspberry Pi 5 driving a screen through Chromium in kiosk mode.

Planned later: music, voice (mic) and presence detection (camera), and Home Assistant control.

## How it works

```
 Pi 5 ─ Node server (server/) ──────────────────────────────┐
   │      • Open-Meteo weather (no key)       every 10 min  │
   │      • Google Calendar secret iCal URLs  every 5 min   │
   │      • Art director:                                   │
   │          Claude writes a prompt from weather + season  │
   │          + time of day + today's events ─► Imagen /    │
   │          gpt-image ─► saved to data/art ─► rotated     │
   │        + Google Photos you pick on your phone          │
   │      • pushes updates to the screen over SSE           │
   └─ Chromium kiosk ─► web/ (Vite + TS, no framework) ─────┘
```

- **Art rotation:** a new AI piece is painted once a day (`ART_SCHEDULE=daily@07:00`, or `every:180` for more). In between, the display rotates through the saved archive every `ART_ROTATE_MINUTES`, at no cost. Nothing is generated during `QUIET_HOURS`, and the panel collapses to a dim clock.
- **Fallbacks:** if there is no network or no keys, it keeps showing the last good weather and calendar data and the archived art, or generated placeholder landscapes on a fresh install.
- **Google Photos:** Google no longer lets apps read a whole library or album (2025 API change). Instead you choose photos in Google's own picker, from the setup page on your phone, and they are downloaded and kept locally. `ART_SOURCE=alternate` mixes them with the AI art.
- **Privacy:** calendar event titles are sent to Claude only to theme the art. Set `ART_USE_CALENDAR=0` to turn that off. Images never contain text.

## What it costs

Each new AI piece is a paid image API call. Claude writing the prompt adds well under a cent.

| Schedule | Pieces / month | Rough cost |
|---|---|---|
| Daily (default) | ~30 | **~$1–1.50** |
| Every 3 h (not overnight) | ~180 | ~$5–8 |

These are estimates of about $0.02–0.05 per image at medium quality, from third-party price lists in November 2026. Check Google's or OpenAI's current pricing.

- **Spend limits:** `ART_MAX_PER_DAY` (default 3) and an optional `ART_MONTHLY_BUDGET` stop all paid calls once reached; the display keeps showing saved art.
- **Tracking:** the setup page shows pieces made today and this month, and the estimated spend.
- **Free options:** `ART_SOURCE=photos` (Google Photos only) or `mock` (built-in placeholder art) cost nothing.

## Quick start (any computer)

```bash
cd smart-display
npm install
npm run dev:mock       # sample data, no keys → http://localhost:5173
```

For real data, run `cp .env.example .env`, fill it in, then `npm run dev`. For production, run `npm run build && npm start`, which serves everything on :8080.

| Page | URL |
|---|---|
| Display | `/` (tap: show/hide panel · double-tap or → key: next piece · `?scale=1.3` to enlarge) |
| Setup / status (use from your phone) | `/setup.html` |

`?clock=12` or `?clock=24` overrides the clock format.

## Tests

```bash
npm test           # vitest: calendar recurrence/all-day, prompt context, cache fallback, weather mapping, art rotation
npm run typecheck
```

## Raspberry Pi install

See [deploy/README.md](deploy/README.md).

## Hardware: A4-size build

The target is **A4 at most** (210×297 mm) and a matte, print-like look. The display hangs **portrait or landscape**: the layout follows the screen, and new art is generated in the matching shape (`ART_ORIENTATION=auto`). These notes come from research in November 2026; **check prices and fit before buying**.

**Watch the width.** Most 13.3" 16:9 monitors are 305–322 mm wide, which is over A4. 3:2 and 4:3 panels fit, and they look more like a print.

| Role | Pick | Notes |
|---|---|---|
| Screen (recommended) | **Framework 13 matte display kit**: 13.5", 2256×1504, 3:2, genuinely matte + an eDP→HDMI driver board (~£30–50) | The best matte panel that fits A4, and 3:2 suits art. Small DIY job (ribbon + board). Outline fit is likely but unverified, so measure before cutting the frame. |
| Screen (plug-and-play) | **Waveshare 11.6" HDMI LCD (H)**, 1920×1080, 10-point touch (~£110–140) | Fits A4 easily and works out of the box with touch. The glass is **glossy**, so add a matte anti-glare film. 16:9 is narrow in portrait. |
| Mic | **ReSpeaker Lite** (35×86 mm, USB, echo cancellation) or **XVF3800** (≈100 mm square) | Needs small holes in the mat or frame. Echo cancellation only works on music played through the board's own audio output. |
| Camera | **Pi Camera Module 3 (Wide)** + the Pi 5 **mini camera cable** | 25×24 mm; needs a pinhole in the mat. Add a privacy cover. |

Allow a **25–35 mm deep box** behind the frame for the Pi 5 with its cooler, the driver board, the mic and the camera. Avoid USB-C-only monitors: the Pi 5 can't output video over USB-C. True colour e-paper at this size (Pimoroni Inky Impression 13.3") would fit but was ruled out: it has muted colours and slow refreshes, and can't run music or home-control screens.

## Roadmap

1. ✅ Weather, calendar, AI art, Google Photos.
2. **E-paper mode** (if you go that route): a headless Chromium screenshot of a static layout, dithered to the panel's palette, pushed over SPI every few minutes.
3. **Voice:** a Wyoming satellite with openWakeWord on the ReSpeaker, feeding Home Assistant Assist.
4. **Presence:** dim or wake the screen when someone is in the room (camera or mmWave sensor).
5. **Music:** a now-playing widget plus Spotify Connect / Music Assistant.
6. **Home control:** a Home Assistant WebSocket widget for scenes, lights and heating.
