/**
 * 향수 상세 (승인 B5·B6·F2, 시안 mock.html detail).
 * - 추천에서 열면(rec·blend 있음): 등급 + 데이터상 유사도, 노트 피라미드(내 조합 표시), "내 조합 vs 이 향수" 비교 막대,
 *   겹친 노트(같은 층 / 다른 층), 이 향수에만 있는 노트.
 * - 검색에서 열면(조합 없음): 비교 막대 없이 그 향수의 어코드와 노트 피라미드만.
 * 찜·시향 기록·"추천이 이상해요"는 5-6·5-7에서 붙인다.
 */
import { useState } from 'react';
import { View } from 'react-native';

import { LAYER_KO, LAYER_LABEL } from '../blend/blendDraft';
import { useCatalog } from '../catalog/CatalogContext';
import { LAYERS, type BlendNote, type Layer, type Recommendation } from '../engine/types';
import { ACCORDS_SHOWN_DETAIL, chipMark, compareRows, onlyInPerfume, overlaps, perfumeAccords, scorePercent } from '../recs/perfumeView';
import { S } from '../strings.ko';
import { Button } from './Button';
import { GradeBadge } from './Grade';
import { Icon } from './Icon';
import { NoteNameText, useNoteNames } from './NoteName';
import { LAYER_BG, LAYER_FG } from './NoteParts';
import { TestIds } from './testIds';
import { Txt } from './Txt';

export function PerfumeDetail({
  perfumeKey,
  rec,
  blend,
}: {
  perfumeKey: string;
  rec?: Recommendation;
  blend?: readonly BlendNote[];
}) {
  const catalog = useCatalog();
  const names = useNoteNames();
  const [allAccords, setAllAccords] = useState(false);
  if (catalog.status !== 'ready') return null;
  const { catalog: cat, engine } = catalog;
  const perfume = cat.perfumes.find((p) => p.key === perfumeKey);
  if (!perfume) {
    return (
      <View testID={TestIds.EMPTY_STATE} className="mt-8">
        <Txt sub>{S.perfumeMissing}</Txt>
      </View>
    );
  }
  const compare = !!blend && blend.length > 0;
  const accordKo = (k: string) => cat.accords.find((a) => a.key === k)?.nameKo ?? k;
  const noteKo = (k: string) => engine.note(k);
  const theirs = perfumeAccords(engine, cat, perfume);
  const over = compare ? overlaps(perfume, blend) : [];
  const overKind = new Map(over.map((o) => [o.noteKey, o]));
  const only = compare ? onlyInPerfume(perfume, blend) : [];
  const rows = compare ? compareRows(engine.displayRatios(blend), theirs) : [];
  const total = compare ? rows.length : theirs.length;
  const folded = !allAccords && total > ACCORDS_SHOWN_DETAIL;
  const source = cat.source?.name === 'sample' ? S.dataSourceSample : `${S.dataSourcePrefix}${cat.source?.name ?? ''}`;

  /** 노트 하나(이름, 겹친 이름은 영어도). 없는 노트 key는 key 그대로 */
  const noteChip = (k: string, layer: Layer, mark?: string) => {
    const n = noteKo(k);
    return (
      <View key={k} className={`mr-2 mt-2 flex-row items-center gap-1.5 rounded-chip px-2.5 py-1.5 ${LAYER_BG[layer]}`}>
        <NoteNameText ko={n?.nameKo ?? k} en={n ? names.en(n) : null} className={LAYER_FG[layer]} />
        {mark ? (
          <Txt variant="caption" className={LAYER_FG[layer]}>
            {`✓ ${mark}`}
          </Txt>
        ) : null}
      </View>
    );
  };

  return (
    <View testID={TestIds.DETAIL_SCREEN}>
      <Txt variant="caption" sub>
        {perfume.brand}
      </Txt>
      <Txt variant="title" accessibilityRole="header" className="mt-0.5">
        {perfume.name}
      </Txt>
      {(perfume.year || perfume.concentration) && (
        <Txt variant="caption" sub className="mt-1">
          {[perfume.concentration, perfume.year].filter(Boolean).join(' · ')}
        </Txt>
      )}
      {rec && (
        <View className="mt-2 flex-row items-center gap-2">
          <GradeBadge grade={rec.grade} />
          <Txt variant="label">{`${S.similarityPrefix}${scorePercent(rec)}%`}</Txt>
        </View>
      )}

      {/* 노트 피라미드 */}
      <View className="mt-8">
        <Txt variant="headline" accessibilityRole="header">
          {S.notesTitle2}
        </Txt>
        <View className="mt-2">
          {LAYERS.map((l) => (
            <View key={l} testID={TestIds.pyramid(l)} className="flex-row items-start border-b border-line py-2 dark:border-line-dark">
              <Txt variant="layer" sub className="w-20 pt-3.5">
                {LAYER_LABEL[l]}
              </Txt>
              <View className="flex-1 flex-row flex-wrap">
                {perfume[l].length === 0 ? (
                  <Txt variant="label" sub className="pt-3">
                    {S.layerNone}
                  </Txt>
                ) : (
                  perfume[l].map((k) => {
                    const m = chipMark(overKind.get(k), l);
                    return noteChip(k, l, m === 'mine' ? S.myBlendMark : m === 'otherLayer' ? S.otherLayerMark : undefined);
                  })
                )}
              </View>
            </View>
          ))}
        </View>
      </View>

      {/* 어코드: 비교 막대(조합 있음) / 향수 어코드만(검색) */}
      <View className="mt-8">
        <Txt variant="headline" accessibilityRole="header">
          {compare ? S.compareTitle : S.perfumeAccordsTitle}
        </Txt>
        <View className="mt-2 gap-4">
          {compare
            ? (folded ? rows.slice(0, ACCORDS_SHOWN_DETAIL) : rows).map((r) => (
                <View
                  key={r.accord}
                  testID={`${TestIds.COMPARE_BAR_PREFIX}${r.accord}`}
                  accessible
                  accessibilityLabel={`${accordKo(r.accord)}, ${S.compareMine} ${r.mine}퍼센트, ${S.compareTheirs} ${r.theirs}퍼센트`}>
                  <Txt variant="label">{accordKo(r.accord)}</Txt>
                  <CompareLine who={S.compareMine} percent={r.mine} mine />
                  <CompareLine who={S.compareTheirs} percent={r.theirs} />
                </View>
              ))
            : (folded ? theirs.slice(0, ACCORDS_SHOWN_DETAIL) : theirs).map((r) => (
                <View
                  key={r.accord}
                  testID={`${TestIds.PERFUME_ACCORD_PREFIX}${r.accord}`}
                  accessible
                  accessibilityLabel={`${accordKo(r.accord)} ${r.percent}퍼센트`}>
                  <View className="flex-row items-baseline justify-between">
                    <Txt variant="label">{accordKo(r.accord)}</Txt>
                    <Txt variant="label">{`${r.percent}%`}</Txt>
                  </View>
                  <Bar percent={r.percent} tone="compare" />
                </View>
              ))}
        </View>
        {total > ACCORDS_SHOWN_DETAIL && (
          <Button
            kind="text"
            className="mt-1 self-start"
            label={allAccords ? S.accordsLess : S.accordsMore}
            accessibilityState={{ expanded: allAccords }}
            onPress={() => setAllAccords((v) => !v)}
          />
        )}
      </View>

      {compare && (
        <>
          <View testID={TestIds.MATCHED_NOTES} className="mt-8">
            <Txt variant="headline" accessibilityRole="header">
              {S.matchedTitle}
            </Txt>
            {over.length === 0 ? (
              <Txt sub className="mt-2">
                {S.matchedEmpty}
              </Txt>
            ) : (
              <View className="mt-2 gap-2">
                {over.map((o) => {
                  const n = noteKo(o.noteKey);
                  return (
                    <View key={o.noteKey} className="flex-row items-center gap-2">
                      <Icon name="check" size={16} color="primary" />
                      <NoteNameText ko={n?.nameKo ?? o.noteKey} en={n ? names.en(n) : null} variant="body" />
                      <Txt variant="caption" sub>
                        {`${o.kind === 'same_layer' ? S.sameLayer : S.otherLayer} · ${LAYER_KO[o.blendLayer]}`}
                      </Txt>
                    </View>
                  );
                })}
              </View>
            )}
          </View>
          <View testID={TestIds.ONLY_IN_PERFUME_NOTES} className="mt-8">
            <Txt variant="headline" accessibilityRole="header">
              {S.onlyTitle}
            </Txt>
            {only.length === 0 ? (
              <Txt sub className="mt-2">
                {S.onlyEmpty}
              </Txt>
            ) : (
              <View className="mt-1 flex-row flex-wrap">
                {only.map((o) => noteChip(o.noteKey, o.layer))}
              </View>
            )}
          </View>
        </>
      )}

      <View className="mt-8 gap-1">
        <Txt variant="caption" sub testID={TestIds.SIMILARITY_DISCLAIMER}>
          {rec ? S.detailDisclaimer : S.perfumeDisclaimer}
        </Txt>
        <Txt variant="caption" sub testID={TestIds.DATA_SOURCE}>
          {`${source}${S.strengthNote}`}
        </Txt>
      </View>
    </View>
  );
}

/** 비교 막대 한 줄: [누구] [막대 6dp] [숫자%] */
function CompareLine({ who, percent, mine = false }: { who: string; percent: number; mine?: boolean }) {
  return (
    <View className="mt-1 flex-row items-center gap-3">
      <Txt variant="caption" sub className="w-12">
        {who}
      </Txt>
      <View className="flex-1">
        <Bar percent={percent} tone={mine ? 'primary' : 'compare'} />
      </View>
      <View className="w-12 items-end">
        <Txt variant="label">{`${percent}%`}</Txt>
      </View>
    </View>
  );
}

function Bar({ percent, tone }: { percent: number; tone: 'primary' | 'compare' }) {
  return (
    <View className="mt-1 h-1.5 bg-surfaceAlt dark:bg-surfaceAlt-dark">
      <View
        style={{ width: `${Math.min(100, percent)}%` }}
        className={`h-1.5 ${tone === 'primary' ? 'bg-primary dark:bg-primary-dark' : 'bg-compare dark:bg-compare-dark'}`}
      />
    </View>
  );
}
