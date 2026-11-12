import { describe, expect, it } from 'vitest';

import { foldAccords, layerUse, noteSections, RARE_LAYER_SHARE } from '../../src/blend/blendDraft';
import type { Layer } from '../../src/engine/types';
import { fixtureCatalog } from './testCatalogs';

/** 5-2 사장 폰 확인 2차(2026-11-12, 갤럭시 S26 울트라) 버그 B 회귀 테스트 */
const cat = fixtureCatalog();
const use = layerUse(cat);
const share = (key: string, layer: Layer) => {
  const all = (use.top.get(key) ?? 0) + (use.middle.get(key) ?? 0) + (use.base.get(key) ?? 0);
  return all === 0 ? 1 : (use[layer].get(key) ?? 0) / all;
};

describe('버그 B: 고른 계열이라도 지금 층에 드물게 쓰는 노트는 앞에 두지 않는다 (BASE에 시트러스부터 나오던 문제)', () => {
  it('시트러스를 고르고 BASE: 첫 묶음은 시트러스가 아니고, 베이스에 쓰는 노트부터', () => {
    const s = noteSections(cat, ['citrus'], 'base');
    expect(s[0].family.code).not.toBe('citrus');
    expect(share(s[0].notes[0].key, 'base')).toBeGreaterThanOrEqual(RARE_LAYER_SHARE);
  });

  it('앞쪽 묶음의 노트는 모두 그 층에 쓰이고, 드문 노트는 맨 뒤 "드물게 쓰는" 묶음으로', () => {
    for (const layer of ['top', 'middle', 'base'] as const) {
      const s = noteSections(cat, ['citrus', 'floral'], layer);
      const firstRare = s.findIndex((x) => x.rare);
      const main = firstRare < 0 ? s : s.slice(0, firstRare);
      for (const x of main) for (const n of x.notes) expect(share(n.key, layer), `${layer} ${n.key}`).toBeGreaterThanOrEqual(RARE_LAYER_SHARE);
      if (firstRare >= 0) for (const x of s.slice(firstRare)) {
        expect(x.rare, `${layer}: 드문 묶음 뒤에 보통 묶음이 섞임`).toBe(true);
        for (const n of x.notes) expect(share(n.key, layer)).toBeLessThan(RARE_LAYER_SHARE);
      }
    }
  });

  it('BASE에서 시트러스 노트는 사라지지 않고 드문 묶음에 있다(고른 계열이 드문 묶음 안에서도 먼저)', () => {
    const s = noteSections(cat, ['citrus'], 'base');
    const rare = s.filter((x) => x.rare);
    expect(rare[0].family.code).toBe('citrus');
    expect(rare[0].notes.map((n) => n.key)).toContain('lemon');
  });

  it('TOP에서는 고른 시트러스가 그대로 맨 앞 (승인 A2)', () => {
    expect(noteSections(cat, ['citrus', 'woody'], 'top')[0].family.code).toBe('citrus');
  });
});

describe('디자인 C: 어코드 목록은 상위 6개 + "그 외" 한 줄로 접는다', () => {
  const r = (n: number) => Array.from({ length: n }, (_, i) => ({ accord: `a${i}`, percent: i === 0 ? 100 - (n - 1) : 1 }));

  it('16개면 6개만 보이고 나머지 10개는 합친 % 한 줄 (보이는 % 합은 그대로 100, 승인 A8)', () => {
    const f = foldAccords(r(16));
    expect(f.shown.map((x) => x.accord)).toEqual(['a0', 'a1', 'a2', 'a3', 'a4', 'a5']);
    expect(f.rest).toEqual({ count: 10, percent: 10 });
    expect(f.shown.reduce((a, x) => a + x.percent, 0) + f.rest!.percent).toBe(100);
  });

  it('7개 이하면 접지 않는다(한두 개 접으려고 한 줄을 더 쓰지 않음)', () => {
    expect(foldAccords(r(7)).rest).toBeNull();
    expect(foldAccords(r(7)).shown).toHaveLength(7);
  });
});
