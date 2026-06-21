// Single-file UI prototype of the Journal app for Expo Snack.
// Paste this over the default code in https://snack.expo.dev to preview the UI
// on your phone. No dependencies beyond React Native core, no API keys needed —
// this is the visual prototype (saving / insights are mocked).
//
// The full multi-file app (real Notion storage + Claude weekly insights) lives
// in the parent journal-app/ folder.

import React, { useState } from 'react';
import {
  SafeAreaView,
  View,
  Text,
  TextInput,
  ScrollView,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  Platform,
  StatusBar,
} from 'react-native';

const colors = {
  bg: '#F4F1EA',
  card: '#FFFFFF',
  text: '#2A2722',
  subtle: '#7A736A',
  border: '#E4DED2',
  accent: '#C2613E',
  accentSoft: '#F0D9CF',
};

const moodColors = {
  Great: '#3C9A57',
  Good: '#3B7DD8',
  Okay: '#D6A640',
  Low: '#D2843B',
  Rough: '#C2493B',
};

const MOODS = ['Great', 'Good', 'Okay', 'Low', 'Rough'];
const TAGS = ['work', 'health', 'relationships', 'gratitude', 'ideas'];

function daysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d;
}

const SAMPLE_ENTRIES = [
  { id: '1', title: 'A slow, good morning', date: daysAgo(0), mood: 'Good', tags: ['gratitude', 'health'] },
  { id: '2', title: 'Shipped the first draft', date: daysAgo(1), mood: 'Great', tags: ['work', 'ideas'] },
  { id: '3', title: 'Tired but okay', date: daysAgo(3), mood: 'Okay', tags: ['health'] },
  { id: '4', title: 'Long call with Mum', date: daysAgo(5), mood: 'Good', tags: ['relationships'] },
];

const SAMPLE_INSIGHT = `This week in a sentence
A busy week that still made room for rest and the people who matter.

Themes
• Momentum at work — you shipped a first draft and felt the lift of finishing.
• Tending relationships — the long call stood out as a highlight.
• Looking after your energy — you noticed tiredness and didn't push through it.

Mood
Mostly steady and positive, with one lower day mid-week that you handled gently.

Worth noticing
Your best-feeling days paired meaningful work with a real break.

A prompt for next week
What's one small thing that reliably restores your energy?

(Sample text — the real app generates this from your own entries with Claude.)`;

function PreviewBanner() {
  return (
    <View style={s.banner}>
      <Text style={s.bannerText}>Preview mode · sample data — UI prototype</Text>
    </View>
  );
}

function Chip({ label, active, dotColor, onPress }) {
  return (
    <TouchableOpacity style={[s.chip, active && s.chipActive]} onPress={onPress}>
      {dotColor ? <View style={[s.dot, { backgroundColor: dotColor }]} /> : null}
      <Text style={[s.chipText, active && s.chipTextActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

function WriteScreen() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [mood, setMood] = useState(null);
  const [tags, setTags] = useState([]);

  const toggleTag = (t) =>
    setTags((c) => (c.includes(t) ? c.filter((x) => x !== t) : [...c, t]));

  const onSave = () => {
    if (!body.trim()) {
      Alert.alert('Empty entry', 'Write something first.');
      return;
    }
    setTitle('');
    setBody('');
    setMood(null);
    setTags([]);
    Alert.alert('Preview mode', 'Nothing was saved — this is the UI prototype.');
  };

  return (
    <ScrollView style={s.flex} contentContainerStyle={s.content}>
      <Text style={s.heading}>New entry</Text>
      <TextInput
        style={s.titleInput}
        placeholder="Title (optional)"
        placeholderTextColor={colors.subtle}
        value={title}
        onChangeText={setTitle}
      />
      <TextInput
        style={s.bodyInput}
        placeholder="What's on your mind?"
        placeholderTextColor={colors.subtle}
        value={body}
        onChangeText={setBody}
        multiline
        textAlignVertical="top"
      />
      <Text style={s.label}>Mood</Text>
      <View style={s.row}>
        {MOODS.map((m) => (
          <Chip
            key={m}
            label={m}
            dotColor={moodColors[m]}
            active={mood === m}
            onPress={() => setMood(mood === m ? null : m)}
          />
        ))}
      </View>
      <Text style={s.label}>Tags</Text>
      <View style={s.row}>
        {TAGS.map((t) => (
          <Chip key={t} label={t} active={tags.includes(t)} onPress={() => toggleTag(t)} />
        ))}
      </View>
      <TouchableOpacity style={s.primaryBtn} onPress={onSave}>
        <Text style={s.primaryText}>Save entry</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

function EntriesScreen() {
  return (
    <View style={s.flex}>
      <Text style={[s.heading, { paddingHorizontal: 20, paddingTop: 20 }]}>Entries</Text>
      <FlatList
        data={SAMPLE_ENTRIES}
        keyExtractor={(i) => i.id}
        contentContainerStyle={{ padding: 16, paddingTop: 8 }}
        renderItem={({ item }) => (
          <View style={s.entryCard}>
            <View style={s.entryHeader}>
              <Text style={s.entryTitle} numberOfLines={1}>{item.title}</Text>
              <View style={s.moodRow}>
                <View style={[s.dot, { backgroundColor: moodColors[item.mood] }]} />
                <Text style={s.moodText}>{item.mood}</Text>
              </View>
            </View>
            <Text style={s.date}>
              {item.date.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}
            </Text>
            <View style={s.tagRow}>
              {item.tags.map((t) => (
                <Text key={t} style={s.tag}>{t}</Text>
              ))}
            </View>
          </View>
        )}
      />
    </View>
  );
}

function InsightsScreen() {
  const [loading, setLoading] = useState(false);
  const [insight, setInsight] = useState(null);

  const onGenerate = () => {
    setLoading(true);
    setTimeout(() => {
      setInsight(SAMPLE_INSIGHT);
      setLoading(false);
    }, 700);
  };

  return (
    <ScrollView style={s.flex} contentContainerStyle={s.content}>
      <Text style={s.heading}>Weekly insight</Text>
      <Text style={s.subtitle}>A reflection on your past 7 days, from your entries.</Text>
      <TouchableOpacity style={s.primaryBtn} onPress={onGenerate} disabled={loading}>
        {loading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={s.primaryText}>{insight ? 'Regenerate' : 'Generate weekly insight'}</Text>
        )}
      </TouchableOpacity>
      {insight ? (
        <View style={s.insightCard}>
          <Text style={s.range}>sample week · 4 entries</Text>
          <Text style={s.insightText}>{insight}</Text>
        </View>
      ) : null}
    </ScrollView>
  );
}

const TABS = [
  { key: 'write', label: 'Write', icon: '✍️' },
  { key: 'entries', label: 'Entries', icon: '📖' },
  { key: 'insights', label: 'Insights', icon: '✨' },
];

export default function App() {
  const [tab, setTab] = useState('write');
  return (
    <SafeAreaView style={s.app}>
      <PreviewBanner />
      <View style={s.flex}>
        {tab === 'write' && <WriteScreen />}
        {tab === 'entries' && <EntriesScreen />}
        {tab === 'insights' && <InsightsScreen />}
      </View>
      <View style={s.tabBar}>
        {TABS.map((t) => {
          const active = tab === t.key;
          return (
            <TouchableOpacity key={t.key} style={s.tabItem} onPress={() => setTab(t.key)}>
              <Text style={[s.tabIcon, { opacity: active ? 1 : 0.5 }]}>{t.icon}</Text>
              <Text style={[s.tabLabel, active && s.tabLabelActive]}>{t.label}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  app: { flex: 1, backgroundColor: colors.bg, paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0 },
  flex: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 20, paddingBottom: 40 },
  heading: { fontSize: 28, fontWeight: '700', color: colors.text, marginBottom: 12 },
  subtitle: { fontSize: 15, color: colors.subtle, marginBottom: 20 },
  label: { fontSize: 14, fontWeight: '600', color: colors.subtle, marginBottom: 8, marginTop: 4 },
  row: { flexDirection: 'row', flexWrap: 'wrap', marginBottom: 16 },
  titleInput: {
    backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border,
    borderRadius: 12, padding: 14, fontSize: 16, color: colors.text, marginBottom: 12,
  },
  bodyInput: {
    backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border,
    borderRadius: 12, padding: 14, fontSize: 16, color: colors.text, minHeight: 160, marginBottom: 16,
  },
  chip: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: colors.card,
    borderWidth: 1, borderColor: colors.border, borderRadius: 20,
    paddingVertical: 8, paddingHorizontal: 14, marginRight: 8, marginBottom: 8,
  },
  chipActive: { backgroundColor: colors.accentSoft, borderColor: colors.accent },
  chipText: { color: colors.text, fontSize: 14 },
  chipTextActive: { color: colors.accent, fontWeight: '600' },
  dot: { width: 10, height: 10, borderRadius: 5, marginRight: 6 },
  primaryBtn: { backgroundColor: colors.accent, borderRadius: 14, paddingVertical: 16, alignItems: 'center', marginTop: 8 },
  primaryText: { color: '#fff', fontSize: 17, fontWeight: '700' },
  entryCard: {
    backgroundColor: colors.card, borderRadius: 14, padding: 16, marginBottom: 12,
    borderWidth: 1, borderColor: colors.border,
  },
  entryHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  entryTitle: { fontSize: 17, fontWeight: '600', color: colors.text, flex: 1, marginRight: 8 },
  moodRow: { flexDirection: 'row', alignItems: 'center' },
  moodText: { fontSize: 13, color: colors.subtle },
  date: { fontSize: 13, color: colors.subtle, marginTop: 4 },
  tagRow: { flexDirection: 'row', flexWrap: 'wrap', marginTop: 10 },
  tag: {
    fontSize: 12, color: colors.accent, backgroundColor: colors.accentSoft,
    borderRadius: 10, paddingVertical: 3, paddingHorizontal: 8, marginRight: 6, overflow: 'hidden',
  },
  insightCard: {
    backgroundColor: colors.card, borderRadius: 16, padding: 20, marginTop: 20,
    borderWidth: 1, borderColor: colors.border,
  },
  range: { fontSize: 13, color: colors.subtle, marginBottom: 12, fontWeight: '600' },
  insightText: { fontSize: 16, lineHeight: 24, color: colors.text },
  banner: { backgroundColor: colors.accentSoft, paddingVertical: 8, paddingHorizontal: 16 },
  bannerText: { color: colors.accent, fontSize: 12, textAlign: 'center', fontWeight: '600' },
  tabBar: {
    flexDirection: 'row', backgroundColor: colors.card,
    borderTopWidth: 1, borderTopColor: colors.border, paddingVertical: 8,
  },
  tabItem: { flex: 1, alignItems: 'center' },
  tabIcon: { fontSize: 22 },
  tabLabel: { fontSize: 12, color: colors.subtle, marginTop: 2 },
  tabLabelActive: { color: colors.accent, fontWeight: '700' },
});
