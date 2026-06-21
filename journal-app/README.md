# Journal

A private journaling app with **weekly AI insights**, storing entries in your own
**Notion** workspace. Built with Expo (React Native) so it runs on your iPhone or
Android phone.

## What it does today (MVP)

- ✍️ **Write entries** — title, body, mood, and tags
- 🔒 **Private storage in Notion** — each entry becomes a page in your Journal database
- 📖 **Browse entries** — see your recent entries with mood and tags
- ✨ **Weekly insight** — Claude reads your past 7 days and writes a short reflection
  (themes, mood arc, gentle observations, a prompt for next week), which you can
  save back to Notion

🎙️ **Speech-to-text** is the next milestone — see the roadmap below.

## How it's wired

```
 Phone (Expo app)
   ├─ Write / Entries  ──HTTPS──▶  Notion API   (your Journal database)
   └─ Weekly insight   ──HTTPS──▶  Claude API   (reads the week, writes reflection)
```

- The Journal database already exists in your Notion workspace:
  **Database ID `72699b7865ae4b22beb360b7b95b5dfd`** with properties
  Title, Date, Mood, Tags, Source, plus a Calendar view.

> ⚠️ **Security note.** This is a *personal* app: your API keys are bundled into
> the app via `.env`. That's fine for an app only you run. Before publishing to an
> app store, move the Notion and Claude calls behind a small backend so the keys
> never ship to the device. The code is structured (`src/api/*`) to make that swap
> easy.

## Setup

### 1. Install dependencies

```bash
cd journal-app
npm install
```

### 2. Connect Notion

1. Go to https://www.notion.so/my-integrations and create an **internal
   integration**. Copy its token (starts with `ntn_` or `secret_`).
2. Open the **Journal** database/page in Notion → `•••` menu → **Connections** →
   add your integration so it can read and write entries.

### 3. Add your keys

```bash
cp .env.example .env
```

Then edit `.env`:

```
EXPO_PUBLIC_NOTION_TOKEN=ntn_xxx
EXPO_PUBLIC_NOTION_DATABASE_ID=72699b7865ae4b22beb360b7b95b5dfd
EXPO_PUBLIC_ANTHROPIC_API_KEY=sk-ant-xxx          # from https://console.anthropic.com
EXPO_PUBLIC_ANTHROPIC_MODEL=claude-opus-4-8       # optional override
```

### 4. Run it

```bash
npx expo start
```

Install **Expo Go** on your phone and scan the QR code, or press `i` / `a` for the
iOS / Android simulator.

## Project structure

```
journal-app/
├─ App.js                  # navigation (Write / Entries / Insights tabs)
├─ src/
│  ├─ config.js            # env vars, mood/tag option lists
│  ├─ theme.js             # colors
│  ├─ api/
│  │  ├─ notion.js         # create/list entries, read entry text
│  │  └─ insights.js       # weekly reflection via the Claude API
│  └─ screens/
│     ├─ WriteScreen.js
│     ├─ EntriesScreen.js
│     └─ InsightsScreen.js
```

## Roadmap: speech-to-text

Speech-to-text is intentionally last because it's the most platform-specific
piece. When you're ready:

- **Option A — on-device (free, no API):** add
  [`expo-speech-recognition`](https://github.com/jamsch/expo-speech-recognition).
  It needs a **development build** (`npx expo run:ios` / `run:android`), not
  plain Expo Go, because it includes native code. Add a mic button on the Write
  screen that streams recognized text into the body field, and set the entry's
  `Source` to `Voice` when saving (the Notion database already supports it).
- **Option B — higher accuracy (cloud):** record audio with `expo-av`, send it to
  a speech-to-text model (e.g. Whisper) via a small backend, and drop the
  transcript into the body field.

The data model is already speech-ready: `createEntry(...)` accepts
`source: 'Voice'`, so dictated entries will be distinguishable from typed ones.

## Cost notes

- **Notion**: free for personal use.
- **Claude**: you pay per weekly-insight generation (a few cents). Switch
  `EXPO_PUBLIC_ANTHROPIC_MODEL` to `claude-haiku-4-5` for a cheaper, faster option
  if you'd like to trade some depth for cost.
