/**
 * 등급 배지(design_rules.md 6절 "등급 표시")와 등급 설명 ⓘ(2026-11-13 사장 결정, docs/grade_explanation.md).
 * 매우 비슷: accent 바탕(화면의 골드는 여기) · 비슷: accentStrong 테두리+글자 · 참고: inkSub 글자만. 글자가 늘 함께.
 */
import { Modal, Pressable, useWindowDimensions, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { Grade } from '../engine/types';
import { S } from '../strings.ko';
import { Button } from './Button';
import { Icon } from './Icon';
import { WIDE_MIN_WIDTH } from './layout';
import { TestIds } from './testIds';
import { Txt } from './Txt';

export const GRADE_KO: Record<Grade, string> = {
  VERY_SIMILAR: S.gradeVery,
  SIMILAR: S.gradeSimilar,
  REFERENCE: S.gradeRef,
};

const BOX: Record<Grade, string> = {
  VERY_SIMILAR: 'bg-accent dark:bg-accent-dark',
  SIMILAR: 'border border-accentStrong dark:border-accentStrong-dark',
  REFERENCE: '',
};
const TEXT: Record<Grade, string> = {
  VERY_SIMILAR: 'text-onAccent dark:text-onAccent-dark',
  SIMILAR: 'text-accentStrong dark:text-accentStrong-dark',
  REFERENCE: 'text-inkSub dark:text-inkSub-dark',
};

/** 배지. testID는 글자 요소에 붙인다(승인 B2가 등급 글자를 그대로 읽음) */
export function GradeBadge({ grade, testID }: { grade: Grade; testID?: string }) {
  return (
    <View className={`self-start rounded-chip px-2 py-0.5 ${BOX[grade]}`}>
      <Txt variant="caption" testID={testID} className={`font-sans-medium ${TEXT[grade]}`}>
        {GRADE_KO[grade]}
      </Txt>
    </View>
  );
}

/** 목록 헤더의 ⓘ 버튼(누르는 영역 48) */
export function GradeInfoButton({ onPress }: { onPress: () => void }) {
  return (
    <Pressable
      testID={TestIds.GRADE_INFO}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={S.gradeInfoOpen}
      className="-mr-3 h-12 w-12 items-center justify-center">
      <Icon name="info" color="inkSub" />
    </Pressable>
  );
}

/** 등급 설명: 폰은 아래 시트, PC는 가운데 카드. 바깥이나 [닫기]로 닫힌다 */
export function GradeInfoSheet({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const reduceMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const wide = useWindowDimensions().width >= WIDE_MIN_WIDTH;
  const rows: [Grade, string][] = [
    ['VERY_SIMILAR', S.gradeInfoVery],
    ['SIMILAR', S.gradeInfoSimilar],
    ['REFERENCE', S.gradeInfoRef],
  ];
  return (
    <Modal visible={visible} transparent animationType={reduceMotion ? 'none' : wide ? 'fade' : 'slide'} onRequestClose={onClose}>
      <View className={`flex-1 ${wide ? 'items-center justify-center' : 'justify-end'}`}>
        <Pressable
          className="absolute inset-0 bg-ink/30 dark:bg-bg-dark/60"
          accessibilityRole="button"
          accessibilityLabel={S.close}
          onPress={onClose}
        />
        <View
          testID={TestIds.GRADE_INFO_SHEET}
          accessibilityViewIsModal
          style={wide ? undefined : { paddingBottom: 24 + insets.bottom }}
          className={`border-line bg-surface px-6 pt-6 dark:border-line-dark dark:bg-surface-dark ${
            wide ? 'w-full max-w-sm rounded border pb-6' : 'rounded-t border-t shadow-sm'
          }`}>
          <Txt variant="headline" accessibilityRole="header">
            {S.gradeInfoTitle}
          </Txt>
          <Txt className="mt-3">{S.gradeInfoBody}</Txt>
          <View className="mt-4 gap-3">
            {rows.map(([g, text]) => (
              <View key={g} className="flex-row items-center gap-3">
                <View className="w-20">
                  <GradeBadge grade={g} />
                </View>
                <Txt variant="label" className="flex-1">
                  {text}
                </Txt>
              </View>
            ))}
          </View>
          <Txt variant="caption" sub className="mt-4">
            {S.gradeInfoNote}
          </Txt>
          <Button kind="secondary" className="mt-6" label={S.close} onPress={onClose} />
        </View>
      </View>
    </Modal>
  );
}
