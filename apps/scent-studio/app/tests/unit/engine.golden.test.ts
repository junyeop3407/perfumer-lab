import { describe, expect, it } from 'vitest';

import { RecommendationEngine } from '../../src/engine/RecommendationEngine';
import { BlendError } from '../../src/engine/types';
import { fixtureCatalog, golden } from './testCatalogs';

/**
 * 골든 테스트 (QA팀 추가 테스트): Python 참조 엔진(pipeline/engine_ref.py)이 만든 기대 결과
 * (tests/golden/recommend_cases.json)와 순위가 같고 점수 차가 1e-9 이하인지 확인한다.
 * (예전 Java GoldenTest 를 옮김)
 */
const EPS = 1e-9;
const engine = new RecommendationEngine(fixtureCatalog());
const g = golden();

const near = (actual: number, expected: number, what: string) =>
  expect(Math.abs(actual - expected), `${what}: ${actual} ≠ ${expected}`).toBeLessThanOrEqual(EPS);

describe('추천 결과가 Python과 같다', () => {
  for (const c of g.cases) {
    it(c.name, () => {
      const recs = engine.recommend(c.blend, new Set(c.disliked), new Set(c.preferred), c.k);
      expect(recs.length, `${c.name}: 개수`).toBe(c.recommendations.length);
      recs.forEach((r, i) => {
        const x = c.recommendations[i];
        const at = `${c.name} ${i + 1}위`;
        expect(r.perfumeKey, `${at} 향수`).toBe(x.perfumeKey);
        near(r.score, x.score, `${at} 점수`);
        near(r.accordSim, x.accordSim, `${at} 어코드 유사도`);
        near(r.noteMatch, x.noteMatch, `${at} 노트 일치도`);
        expect(r.grade, `${at} 등급`).toBe(x.grade);
        expect(r.sharedAccords.length, `${at} 공통 어코드 수`).toBe(x.sharedAccords.length);
        x.sharedAccords.forEach((s, j) => {
          const a = r.sharedAccords[j];
          expect(a.accord, `${at} 공통 어코드`).toBe(s.accord);
          near(a.perfumePct, s.perfumePct, `${at} 향수 %`);
          near(a.blendPct, s.blendPct, `${at} 조합 %`);
        });
        expect(r.matchedNotes, `${at} 일치 노트`).toEqual(x.matchedNotes);
        expect(r.missingNotes, `${at} 빠진 노트`).toEqual(x.missingNotes);
      });
    });
  }
});

describe('화면 비율과 균형 안내가 Python과 같다', () => {
  for (const c of g.cases) {
    it(c.name, () => {
      expect(engine.displayRatios(c.blend), `${c.name}: 화면 비율`).toEqual(c.displayRatios);
      expect(engine.balanceHints(c.blend), `${c.name}: 균형 안내`).toEqual(c.balanceHints);
    });
  }
});

describe('잘못된 조합은 같은 오류', () => {
  for (const c of g.errors) {
    it(c.name, () => {
      let caught: unknown;
      try {
        engine.recommend(c.blend, new Set(), new Set(), 10);
      } catch (e) {
        caught = e;
      }
      expect(caught, `${c.name}: 오류가 나지 않음`).toBeInstanceOf(BlendError);
      expect((caught as BlendError).code).toBe(c.error);
    });
  }
});
