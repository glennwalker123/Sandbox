// Central configuration. Values come from environment variables that Expo
// inlines at build time (must be prefixed with EXPO_PUBLIC_). See .env.example.

export const NOTION_TOKEN = process.env.EXPO_PUBLIC_NOTION_TOKEN || '';

export const NOTION_DATABASE_ID =
  process.env.EXPO_PUBLIC_NOTION_DATABASE_ID ||
  '72699b7865ae4b22beb360b7b95b5dfd';

export const ANTHROPIC_API_KEY = process.env.EXPO_PUBLIC_ANTHROPIC_API_KEY || '';

export const ANTHROPIC_MODEL =
  process.env.EXPO_PUBLIC_ANTHROPIC_MODEL || 'claude-opus-4-8';

// Shared option lists — these mirror the select/multi-select options defined on
// the Notion database, so writes always use valid values.
export const MOODS = ['Great', 'Good', 'Okay', 'Low', 'Rough'];
export const TAGS = ['work', 'health', 'relationships', 'gratitude', 'ideas'];

export const hasNotion = () => Boolean(NOTION_TOKEN && NOTION_DATABASE_ID);
export const hasAnthropic = () => Boolean(ANTHROPIC_API_KEY);

// When any key is missing, the app runs in "preview" mode with sample data so
// the UI can be prototyped (e.g. in Expo Snack) without configuration.
export const isPreview = () => !hasNotion() || !hasAnthropic();
