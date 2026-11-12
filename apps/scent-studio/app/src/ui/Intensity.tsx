/**
 * 강도 표시(세로 눈금 3칸 + 약·중·강 글자)와 강도 고르기(3칸 구분 버튼) — design_rules.md 6절.
 */
import { Pressable, View } from 'react-native';

import { LEVEL_KO, LEVELS } from '../blend/blendDraft';
import { TestIds, type IntensityLevel } from './testIds';
import { Txt } from './Txt';

const FILLED: Record<IntensityLevel, number> = { weak: 1, medium: 2, strong: 3 };

/** 눈금 3칸. 색은 둘레 글자색(className)을 따른다 */
export function Ticks({ level, className = 'bg-ink dark:bg-ink-dark' }: { level: IntensityLevel; className?: string }) {
  return (
    <View className="flex-row items-end gap-0.5" accessible={false}>
      {[1, 2, 3].map((i) => (
        <View key={i} className={`h-3 w-0.5 ${className} ${i <= FILLED[level] ? '' : 'opacity-25'}`} />
      ))}
    </View>
  );
}

export function IntensityPicker({
  noteKey,
  noteName,
  value,
  onChange,
}: {
  noteKey: string;
  noteName: string;
  value: IntensityLevel;
  onChange: (l: IntensityLevel) => void;
}) {
  return (
    <View
      accessibilityRole="radiogroup"
      accessibilityLabel={`${noteName} 강도`}
      className="flex-row overflow-hidden rounded border border-line dark:border-line-dark">
      {LEVELS.map((l) => {
        const on = l === value;
        return (
          <Pressable
            key={l}
            testID={TestIds.intensity(noteKey, l)}
            accessibilityRole="radio"
            accessibilityState={{ checked: on }}
            accessibilityLabel={`${noteName} ${LEVEL_KO[l]}`}
            onPress={() => onChange(l)}
            className={`min-h-touch flex-1 items-center justify-center ${on ? 'bg-primary dark:bg-primary-dark' : ''}`}>
            <Txt variant="label" className={on ? 'text-onPrimary dark:text-onPrimary-dark' : ''} sub={!on}>
              {LEVEL_KO[l]}
            </Txt>
          </Pressable>
        );
      })}
    </View>
  );
}
