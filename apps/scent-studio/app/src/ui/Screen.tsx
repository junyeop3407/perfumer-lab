/**
 * 화면 틀: 위쪽 safe area(노치·상태 표시줄), 세로 스크롤, 좌우 여백 24, 최대 폭.
 * - side: PC에서 스크롤 밖 오른쪽 패널(목록을 내려도 화면에 그대로 보임)
 * - footer: 스크롤 밖 아래 고정 막대(안내·강도·다음 버튼이 목록 위치와 상관없이 보임)
 * - needsCatalog: 카탈로그가 준비되기 전에는 "불러오는 중"·"오류 + 다시 시도"를 보여 준다(네 가지 상태, design_rules.md 7절)
 * 아래쪽 safe area는 하단 탭이 맡는다. 키보드: 목록을 끌면 닫히고, 버튼은 키보드가 떠 있어도 한 번에 눌린다(D3).
 */
import type { ReactNode } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useCatalog } from '../catalog/CatalogContext';
import { S } from '../strings.ko';
import { Button } from './Button';
import { Icon } from './Icon';
import { TestIds } from './testIds';
import { Txt } from './Txt';

export function Screen({
  children,
  wide = false,
  side,
  footer,
  needsCatalog = false,
}: {
  children: ReactNode;
  wide?: boolean;
  side?: ReactNode;
  footer?: ReactNode;
  needsCatalog?: boolean;
}) {
  const catalog = useCatalog();
  // 가상 데이터 띠가 있으면 띠가 위쪽 safe area를 이미 맡는다(_layout.tsx SampleBanner)
  const banner = catalog.status === 'ready' && catalog.isSample;
  const gate = needsCatalog && catalog.status !== 'ready';
  return (
    <SafeAreaView edges={banner ? [] : ['top']} className="flex-1 bg-bg dark:bg-bg-dark">
      <View className="flex-1 flex-row">
        <View className="flex-1">
          <ScrollView keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag" contentContainerClassName="px-6 pb-12 pt-4">
            <View className={`w-full ${wide ? 'max-w-page' : 'max-w-text'}`}>{gate ? <CatalogGate /> : children}</View>
          </ScrollView>
          {!gate && footer}
        </View>
        {!gate && side ? <View className="w-panel pr-6 pt-4">{side}</View> : null}
      </View>
    </SafeAreaView>
  );
}

/** 카탈로그 준비 전: 불러오는 중(움직임 없는 막대 + 한 줄) 또는 오류 + 다시 시도 */
function CatalogGate() {
  const catalog = useCatalog();
  if (catalog.status === 'error') {
    return (
      <View className="mt-8 gap-4">
        <Txt testID={TestIds.ERROR_MESSAGE} accessibilityLiveRegion="polite">
          {S.catalogError}
        </Txt>
        <Button kind="secondary" testID={TestIds.ERROR_RETRY} label={S.retry} onPress={catalog.retry} />
      </View>
    );
  }
  return (
    <View className="mt-8 gap-3" accessibilityLiveRegion="polite">
      <View className="h-4 w-2/3 bg-surfaceAlt dark:bg-surfaceAlt-dark" />
      <View className="h-4 w-1/2 bg-surfaceAlt dark:bg-surfaceAlt-dark" />
      <Txt variant="caption" sub>
        {S.catalogLoading}
      </Txt>
    </View>
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
          accessibilityLabel={S.back}
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
