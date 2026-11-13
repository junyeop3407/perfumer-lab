import { useState } from 'react';
import { ScrollView, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useCatalog } from '@/src/catalog/CatalogContext';
import type { Recommendation } from '@/src/engine/types';
import { useRecs } from '@/src/recs/useRecs';
import { S } from '@/src/strings.ko';
import { Button } from '@/src/ui/Button';
import { GradeInfoButton, GradeInfoSheet } from '@/src/ui/Grade';
import { WIDE_MIN_WIDTH } from '@/src/ui/layout';
import { go, goBack } from '@/src/ui/nav';
import { PerfumeDetail } from '@/src/ui/PerfumeDetail';
import { RecCard } from '@/src/ui/RecCard';
import { Header, Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { Txt } from '@/src/ui/Txt';

/**
 * 추천 목록 (승인 B1~B4·B6·H5, 시안 mock.html recs).
 * 폰: 목록 → 누르면 향수 상세 화면. PC: 목록(400) 옆에 상세가 나란히(처음엔 1위).
 */
export default function RecsScreen() {
  const catalog = useCatalog();
  const { blend, recs } = useRecs();
  const wide = useWindowDimensions().width >= WIDE_MIN_WIDTH;
  const insets = useSafeAreaInsets();
  const [infoOpen, setInfoOpen] = useState(false);
  const [picked, setPicked] = useState<string | null>(null);

  if (catalog.status !== 'ready') {
    return (
      <Screen needsCatalog>
        <View />
      </Screen>
    );
  }
  const { catalog: cat, engine } = catalog;
  const mine = blend.length > 0 ? engine.displayRatios(blend) : [];
  const perfumeOf = (key: string) => cat.perfumes.find((p) => p.key === key)!;
  const selectedKey = wide ? (picked && recs.some((r) => r.perfumeKey === picked) ? picked : recs[0]?.perfumeKey) : null;
  const selectedRec = recs.find((r) => r.perfumeKey === selectedKey);
  const source = cat.source?.name === 'sample' ? S.dataSourceSample : `${S.dataSourcePrefix}${cat.source?.name ?? ''}`;

  const list = (
    <View>
      <Header
        title={S.recsTitle}
        sub={S.recsSub}
        onBack={goBack}
        right={recs.length > 0 ? <GradeInfoButton onPress={() => setInfoOpen(true)} /> : undefined}
      />
      {blend.length === 0 ? (
        <View testID={TestIds.EMPTY_STATE} className="mt-4 gap-4">
          <Txt sub>{S.summaryEmpty}</Txt>
          <Button label={S.newBlend} onPress={() => go('/families?fresh=1')} />
        </View>
      ) : recs.length === 0 ? (
        <View testID={TestIds.EMPTY_STATE} className="mt-4 gap-4">
          <Txt sub>{S.recsEmpty}</Txt>
          <Button kind="secondary" label={S.editNotes} onPress={goBack} />
        </View>
      ) : (
        <View testID={TestIds.REC_LIST} className="border-t border-line dark:border-line-dark">
          {recs.map((r: Recommendation, i) => (
            <RecCard
              key={r.perfumeKey}
              rank={i + 1}
              rec={r}
              perfume={perfumeOf(r.perfumeKey)}
              catalog={cat}
              engine={engine}
              blend={blend}
              mine={mine}
              selected={r.perfumeKey === selectedKey}
              onPress={() => (wide ? setPicked(r.perfumeKey) : go(`/perfume?key=${encodeURIComponent(r.perfumeKey)}`))}
            />
          ))}
        </View>
      )}
      {recs.length > 0 && (
        <View className="mt-4 gap-1">
          <Txt variant="caption" sub testID={TestIds.SIMILARITY_DISCLAIMER}>
            {S.detailDisclaimer}
          </Txt>
          <Txt variant="caption" sub testID={TestIds.DATA_SOURCE}>
            {`${source}${S.strengthNote}`}
          </Txt>
        </View>
      )}
    </View>
  );

  const sheet = <GradeInfoSheet visible={infoOpen} onClose={() => setInfoOpen(false)} />;

  if (!wide) {
    return (
      <>
        <Screen>{list}</Screen>
        {sheet}
      </>
    );
  }

  // PC: 목록 400 + 상세 나머지, 둘 다 따로 스크롤 (design_rules.md 4절)
  return (
    <View style={{ paddingTop: cat.source?.name === 'sample' ? 0 : insets.top }} className="flex-1 flex-row bg-bg dark:bg-bg-dark">
      <ScrollView className="w-list flex-none border-r border-line dark:border-line-dark" contentContainerClassName="px-6 pb-16 pt-4">
        {list}
      </ScrollView>
      <ScrollView className="flex-1" contentContainerClassName="px-8 pb-16 pt-8">
        <View className="w-full max-w-text">
          {selectedKey ? (
            <PerfumeDetail key={selectedKey} perfumeKey={selectedKey} rec={selectedRec} blend={blend} />
          ) : (
            <Txt sub>{S.recsPickHint}</Txt>
          )}
        </View>
      </ScrollView>
      {sheet}
    </View>
  );
}
