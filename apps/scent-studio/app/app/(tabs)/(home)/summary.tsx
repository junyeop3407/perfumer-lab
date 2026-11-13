import { useState } from 'react';
import { useWindowDimensions, View } from 'react-native';

import { brandLabel } from '@/src/catalog/brandNames';
import { scorePercent } from '@/src/recs/perfumeView';
import { useRecs } from '@/src/recs/useRecs';
import { GradeBadge } from '@/src/ui/Grade';
import { WIDE_MIN_WIDTH } from '@/src/ui/layout';
import { sharedAccordNames } from '@/src/ui/RecCard';

import { foldAccords, hintTexts, LAYER_LABEL, LEVEL_KO, toEngineBlend } from '@/src/blend/blendDraft';
import { useDraft } from '@/src/blend/DraftContext';
import { useCatalog } from '@/src/catalog/CatalogContext';
import { LAYERS } from '@/src/engine/types';
import { S } from '@/src/strings.ko';
import { Button } from '@/src/ui/Button';
import { Ticks } from '@/src/ui/Intensity';
import { go, goBack } from '@/src/ui/nav';
import { NoteNameText, useNoteNames } from '@/src/ui/NoteName';
import { LAYER_BG } from '@/src/ui/NoteParts';
import { Header, Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { Txt } from '@/src/ui/Txt';

/** 조합 요약: "조합 처방전"(이 앱의 강조 하나) + 어코드 막대 + 균형 안내 (승인 A8~A10, design_rules.md 1절) */
export default function SummaryScreen() {
  const catalog = useCatalog();
  const { draft } = useDraft();
  const names = useNoteNames();
  // 어코드가 많으면 상위 6개 + "그 외"로 접는다(5-2 폰 확인 C)
  const [allAccords, setAllAccords] = useState(false);
  const wide = useWindowDimensions().width >= WIDE_MIN_WIDTH;
  const { recs } = useRecs();
  if (catalog.status !== 'ready') {
    return (
      <Screen needsCatalog>
        <View />
      </Screen>
    );
  }
  const { catalog: cat, engine } = catalog;

  if (draft.notes.length === 0) {
    return (
      <Screen>
        <Header title={S.summaryTitle} onBack={goBack} />
        <View testID={TestIds.EMPTY_STATE} className="mt-8 items-center gap-4">
          <Txt sub>{S.summaryEmpty}</Txt>
          <Button label={S.newBlend} onPress={() => go('/families?fresh=1')} />
        </View>
      </Screen>
    );
  }

  const blend = toEngineBlend(draft);
  const ratios = engine.displayRatios(blend);
  const hints = hintTexts(engine, blend);
  const folded = foldAccords(ratios);
  const shown = allAccords ? ratios : folded.shown;
  const accordKo = (k: string) => cat.accords.find((a) => a.key === k)?.nameKo ?? k;
  const noteOf = (k: string) => engine.note(k)!;

  // PC: 오른쪽에 추천 미리보기 상위 3개 + [추천 전체 보기] (design_rules.md 4절). 폰: [추천 보기] 버튼만
  const preview = wide ? (
    <View>
      <Txt variant="headline" accessibilityRole="header">
        {S.previewTitle}
      </Txt>
      <Txt variant="caption" sub className="mt-1">
        {S.previewSub}
      </Txt>
      <View className="mt-4 gap-3">
        {recs.slice(0, 3).map((r) => {
          const p = cat.perfumes.find((x) => x.key === r.perfumeKey)!;
          return (
            <View key={r.perfumeKey} className="rounded border border-line bg-surface p-4 dark:border-line-dark dark:bg-surface-dark">
              <View className="flex-row items-baseline justify-between">
                <Txt variant="caption" sub>
                  {brandLabel(p.brand)}
                </Txt>
                <Txt variant="label">{`${scorePercent(r)}%`}</Txt>
              </View>
              <View className="mt-0.5 flex-row items-center justify-between gap-2">
                <Txt variant="noteName" className="flex-1">
                  {p.name}
                </Txt>
                <GradeBadge grade={r.grade} />
              </View>
              <Txt variant="caption" sub className="mt-1">
                {sharedAccordNames(cat, r).join(' · ')}
              </Txt>
            </View>
          );
        })}
      </View>
      <Button className="mt-4" testID={TestIds.SHOW_RECS} label={S.showAllRecs} onPress={() => go('/recs')} />
    </View>
  ) : undefined;

  return (
    <Screen wide={wide} side={preview}>
      <View testID={TestIds.SUMMARY_SCREEN}>
        <Header title={S.summaryTitle} onBack={goBack} />

        {/* 처방전 */}
        <View className="rounded border border-line bg-surface p-6 dark:border-line-dark dark:bg-surface-dark">
          <View className="flex-row items-baseline justify-between border-b border-ink pb-3 dark:border-ink-dark">
            <Txt variant="title">{S.myBlend}</Txt>
            <Txt variant="caption" sub>{`${S.noteCount}${draft.notes.length}${S.noteCountUnit}`}</Txt>
          </View>
          <Txt variant="caption" sub className="pt-2">
            {S.intensityLegend}
          </Txt>
          {LAYERS.map((l, i) => {
            const notes = draft.notes.filter((n) => n.layer === l);
            return (
              <View
                key={l}
                className={`flex-row gap-4 py-4 ${i < LAYERS.length - 1 ? 'border-b border-line dark:border-line-dark' : 'pb-0'}`}>
                <View className="w-20 flex-row items-center gap-1.5 self-start pt-1.5">
                  <View className={`h-2 w-2 rounded-chip ${LAYER_BG[l]}`} />
                  <Txt variant="layer">{LAYER_LABEL[l]}</Txt>
                </View>
                <View className="flex-1">
                  {notes.length === 0 ? (
                    <Txt variant="label" sub className="pt-1">
                      {S.layerEmpty}
                    </Txt>
                  ) : (
                    notes.map((n) => (
                      <View
                        key={n.noteKey}
                        testID={TestIds.selectedNote(l, n.noteKey)}
                        accessible
                        accessibilityLabel={`${names.label(noteOf(n.noteKey))}, ${LEVEL_KO[n.level]}`}
                        className="min-h-8 flex-row items-center justify-between gap-2">
                        <View className="flex-1">
                          <NoteNameText variant="noteName" ko={noteOf(n.noteKey).nameKo} en={names.en(noteOf(n.noteKey))} />
                        </View>
                        <View className="flex-row items-center gap-2">
                          <Ticks level={n.level} className="bg-inkSub dark:bg-inkSub-dark" />
                          <Txt variant="label" sub>
                            {LEVEL_KO[n.level]}
                          </Txt>
                        </View>
                      </View>
                    ))
                  )}
                </View>
              </View>
            );
          })}
        </View>

        {/* 어코드 */}
        <View className="mt-8">
          <Txt variant="headline" accessibilityRole="header">
            {S.accordsTitle}
          </Txt>
          <Txt variant="caption" sub className="mt-1" testID={TestIds.SIMILARITY_DISCLAIMER}>
            {S.accordsDisclaimer}
          </Txt>
          <View className="mt-4 gap-3">
            {shown.map((r) => (
              <AccordBar key={r.accord} testID={TestIds.accordBar(r.accord)} name={accordKo(r.accord)} percent={r.percent} />
            ))}
            {folded.rest && !allAccords && (
              <AccordBar
                testID={TestIds.accordBar('other')}
                name={`${S.accordsOther}${folded.rest.count}${S.noteCountUnit}`}
                percent={folded.rest.percent}
                muted
              />
            )}
          </View>
          {folded.rest && (
            <Button
              kind="text"
              className="mt-1 self-start"
              testID={TestIds.ACCORDS_MORE}
              label={allAccords ? S.accordsLess : S.accordsMore}
              accessibilityState={{ expanded: allAccords }}
              onPress={() => setAllAccords((v) => !v)}
            />
          )}
        </View>

        {hints.length > 0 && (
          <View testID={TestIds.BALANCE_HINT} className="mt-8 gap-1 rounded bg-accentSoft px-4 py-3 dark:bg-accentSoft-dark">
            {hints.map((h) => (
              <Txt key={h} variant="label">
                {h}
              </Txt>
            ))}
          </View>
        )}

        {!wide && <Button className="mt-8" testID={TestIds.SHOW_RECS} label={S.showRecs} onPress={() => go('/recs')} />}
        <View className={`${wide ? 'mt-8' : 'mt-2'} flex-row gap-2`}>
          <Button kind="secondary" className="flex-1" testID={TestIds.EDIT_NOTES} label={S.editNotes} onPress={goBack} />
        </View>
      </View>
    </Screen>
  );
}

/** 어코드 막대 한 줄: 이름과 %를 글자로, 막대 길이는 실제 %(5-2 기기 확인 디자인 6). muted는 "그 외" 묶음 */
function AccordBar({ testID, name, percent, muted = false }: { testID: string; name: string; percent: number; muted?: boolean }) {
  return (
    <View testID={testID} accessible accessibilityLabel={`${name} ${percent}퍼센트`}>
      <View className="flex-row items-baseline justify-between">
        <Txt variant="label" sub={muted}>
          {name}
        </Txt>
        <Txt variant="label" sub={muted}>{`${percent}%`}</Txt>
      </View>
      <View className="mt-1 h-1.5 bg-surfaceAlt dark:bg-surfaceAlt-dark">
        <View
          style={{ width: `${percent}%` }}
          className={`h-1.5 ${muted ? 'bg-compare dark:bg-compare-dark' : 'bg-primary dark:bg-primary-dark'}`}
        />
      </View>
    </View>
  );
}
