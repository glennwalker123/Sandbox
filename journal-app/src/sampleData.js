// Sample content shown when the app runs without API keys (e.g. prototyping the
// UI in Expo Snack). Lets every screen look alive with zero configuration.

function daysAgoISO(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString();
}

export const SAMPLE_ENTRIES = [
  {
    id: 'sample-1',
    title: 'A slow, good morning',
    date: daysAgoISO(0),
    mood: 'Good',
    tags: ['gratitude', 'health'],
    source: 'Typed',
  },
  {
    id: 'sample-2',
    title: 'Shipped the first draft',
    date: daysAgoISO(1),
    mood: 'Great',
    tags: ['work', 'ideas'],
    source: 'Typed',
  },
  {
    id: 'sample-3',
    title: 'Tired but okay',
    date: daysAgoISO(3),
    mood: 'Okay',
    tags: ['health'],
    source: 'Voice',
  },
  {
    id: 'sample-4',
    title: 'Long call with Mum',
    date: daysAgoISO(5),
    mood: 'Good',
    tags: ['relationships'],
    source: 'Typed',
  },
];

export const SAMPLE_INSIGHT = {
  entryCount: SAMPLE_ENTRIES.length,
  rangeLabel: 'sample week',
  text: `**This week in a sentence**
A busy week that still made room for rest and the people who matter.

**Themes**
• Momentum at work — you shipped a first draft and felt the lift of finishing.
• Tending relationships — the long call stood out as a highlight.
• Looking after your energy — you noticed tiredness and didn't push through it.

**Mood**
Mostly steady and positive, with one lower-energy day mid-week that you handled gently.

**Worth noticing**
Your best-feeling days paired meaningful work with a real break. That combination seems to matter for you.

**A prompt for next week**
What's one small thing that reliably restores your energy — and when could you schedule it?

(This is sample text. Connect your Anthropic API key to generate real insights from your own entries.)`,
};
