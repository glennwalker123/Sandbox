import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { colors, moodColors } from '../theme';
import { MOODS, TAGS, hasNotion } from '../config';
import { createEntry } from '../api/notion';

export default function WriteScreen() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [mood, setMood] = useState(null);
  const [tags, setTags] = useState([]);
  const [saving, setSaving] = useState(false);

  const toggleTag = (t) =>
    setTags((cur) => (cur.includes(t) ? cur.filter((x) => x !== t) : [...cur, t]));

  const reset = () => {
    setTitle('');
    setBody('');
    setMood(null);
    setTags([]);
  };

  const onSave = async () => {
    if (!hasNotion()) {
      Alert.alert('Not configured', 'Add your Notion token to .env first (see README).');
      return;
    }
    if (!body.trim()) {
      Alert.alert('Empty entry', 'Write something before saving.');
      return;
    }
    setSaving(true);
    try {
      await createEntry({
        title: title.trim() || defaultTitle(),
        body: body.trim(),
        mood,
        tags,
        source: 'Typed',
        dateISO: new Date().toISOString(),
      });
      reset();
      Alert.alert('Saved', 'Your entry is in Notion.');
    } catch (e) {
      Alert.alert('Could not save', e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView style={styles.flex} contentContainerStyle={styles.content}>
        <Text style={styles.heading}>New entry</Text>

        <TextInput
          style={styles.titleInput}
          placeholder="Title (optional)"
          placeholderTextColor={colors.subtle}
          value={title}
          onChangeText={setTitle}
        />

        <TextInput
          style={styles.bodyInput}
          placeholder="What's on your mind?"
          placeholderTextColor={colors.subtle}
          value={body}
          onChangeText={setBody}
          multiline
          textAlignVertical="top"
        />

        <Text style={styles.label}>Mood</Text>
        <View style={styles.row}>
          {MOODS.map((m) => {
            const active = mood === m;
            return (
              <TouchableOpacity
                key={m}
                style={[styles.chip, active && styles.chipActive]}
                onPress={() => setMood(active ? null : m)}
              >
                <View style={[styles.dot, { backgroundColor: moodColors[m] }]} />
                <Text style={[styles.chipText, active && styles.chipTextActive]}>{m}</Text>
              </TouchableOpacity>
            );
          })}
        </View>

        <Text style={styles.label}>Tags</Text>
        <View style={styles.row}>
          {TAGS.map((t) => {
            const active = tags.includes(t);
            return (
              <TouchableOpacity
                key={t}
                style={[styles.chip, active && styles.chipActive]}
                onPress={() => toggleTag(t)}
              >
                <Text style={[styles.chipText, active && styles.chipTextActive]}>{t}</Text>
              </TouchableOpacity>
            );
          })}
        </View>

        <TouchableOpacity
          style={[styles.saveBtn, saving && styles.saveBtnDisabled]}
          onPress={onSave}
          disabled={saving}
        >
          {saving ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.saveText}>Save entry</Text>
          )}
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function defaultTitle() {
  return new Date().toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'short',
    day: 'numeric',
  });
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 20, paddingBottom: 40 },
  heading: { fontSize: 28, fontWeight: '700', color: colors.text, marginBottom: 16 },
  titleInput: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
    fontSize: 16,
    color: colors.text,
    marginBottom: 12,
  },
  bodyInput: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
    fontSize: 16,
    color: colors.text,
    minHeight: 180,
    marginBottom: 16,
  },
  label: { fontSize: 14, fontWeight: '600', color: colors.subtle, marginBottom: 8, marginTop: 4 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 16 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 20,
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  chipActive: { backgroundColor: colors.accentSoft, borderColor: colors.accent },
  chipText: { color: colors.text, fontSize: 14 },
  chipTextActive: { color: colors.accent, fontWeight: '600' },
  dot: { width: 10, height: 10, borderRadius: 5, marginRight: 6 },
  saveBtn: {
    backgroundColor: colors.accent,
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  saveBtnDisabled: { opacity: 0.6 },
  saveText: { color: '#fff', fontSize: 17, fontWeight: '700' },
});
