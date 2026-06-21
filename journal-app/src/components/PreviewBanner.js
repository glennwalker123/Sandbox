import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors } from '../theme';

// Shown when the app is running without API keys (preview / prototyping mode).
export default function PreviewBanner() {
  return (
    <View style={styles.banner}>
      <Text style={styles.text}>
        Preview mode · showing sample data — add your keys in .env to go live
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: colors.accentSoft,
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  text: { color: colors.accent, fontSize: 12, textAlign: 'center', fontWeight: '600' },
});
