import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';

import { applyFamilies } from '@/src/blend/blendDraft';
import { useDraft } from '@/src/blend/DraftContext';
import { useCatalog } from '@/src/catalog/CatalogContext';
import { S } from '@/src/strings.ko';
import { Button } from '@/src/ui/Button';
import { go, goBack } from '@/src/ui/nav';
import { Icon } from '@/src/ui/Icon';
import { Header, Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { Txt } from '@/src/ui/Txt';

/** 계열 고르기 (테두리 칩, 고르면 accentSoft 바탕 + accentStrong 테두리 + 체크 — design_rules.md 6절) */
export default function FamiliesScreen() {
  const catalog = useCatalog();
  const { draft, setDraft } = useDraft();
  // fresh=1: 홈의 "새 조합 만들기"로 옴. 만들던 조합은 "다음"을 누를 때 비운다(뒤로 가면 그대로)
  const fresh = useLocalSearchParams<{ fresh?: string }>().fresh === '1';
  const [picked, setPicked] = useState<string[]>(fresh ? [] : draft.families);
  if (catalog.status !== 'ready') {
    return (
      <Screen needsCatalog>
        <Header title={S.familiesTitle} onBack={goBack} />
      </Screen>
    );
  }
  const families = [...catalog.catalog.families].sort((a, b) => a.wheelOrder - b.wheelOrder);

  const toggle = (code: string) =>
    setPicked((p) => (p.includes(code) ? p.filter((c) => c !== code) : [...p, code]));

  return (
    <Screen>
      <Header title={S.familiesTitle} onBack={goBack} />
      <Txt sub>{S.familiesLead}</Txt>
      <View className="mt-6 flex-row flex-wrap gap-x-3">
        {families.map((f) => {
          const on = picked.includes(f.code);
          return (
            <Pressable
              key={f.code}
              testID={TestIds.family(f.code)}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: on }}
              accessibilityLabel={f.nameKo}
              onPress={() => toggle(f.code)}
              className="min-h-touch justify-center">
              <View
                className={`h-9 flex-row items-center gap-1 rounded-chip border px-2.5 ${
                  on
                    ? 'border-accentStrong bg-accentSoft dark:border-accentStrong-dark dark:bg-accentSoft-dark'
                    : 'border-line dark:border-line-dark'
                }`}>
                {on && <Icon name="check" size={16} />}
                <Txt variant="label">{f.nameKo}</Txt>
              </View>
            </Pressable>
          );
        })}
      </View>
      <Button
        className="mt-8"
        testID={TestIds.NEXT}
        label={S.next}
        onPress={() => {
          setDraft(applyFamilies(draft, picked, fresh));
          go('/notes');
        }}
      />
    </Screen>
  );
}
