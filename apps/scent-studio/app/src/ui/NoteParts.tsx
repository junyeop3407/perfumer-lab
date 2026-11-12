/**
 * 노트 고르기 부품: 목록 칩(+ 정보 버튼), 고른 노트 칩(층 색), 노트 카드 내용.
 * 칩은 보이는 높이 36, 눌리는 높이 48 (design_rules.md 6절 누르는 영역 규칙).
 */
import { Pressable, View } from 'react-native';

import { LAYER_KO, LEVEL_KO } from '../blend/blendDraft';
import type { Catalog, Layer, Note } from '../engine/types';
import { S } from '../strings.ko';
import { Button } from './Button';
import { Icon } from './Icon';
import { Ticks } from './Intensity';
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

/** 목록의 노트 하나: [이름 칩][ⓘ]. 고른 노트는 ✓, 다른 층에 고른 노트는 층 이름을 같이 */
export function NoteListItem({
  note,
  familyKo,
  pickedLayer,
  onPick,
  onInfo,
}: {
  note: Note;
  familyKo: string;
  pickedLayer: Layer | null;
  onPick: () => void;
  onInfo: () => void;
}) {
  const label = `${note.nameKo}, ${familyKo}${pickedLayer ? `, ${LAYER_KO[pickedLayer]}에 고름` : ''}`;
  return (
    <View className="flex-row items-center">
      <Pressable
        testID={TestIds.noteItem(note.key)}
        accessibilityRole="button"
        accessibilityLabel={label}
        onPress={onPick}
        className="min-h-touch justify-center">
        <View
          className={`h-9 flex-row items-center gap-1 rounded-chip px-2.5 ${
            pickedLayer ? LAYER_BG[pickedLayer] : 'border border-line dark:border-line-dark'
          }`}>
          {pickedLayer && <Icon name="check" size={16} color={pickedLayer === 'base' ? 'onNoteBase' : 'onNoteTop'} />}
          <Txt variant="label" className={pickedLayer ? LAYER_FG[pickedLayer] : ''}>
            {note.nameKo}
          </Txt>
        </View>
      </Pressable>
      <Pressable
        testID={TestIds.noteInfo(note.key)}
        accessibilityRole="button"
        accessibilityLabel={`${note.nameKo}${S.noteInfo}`}
        onPress={onInfo}
        className="h-12 w-10 items-center justify-center">
        <Icon name="info" size={18} color="inkSub" />
      </Pressable>
    </View>
  );
}

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
  return (
    <Pressable
      testID={TestIds.selectedNote(layer, note.key)}
      accessibilityRole="button"
      accessibilityState={{ selected: focused }}
      accessibilityLabel={`${note.nameKo}, ${LEVEL_KO[level]}`}
      onPress={onPress}
      className="min-h-touch justify-center">
      <View className={`h-9 flex-row items-center gap-2 rounded-chip px-2.5 ${LAYER_BG[layer]}`}>
        <Txt variant="label" className={LAYER_FG[layer]}>
          {`✓ ${note.nameKo}`}
        </Txt>
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
  const pairings = note.pairings
    .map((k) => catalog.notes.find((n) => n.key === k))
    .filter((n): n is Note => !!n && n.tier !== 'hidden')
    .slice(0, 3);
  return (
    <View>
      <View className="flex-row items-start justify-between">
        <Txt variant="title" accessibilityRole="header" className="flex-1">
          {note.nameKo}
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
              <View key={p.key} testID={TestIds.NOTE_CARD_PAIRING} className="mt-2 rounded-chip border border-line px-2 py-1 dark:border-line-dark">
                <Txt variant="label">{p.nameKo}</Txt>
              </View>
            ))}
          </View>
        </View>
      )}
      {canAdd && <Button className="mt-6" label={`${S.noteCardAdd} (${LAYER_KO[layer]})`} onPress={onAdd} />}
    </View>
  );
}
