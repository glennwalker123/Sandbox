import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { colors } from '../theme';
import { hasNotion, hasAnthropic } from '../config';
import { generateWeeklyInsight, saveInsightToNotion } from '../api/insights';

export default function InsightsScreen() {
  const [loading, setLoading] = useState(false);
  const [insight, setInsight] = useState(null); // { text, entryCount, rangeLabel }
  const [saving, setSaving] = useState(false);

  const onGenerate = async () => {
    if (!hasNotion() || !hasAnthropic()) {
      Alert.alert(
        'Not configured',
        'Add both your Notion token and Anthropic API key to .env first (see README).'
      );
      return;
    }
    setLoading(true);
    try {
      setInsight(await generateWeeklyInsight());
    } catch (e) {
      Alert.alert('Could not generate insight', e.message);
    } finally {
      setLoading(false);
    }
  };

  const onSave = async () => {
    if (!insight?.text) return;
    setSaving(true);
    try {
      await saveInsightToNotion(insight);
      Alert.alert('Saved', 'Your weekly insight is saved to Notion.');
    } catch (e) {
      Alert.alert('Could not save', e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScrollView style={styles.flex} contentContainerStyle={styles.content}>
      <Text style={styles.heading}>Weekly insight</Text>
      <Text style={styles.subtitle}>
        A reflection on your past 7 days, generated from your entries.
      </Text>

      <TouchableOpacity
        style={[styles.btn, loading && styles.btnDisabled]}
        onPress={onGenerate}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.btnText}>
            {insight ? 'Regenerate' : 'Generate weekly insight'}
          </Text>
        )}
      </TouchableOpacity>

      {insight ? (
        <View style={styles.resultCard}>
          {insight.rangeLabel ? (
            <Text style={styles.range}>
              {insight.rangeLabel}
              {insight.entryCount
                ? ` · ${insight.entryCount} ${insight.entryCount === 1 ? 'entry' : 'entries'}`
                : ''}
            </Text>
          ) : null}
          <Text style={styles.insightText}>{stripMarkdown(insight.text)}</Text>

          {insight.entryCount > 0 ? (
            <TouchableOpacity
              style={[styles.saveBtn, saving && styles.btnDisabled]}
              onPress={onSave}
              disabled={saving}
            >
              {saving ? (
                <ActivityIndicator color={colors.accent} />
              ) : (
                <Text style={styles.saveText}>Save to Notion</Text>
              )}
            </TouchableOpacity>
          ) : null}
        </View>
      ) : null}
    </ScrollView>
  );
}

// Light touch: render markdown as readable plain text (drop ** and leading #/-).
function stripMarkdown(md) {
  if (!md) return '';
  return md
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^#+\s*/gm, '')
    .replace(/^[-*]\s+/gm, '• ')
    .trim();
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 20, paddingBottom: 40 },
  heading: { fontSize: 28, fontWeight: '700', color: colors.text },
  subtitle: { fontSize: 15, color: colors.subtle, marginTop: 6, marginBottom: 20 },
  btn: {
    backgroundColor: colors.accent,
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
  },
  btnDisabled: { opacity: 0.6 },
  btnText: { color: '#fff', fontSize: 17, fontWeight: '700' },
  resultCard: {
    backgroundColor: colors.card,
    borderRadius: 16,
    padding: 20,
    marginTop: 20,
    borderWidth: 1,
    borderColor: colors.border,
  },
  range: { fontSize: 13, color: colors.subtle, marginBottom: 12, fontWeight: '600' },
  insightText: { fontSize: 16, lineHeight: 24, color: colors.text },
  saveBtn: {
    marginTop: 20,
    borderWidth: 1,
    borderColor: colors.accent,
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
  },
  saveText: { color: colors.accent, fontSize: 15, fontWeight: '700' },
});
