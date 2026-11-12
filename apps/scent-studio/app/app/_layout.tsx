import '../global.css';

import { useFonts } from 'expo-font';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { useColorScheme, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';

import { DraftProvider } from '@/src/blend/DraftContext';
import { CatalogProvider, useCatalog } from '@/src/catalog/CatalogContext';
import { S } from '@/src/strings.ko';
import { FONT_SOURCES } from '@/src/ui/fontSources';
import { ICON_FONT } from '@/src/ui/Icon';
import { Txt } from '@/src/ui/Txt';

export { ErrorBoundary } from 'expo-router';

void SplashScreen.preventAutoHideAsync().catch(() => undefined);

/** 샘플(가상) 카탈로그면 화면 맨 위에 늘 표시 (design_rules.md 8절, plan_b_portfolio.md 1-7) */
function SampleBanner() {
  const catalog = useCatalog();
  const insets = useSafeAreaInsets();
  if (catalog.status !== 'ready' || !catalog.isSample) return null;
  return (
    <View style={{ paddingTop: insets.top }} className="bg-accentSoft px-6 dark:bg-accentSoft-dark">
      <Txt variant="caption" className="py-1.5" testID="sampleBanner">
        {S.sampleBanner}
      </Txt>
    </View>
  );
}

export default function RootLayout() {
  const scheme = useColorScheme();
  // 글꼴을 못 읽어도(error) 시스템 글꼴로 계속 연다 (D6)
  const [fontsLoaded, fontError] = useFonts({ ...FONT_SOURCES, ...ICON_FONT });
  const fontsDone = fontsLoaded || !!fontError;

  useEffect(() => {
    if (fontError) console.warn(`[fonts] 글꼴을 읽지 못해 시스템 글꼴로 엽니다: ${fontError.message}`);
    if (fontsDone) void SplashScreen.hideAsync().catch(() => undefined);
  }, [fontsDone, fontError]);

  return (
    <SafeAreaProvider>
      <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
        <CatalogProvider>
          <DraftProvider>
            {fontsDone ? (
              <View className="flex-1 bg-bg dark:bg-bg-dark">
                <SampleBanner />
                <Stack screenOptions={{ headerShown: false }}>
                  <Stack.Screen name="(tabs)" />
                </Stack>
              </View>
            ) : null}
          </DraftProvider>
        </CatalogProvider>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
