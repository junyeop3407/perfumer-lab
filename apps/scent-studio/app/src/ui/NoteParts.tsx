/**
 * 노트 고르기 부품: 목록 칩(+ 정보 버튼), 고른 노트 칩(층 색), 노트 카드 내용.
 * 칩은 보이는 높이 36, 눌리는 높이 48 (design_rules.md 6절 누르는 영역 규칙).
 */
import { memo } from 'react';
import { Pressable, View } from 'react-native';

import { LAYER_KO, LEVEL_KO, pairingsFor } from '../blend/blendDraft';
import type { Catalog, Layer, Note } from '../engine/types';
import { S } from '../strings.ko';
import { Button } from './Button';
import { Icon } from './Icon';
import { Ticks } from './Intensity';
import { NoteNameText, useNoteNames } from './NoteName';
import { TestIds, type IntensityLevel } from './testIds';
import { Txt } from './Txt';

export const LAYER_BG: Record<Layer, string> = {
  top: 'bg-noteTop dark:bg-noteTop-dark',
  middle: 'bg-noteMiddle dark:bg-noteMiddle-dark',
  base: 'bg-noteBase dark:bg-noteBase-dark',
};
export const LAYER_FG: Record<Layer, string> = {
  top: 'text-onNoteTop dark:text-onNoteTop-dark',
  middle: 'text-onNoteMiddle dark:text-onNoteMiddle-dark',
  base: 'text-onNoteBase dark:text-onNoteBase-dark',
};
const LAYER_TICK: Record<Layer, string> = {
  top: 'bg-onNoteTop dark:bg-onNoteTop-dark',
  middle: 'bg-onNoteMiddle dark:bg-onNoteMiddle-dark',
  base: 'bg-onNoteBase dark:bg-onNoteBase-dark',
};

export const familyName = (catalog: Catalog, code: string) => catalog.families.find((f) => f.code === code)?.nameKo ?? code;

/** 목록의 노트 하나: 칩 안에 [이름 | ⓘ]. 고른 노트는 ✓ + 층 색, 화면 읽기에는 고른 층을 같이 */
export const NoteListItem = memo(function NoteListItem({
  note,
  en,
  familyKo,
  pickedLayer,
  onPick,
  onInfo,
}: {
  note: Note;
  /** 한글 이름이 겹칠 때 붙일 영어 이름 */
  en: string | null;
  familyKo: string;
  pickedLayer: Layer | null;
  onPick: (key: string) => void;
  onInfo: (key: string) => void;
}) {
  const name = en ? `${note.nameKo} ${en}` : note.nameKo;
  const label = `${name}, ${familyKo}${pickedLayer ? `, ${LAYER_KO[pickedLayer]}에 고름` : ''}`;
  const picked = pickedLayer ? LAYER_FG[pickedLayer] : '';
  // 칩 하나 안에 [이름 | ⓘ] (5-2 기기 확인 디자인 9: 칩 밖 ⓘ가 줄을 지저분하게 함).
  // 보이는 높이 36, 두 누르는 곳은 각각 높이 48(위아래 -6으로 칩 밖까지)·폭 48 이상
  return (
    <View className="min-h-touch justify-center">
      <View
        className={`h-9 flex-row items-center rounded-chip ${
          pickedLayer ? LAYER_BG[pickedLayer] : 'border border-line dark:border-line-dark'
        }`}>
        <Pressable
          testID={TestIds.noteItem(note.key)}
          accessibilityRole="button"
          accessibilityLabel={label}
          onPress={() => onPick(note.key)}
          className="-my-1.5 h-12 min-w-12 flex-row items-center justify-center gap-1 pl-2.5 pr-1">
          {pickedLayer && <Icon name="check" size={16} color={pickedLayer === 'base' ? 'onNoteBase' : 'onNoteTop'} />}
          <NoteNameText ko={note.nameKo} en={en} className={picked} />
        </Pressable>
        <Pressable
          testID={TestIds.noteInfo(note.key)}
          accessibilityRole="button"
          accessibilityLabel={`${name}${S.noteInfo}`}
          onPress={() => onInfo(note.key)}
          className="-my-1.5 h-12 w-12 flex-row items-center justify-center">
          <View className={`mr-2 h-5 border-l ${pickedLayer ? 'border-ink/20 dark:border-bg-dark/30' : 'border-line dark:border-line-dark'}`} />
          <Icon name="info" size={16} color={pickedLayer === 'base' ? 'onNoteBase' : pickedLayer ? 'onNoteTop' : 'inkSub'} />
        </Pressable>
      </View>
    </View>
  );
});

/** 고른 노트 칩: 층 색 바탕, ✓ + 이름 + 눈금 + 약·중·강 (테두리 없음) */
export function SelectedChip({
  note,
  layer,
  level,
  focused,
  onPress,
}: {
  note: Note;
  layer: Layer;
  level: IntensityLevel;
  focused: boolean;
  onPress: () => void;
}) {
  const { en, label } = useNoteNames();
  return (
    <Pressable
      testID={TestIds.selectedNote(layer, note.key)}
      accessibilityRole="button"
      accessibilityState={{ selected: focused }}
      accessibilityLabel={`${label(note)}, ${LEVEL_KO[level]}`}
      onPress={onPress}
      className="min-h-touch justify-center">
      <View className={`h-9 flex-row items-center gap-2 rounded-chip px-2.5 ${LAYER_BG[layer]}`}>
        <NoteNameText ko={`✓ ${note.nameKo}`} en={en(note)} className={LAYER_FG[layer]} />
        <Ticks level={level} className={LAYER_TICK[layer]} />
        <Txt variant="label" className={LAYER_FG[layer]}>
          {LEVEL_KO[level]}
        </Txt>
      </View>
    </Pressable>
  );
}

/** 노트 카드 내용 (폰: 아래 시트, PC: 옆 패널) */
export function NoteCardBody({
  catalog,
  note,
  layer,
  canAdd,
  onAdd,
  onClose,
}: {
  catalog: Catalog;
  note: Note;
  layer: Layer;
  canAdd: boolean;
  onAdd: () => void;
  onClose: () => void;
}) {
  const { en } = useNoteNames();
  // 실제 함께 쓰인 노트 먼저, 모자라면 같은 계열 추천으로 늘 3개 (사장 결정 2026-11-12)
  const pairings = pairingsFor(catalog, note);
  return (
    <View>
      <View className="flex-row items-start justify-between">
        <Txt variant="title" accessibilityRole="header" className="flex-1">
          {note.nameKo}
          {en(note) ? <Txt variant="caption" sub>{` ${en(note)}`}</Txt> : null}
        </Txt>
        <Pressable
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel={S.noteCardClose}
          className="-mr-3 -mt-2 h-12 w-12 items-center justify-center">
          <Icon name="close" />
        </Pressable>
      </View>
      <Txt variant="caption" sub testID={TestIds.NOTE_CARD_FAMILY}>
        {`${S.noteCardFamily}${familyName(catalog, note.family)} · ${note.nameEn}`}
      </Txt>
      <Txt className="mt-3" testID={TestIds.NOTE_CARD_DESCRIPTION}>
        {note.description}
      </Txt>
      {pairings.length > 0 && (
        <View className="mt-4">
          <Txt variant="label" sub>
            {S.noteCardPairings}
          </Txt>
          <View className="mt-1 flex-row flex-wrap gap-x-2">
            {pairings.map((p) => (
              <View
                key={p.note.key}
                testID={TestIds.NOTE_CARD_PAIRING}
                className="mt-2 rounded-chip border border-line px-2 py-1 dark:border-line-dark">
                <NoteNameText ko={p.note.nameKo} en={en(p.note)} />
                {p.filled && (
                  <Txt variant="caption" sub>
                    {S.pairingFilled}
                  </Txt>
                )}
              </View>
            ))}
          </View>
        </View>
      )}
      {canAdd && <Button className="mt-6" label={`${S.noteCardAdd} (${LAYER_KO[layer]})`} onPress={onAdd} />}
    </View>
  );
}
