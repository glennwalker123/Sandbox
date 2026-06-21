// Generates a weekly reflection from the past week's journal entries using the
// Claude API. Called directly over fetch (the Node SDK adds bundler/polyfill
// friction in React Native, and there is no browser CORS on a device).

import { ANTHROPIC_API_KEY, ANTHROPIC_MODEL } from '../config';
import { listEntries, getEntryText, createEntry } from './notion';

const ANTHROPIC_URL = 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';

const SYSTEM_PROMPT = `You are a warm, perceptive journaling companion. You are given a person's journal entries from the past week. Write a short weekly reflection for them.

Structure your response in plain markdown with these sections:
- **This week in a sentence** — one sentence capturing the week.
- **Themes** — 2-4 recurring topics or patterns you noticed.
- **Mood** — a brief read on their emotional arc across the week.
- **Worth noticing** — one or two gentle, specific observations they might have missed.
- **A prompt for next week** — a single reflective question.

Be specific and reference what they actually wrote. Be kind and non-judgmental. Never invent events that aren't in the entries. Keep the whole reflection under ~300 words.`;

function startOfWeekAgoISO() {
  const d = new Date();
  d.setDate(d.getDate() - 7);
  return d.toISOString();
}

// Pulls the past week's entries (with their text) and asks Claude for a
// reflection. Returns { text, entryCount, rangeLabel }.
export async function generateWeeklyInsight() {
  const sinceISO = startOfWeekAgoISO();
  const entries = await listEntries({ sinceISO });

  if (entries.length === 0) {
    return {
      text: 'No entries in the past 7 days yet. Write a few and check back — your weekly reflection will appear here.',
      entryCount: 0,
      rangeLabel: weekRangeLabel(),
    };
  }

  // Fetch each entry's body text in parallel.
  const withText = await Promise.all(
    entries.map(async (e) => ({ ...e, text: await getEntryText(e.id) }))
  );

  const transcript = withText
    .map((e) => {
      const day = new Date(e.date).toDateString();
      const meta = [day, e.mood && `mood: ${e.mood}`, e.tags.length && `tags: ${e.tags.join(', ')}`]
        .filter(Boolean)
        .join(' · ');
      return `### ${e.title}\n${meta}\n${e.text || '(no body)'}`;
    })
    .join('\n\n');

  const userMessage = `Here are my journal entries from the past week:\n\n${transcript}`;

  const res = await fetch(ANTHROPIC_URL, {
    method: 'POST',
    headers: {
      'x-api-key': ANTHROPIC_API_KEY,
      'anthropic-version': ANTHROPIC_VERSION,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model: ANTHROPIC_MODEL,
      max_tokens: 1500,
      system: SYSTEM_PROMPT,
      messages: [{ role: 'user', content: userMessage }],
    }),
  });

  const data = await res.json();
  if (!res.ok) {
    const message = data?.error?.message || `Claude request failed (${res.status})`;
    throw new Error(message);
  }

  const text = (data.content || [])
    .filter((b) => b.type === 'text')
    .map((b) => b.text)
    .join('\n')
    .trim();

  return { text, entryCount: entries.length, rangeLabel: weekRangeLabel() };
}

function weekRangeLabel() {
  const end = new Date();
  const start = new Date();
  start.setDate(start.getDate() - 7);
  const fmt = (d) => d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  return `${fmt(start)} – ${fmt(end)}`;
}

// Saves a generated reflection back into Notion as its own page.
export async function saveInsightToNotion({ text, rangeLabel }) {
  return createEntry({
    title: `Weekly Insight (${rangeLabel})`,
    body: text,
    source: 'Typed',
    dateISO: new Date().toISOString(),
  });
}
