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

- **Art rotation:** a new AI piece is generated every `ART_INTERVAL_MINUTES` (default 3 h). In between, the display rotates through the archive every `ART_ROTATE_MINUTES`. Nothing is generated during `QUIET_HOURS`, and the panel collapses to a dim clock. With roughly six generations a day, image costs stay small.
- **Fallbacks:** if there is no network or no keys, it keeps showing the last good weather and calendar data and the archived art, or generated placeholder landscapes on a fresh install.
- **Google Photos:** Google no longer lets apps read a whole library or album (2025 API change). Instead you choose photos in Google's own picker, from the setup page on your phone, and they are downloaded and kept locally. `ART_SOURCE=alternate` mixes them with the AI art.
- **Privacy:** calendar event titles are sent to Claude only to theme the art. Set `ART_USE_CALENDAR=0` to turn that off. Images never contain text.

## Quick start (any computer)

```bash
cd smart-display
npm install
npm run dev:mock       # sample data, no keys → http://localhost:5173
```

For real data, run `cp .env.example .env`, fill it in, then `npm run dev`. For production, run `npm run build && npm start`, which serves everything on :8080.

| Page | URL |
|---|---|
| Display | `/` (tap: show/hide panel · double-tap or → key: next piece) |
| Setup / status (use from your phone) | `/setup.html` |

`?clock=12` or `?clock=24` overrides the clock format.

## Tests

```bash
npm test           # vitest: calendar recurrence/all-day, prompt context, cache fallback, weather mapping, art rotation
npm run typecheck
```

## Raspberry Pi install

See [deploy/README.md](deploy/README.md).

## Hardware notes

These come from research in November 2026. **Check prices and stock before buying.**

| Role | Pick | Why |
|---|---|---|
| Screen (paper-like) | **Samsung The Frame 32"** (~$530–600) | The matte "canvas" anti-glare finish is the most print-like HDMI panel available. Mounts flush. 1080p, no touch. |
| Screen (touch) | **Dell P2424HT** 23.8" (~$330–400 / £299) | Matte anti-glare, 10-point USB touch that works on Linux. Needs a frame built around it for the wall. |
| Screen (budget) | **Raspberry Pi Monitor 15.6"** (~$100 / £75) + matte paper-feel film | Native to the Pi, thin, VESA mount, built-in speakers. Dimmer (~250 nits). |
| Screen (true e-paper) | **Pimoroni Inky Impression 13.3"** Spectra 6 (~£191–230) | Looks exactly like a print, but 6 muted colours and a ~12 s refresh. SPI rather than HDMI, so it needs a render-to-image mode (see roadmap). No music or video UI. |
| Mic | **ReSpeaker XVF3800 USB 4-mic array** (~$60 / £38–53) | On-board echo cancellation and beamforming, so the wake word still works while music plays. |
| Camera | **Pi Camera Module 3 Wide** (~$35–45) + **Pi 5 ribbon cable** | 120° field of view for presence detection. Add a privacy cover. Alternative: a Logitech Brio 101 (USB, shutter). |

The Dell **P2424HEB** is the only screen found with a built-in camera (with shutter) and mics. TCL NXTPAPER isn't sold as an HDMI monitor, and the Meural Canvas has no HDMI input.

## Roadmap

1. ✅ Weather, calendar, AI art, Google Photos.
2. **E-paper mode** (if you go that route): a headless Chromium screenshot of a static layout, dithered to the panel's palette, pushed over SPI every few minutes.
3. **Voice:** a Wyoming satellite with openWakeWord on the ReSpeaker, feeding Home Assistant Assist.
4. **Presence:** dim or wake the screen when someone is in the room (camera or mmWave sensor).
5. **Music:** a now-playing widget plus Spotify Connect / Music Assistant.
6. **Home control:** a Home Assistant WebSocket widget for scenes, lights and heating.
