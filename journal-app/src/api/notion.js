// Minimal Notion REST client built on fetch. No SDK needed — React Native's
// fetch talks to the Notion API directly (no browser CORS on a device).

import { NOTION_TOKEN, NOTION_DATABASE_ID } from '../config';

const BASE = 'https://api.notion.com/v1';
const NOTION_VERSION = '2022-06-28';

function headers() {
  return {
    Authorization: `Bearer ${NOTION_TOKEN}`,
    'Notion-Version': NOTION_VERSION,
    'Content-Type': 'application/json',
  };
}

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, { ...options, headers: headers() });
  const data = await res.json();
  if (!res.ok) {
    const message = data?.message || `Notion request failed (${res.status})`;
    throw new Error(message);
  }
  return data;
}

// Splits a body string into Notion paragraph blocks (one per non-empty line).
// Notion caps rich-text content at 2000 chars per block, so long lines are split.
function bodyToBlocks(body) {
  const lines = (body || '').split('\n');
  const blocks = [];
  for (const line of lines) {
    const text = line.trim();
    if (!text) continue;
    for (let i = 0; i < text.length; i += 1900) {
      const chunk = text.slice(i, i + 1900);
      blocks.push({
        object: 'block',
        type: 'paragraph',
        paragraph: {
          rich_text: [{ type: 'text', text: { content: chunk } }],
        },
      });
    }
  }
  return blocks;
}

// Creates a journal entry page in the Journal database.
export async function createEntry({
  title,
  body,
  mood,
  tags = [],
  source = 'Typed',
  dateISO,
}) {
  const properties = {
    Title: {
      title: [{ type: 'text', text: { content: title || 'Untitled entry' } }],
    },
    Date: { date: { start: dateISO || new Date().toISOString() } },
    Source: { select: { name: source } },
  };
  if (mood) properties.Mood = { select: { name: mood } };
  if (tags.length) {
    properties.Tags = { multi_select: tags.map((name) => ({ name })) };
  }

  return request('/pages', {
    method: 'POST',
    body: JSON.stringify({
      parent: { database_id: NOTION_DATABASE_ID },
      properties,
      children: bodyToBlocks(body),
    }),
  });
}

// Fetches entries, newest first. Pass `sinceISO` to only return entries on or
// after a date (used for weekly insights).
export async function listEntries({ sinceISO, pageSize = 50 } = {}) {
  const filter = sinceISO
    ? { property: 'Date', date: { on_or_after: sinceISO } }
    : undefined;

  const data = await request(`/databases/${NOTION_DATABASE_ID}/query`, {
    method: 'POST',
    body: JSON.stringify({
      page_size: pageSize,
      sorts: [{ property: 'Date', direction: 'descending' }],
      ...(filter ? { filter } : {}),
    }),
  });

  return (data.results || []).map(parseEntry);
}

function parseEntry(page) {
  const props = page.properties || {};
  return {
    id: page.id,
    url: page.url,
    title: props.Title?.title?.[0]?.plain_text || 'Untitled entry',
    date: props.Date?.date?.start || page.created_time,
    mood: props.Mood?.select?.name || null,
    tags: (props.Tags?.multi_select || []).map((t) => t.name),
    source: props.Source?.select?.name || null,
  };
}

// Returns the plain-text body of an entry by reading its page blocks.
export async function getEntryText(pageId) {
  const data = await request(`/blocks/${pageId}/children?page_size=100`);
  const parts = [];
  for (const block of data.results || []) {
    const rich = block[block.type]?.rich_text;
    if (Array.isArray(rich)) {
      parts.push(rich.map((r) => r.plain_text).join(''));
    }
  }
  return parts.join('\n').trim();
}
