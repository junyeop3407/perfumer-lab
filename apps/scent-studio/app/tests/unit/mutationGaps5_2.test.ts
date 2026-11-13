import { describe, expect, it } from 'vitest';

import { addNote, emptyDraft, hintTexts, removeNote, setLevel, toEngineBlend } from '../../src/blend/blendDraft';
import { ambiguousNoteKeys } from '../../src/catalog/noteNames';
import { RecommendationEngine } from '../../src/engine/RecommendationEngine';
import type { Catalog, Note } from '../../src/engine/types';
import { fixtureCatalog } from './testCatalogs';

/** 5-2 망가뜨려 보기(2026-11-13)에서 테스트가 못 잡은 것 — 사장 지시로 추가(docs/NOTES.md "2026-11-13 사장 지시") */
const ok = (r: ReturnType<typeof addNote>) => {
  if (!r.ok) throw new Error(`추가 실패: ${r.error}`);
  return r.draft;
};

describe('V15 한 계열 안내의 이웃 계열', () => {
  it('시트러스만 고르면 휠의 양옆(프레시·아쿠아틱 · 프루티)을 권한다 — 시트러스는 휠 첫 칸이라 앞쪽은 맨 끝으로 돈다', () => {
    const engine = new RecommendationEngine(fixtureCatalog());
    let d = ok(addNote(emptyDraft(['citrus']), 'top', 'lemon'));
    d = ok(addNote(d, 'middle', 'bergamot'));
    d = ok(addNote(d, 'base', 'grapefruit'));
    expect(hintTexts(engine, toEngineBlend(d))).toContain('한 계열로만 이루어져 단조로울 수 있어요. 이웃 계열(프레시·아쿠아틱 · 프루티)을 더해 보세요');
  });
});

describe('V20 숨김 노트는 한글 이름 겹침에 세지 않는다', () => {
  const note = (key: string, tier: Note['tier']): Note => ({
    key, nameEn: key, nameKo: '흙', family: 'woody', df: 1, tier, profile: [1], pairings: [], description: '',
  });
  it('보이는 "흙" 하나 + 숨김 "흙" 하나면 겹침이 아니다(영어를 붙이지 않음)', () => {
    expect([...ambiguousNoteKeys({ notes: [note('soil', 'search'), note('dirt', 'hidden')] } as unknown as Catalog)]).toEqual([]);
  });
  it('둘 다 보이면 겹침', () => {
    expect(ambiguousNoteKeys({ notes: [note('soil', 'search'), note('dirt', 'browse')] } as unknown as Catalog)).toEqual(new Set(['soil', 'dirt']));
  });
});

describe('V23 [조합에서 빼기]는 그 노트 하나만 뺀다', () => {
  it('세 노트 중 가운데를 빼면 나머지 둘과 강도가 그대로', () => {
    let d = ok(addNote(emptyDraft([]), 'top', 'lemon'));
    d = ok(addNote(d, 'middle', 'rose'));
    d = setLevel(ok(addNote(d, 'base', 'sandalwood')), 'sandalwood', 'weak');
    expect(removeNote(d, 'rose').notes).toEqual([
      { noteKey: 'lemon', layer: 'top', level: 'medium' },
      { noteKey: 'sandalwood', layer: 'base', level: 'weak' },
    ]);
  });
});
