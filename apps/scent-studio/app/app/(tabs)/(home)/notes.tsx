import { useMemo, useState } from 'react';
import { Modal, Pressable, TextInput, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { addNote, LAYER_KO, LAYER_LABEL, noteSections, removeNote, searchNotes, setLevel } from '@/src/blend/blendDraft';
import { useDraft } from '@/src/blend/DraftContext';
import { useCatalog } from '@/src/catalog/CatalogContext';
import { LAYERS, type Layer, type Note } from '@/src/engine/types';
import { S } from '@/src/strings.ko';
import { Button } from '@/src/ui/Button';
import { Icon } from '@/src/ui/Icon';
import { IntensityPicker } from '@/src/ui/Intensity';
import { WIDE_MIN_WIDTH } from '@/src/ui/layout';
import { go, goBack } from '@/src/ui/nav';
import { familyName, NoteCardBody, NoteListItem, SelectedChip } from '@/src/ui/NoteParts';
import { Header, Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { useTokenColor } from '@/src/ui/tokens';
import { Txt } from '@/src/ui/Txt';

type Notice = { kind: 'limit' } | { kind: 'other'; layer: Layer } | null;

/** 노트 고르기: 층 탭 · 검색 · 고른 노트(강도) · 노트 목록 · 노트 카드 (승인 A2~A7) */
export default function NotesScreen() {
  const catalog = useCatalog();
  const { draft, setDraft } = useDraft();
  const { width } = useWindowDimensions();
  const wide = width >= WIDE_MIN_WIDTH;
  const insets = useSafeAreaInsets();
  const color = useTokenColor();

  const [layer, setLayer] = useState<Layer>('top');
  const [query, setQuery] = useState('');
  const [focusKey, setFocusKey] = useState<string | null>(null);
  const [infoKey, setInfoKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice>(null);

  const cat = catalog.status === 'ready' ? catalog.catalog : null;
  const sections = useMemo(() => (cat ? noteSections(cat, draft.families) : []), [cat, draft.families]);
  const results = useMemo(() => (cat ? searchNotes(cat, query) : []), [cat, query]);
  if (!cat) return null;

  const noteOf = (k: string) => cat.notes.find((n) => n.key === k)!;
  const pickedLayerOf = (k: string) => draft.notes.find((n) => n.noteKey === k)?.layer ?? null;
  const focused = draft.notes.find((n) => n.noteKey === focusKey) ?? null;
  const infoNote = infoKey ? noteOf(infoKey) : null;

  const pick = (k: string) => {
    const already = pickedLayerOf(k);
    if (already === layer) {
      setFocusKey(k);
      setNotice(null);
      return;
    }
    const r = addNote(draft, layer, k, cat);
    if (r.ok) {
      setDraft(r.draft);
      setFocusKey(k);
      setNotice(null);
    } else if (r.error === 'LAYER_LIMIT') setNotice({ kind: 'limit' });
    else if (r.error === 'IN_OTHER_LAYER') setNotice({ kind: 'other', layer: r.layer });
  };

  const item = (n: Note) => (
    <NoteListItem
      key={n.key}
      note={n}
      familyKo={familyName(cat, n.family)}
      pickedLayer={pickedLayerOf(n.key)}
      onPick={() => pick(n.key)}
      onInfo={() => setInfoKey(n.key)}
    />
  );

  const familiesText =
    draft.families.length > 0 ? S.familiesPicked + draft.families.map((c) => familyName(cat, c)).join(' · ') : S.familiesNone;

  const card = infoNote && (
    <NoteCardBody
      catalog={cat}
      note={infoNote}
      layer={layer}
      canAdd={pickedLayerOf(infoNote.key) === null}
      onAdd={() => {
        pick(infoNote.key);
        setInfoKey(null);
      }}
      onClose={() => setInfoKey(null)}
    />
  );

  const main = (
    <View className="w-full max-w-text flex-1">
      <Header title={S.notesTitle} sub={familiesText} onBack={goBack} />

      {/* 층 탭 */}
      <View accessibilityRole="tablist" className="flex-row border-b border-line dark:border-line-dark">
        {LAYERS.map((l) => {
          const on = l === layer;
          const count = draft.notes.filter((n) => n.layer === l).length;
          return (
            <Pressable
              key={l}
              testID={TestIds.layerTab(l)}
              accessibilityRole="tab"
              accessibilityState={{ selected: on }}
              accessibilityLabel={`${LAYER_KO[l]}, ${count}개 고름`}
              onPress={() => {
                setLayer(l);
                setNotice(null);
              }}
              className={`min-h-touch flex-1 items-center justify-center ${on ? 'border-b-2 border-primary dark:border-primary-dark' : ''}`}>
              <Txt variant="label" className={on ? 'text-primary dark:text-primary-dark' : ''} sub={!on}>
                {`${LAYER_LABEL[l]} ${count}`}
              </Txt>
            </Pressable>
          );
        })}
      </View>

      {/* 검색 */}
      <View className="mt-4 min-h-touch flex-row items-center gap-2 rounded bg-surfaceAlt px-3 dark:bg-surfaceAlt-dark">
        <Icon name="search" color="inkSub" />
        <TextInput
          testID={TestIds.NOTE_SEARCH}
          value={query}
          onChangeText={setQuery}
          placeholder={S.noteSearchPlaceholder}
          placeholderTextColor={color('inkSub')}
          accessibilityLabel={S.noteSearchLabel}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
          className="min-h-touch flex-1 font-sans text-body text-ink dark:text-ink-dark"
        />
        {query !== '' && (
          <Pressable
            onPress={() => setQuery('')}
            accessibilityRole="button"
            accessibilityLabel="검색어 지우기"
            className="-mr-3 h-12 w-12 items-center justify-center">
            <Icon name="close" color="inkSub" size={20} />
          </Pressable>
        )}
      </View>

      {/* 고른 노트 (세 층 모두) */}
      <View className="mt-4">
        <Txt variant="caption" sub>
          {S.pickedTitle}
        </Txt>
        {draft.notes.length === 0 ? (
          <Txt variant="label" sub className="mt-2">
            {S.pickedEmpty}
          </Txt>
        ) : (
          LAYERS.filter((l) => draft.notes.some((n) => n.layer === l)).map((l) => (
            <View key={l} className="flex-row flex-wrap items-center gap-x-3">
              <Txt variant="layer" sub className="w-16">
                {LAYER_LABEL[l]}
              </Txt>
              {draft.notes
                .filter((n) => n.layer === l)
                .map((n) => (
                  <SelectedChip
                    key={n.noteKey}
                    note={noteOf(n.noteKey)}
                    layer={l}
                    level={n.level}
                    focused={n.noteKey === focusKey}
                    onPress={() => {
                      setLayer(l);
                      setFocusKey(n.noteKey);
                    }}
                  />
                ))}
            </View>
          ))
        )}
        {notice && (
          <Txt
            variant="label"
            testID={notice.kind === 'limit' ? TestIds.LAYER_LIMIT_MESSAGE : 'notes:notice'}
            accessibilityLiveRegion="polite"
            className="mt-2 text-accentStrong dark:text-accentStrong-dark">
            {notice.kind === 'limit' ? S.layerLimit : `${S.inOtherLayer}${LAYER_KO[notice.layer]}`}
          </Txt>
        )}
      </View>

      {/* 강도 고르기 (고른 노트 하나) */}
      {focused && (
        <View className="mt-4 rounded border border-line bg-surface p-4 dark:border-line-dark dark:bg-surface-dark">
          <View className="flex-row items-baseline justify-between">
            <Txt variant="headline" className="font-serif">
              {noteOf(focused.noteKey).nameKo}
            </Txt>
            <Txt variant="caption" sub>
              {`${LAYER_LABEL[focused.layer]} · ${familyName(cat, noteOf(focused.noteKey).family)}`}
            </Txt>
          </View>
          <Txt variant="caption" sub className="mb-3 mt-1">
            {noteOf(focused.noteKey).description}
          </Txt>
          <IntensityPicker
            noteKey={focused.noteKey}
            noteName={noteOf(focused.noteKey).nameKo}
            value={focused.level}
            onChange={(l) => setDraft(setLevel(draft, focused.noteKey, l))}
          />
          <Button
            kind="text"
            className="mt-2"
            label={S.removeNote}
            onPress={() => {
              setDraft(removeNote(draft, focused.noteKey));
              setFocusKey(null);
              setNotice(null);
            }}
          />
        </View>
      )}

      {/* 노트 목록 / 검색 결과 */}
      <View testID={TestIds.NOTE_LIST} className="mt-6">
        {query.trim() !== '' ? (
          <View>
            <Txt variant="label" sub>
              {S.searchResults}
            </Txt>
            {results.length === 0 ? (
              <Txt sub className="mt-2" testID={TestIds.EMPTY_STATE}>
                {S.searchEmpty}
              </Txt>
            ) : (
              <View className="flex-row flex-wrap gap-x-3">{results.map(item)}</View>
            )}
          </View>
        ) : (
          sections.map((s) => (
            <View key={s.family.code} className="mb-6">
              <Txt variant="label" sub>
                {s.family.nameKo + S.oftenUsed}
              </Txt>
              <View className="flex-row flex-wrap gap-x-3">{s.notes.map(item)}</View>
            </View>
          ))
        )}
      </View>

      <Button
        className="mt-4"
        testID={TestIds.TO_SUMMARY}
        label={S.toSummary}
        disabled={draft.notes.length === 0}
        onPress={() => go('/summary')}
      />
    </View>
  );

  return (
    <>
      <Screen wide={wide}>
        {wide ? (
          <View className="flex-row items-start gap-10">
            {main}
            {card && (
              <View testID={TestIds.NOTE_CARD} className="w-panel rounded border border-line bg-surface p-6 dark:border-line-dark dark:bg-surface-dark">
                {card}
              </View>
            )}
          </View>
        ) : (
          main
        )}
      </Screen>
      {!wide && (
        <Modal visible={!!card} transparent animationType="slide" onRequestClose={() => setInfoKey(null)}>
          <View className="flex-1 justify-end">
            <Pressable
              className="absolute inset-0 bg-ink/30 dark:bg-bg-dark/60"
              accessibilityRole="button"
              accessibilityLabel={S.noteCardClose}
              onPress={() => setInfoKey(null)}
            />
            <View
              testID={TestIds.NOTE_CARD}
              style={{ paddingBottom: 24 + insets.bottom }}
              className="rounded-t border-t border-line bg-surface px-6 pt-6 shadow-sm dark:border-line-dark dark:bg-surface-dark">
              {card}
            </View>
          </View>
        </Modal>
      )}
    </>
  );
}
