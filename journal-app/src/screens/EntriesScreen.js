import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { colors, moodColors } from '../theme';
import { isPreview } from '../config';
import { listEntries } from '../api/notion';
import { SAMPLE_ENTRIES } from '../sampleData';
import PreviewBanner from '../components/PreviewBanner';

export default function EntriesScreen() {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    if (isPreview()) {
      setEntries(SAMPLE_ENTRIES);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setEntries(await listEntries({ pageSize: 50 }));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  // Reload whenever the tab regains focus, so new entries show up.
  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  if (error) {
    return (
      <View style={styles.center}>
        <Text style={styles.error}>{error}</Text>
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      {isPreview() ? <PreviewBanner /> : null}
      <Text style={styles.heading}>Entries</Text>
      <FlatList
        data={entries}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={load} />}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator style={{ marginTop: 40 }} color={colors.accent} />
          ) : (
            <Text style={styles.empty}>No entries yet. Write your first one!</Text>
          )
        }
        renderItem={({ item }) => <EntryCard entry={item} />}
      />
    </View>
  );
}

function EntryCard({ entry }) {
  const dateLabel = new Date(entry.date).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
  return (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle} numberOfLines={1}>
          {entry.title}
        </Text>
        {entry.mood ? (
          <View style={styles.moodRow}>
            <View style={[styles.dot, { backgroundColor: moodColors[entry.mood] }]} />
            <Text style={styles.moodText}>{entry.mood}</Text>
          </View>
        ) : null}
      </View>
      <Text style={styles.date}>{dateLabel}</Text>
      {entry.tags.length ? (
        <View style={styles.tagRow}>
          {entry.tags.map((t) => (
            <Text key={t} style={styles.tag}>
              {t}
            </Text>
          ))}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: colors.bg },
  heading: { fontSize: 28, fontWeight: '700', color: colors.text, paddingHorizontal: 20, paddingTop: 20, paddingBottom: 8 },
  listContent: { padding: 16, paddingTop: 8 },
  empty: { textAlign: 'center', color: colors.subtle, marginTop: 40 },
  error: { color: colors.accent, textAlign: 'center', fontSize: 15 },
  card: {
    backgroundColor: colors.card,
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: colors.border,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  cardTitle: { fontSize: 17, fontWeight: '600', color: colors.text, flex: 1, marginRight: 8 },
  moodRow: { flexDirection: 'row', alignItems: 'center' },
  moodText: { fontSize: 13, color: colors.subtle },
  dot: { width: 9, height: 9, borderRadius: 5, marginRight: 5 },
  date: { fontSize: 13, color: colors.subtle, marginTop: 4 },
  tagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 10 },
  tag: {
    fontSize: 12,
    color: colors.accent,
    backgroundColor: colors.accentSoft,
    borderRadius: 10,
    paddingVertical: 3,
    paddingHorizontal: 8,
    overflow: 'hidden',
  },
});
