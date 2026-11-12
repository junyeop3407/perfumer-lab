import { describe, expect, it } from 'vitest';

import {
  addNote,
  emptyDraft,
  hintTexts,
  noteSections,
  removeNote,
  searchNotes,
  setLevel,
  toEngineBlend,
} from '../../src/blend/blendDraft';
import { RecommendationEngine } from '../../src/engine/RecommendationEngine';
import { fixtureCatalog } from './testCatalogs';

/** 5-2 조합 만들기의 순수 로직 (화면 없이). 승인 A2~A6·A9의 바탕 */
const catalog = fixtureCatalog();
const engine = new RecommendationEngine(catalog);

const ok = (r: ReturnType<typeof addNote>) => {
  if (!r.ok) throw new Error(`추가 실패: ${r.error}`);
  return r.draft;
};

describe('조합 초안: 노트 넣기·빼기·강도', () => {
  it('새로 넣은 노트의 강도는 중이다', () => {
    const d = ok(addNote(emptyDraft(['citrus']), 'top', 'lemon'));
    expect(d.notes).toEqual([{ noteKey: 'lemon', layer: 'top', level: 'medium' }]);
  });

  it('층마다 3개까지만: 4번째는 LAYER_LIMIT, 초안은 그대로', () => {
    let d = emptyDraft([]);
    for (const k of ['lemon', 'bergamot', 'grapefruit']) d = ok(addNote(d, 'top', k));
    const r = addNote(d, 'top', 'mandarin orange');
    expect(r).toEqual({ ok: false, error: 'LAYER_LIMIT' });
    // 다른 층은 따로 센다
    expect(addNote(d, 'middle', 'rose').ok).toBe(true);
  });

  it('같은 노트는 조합 전체에서 한 번만: 다른 층에 있으면 IN_OTHER_LAYER와 그 층', () => {
    const d = ok(addNote(emptyDraft([]), 'top', 'lemon'));
    expect(addNote(d, 'middle', 'lemon')).toEqual({ ok: false, error: 'IN_OTHER_LAYER', layer: 'top' });
  });

  it('같은 층에서 다시 넣으면 변화 없음(ok)', () => {
    const d = ok(addNote(emptyDraft([]), 'top', 'lemon'));
    expect(ok(addNote(d, 'top', 'lemon')).notes).toHaveLength(1);
  });

  it('숨김 노트·없는 노트는 넣지 않는다', () => {
    expect(addNote(emptyDraft([]), 'top', 'ginger', catalog)).toEqual({ ok: false, error: 'NOT_FOUND' });
    expect(addNote(emptyDraft([]), 'top', 'nope', catalog)).toEqual({ ok: false, error: 'NOT_FOUND' });
  });

  it('강도 바꾸기와 빼기', () => {
    let d = ok(addNote(emptyDraft([]), 'top', 'lemon'));
    d = setLevel(d, 'lemon', 'strong');
    expect(d.notes[0].level).toBe('strong');
    d = removeNote(d, 'lemon');
    expect(d.notes).toEqual([]);
  });

  it('엔진용 조합: 약 0.5 · 중 1.0 · 강 3.0, 엔진 검사 통과', () => {
    let d = emptyDraft(['citrus', 'woody']);
    d = setLevel(ok(addNote(d, 'top', 'lemon')), 'lemon', 'strong');
    d = ok(addNote(d, 'middle', 'rose'));
    d = setLevel(ok(addNote(d, 'base', 'sandalwood')), 'sandalwood', 'weak');
    const blend = toEngineBlend(d);
    expect(blend).toEqual([
      { noteKey: 'lemon', layer: 'top', intensity: 3.0 },
      { noteKey: 'rose', layer: 'middle', intensity: 1.0 },
      { noteKey: 'sandalwood', layer: 'base', intensity: 0.5 },
    ]);
    expect(() => engine.validate(blend)).not.toThrow();
  });
});

describe('노트 목록(검색 없이): 많이 쓰는 노트만, 고른 계열 먼저', () => {
  it('고른 계열 순서대로 묶음이 먼저 오고, 묶음 안은 많이 쓰는 순', () => {
    const sections = noteSections(catalog, ['woody', 'citrus']);
    expect(sections[0].family.code).toBe('woody');
    expect(sections[1].family.code).toBe('citrus');
    expect(sections[0].notes[0].key).toBe('sandalwood'); // df 23
    expect(sections[1].notes.map((n) => n.key)).toEqual(['lemon', 'bergamot', 'grapefruit']);
  });

  it('나머지 계열은 휠 순서로 뒤에 온다', () => {
    const codes = noteSections(catalog, ['woody']).map((s) => s.family.code);
    expect(codes[0]).toBe('woody');
    const rest = codes.slice(1);
    const order = rest.map((c) => catalog.families.find((f) => f.code === c)!.wheelOrder);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
  });

  it('검색 전용·숨김 노트는 목록에 없다 (A6 히노키)', () => {
    const keys = noteSections(catalog, []).flatMap((s) => s.notes.map((n) => n.key));
    expect(keys).not.toContain('hinoki');
    expect(keys).not.toContain('ginger');
    expect(keys).toContain('lemon');
  });

  it('노트가 없는 계열 묶음은 뺀다', () => {
    for (const s of noteSections(catalog, [])) expect(s.notes.length).toBeGreaterThan(0);
  });
});

describe('노트 검색 (A5·A6)', () => {
  const keys = (q: string) => searchNotes(catalog, q).map((n) => n.key);

  it('한글 앞부분 "베르가"와 영문 "berg" 모두 베르가못', () => {
    expect(keys('베르가')).toContain('bergamot');
    expect(keys('berg')).toContain('bergamot');
    expect(keys('BERG')).toContain('bergamot');
  });

  it('검색 전용 노트도 나오고 숨김 노트는 안 나온다', () => {
    expect(keys('히노키')).toEqual(['hinoki']);
    expect(keys('생강')).toEqual([]);
  });

  it('띄어쓰기를 무시한다: "핑크페퍼" = "핑크 페퍼"', () => {
    expect(keys('핑크페퍼')).toContain('pink pepper');
  });

  it('조합 중인 한글(분해형 NFD)도 찾는다 (D4)', () => {
    expect(keys('베르가'.normalize('NFD'))).toContain('bergamot');
  });

  it('빈 검색어는 결과 없음', () => {
    expect(keys('  ')).toEqual([]);
  });

  it('앞부분이 맞는 노트가 먼저: "머스크" → 머스크, 그다음 화이트 머스크', () => {
    expect(keys('머스크')).toEqual(['musk', 'white musk']);
  });
});

describe('균형 안내 문구 (A9, design.md 8-7)', () => {
  const texts = (draftNotes: [string, 'top' | 'middle' | 'base'][]) => {
    let d = emptyDraft([]);
    for (const [k, l] of draftNotes) d = ok(addNote(d, l, k));
    return hintTexts(engine, toEngineBlend(d));
  };

  it('베이스가 비면 베이스 안내', () => {
    expect(texts([['lemon', 'top'], ['rose', 'middle']]).join('\n')).toContain('베이스가 비어 있어 잔향이 가볍게 느껴질 수 있어요');
  });

  it('세 층이 다 차면 빈 층 안내가 없다', () => {
    const t = texts([['lemon', 'top'], ['rose', 'middle'], ['sandalwood', 'base']]).join('\n');
    expect(t).not.toContain('비어 있어');
  });

  it('한 계열만이면 이웃 계열 이름을 함께 안내', () => {
    const t = texts([['lemon', 'top'], ['bergamot', 'middle'], ['grapefruit', 'base']]).join('\n');
    expect(t).toContain('한 계열로만 이루어져 단조로울 수 있어요');
    expect(t).toMatch(/프루티|프레시·아쿠아틱/); // 시트러스(1)의 이웃: 프루티(2)·프레시(10)
  });

  it('모든 엔진 코드에 문구가 있다(빈 문자열·코드 그대로 노출 없음)', () => {
    const t = texts([['lemon', 'top'], ['bergamot', 'middle'], ['grapefruit', 'base']]);
    for (const line of t) {
      expect(line.trim()).not.toBe('');
      expect(line).not.toMatch(/[A-Z_]{4,}/);
    }
  });
});
