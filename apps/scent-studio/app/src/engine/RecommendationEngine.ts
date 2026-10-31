import { EngineParams as P } from './params';
import {
  BlendError,
  LAYERS,
  type AccordRatio,
  type BlendNote,
  type Catalog,
  type Grade,
  type Layer,
  type MatchedNote,
  type MatchKind,
  type Note,
  type Perfume,
  type Recommendation,
  type SharedAccord,
} from './types';

/** 향수마다 미리 계산해 두는 것 */
type Indexed = { perfume: Perfume; vector: number[]; layers: Record<Layer, Set<string>>; all: Set<string> };

const INTENSITY_VALUES: readonly number[] = Object.values(P.INTENSITIES);

/** Java Math.round 와 같은 반올림 (0.5에서 올림) */
const roundHalfUp = (x: number) => Math.floor(x + 0.5);

/** Java String.compareTo 와 같은 순서 (UTF-16 코드 단위) */
const compareStrings = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

const sum = (v: readonly number[]) => {
  let s = 0;
  for (const x of v) s += x;
  return s;
};

/**
 * 추천 엔진 (docs/design.md 8절). Python pipeline/engine_ref.py 와 계산 순서까지 똑같이 맞춘다
 * (골든 테스트가 점수 차 1e-9 이하를 확인한다). 화면·서버를 모른다.
 * (예전 Java engine/RecommendationEngine.java 를 옮김)
 */
export class RecommendationEngine {
  private readonly accords: string[];
  private readonly accordIndex = new Map<string, number>();
  private readonly accordFamily: string[];
  private readonly wheel = new Map<string, number>();
  private readonly notes = new Map<string, Note>();
  private readonly perfumes: Indexed[] = [];

  constructor(readonly catalog: Catalog) {
    this.accords = catalog.accords.map((a) => a.key);
    this.accordFamily = catalog.accords.map((a) => a.family);
    this.accords.forEach((key, i) => this.accordIndex.set(key, i));
    for (const f of catalog.families) this.wheel.set(f.code, f.wheelOrder);
    for (const n of catalog.notes) this.notes.set(n.key, n);
    for (const p of catalog.perfumes) {
      this.perfumes.push({
        perfume: p,
        vector: this.perfumeVector(p.accords),
        layers: { top: new Set(p.top), middle: new Set(p.middle), base: new Set(p.base) },
        all: new Set([...p.top, ...p.middle, ...p.base]),
      });
    }
  }

  note(key: string): Note | undefined {
    return this.notes.get(key);
  }

  /** 8-1절: 목록 순서대로 5·4·3·2·1, 합 100 */
  perfumeVector(perfumeAccords: readonly string[]): number[] {
    const n = Math.min(perfumeAccords.length, P.RANK_WEIGHTS.length);
    let total = 0;
    for (let i = 0; i < n; i++) total += P.RANK_WEIGHTS[i];
    const v = new Array<number>(this.accords.length).fill(0);
    for (let i = 0; i < n; i++) v[this.accordIndex.get(perfumeAccords[i])!] = (100.0 * P.RANK_WEIGHTS[i]) / total;
    return v;
  }

  validate(blend: readonly BlendNote[]): void {
    if (blend.length === 0) throw new BlendError('EMPTY_BLEND');
    for (const layer of LAYERS) {
      if (blend.filter((b) => b.layer === layer).length > P.MAX_PER_LAYER) throw new BlendError('LAYER_LIMIT');
    }
    if (new Set(blend.map((b) => b.noteKey)).size !== blend.length) throw new BlendError('DUPLICATE_NOTE');
    for (const b of blend) {
      if (!LAYERS.includes(b.layer) || !INTENSITY_VALUES.includes(b.intensity)) throw new BlendError('BAD_BLEND_NOTE');
      const note = this.notes.get(b.noteKey);
      if (!note || note.tier === 'hidden') throw new BlendError('NOTE_NOT_FOUND');
    }
  }

  /** 8-3절: b = Σ 강도 × 층 가중 × 노트 프로필 */
  blendVector(blend: readonly BlendNote[]): number[] {
    const b = new Array<number>(this.accords.length).fill(0);
    for (const x of blend) {
      const prof = this.notes.get(x.noteKey)!.profile;
      const s = x.intensity * P.LAYER_WEIGHT[x.layer];
      for (let i = 0; i < prof.length; i++) b[i] += s * prof[i];
    }
    return b;
  }

  /** 화면용 비율: 반올림해서 1% 이상인 어코드만, 합 100이 되게 가장 큰 항목에서 맞춘다. 큰 순서 */
  displayRatios(blend: readonly BlendNote[]): AccordRatio[] {
    const b = this.blendVector(blend);
    const total = sum(b);
    const shown: AccordRatio[] = [];
    if (total === 0) return shown;
    b.forEach((x, i) => {
      const p = roundHalfUp((100.0 * x) / total);
      if (p >= 1) shown.push({ accord: this.accords[i], percent: p });
    });
    shown.sort((r, s) => s.percent - r.percent || this.accordIndex.get(r.accord)! - this.accordIndex.get(s.accord)!);
    const diff = 100 - sum(shown.map((r) => r.percent));
    if (shown.length > 0 && diff !== 0) shown[0] = { ...shown[0], percent: shown[0].percent + diff };
    return shown;
  }

  /** 8-7절: EMPTY_LAYER:top|middle|base, ONE_FAMILY, DOMINANT, FAR_FAMILIES (이 순서) */
  balanceHints(blend: readonly BlendNote[]): string[] {
    const hints: string[] = [];
    for (const layer of LAYERS) if (!blend.some((x) => x.layer === layer)) hints.push(`EMPTY_LAYER:${layer}`);
    const fams = new Set(blend.map((x) => this.notes.get(x.noteKey)!.family));
    if (blend.length >= 2 && fams.size === 1) hints.push('ONE_FAMILY');
    const b = this.blendVector(blend);
    const total = sum(b);
    if (total > 0 && Math.max(...b) / total >= P.DOMINANT_SHARE) hints.push('DOMINANT');
    if (fams.size === 2) {
      const [x, y] = [...fams];
      if (this.wheelDistance(x, y) >= P.FAR_FAMILY_DISTANCE) hints.push('FAR_FAMILIES');
    }
    return hints;
  }

  wheelDistance(x: string, y: string): number {
    const d = Math.abs(this.wheel.get(x)! - this.wheel.get(y)!);
    return Math.min(d, this.wheel.size - d);
  }

  /** 8-4~8-6절. disliked: 불호 노트 key, preferredFamilies: 선호 계열 code */
  recommend(
    blend: readonly BlendNote[],
    disliked: ReadonlySet<string>,
    preferredFamilies: ReadonlySet<string>,
    k: number,
  ): Recommendation[] {
    this.validate(blend);
    const b = this.blendVector(blend);
    const bTotal = sum(b);
    const n = this.perfumes.length;
    const weights = new Map<string, number>();
    let wSum = 0;
    for (const x of blend) {
      const w = Math.log(1 + n / this.notes.get(x.noteKey)!.df);
      weights.set(x.noteKey, w);
      wSum += w;
    }

    type Scored = { p: Indexed; score: number; accordSim: number; noteMatch: number; matched: MatchedNote[] };
    const scored: Scored[] = [];
    for (const p of this.perfumes) {
      if ([...disliked].some((d) => p.all.has(d))) continue;
      const accordSim = cosine(p.vector, b);
      const fams = new Set<string>();
      for (const key of p.all) fams.add(this.notes.get(key)!.family);
      const matched: MatchedNote[] = [];
      let nm = 0;
      for (const x of blend) {
        let c: number;
        let kind: MatchKind | null;
        if (p.layers[x.layer].has(x.noteKey)) {
          c = P.C_SAME_LAYER;
          kind = 'same_layer';
        } else if (p.all.has(x.noteKey)) {
          c = P.C_OTHER_LAYER;
          kind = 'other_layer';
        } else if (fams.has(this.notes.get(x.noteKey)!.family)) {
          c = P.C_SAME_FAMILY;
          kind = 'same_family';
        } else {
          c = 0;
          kind = null;
        }
        nm += weights.get(x.noteKey)! * c;
        if (kind) matched.push({ noteKey: x.noteKey, layer: x.layer, match: kind });
      }
      const noteMatch = nm / wSum;
      let score: number;
      if (preferredFamilies.size > 0) {
        let pref = 0;
        p.vector.forEach((a, i) => {
          if (preferredFamilies.has(this.accordFamily[i])) pref += a;
        });
        pref = pref / 100.0;
        score = P.W_ACCORD * accordSim + P.W_NOTE * noteMatch + P.W_PREF * pref;
      } else {
        score = (P.W_ACCORD * accordSim + P.W_NOTE * noteMatch) / (P.W_ACCORD + P.W_NOTE);
      }
      scored.push({ p, score, accordSim, noteMatch, matched });
    }

    scored.sort(
      (s, t) =>
        roundHalfUp(t.score * P.SCORE_QUANTUM) - roundHalfUp(s.score * P.SCORE_QUANTUM) ||
        t.p.perfume.ratingCount - s.p.perfume.ratingCount ||
        compareStrings(s.p.perfume.key, t.p.perfume.key),
    );

    const blendKeys = new Set(blend.map((x) => x.noteKey));
    const perBrand = new Map<string, number>();
    const groups = new Set<string>();
    const out: Recommendation[] = [];
    for (const s of scored) {
      const perfume = s.p.perfume;
      // 농도만 다른 같은 향수는 점수가 가장 높은 하나만
      if (groups.has(perfume.group)) continue;
      if ((perBrand.get(perfume.brand) ?? 0) >= P.BRAND_CAP) continue;
      groups.add(perfume.group);
      perBrand.set(perfume.brand, (perBrand.get(perfume.brand) ?? 0) + 1);

      const shared: SharedAccord[] = s.p.vector.map((a, i) => ({
        accord: this.accords[i],
        perfumePct: a,
        blendPct: (100.0 * b[i]) / bTotal,
      }));
      shared.sort(
        (x, y) =>
          Math.min(y.perfumePct, y.blendPct) - Math.min(x.perfumePct, x.blendPct) ||
          this.accordIndex.get(x.accord)! - this.accordIndex.get(y.accord)!,
      );
      const missing = [...s.p.all].filter((key) => !blendKeys.has(key));
      missing.sort((x, y) => this.notes.get(y)!.df - this.notes.get(x)!.df || compareStrings(x, y));

      out.push({
        perfumeKey: perfume.key,
        score: s.score,
        accordSim: s.accordSim,
        noteMatch: s.noteMatch,
        grade: grade(s.score),
        sharedAccords: shared.slice(0, 3),
        matchedNotes: s.matched,
        missingNotes: missing.slice(0, 3),
      });
      if (out.length === k) break;
    }
    return out;
  }
}

export function grade(score: number): Grade {
  if (score >= P.GRADE_VERY_SIMILAR) return 'VERY_SIMILAR';
  if (score >= P.GRADE_SIMILAR) return 'SIMILAR';
  return 'REFERENCE';
}

export function cosine(a: readonly number[], b: readonly number[]): number {
  let dot = 0;
  let na = 0;
  let nb = 0;
  for (let i = 0; i < a.length; i++) dot += a[i] * b[i];
  for (const x of a) na += x * x;
  for (const y of b) nb += y * y;
  na = Math.sqrt(na);
  nb = Math.sqrt(nb);
  return na === 0 || nb === 0 ? 0.0 : dot / (na * nb);
}
