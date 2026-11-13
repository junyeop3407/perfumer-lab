/**
 * 추천 목록 · 향수 상세 · 향수 찾기 화면이 쓰는 계산 (5-3). 화면을 모른다.
 * - 화면 점수는 선호를 뺀 유사도(similarity)를 반올림한 %다(2026-11-02 사장 결정).
 * - "겹친 노트"는 실제 같은 노트만, 같은 층 / 다른 층으로 나눈다(2026-11-12 사장 결정). 같은 계열 일치는 보여 주지 않는다.
 */
import { normalizeQuery } from '../blend/blendDraft';
import { brandSearchNames } from '../catalog/brandNames';
import type { RecommendationEngine } from '../engine/RecommendationEngine';
import { LAYERS, type AccordRatio, type BlendNote, type Catalog, type Layer, type Perfume, type Recommendation } from '../engine/types';

export const REC_COUNT = 10;

/** 상세의 어코드 막대는 상위 6개 + [어코드 모두 보기] (요약과 같은 개수, 2026-11-12 사장 결정) */
export const ACCORDS_SHOWN_DETAIL = 6;

const EMPTY: ReadonlySet<string> = new Set();

/** 추천 10개. 불호·선호는 5-4에서 넣는다 */
export function recommendFor(
  engine: RecommendationEngine,
  blend: readonly BlendNote[],
  disliked: ReadonlySet<string> = EMPTY,
  preferred: ReadonlySet<string> = EMPTY,
): Recommendation[] {
  if (blend.length === 0) return [];
  return engine.recommend(blend, disliked, preferred, REC_COUNT);
}

/** 화면 점수(정수 %) */
export const scorePercent = (r: Recommendation) => Math.round(r.similarity * 100);

/** 실제 같은 노트만: 같은 층 / 다른 층 */
export type Overlap = { noteKey: string; blendLayer: Layer; kind: 'same_layer' | 'other_layer' };

export function overlaps(perfume: Perfume, blend: readonly BlendNote[]): Overlap[] {
  const out: Overlap[] = [];
  for (const b of blend) {
    if (perfume[b.layer].includes(b.noteKey)) out.push({ noteKey: b.noteKey, blendLayer: b.layer, kind: 'same_layer' });
    else if (LAYERS.some((l) => perfume[l].includes(b.noteKey)))
      out.push({ noteKey: b.noteKey, blendLayer: b.layer, kind: 'other_layer' });
  }
  return out;
}

/**
 * 피라미드 칩(향수의 층 l)에 붙일 표시. "겹친 노트" 목록과 같은 말을 하게(5-3 QA):
 * 내 조합과 같은 층이면 "내 조합", 다른 층에만 겹치면 그 칩에 "다른 층", 같은 층에도 있으면 다른 층 칩에는 표시 없음
 */
export function chipMark(o: Overlap | undefined, l: Layer): 'mine' | 'otherLayer' | null {
  if (!o) return null;
  if (o.blendLayer === l) return 'mine';
  return o.kind === 'other_layer' ? 'otherLayer' : null;
}

/** 이 향수에만 있는 노트 (층 순서대로, 겹치는 노트는 뺀다) */
export function onlyInPerfume(perfume: Perfume, blend: readonly BlendNote[]): { noteKey: string; layer: Layer }[] {
  const mine = new Set(blend.map((b) => b.noteKey));
  const seen = new Set<string>();
  const out: { noteKey: string; layer: Layer }[] = [];
  for (const l of LAYERS)
    for (const k of perfume[l]) {
      if (mine.has(k) || seen.has(k)) continue;
      seen.add(k);
      out.push({ noteKey: k, layer: l });
    }
  return out;
}

/** 향수 어코드 비중(%, 큰 순, 1% 이상). 엔진과 같은 값: accordShares, 없으면 순위 비중 */
export function perfumeAccords(engine: RecommendationEngine, catalog: Catalog, perfume: Perfume): AccordRatio[] {
  const keys = catalog.accords.map((a) => a.key);
  const v = perfume.accordShares ? keys.map((k) => perfume.accordShares![k] ?? 0) : engine.perfumeVector(perfume.accords);
  return keys
    .map((accord, i) => ({ accord, percent: Math.round(v[i]) }))
    .filter((r) => r.percent >= 1)
    .sort((a, b) => b.percent - a.percent || keys.indexOf(a.accord) - keys.indexOf(b.accord));
}

/** 카드 구분 ①: "+ 우디"로 보일 기준 — 이 향수 %가 내 조합보다 이만큼(%p) 이상 많을 때만 (2026-11-13 사장 4번) */
export const EXTRA_ACCORD_MIN = 10;

/** 이 향수에 내 조합보다 가장 많이 더 있는 어코드(향수 % − 내 조합 %가 가장 큰 것). EXTRA_ACCORD_MIN 미만이면 null */
export function extraAccord(mine: readonly AccordRatio[], theirs: readonly AccordRatio[]): string | null {
  const m = new Map(mine.map((r) => [r.accord, r.percent]));
  let best: string | null = null;
  let gap = EXTRA_ACCORD_MIN - 1e-9;
  for (const r of theirs) {
    const d = r.percent - (m.get(r.accord) ?? 0);
    if (d > gap) {
      gap = d;
      best = r.accord;
    }
  }
  return best;
}

/** 비교 막대 한 줄: 내 조합 % / 이 향수 % */
export type CompareRow = { accord: string; mine: number; theirs: number };

/** 둘 중 하나라도 1% 이상인 어코드. 둘 중 큰 값 순 → 합 순 */
export function compareRows(mine: readonly AccordRatio[], theirs: readonly AccordRatio[]): CompareRow[] {
  const m = new Map(mine.map((r) => [r.accord, r.percent]));
  const t = new Map(theirs.map((r) => [r.accord, r.percent]));
  const order = [...mine, ...theirs].map((r) => r.accord);
  const keys = [...new Set(order)];
  return keys
    .map((accord) => ({ accord, mine: m.get(accord) ?? 0, theirs: t.get(accord) ?? 0 }))
    .sort(
      (a, b) =>
        Math.max(b.mine, b.theirs) - Math.max(a.mine, a.theirs) ||
        b.mine + b.theirs - (a.mine + a.theirs) ||
        order.indexOf(a.accord) - order.indexOf(b.accord),
    );
}

const SEARCH_LIMIT = 50;

type Indexed = { p: Perfume; names: string[] };
const searchIndexCache = new WeakMap<Catalog, Indexed[]>();

/** 검색용으로 정리한 이름(이름·브랜드·브랜드+이름). 카탈로그마다 한 번만 만든다(5-3 Gemini 리뷰: 글자마다 다시 정리하면 느림) */
export function perfumeSearchIndex(catalog: Catalog): Indexed[] {
  let idx = searchIndexCache.get(catalog);
  if (!idx) {
    // 한글 브랜드 이름·별칭으로도 찾는다(2026-11-13 사장, brandNames.ts)
    idx = catalog.perfumes.map((p) => ({ p, names: [p.name, p.brand, `${p.brand}${p.name}`, ...brandSearchNames(p.brand)].map(fold) }));
    searchIndexCache.set(catalog, idx);
  }
  return idx;
}

/** 향수 이름·브랜드 일부로 찾는다. 앞부분이 맞는 것 먼저, 그다음 평가 수 많은 순 */
export function searchPerfumes(catalog: Catalog, query: string): Perfume[] {
  const q = fold(query);
  if (q === '') return [];
  const hits: { p: Perfume; prefix: boolean }[] = [];
  for (const { p, names } of perfumeSearchIndex(catalog)) {
    if (!names.some((x) => x.includes(q))) continue;
    hits.push({ p, prefix: names.some((x) => x.startsWith(q)) });
  }
  hits.sort((a, b) => Number(b.prefix) - Number(a.prefix) || b.p.ratingCount - a.p.ratingCount || (a.p.key < b.p.key ? -1 : 1));
  return hits.slice(0, SEARCH_LIMIT).map((h) => h.p);
}

/** 검색용: 띄어쓰기·대소문자 무시, 악센트 무시(Lumiere = Lumière) */
function fold(s: string): string {
  return normalizeQuery(s.normalize('NFD').replace(/[\u0300-\u036f]/g, '')).normalize('NFC');
}
