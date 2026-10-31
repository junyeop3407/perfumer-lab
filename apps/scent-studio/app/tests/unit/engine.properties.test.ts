import { describe, expect, it } from 'vitest';

import { EngineParams } from '../../src/engine/params';
import { RecommendationEngine } from '../../src/engine/RecommendationEngine';
import { BlendError, type BlendNote, type Perfume } from '../../src/engine/types';
import { fixtureCatalog } from './testCatalogs';

/** 엔진 성질 테스트 (QA팀 추가 테스트, docs/design.md 14절. 예전 Java EnginePropertiesTest 를 옮김) */
const engine = new RecommendationEngine(fixtureCatalog());
const byKey = new Map<string, Perfume>(engine.catalog.perfumes.map((p) => [p.key, p]));

const A3: BlendNote[] = [
  { noteKey: 'lemon', layer: 'top', intensity: 3.0 },
  { noteKey: 'rose', layer: 'middle', intensity: 1.0 },
  { noteKey: 'sandalwood', layer: 'base', intensity: 0.5 },
];
const none = new Set<string>();

describe('엔진 성질', () => {
  it('화면 비율의 합은 100', () => {
    expect(engine.displayRatios(A3).reduce((s, r) => s + r.percent, 0)).toBe(100);
  });

  it('불호 노트가 든 향수는 절대 나오지 않는다', () => {
    for (const disliked of ['patchouli', 'musk', 'sandalwood', 'rose']) {
      for (const r of engine.recommend(A3, new Set([disliked]), none, 100)) {
        const p = byKey.get(r.perfumeKey)!;
        expect([...p.top, ...p.middle, ...p.base], `${disliked} 가 든 ${p.name}`).not.toContain(disliked);
      }
    }
  });

  it('같은 입력이면 같은 결과', () => {
    expect(engine.recommend(A3, none, none, 10)).toEqual(engine.recommend(A3, none, none, 10));
  });

  it('한 브랜드는 최대 2개', () => {
    const count = new Map<string, number>();
    for (const r of engine.recommend(A3, none, none, 100)) {
      const brand = byKey.get(r.perfumeKey)!.brand;
      count.set(brand, (count.get(brand) ?? 0) + 1);
    }
    for (const [brand, n] of count) expect(n, `${brand} ${n}개`).toBeLessThanOrEqual(EngineParams.BRAND_CAP);
  });

  it('농도만 다른 같은 향수는 하나만', () => {
    // 가짜 데이터의 T041은 T001(Lumière d'Agrumes)의 EDP 버전이다
    const keys = engine.recommend(A3, none, none, 100).map((r) => r.perfumeKey);
    expect(keys, 'T001이 있어야 함').toContain('T001');
    expect(keys, 'T041이 함께 나오면 안 됨').not.toContain('T041');
    const groups = keys.map((k) => byKey.get(k)!.group);
    expect(new Set(groups).size, '묶음 중복').toBe(groups.length);
  });

  it('점수는 내림차순이고 0과 1 사이', () => {
    const recs = engine.recommend(A3, none, new Set(['floral']), 100);
    recs.forEach((r, i) => {
      expect(r.score).toBeGreaterThanOrEqual(0);
      expect(r.score).toBeLessThanOrEqual(1);
      if (i > 0) expect(recs[i - 1].score).toBeGreaterThanOrEqual(r.score - 1e-9);
    });
  });

  it('노트가 없으면 오류', () => {
    expect(() => engine.recommend([], none, none, 10)).toThrow(BlendError);
  });

  it('실제 크기 카탈로그에서도 빠르다', () => {
    // 가짜 카탈로그를 1.4만 개로 늘려 계산 시간을 잰다 (기준: 폰 300ms, PC에서는 훨씬 빨라야 함)
    const c = engine.catalog;
    const many: Perfume[] = [];
    for (let i = 0; i < 400; i++) {
      for (const p of c.perfumes) many.push({ ...p, key: `${p.key}-${i}`, brand: `${p.brand}${i}`, group: `${p.group}${i}` });
    }
    const big = new RecommendationEngine({ ...c, perfumes: many });
    big.recommend(A3, none, none, 10); // 데우기
    const t = performance.now();
    big.recommend(A3, new Set(['patchouli']), new Set(['floral']), 10);
    const ms = performance.now() - t;
    expect(ms, `${many.length}개 계산에 ${Math.round(ms)}ms`).toBeLessThan(150);
  });
});
