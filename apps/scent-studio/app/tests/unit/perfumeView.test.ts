import { describe, expect, it } from 'vitest';

import { RecommendationEngine } from '../../src/engine/RecommendationEngine';
import type { BlendNote, Perfume } from '../../src/engine/types';
import {
  compareRows,
  onlyInPerfume,
  overlaps,
  perfumeAccords,
  recommendFor,
  scorePercent,
  searchPerfumes,
} from '../../src/recs/perfumeView';
import { fixtureCatalog } from './testCatalogs';

/** 5-3 추천·상세·찾기 계산 (src/recs/perfumeView.ts) */
const catalog = fixtureCatalog();
const engine = new RecommendationEngine(catalog);
const T001 = catalog.perfumes.find((p) => p.key === 'T001')!;
const b = (noteKey: string, layer: BlendNote['layer']): BlendNote => ({ noteKey, layer, intensity: 1 });

describe('겹친 노트: 실제 같은 노트만, 같은 층 / 다른 층', () => {
  it('같은 층·다른 층을 나누고, 같은 계열만 맞는 노트는 넣지 않는다', () => {
    // T001: 탑 lemon·bergamot / 미들 osmanthus·jasmine / 베이스 sandalwood·white musk
    const blend = [b('lemon', 'top'), b('sandalwood', 'middle'), b('rose', 'middle')];
    expect(overlaps(T001, blend)).toEqual([
      { noteKey: 'lemon', blendLayer: 'top', kind: 'same_layer' },
      { noteKey: 'sandalwood', blendLayer: 'middle', kind: 'other_layer' },
    ]);
  });

  it('이 향수에만 있는 노트는 층 순서대로, 겹친 노트는 빠진다', () => {
    const only = onlyInPerfume(T001, [b('lemon', 'top'), b('sandalwood', 'middle')]).map((o) => o.noteKey);
    expect(only).toEqual(['bergamot', 'osmanthus', 'jasmine', 'white musk']);
  });

  it('한 노트가 향수의 여러 층에 있어도 한 번만', () => {
    const p: Perfume = { ...T001, top: ['lemon'], middle: ['lemon', 'rose'], base: [] };
    expect(onlyInPerfume(p, []).map((o) => o.noteKey)).toEqual(['lemon', 'rose']);
  });
});

describe('어코드 비교 막대', () => {
  it('향수 어코드는 accordShares를 반올림한 %, 큰 순', () => {
    expect(perfumeAccords(engine, catalog, T001)).toEqual([
      { accord: 'citrus', percent: 43 },
      { accord: 'floral', percent: 30 },
      { accord: 'fresh', percent: 17 },
      { accord: 'woody', percent: 9 },
    ]);
  });

  it('둘 중 하나에만 있는 어코드도 0%로 함께, 큰 값 순', () => {
    const rows = compareRows(
      [
        { accord: 'woody', percent: 60 },
        { accord: 'citrus', percent: 40 },
      ],
      [
        { accord: 'citrus', percent: 70 },
        { accord: 'floral', percent: 30 },
      ],
    );
    expect(rows).toEqual([
      { accord: 'citrus', mine: 40, theirs: 70 },
      { accord: 'woody', mine: 60, theirs: 0 },
      { accord: 'floral', mine: 0, theirs: 30 },
    ]);
  });
});

describe('추천 10개와 화면 점수', () => {
  it('빈 조합이면 빈 목록(오류 없음)', () => {
    expect(recommendFor(engine, [])).toEqual([]);
  });

  it('10개, 화면 점수는 유사도의 반올림 %, 점수 순서가 뒤집히지 않는다', () => {
    const recs = recommendFor(engine, [b('lemon', 'top'), b('rose', 'middle'), b('sandalwood', 'base')]);
    expect(recs).toHaveLength(10);
    const scores = recs.map(scorePercent);
    for (let i = 1; i < scores.length; i++) expect(scores[i - 1]).toBeGreaterThanOrEqual(scores[i]);
    for (const r of recs) expect(scorePercent(r)).toBe(Math.round(r.similarity * 100));
  });
});

describe('향수 찾기', () => {
  it('이름 일부·브랜드로 찾고, 악센트·대소문자·띄어쓰기를 무시한다', () => {
    expect(searchPerfumes(catalog, 'Lumière').map((p) => p.key)).toContain('T001');
    expect(searchPerfumes(catalog, 'lumiere').map((p) => p.key)).toContain('T001');
    expect(searchPerfumes(catalog, 'LUMI ERE').map((p) => p.key)).toContain('T001');
    const noir = searchPerfumes(catalog, 'Noirwood').map((p) => p.key);
    for (const k of ['T002', 'T022', 'T023', 'T036']) expect(noir).toContain(k);
  });

  it('빈 검색어와 맞는 것이 없는 검색어는 빈 목록', () => {
    expect(searchPerfumes(catalog, '   ')).toEqual([]);
    expect(searchPerfumes(catalog, 'zzzz-없는향수')).toEqual([]);
  });
});
