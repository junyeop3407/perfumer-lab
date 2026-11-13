import { useCallback, useDeferredValue, useMemo, useRef, useState } from 'react';
import { Keyboard, Modal, Pressable, TextInput, useWindowDimensions, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
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
import { useNoteNames } from '@/src/ui/NoteName';
import { familyName, NoteCardBody, NoteListItem, SelectedChip } from '@/src/ui/NoteParts';
import { Header, Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { useTokenColor } from '@/src/ui/tokens';
import { Txt } from '@/src/ui/Txt';

type Notice = { kind: 'limit' } | { kind: 'other'; layer: Layer } | null;

/**
 * 노트 고르기: 층 탭 · 검색 · 고른 노트 · 노트 목록 · 노트 카드 (승인 A2~A7)
 * 아래 고정 막대: 안내(3개까지 등) + 방금 고른 노트의 강도 + [조합 요약 보기] — 목록 아래쪽에서 눌러도 보인다.
 */
export default function NotesScreen() {
  const catalog = useCatalog();
  const { draft, setDraft, nextLevel, setNextLevel } = useDraft();
  const { width } = useWindowDimensions();
  const wide = width >= WIDE_MIN_WIDTH;
  const insets = useSafeAreaInsets();
  const color = useTokenColor();
  const reduceMotion = useReducedMotion();
  const names = useNoteNames();

  const [layer, setLayer] = useState<Layer>('top');
  const [query, setQuery] = useState('');
  const [searchFocused, setSearchFocused] = useState(false);
  const [focusKey, setFocusKey] = useState<string | null>(null);
  // 노트 카드: 닫는 동안에도 내용이 남도록 열림과 내용을 따로 둔다
  const [infoKey, setInfoKey] = useState<string | null>(null);
  const [infoOpen, setInfoOpen] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  // 검색은 입력을 막지 않게 한 박자 늦게 (한글 조합 중 다시 그리기 부담 줄임, D4)
  const deferredQuery = useDeferredValue(query);

  const cat = catalog.status === 'ready' ? catalog.catalog : null;
  const noteByKey = useMemo(() => new Map((cat?.notes ?? []).map((n) => [n.key, n])), [cat]);
  const sections = useMemo(() => (cat ? noteSections(cat, draft.families, layer) : []), [cat, draft.families, layer]);
  const results = useMemo(() => (cat ? searchNotes(cat, deferredQuery) : []), [cat, deferredQuery]);

  // 목록은 고른 노트·층이 바뀔 때만 다시 그린다(강도만 바꾸면 그대로)
  const pickedSig = draft.notes.map((n) => `${n.noteKey}@${n.layer}`).join('|');
  const pickedLayer = useMemo(
    () => new Map(pickedSig === '' ? [] : pickedSig.split('|').map((x) => x.split('@') as [string, Layer])),
    [pickedSig],
  );

  // 목록 칩의 누르기는 최신 상태를 보는 고정 함수로 (memo된 칩이 다시 그려지지 않게)
  const latest = useRef({ draft, layer, cat, nextLevel });
  latest.current = { draft, layer, cat, nextLevel };
  const pick = useCallback(
    (k: string) => {
      const { draft: d, layer: l, cat: c, nextLevel: lv } = latest.current;
      if (!c) return;
      const already = d.notes.find((n) => n.noteKey === k)?.layer ?? null;
      if (already === l) {
        setFocusKey(k);
        setNotice(null);
        return;
      }
      const r = addNote(d, l, k, c, lv); // 마지막에 누른 강도로 시작(2026-11-13 사장)
      if (r.ok) {
        setDraft(r.draft);
        setFocusKey(k);
        setNotice(null);
      } else if (r.error === 'LAYER_LIMIT') setNotice({ kind: 'limit' });
      else if (r.error === 'IN_OTHER_LAYER') setNotice({ kind: 'other', layer: r.layer });
    },
    [setDraft],
  );
  const openInfo = useCallback((k: string) => {
    Keyboard.dismiss(); // 키보드가 노트 카드를 가리지 않게 (D3)
    setInfoKey(k);
    setInfoOpen(true);
  }, []);

  const list = useMemo(() => {
    if (!cat) return null;
    const item = (n: Note) => (
      <NoteListItem
        key={n.key}
        note={n}
        en={names.en(n)}
        familyKo={familyName(cat, n.family)}
        pickedLayer={pickedLayer.get(n.key) ?? null}
        onPick={pick}
        onInfo={openInfo}
      />
    );
    if (deferredQuery.trim() !== '') {
      return (
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
      );
    }
    return sections.map((s) => (
      <View key={`${s.family.code}${s.rare ? ':rare' : ''}`} className="mb-6">
        <Txt variant="label" sub>
          {`${s.family.nameKo} · ${LAYER_KO[layer]}${s.rare ? S.rareInLayer : S.oftenUsedInLayer}`}
        </Txt>
        <View className="flex-row flex-wrap gap-x-3">{s.notes.map(item)}</View>
      </View>
    ));
  }, [cat, deferredQuery, results, sections, pickedLayer, pick, openInfo, names, layer]);

  if (!cat) {
    return (
      <Screen needsCatalog>
        <Header title={S.notesTitle} onBack={goBack} />
      </Screen>
    );
  }

  const noteOf = (k: string) => noteByKey.get(k)!;
  const focused = draft.notes.find((n) => n.noteKey === focusKey) ?? null;
  const infoNote = infoKey ? (noteByKey.get(infoKey) ?? null) : null;
  const closeInfo = () => setInfoOpen(false);

  const familiesText =
    draft.families.length > 0 ? S.familiesPicked + draft.families.map((c) => familyName(cat, c)).join(' · ') : S.familiesNone;

  const card = infoNote && (
    <NoteCardBody
      catalog={cat}
      note={infoNote}
      layer={layer}
      canAdd={!pickedLayer.has(infoNote.key)}
      onAdd={() => {
        pick(infoNote.key);
        closeInfo();
      }}
      onClose={closeInfo}
    />
  );

  const footer = (
    <View className="border-t border-line bg-surface px-6 pb-3 pt-2 dark:border-line-dark dark:bg-surface-dark">
      <View className="w-full max-w-text">
        {notice && (
          <Txt
            variant="label"
            testID={notice.kind === 'limit' ? TestIds.LAYER_LIMIT_MESSAGE : 'notes:notice'}
            accessibilityLiveRegion="polite"
            className="mb-2 text-accentStrong dark:text-accentStrong-dark">
            {notice.kind === 'limit' ? S.layerLimit : `${S.inOtherLayer}${LAYER_KO[notice.layer]}`}
          </Txt>
        )}
        {focused && (
          <View className="mb-2">
            <View className="flex-row items-center justify-between">
              <Txt variant="label">{`${LAYER_LABEL[focused.layer]} · ${names.label(noteOf(focused.noteKey))}`}</Txt>
              <Button
                kind="text"
                label={S.removeNote}
                onPress={() => {
                  setDraft(removeNote(draft, focused.noteKey));
                  setFocusKey(null);
                  setNotice(null);
                }}
              />
            </View>
            <IntensityPicker
              noteKey={focused.noteKey}
              noteName={names.label(noteOf(focused.noteKey))}
              value={focused.level}
              onChange={(l) => {
                setDraft(setLevel(draft, focused.noteKey, l));
                setNextLevel(l); // 다음 노트도 이 강도로 시작
              }}
            />
          </View>
        )}
        <Button testID={TestIds.TO_SUMMARY} label={S.toSummary} disabled={draft.notes.length === 0} onPress={() => go('/summary')} />
      </View>
    </View>
  );

  const side =
    wide && infoOpen && card ? (
      <View testID={TestIds.NOTE_CARD} className="rounded border border-line bg-surface p-6 dark:border-line-dark dark:bg-surface-dark">
        {card}
      </View>
    ) : null;

  return (
    <>
      <Screen wide={wide} side={side} footer={footer}>
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

        {/* 검색 (포커스면 1dp primary 테두리) */}
        <View
          className={`mt-4 min-h-touch flex-row items-center gap-2 rounded border bg-surfaceAlt px-3 dark:bg-surfaceAlt-dark ${
            searchFocused ? 'border-primary dark:border-primary-dark' : 'border-surfaceAlt dark:border-surfaceAlt-dark'
          }`}>
          <Icon name="search" color="inkSub" />
          <TextInput
            testID={TestIds.NOTE_SEARCH}
            value={query}
            onChangeText={setQuery}
            onFocus={() => setSearchFocused(true)}
            onBlur={() => setSearchFocused(false)}
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
          <Txt variant="caption" sub>
            {S.intensityLegend}
          </Txt>
          {draft.notes.length === 0 ? (
            <Txt variant="label" sub className="mt-2">
              {S.pickedEmpty}
            </Txt>
          ) : (
            LAYERS.filter((l) => draft.notes.some((n) => n.layer === l)).map((l) => (
              <View key={l} className="flex-row items-start">
                <Txt variant="layer" sub className="w-16 pt-4">
                  {LAYER_LABEL[l]}
                </Txt>
                <View className="flex-1 flex-row flex-wrap gap-x-3">
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
              </View>
            ))
          )}
        </View>

        {/* 노트 목록 / 검색 결과 */}
        <View testID={TestIds.NOTE_LIST} className="mt-6">
          {list}
        </View>
      </Screen>
      {!wide && (
        // 안드로이드: 시트를 내비게이션 바 아래까지 그려야 아래 여백(24 + insets.bottom)이 한 번만 들어간다(2026-11-13 사장 6번)
        <Modal
          visible={infoOpen && !!card}
          transparent
          statusBarTranslucent
          navigationBarTranslucent
          animationType={reduceMotion ? 'none' : 'slide'}
          onRequestClose={closeInfo}>
          <View className="flex-1 justify-end">
            <Pressable
              className="absolute inset-0 bg-ink/30 dark:bg-bg-dark/60"
              accessibilityRole="button"
              accessibilityLabel={S.noteCardClose}
              onPress={closeInfo}
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
