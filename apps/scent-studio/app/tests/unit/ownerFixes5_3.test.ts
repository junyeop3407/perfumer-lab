import { describe, expect, it } from 'vitest';

import { addNote, emptyDraft, setLevel } from '../../src/blend/blendDraft';
import { fixNoteNames, noteLabel } from '../../src/catalog/noteNames';
import type { Catalog, Note } from '../../src/engine/types';

/** 2026-11-13 사장 지시(5-3 폰 확인 뒤) 회귀 테스트 */
const ok = (r: ReturnType<typeof addNote>) => {
  if (!r.ok) throw new Error(`추가 실패: ${r.error}`);
  return r.draft;
};

describe('강도 유지: 새 노트는 마지막에 고른 강도로 시작한다(사장 1번)', () => {
  it('강도를 주면 새 노트가 그 강도로 들어가고, 이미 고른 노트의 강도는 그대로', () => {
    let d = ok(addNote(emptyDraft(['citrus']), 'top', 'lemon'));
    d = setLevel(d, 'lemon', 'weak');
    d = ok(addNote(d, 'top', 'bergamot', undefined, 'weak'));
    expect(d.notes).toEqual([
      { noteKey: 'lemon', layer: 'top', level: 'weak' },
      { noteKey: 'bergamot', layer: 'top', level: 'weak' },
    ]);
  });

  it('강도를 주지 않으면 지금처럼 중', () => {
    expect(ok(addNote(emptyDraft([]), 'top', 'lemon')).notes[0].level).toBe('medium');
  });

  it('같은 층에 이미 있는 노트를 다시 눌러도 그 노트의 강도는 바뀌지 않는다', () => {
    let d = setLevel(ok(addNote(emptyDraft([]), 'top', 'lemon')), 'lemon', 'strong');
    d = ok(addNote(d, 'top', 'lemon', undefined, 'weak'));
    expect(d.notes[0].level).toBe('strong');
  });
});

describe('감초 Glycyrrhiza glabra → 영어 이름으로(사장 6번)', () => {
  const note = (key: string, nameEn: string): Note => ({
    key, nameEn, nameKo: '감초', family: 'spicy', df: 10, tier: 'browse', profile: [1], pairings: [], description: '',
  });
  const cat = { notes: [note('liquorice', 'Liquorice'), note('glycyrrhiza glabra', 'Glycyrrhiza glabra')] } as unknown as Catalog;

  it('학명 대신 "Licorice root"를 붙이고, 다른 감초(Liquorice)와는 여전히 구분된다', () => {
    const fixed = fixNoteNames(cat);
    const g = fixed.notes.find((n) => n.key === 'glycyrrhiza glabra')!;
    expect(g.nameEn).toBe('Licorice root');
    const amb = new Set(['liquorice', 'glycyrrhiza glabra']);
    expect(noteLabel(g, amb)).toBe('감초 Licorice root');
    expect(noteLabel(fixed.notes[0], amb)).toBe('감초 Liquorice');
  });
});
