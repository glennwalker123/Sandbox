import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import WriteScreen from './src/screens/WriteScreen';
import EntriesScreen from './src/screens/EntriesScreen';
import InsightsScreen from './src/screens/InsightsScreen';
import { colors } from './src/theme';

const Tab = createBottomTabNavigator();

const navTheme = {
  ...DefaultTheme,
  colors: { ...DefaultTheme.colors, background: colors.bg, card: colors.card, border: colors.border },
};

const ICONS = {
  Write: 'create-outline',
  Entries: 'book-outline',
  Insights: 'sparkles-outline',
};

export default function App() {
  return (
    <SafeAreaProvider>
      <NavigationContainer theme={navTheme}>
        <StatusBar style="dark" />
        <Tab.Navigator
          screenOptions={({ route }) => ({
            headerShown: false,
            tabBarActiveTintColor: colors.accent,
            tabBarInactiveTintColor: colors.subtle,
            tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.border },
            tabBarIcon: ({ color, size }) => (
              <Ionicons name={ICONS[route.name]} size={size} color={color} />
            ),
          })}
        >
          <Tab.Screen name="Write" component={WriteScreen} />
          <Tab.Screen name="Entries" component={EntriesScreen} />
          <Tab.Screen name="Insights" component={InsightsScreen} />
        </Tab.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
