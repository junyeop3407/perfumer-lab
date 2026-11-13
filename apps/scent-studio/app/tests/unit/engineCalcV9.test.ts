import { gzipSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';

import { openCatalog } from '../../src/catalog/catalogUpdate';
import { EngineParams as P } from '../../src/engine/params';
import { RecommendationEngine } from '../../src/engine/RecommendationEngine';
import type { BlendNote, Catalog } from '../../src/engine/types';
import { fixtureCatalog } from './testCatalogs';

/**
 * 계산 버전 9 = ㉮ "노트를 많이 골라도 불리하지 않게"(2026-11-13 사장 결정, docs/engine_always_reference_compare.md):
 * 노트 일치는 고른 노트 중 가장 무거운 4개(NOTE_FULL_COUNT)까지 다 맞으면 만점. 4개 이하면 지금과 똑같다.
 * 카탈로그 버전(ENGINE_VERSION 8)은 그대로 — 계산 버전만 따로 올린다(사장 결정).
 */

/** 손으로 셀 수 있는 작은 카탈로그: a~d는 드문 노트(무거움), e·f는 흔한 노트(가벼움). 향수 P1은 a·b·c(탑)·d(미들)만 */
function mini(): Catalog {
  const note = (key: string, df: number) => ({
    key, nameEn: key, nameKo: key, family: 'citrus', df, tier: 'browse' as const, profile: [1, 0], pairings: [], description: '',
  });
  const perfume = (key: string, top: string[], middle: string[], base: string[]) => ({
    key, brand: `B${key}`, name: key, year: null, concentration: null, rating: null, ratingCount: 1,
    accords: ['citrus'], top, middle, base, group: key,
  });
  return {
    version: 'test', engineVersion: 8, source: { name: 'test', note: '' },
    families: [{ code: 'citrus', nameKo: '시트러스', wheelOrder: 1 }],
    accords: [{ key: 'citrus', nameKo: '시트러스', family: 'citrus' }, { key: 'woody', nameKo: '우디', family: 'citrus' }],
    notes: [note('a', 1), note('b', 1), note('c', 1), note('d', 1), note('e', 4), note('f', 4)],
    perfumes: [perfume('P1', ['a', 'b', 'c'], ['d'], []), perfume('P2', ['e'], ['f'], []), perfume('P3', [], [], ['a']), perfume('P4', [], [], ['e'])],
  } as unknown as Catalog;
}
const SIX: BlendNote[] = [
  { noteKey: 'a', layer: 'top', intensity: 1 }, { noteKey: 'b', layer: 'top', intensity: 1 }, { noteKey: 'c', layer: 'top', intensity: 1 },
  { noteKey: 'd', layer: 'middle', intensity: 1 }, { noteKey: 'e', layer: 'middle', intensity: 1 }, { noteKey: 'f', layer: 'base', intensity: 1 },
];

describe('계산 버전 9: 노트 4개까지 맞으면 만점', () => {
  it('숫자: 계산 버전 9, 카탈로그 버전 8, 만점 기준 4개', () => {
    expect(P.CALC_VERSION).toBe(9);
    expect(P.ENGINE_VERSION).toBe(8);
    expect(P.NOTE_FULL_COUNT).toBe(4);
  });

  it('노트 6개를 골랐고 향수가 가장 무거운 4개를 같은 층에 다 가지면 노트 일치는 만점(1)', () => {
    const r = new RecommendationEngine(mini()).recommend(SIX, new Set(), new Set(), 4).find((x) => x.perfumeKey === 'P1')!;
    // 예전 계산이면 무거운 4개 몫 / 6개 전체 몫이라 1보다 작았다
    expect(r.noteMatch).toBeCloseTo(1, 12);
  });

  it('노트 4개 이하면 예전 계산(고른 노트 전체의 무게 합으로 나눔)과 같다', () => {
    const e = new RecommendationEngine(fixtureCatalog());
    const blend: BlendNote[] = [
      { noteKey: 'lemon', layer: 'top', intensity: 3 }, { noteKey: 'rose', layer: 'middle', intensity: 1 },
      { noteKey: 'sandalwood', layer: 'base', intensity: 0.5 }, { noteKey: 'bergamot', layer: 'top', intensity: 1 },
    ];
    const n = fixtureCatalog().perfumes.length;
    const w = (k: string) => Math.log(1 + n / e.note(k)!.df);
    const wSum = blend.reduce((a, x) => a + w(x.noteKey), 0);
    for (const r of e.recommend(blend, new Set(), new Set(), 10)) {
      const p = fixtureCatalog().perfumes.find((x) => x.key === r.perfumeKey)!;
      const all = new Set([...p.top, ...p.middle, ...p.base]);
      const fams = new Set([...all].map((k) => e.note(k)!.family));
      let nm = 0;
      let held = 0;
      for (const x of blend) {
        nm += w(x.noteKey) * (p[x.layer].includes(x.noteKey) ? 1 : all.has(x.noteKey) ? 0.5 : fams.has(e.note(x.noteKey)!.family) ? 0.3 : 0);
        if (all.has(x.noteKey)) held += p.noteWeights ? p.noteWeights[x.noteKey] : 1 / all.size;
      }
      const old = (nm / wSum) * (P.STRENGTH_BASE + (1 - P.STRENGTH_BASE) * Math.sqrt(Math.min(held, 1)));
      expect(r.noteMatch, r.perfumeKey).toBeCloseTo(old, 12);
    }
  });
});

describe('카탈로그 버전과 계산 버전은 따로', () => {
  const bytes = (engineVersion: number) => new Uint8Array(gzipSync(JSON.stringify({ ...fixtureCatalog(), version: '2026.11.05', engineVersion })));
  it('계산 버전이 9여도 지금 카탈로그(버전 8)는 열린다', async () => {
    const r = await openCatalog({ bundled: async () => bytes(8), cache: null, appVersion: '1.0.0' });
    expect(r.catalog.engineVersion).toBe(8);
  });
  it('카탈로그 버전이 다르면(7·9) 지금처럼 거부한다', async () => {
    for (const v of [7, 9]) await expect(openCatalog({ bundled: async () => bytes(v), cache: null, appVersion: '1.0.0' }), String(v)).rejects.toThrow();
  });
});
