import { Tabs } from 'expo-router';
import { useWindowDimensions, View } from 'react-native';

import { S } from '@/src/strings.ko';
import { FONT } from '@/src/ui/fontSet';
import { RawIcon, type IconName } from '@/src/ui/Icon';
import { WIDE_MIN_WIDTH } from '@/src/ui/layout';
import { TestIds } from '@/src/ui/testIds';
import { useTokenColor } from '@/src/ui/tokens';

/** 탭 아이콘. 폰에서 고른 탭은 아이콘 아래 2dp 선 (design_rules.md 6절 하단 탭) */
const icon =
  (name: IconName, underline: boolean) =>
  ({ color, focused }: { color: unknown; focused: boolean }) => (
    <View style={{ borderBottomWidth: underline && focused ? 2 : 0, borderColor: String(color), paddingBottom: 2 }}>
      <RawIcon name={name} color={String(color)} />
    </View>
  );

export default function TabLayout() {
  const { width } = useWindowDimensions();
  const wide = width >= WIDE_MIN_WIDTH;
  const c = useTokenColor();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarPosition: wide ? 'left' : 'bottom',
        tabBarVariant: wide ? 'material' : 'uikit',
        tabBarLabelPosition: wide ? 'beside-icon' : 'below-icon',
        tabBarActiveTintColor: c('primary'),
        tabBarInactiveTintColor: c('inkSub'),
        tabBarActiveBackgroundColor: wide ? c('surface') : undefined,
        tabBarStyle: { backgroundColor: wide ? c('bg') : c('surface'), borderColor: c('line'), ...(wide ? { width: 240, paddingTop: 24 } : {}) },
        tabBarItemStyle: wide ? { borderRadius: 4, marginHorizontal: 16, minHeight: 48 } : { minHeight: 56 },
        // 줄 높이를 정해 주지 않으면 글꼴에 따라 글자 아래가 잘린다
        tabBarLabelStyle: { fontFamily: FONT.sansMedium, fontSize: wide ? 14 : 12, lineHeight: wide ? 20 : 16 },
      }}>
      <Tabs.Screen name="(home)" options={{ title: S.tabHome, tabBarButtonTestID: TestIds.TAB_HOME, tabBarIcon: icon('home', !wide) }} />
      <Tabs.Screen name="search" options={{ title: S.tabSearch, tabBarButtonTestID: TestIds.TAB_SEARCH, tabBarIcon: icon('search', !wide) }} />
      <Tabs.Screen name="library" options={{ title: S.tabLibrary, tabBarButtonTestID: TestIds.TAB_LIBRARY, tabBarIcon: icon('library', !wide) }} />
      <Tabs.Screen name="my" options={{ title: S.tabMy, tabBarButtonTestID: TestIds.TAB_MY, tabBarIcon: icon('my', !wide) }} />
    </Tabs>
  );
}
