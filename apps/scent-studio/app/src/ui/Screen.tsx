/**
 * 화면 틀: 위쪽 safe area(노치·상태 표시줄), 세로 스크롤, 좌우 여백 24, 최대 폭.
 * 아래쪽 safe area는 하단 탭이 맡는다. 키보드: 목록을 끌면 닫히고, 버튼은 키보드가 떠 있어도 한 번에 눌린다(D3).
 */
import type { ReactNode } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useCatalog } from '../catalog/CatalogContext';
import { Icon } from './Icon';
import { Txt } from './Txt';

export function Screen({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  const catalog = useCatalog();
  // 가상 데이터 띠가 있으면 띠가 위쪽 safe area를 이미 맡는다(_layout.tsx SampleBanner)
  const banner = catalog.status === 'ready' && catalog.isSample;
  return (
    <SafeAreaView edges={banner ? [] : ['top']} className="flex-1 bg-bg dark:bg-bg-dark">
      <ScrollView keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag" contentContainerClassName="px-6 pb-12 pt-4">
        <View className={`w-full ${wide ? 'max-w-page' : 'max-w-text'}`}>{children}</View>
      </ScrollView>
    </SafeAreaView>
  );
}

/** 화면 위 제목줄: [←] 제목 / 작은 설명 */
export function Header({ title, sub, onBack }: { title: string; sub?: string; onBack?: () => void }) {
  return (
    <View className="mb-4 min-h-touch flex-row items-center gap-2">
      {onBack && (
        <Pressable
          onPress={onBack}
          accessibilityRole="button"
          accessibilityLabel="뒤로"
          testID="header:back"
          className="-ml-3 h-12 w-12 items-center justify-center">
          <Icon name="back" />
        </Pressable>
      )}
      <View className="flex-1">
        <Txt variant="headline" accessibilityRole="header">
          {title}
        </Txt>
        {sub ? (
          <Txt variant="caption" sub>
            {sub}
          </Txt>
        ) : null}
      </View>
    </View>
  );
}
