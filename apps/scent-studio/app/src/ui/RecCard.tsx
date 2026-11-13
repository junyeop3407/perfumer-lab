/**
 * 추천 목록의 향수 한 줄 (승인 B2, 시안 mock.html recList): [순위] 브랜드·이름·공통 어코드·겹친 노트 | 점수·등급.
 * 글자를 읽는 요소마다 testID를 붙인다(승인 테스트가 각각 읽는다). 등급·점수 요소에는 화면 읽기 설명을 따로 달지 않는다
 * (테스트가 읽는 글자에 섞이지 않게, 카드 전체 설명에 함께 넣는다).
 */
import { Pressable, View } from 'react-native';

import type { Catalog, Perfume, Recommendation } from '../engine/types';
import { overlaps, scorePercent } from '../recs/perfumeView';
import type { BlendNote } from '../engine/types';
import type { RecommendationEngine } from '../engine/RecommendationEngine';
import { S } from '../strings.ko';
import { GRADE_KO, GradeBadge } from './Grade';
import { TestIds } from './testIds';
import { Txt } from './Txt';

/** 화면에 보일 공통 어코드: 둘 다 1% 이상인 것만, 최대 3개(엔진이 겹치는 몫이 큰 순으로 준다) */
export function sharedAccordNames(catalog: Catalog, rec: Recommendation): string[] {
  return rec.sharedAccords
    .filter((a) => Math.min(a.perfumePct, a.blendPct) >= 1)
    .map((a) => catalog.accords.find((x) => x.key === a.accord)?.nameKo ?? a.accord);
}

export function RecCard({
  rank,
  rec,
  perfume,
  catalog,
  engine,
  blend,
  selected,
  onPress,
}: {
  rank: number;
  rec: Recommendation;
  perfume: Perfume;
  catalog: Catalog;
  engine: RecommendationEngine;
  blend: readonly BlendNote[];
  selected: boolean;
  onPress: () => void;
}) {
  const shared = sharedAccordNames(catalog, rec);
  const matched = overlaps(perfume, blend).map((o) => engine.note(o.noteKey)?.nameKo ?? o.noteKey);
  const score = scorePercent(rec);
  const sharedText = shared.length > 0 ? shared.join(' · ') : S.sharedNone;
  return (
    <Pressable
      testID={TestIds.rec(rank)}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={`${rank}${S.rankUnit}, ${perfume.brand} ${perfume.name}, ${score}%, ${GRADE_KO[rec.grade]}`}
      className={`flex-row gap-3 border-b border-line py-4 active:bg-surfaceAlt dark:border-line-dark dark:active:bg-surfaceAlt-dark ${
        selected ? 'bg-surface dark:bg-surface-dark' : ''
      }`}>
      <View className="w-7 pl-1 pt-0.5">
        <Txt variant="label" sub>
          {rank}
        </Txt>
      </View>
      <View className="flex-1">
        <Txt variant="caption" sub testID={TestIds.recBrand(rank)}>
          {perfume.brand}
        </Txt>
        <Txt variant="noteName" testID={TestIds.recName(rank)}>
          {perfume.name}
        </Txt>
        <Txt variant="caption" className="mt-1" testID={TestIds.recSharedAccords(rank)}>
          {sharedText}
        </Txt>
        {/* 실제로 겹친 노트가 없으면 이 줄을 숨긴다(2026-11-13 사장). 상세에서 "겹친 노트는 없지만…"으로 알린다 */}
        {matched.length > 0 && (
          <Txt variant="caption" sub className="mt-0.5" testID={TestIds.recMatchedNotes(rank)}>
            {`${S.matchedLabel}${matched.join(', ')}`}
          </Txt>
        )}
      </View>
      <View className="items-end gap-1.5 pr-1">
        <Txt variant="label" testID={TestIds.recScore(rank)}>{`${score}%`}</Txt>
        <GradeBadge grade={rec.grade} testID={TestIds.recGrade(rank)} />
      </View>
    </Pressable>
  );
}
