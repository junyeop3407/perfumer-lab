import { Tabs } from 'expo-router';
import { useWindowDimensions } from 'react-native';

import { S } from '@/src/strings.ko';
import { TestIds } from '@/src/ui/testIds';

/** 이 너비부터 PC 배치(하단 탭 → 왼쪽 메뉴) */
export const WIDE_MIN_WIDTH = 1024;

export default function TabLayout() {
  const { width } = useWindowDimensions();
  const wide = width >= WIDE_MIN_WIDTH;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarPosition: wide ? 'left' : 'bottom',
        tabBarVariant: wide ? 'material' : 'uikit',
        tabBarLabelPosition: wide ? 'beside-icon' : 'below-icon',
        tabBarIcon: () => null,
      }}>
      <Tabs.Screen name="index" options={{ title: S.tabHome, tabBarButtonTestID: TestIds.TAB_HOME }} />
      <Tabs.Screen name="search" options={{ title: S.tabSearch, tabBarButtonTestID: TestIds.TAB_SEARCH }} />
      <Tabs.Screen name="library" options={{ title: S.tabLibrary, tabBarButtonTestID: TestIds.TAB_LIBRARY }} />
      <Tabs.Screen name="my" options={{ title: S.tabMy, tabBarButtonTestID: TestIds.TAB_MY }} />
    </Tabs>
  );
}
