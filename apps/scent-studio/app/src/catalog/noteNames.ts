/**
 * 노트 한글 이름 고치기·구분 (사장 결정 2026-11-12).
 * - 고친 이름: pipeline/manual/notes_ko.csv에도 같은 값을 넣었다. 다음 카탈로그부터는 원본에 들어 있고,
 *   지금 배포된 카탈로그(2026.11.05)에도 맞게 앱이 열 때 한 번 더 고친다.
 * - 그래도 한글 이름이 겹치는 노트(배 Pear·Nashi pear 등)는 영어 이름을 작게 붙여 구분한다.
 */
import type { Catalog, Note } from '../engine/types';

export const NOTE_NAME_FIXES: Record<string, string> = {
  oud: '우드(침향)',
  woods: '나무향',
  'white oud': '화이트 우드(침향)',
};

export function fixNoteNames(catalog: Catalog): Catalog {
  if (!catalog.notes.some((n) => NOTE_NAME_FIXES[n.key] && NOTE_NAME_FIXES[n.key] !== n.nameKo)) return catalog;
  return { ...catalog, notes: catalog.notes.map((n) => (NOTE_NAME_FIXES[n.key] ? { ...n, nameKo: NOTE_NAME_FIXES[n.key] } : n)) };
}

/** 화면에 나오는(숨김 아닌) 노트 중 한글 이름이 다른 노트와 같은 노트 key */
export function ambiguousNoteKeys(catalog: Catalog): Set<string> {
  const byName = new Map<string, string[]>();
  for (const n of catalog.notes) {
    if (n.tier === 'hidden') continue;
    const keys = byName.get(n.nameKo) ?? [];
    keys.push(n.key);
    byName.set(n.nameKo, keys);
  }
  return new Set([...byName.values()].filter((keys) => keys.length > 1).flat());
}

/** 글자 하나로 쓸 때(화면 읽기·안내 문구): 겹치면 "배 Pear" */
export function noteLabel(note: Note, ambiguous: ReadonlySet<string>): string {
  return ambiguous.has(note.key) ? `${note.nameKo} ${note.nameEn}` : note.nameKo;
}
