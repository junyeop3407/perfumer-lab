import { describe, expect, it, vi } from 'vitest';

import { EngineParams } from '../../src/engine/params';
import type { Perfume } from '../../src/engine/types';
import { chipMark, overlaps, perfumeSearchIndex, searchPerfumes } from '../../src/recs/perfumeView';
import { S } from '../../src/strings.ko';
import { fixtureCatalog } from './testCatalogs';

describe('등급 설명 문구의 숫자가 엔진 기준값과 같다 (QA 중간)', () => {
  it('"약 76% 이상" · "약 65% 이상"은 GRADE_VERY_SIMILAR · GRADE_SIMILAR를 반올림한 %', () => {
    expect(S.gradeInfoVery).toBe(`약 ${Math.round(EngineParams.GRADE_VERY_SIMILAR * 100)}% 이상`);
    expect(S.gradeInfoSimilar).toBe(`약 ${Math.round(EngineParams.GRADE_SIMILAR * 100)}% 이상`);
  });
});

describe('피라미드 칩 표시와 "겹친 노트" 목록이 같은 말을 한다 (QA 낮음)', () => {
  it('내 조합 탑 레몬, 향수는 레몬이 탑·미들 둘 다 → 탑 칩만 "내 조합", 미들 칩은 표시 없음, 목록은 같은 층', () => {
    const p = { ...fixtureCatalog().perfumes[0], top: ['lemon'], middle: ['lemon', 'rose'], base: [] } as Perfume;
    const [o] = overlaps(p, [{ noteKey: 'lemon', layer: 'top', intensity: 1 }]);
    expect(o.kind).toBe('same_layer');
    expect(chipMark(o, 'top')).toBe('mine');
    expect(chipMark(o, 'middle')).toBeNull();
  });

  it('다른 층에만 있으면 그 칩에 "다른 층"', () => {
    const p = { ...fixtureCatalog().perfumes[0], top: [], middle: ['lemon'], base: [] } as Perfume;
    const [o] = overlaps(p, [{ noteKey: 'lemon', layer: 'top', intensity: 1 }]);
    expect(o.kind).toBe('other_layer');
    expect(chipMark(o, 'middle')).toBe('otherLayer');
  });
});

/** 5-3 리뷰(Gemini 교차 리뷰·QA 리뷰)에서 나온 문제의 회귀 테스트 */
describe('향수 찾기: 글자를 칠 때마다 모든 향수 이름을 다시 정리하지 않는다 (Gemini 중간)', () => {
  it('검색용 정리 결과는 카탈로그마다 한 번만 만든다', () => {
    const catalog = fixtureCatalog();
    const first = perfumeSearchIndex(catalog);
    expect(perfumeSearchIndex(catalog)).toBe(first);
    expect(first).toHaveLength(catalog.perfumes.length);
  });

  it('두 번째 검색부터는 향수 이름을 다시 정리(normalize)하지 않는다', () => {
    const catalog = fixtureCatalog();
    searchPerfumes(catalog, 'lumi');
    const spy = vi.spyOn(String.prototype, 'normalize');
    searchPerfumes(catalog, 'lumie');
    // 검색어 하나만 정리한다(NFD·NFC 몇 번). 향수 37개 × 이름 3개를 다시 정리하면 100번이 넘는다
    expect(spy.mock.calls.length).toBeLessThan(10);
    spy.mockRestore();
  });
});
