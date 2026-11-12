import { View } from 'react-native';

import { hintTexts, LAYER_LABEL, LEVEL_KO, toEngineBlend } from '@/src/blend/blendDraft';
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
  const maxPct = Math.max(1, ...ratios.map((r) => r.percent));
  const hints = hintTexts(engine, blend);
  const accordKo = (k: string) => cat.accords.find((a) => a.key === k)?.nameKo ?? k;
  const noteOf = (k: string) => engine.note(k)!;

  return (
    <Screen>
      <View testID={TestIds.SUMMARY_SCREEN}>
        <Header title={S.summaryTitle} onBack={goBack} />

        {/* 처방전 */}
        <View className="rounded border border-line bg-surface p-6 dark:border-line-dark dark:bg-surface-dark">
          <View className="flex-row items-baseline justify-between border-b border-ink pb-3 dark:border-ink-dark">
            <Txt variant="title">{S.myBlend}</Txt>
            <Txt variant="caption" sub>{`${S.noteCount}${draft.notes.length}${S.noteCountUnit}`}</Txt>
          </View>
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
            {ratios.map((r) => (
              <View
                key={r.accord}
                testID={TestIds.accordBar(r.accord)}
                accessible
                accessibilityLabel={`${accordKo(r.accord)} ${r.percent}퍼센트`}>
                <View className="flex-row items-baseline justify-between">
                  <Txt variant="label">{accordKo(r.accord)}</Txt>
                  <Txt variant="label">{`${r.percent}%`}</Txt>
                </View>
                <View className="mt-1 h-1.5 bg-surfaceAlt dark:bg-surfaceAlt-dark">
                  <View style={{ width: `${(r.percent / maxPct) * 100}%` }} className="h-1.5 bg-primary dark:bg-primary-dark" />
                </View>
              </View>
            ))}
          </View>
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

        <View className="mt-8 flex-row gap-2">
          <Button kind="secondary" className="flex-1" testID={TestIds.EDIT_NOTES} label={S.editNotes} onPress={goBack} />
        </View>
      </View>
    </Screen>
  );
}
