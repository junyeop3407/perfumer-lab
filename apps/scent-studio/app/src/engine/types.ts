/** 카탈로그 파일(catalog.json.gz)의 내용 (docs/design.md 7절 11번)과 엔진의 입력·출력 (8절) */

/** 카탈로그·API의 표기 */
export type Layer = 'top' | 'middle' | 'base';
export const LAYERS: readonly Layer[] = ['top', 'middle', 'base'];

export type Tier = 'browse' | 'search' | 'hidden';

export type Family = { code: string; nameKo: string; wheelOrder: number };

export type Accord = { key: string; nameKo: string; family: string };

/** profile: 어코드 순서(Catalog.accords)대로, 합 1 */
export type Note = {
  key: string;
  nameEn: string;
  nameKo: string;
  family: string;
  df: number;
  tier: Tier;
  profile: number[];
  pairings: string[];
  description: string;
};

/**
 * accords: 진한 순서 (최대 5개).
 * group: 농도만 다른 같은 향수끼리 같은 값 (추천에서는 묶음마다 하나만, design.md 8-5절)
 */
export type Perfume = {
  key: string;
  brand: string;
  name: string;
  year: number | null;
  concentration: string | null;
  rating: number | null;
  ratingCount: number;
  accords: string[];
  top: string[];
  middle: string[];
  base: string[];
  group: string;
};

export type Catalog = {
  version: string;
  engineVersion: number;
  source: { name: string; note: string };
  families: Family[];
  accords: Accord[];
  globalMean?: number[];
  notes: Note[];
  perfumes: Perfume[];
};

/** 고른 노트 하나. intensity: 0.5(약) / 1.0(중) / 3.0(강, 2026-10-31 1.5 → 2.0 → 3.0) */
export type BlendNote = { noteKey: string; layer: Layer; intensity: number };

export type Grade = 'VERY_SIMILAR' | 'SIMILAR' | 'REFERENCE';

export type MatchKind = 'same_layer' | 'other_layer' | 'same_family';

export type MatchedNote = { noteKey: string; layer: Layer; match: MatchKind };

/** perfumePct·blendPct: 0~100 */
export type SharedAccord = { accord: string; perfumePct: number; blendPct: number };

export type Recommendation = {
  perfumeKey: string;
  score: number;
  accordSim: number;
  noteMatch: number;
  grade: Grade;
  sharedAccords: SharedAccord[];
  matchedNotes: MatchedNote[];
  missingNotes: string[];
};

/** 화면용 어코드 비율 (정수 %, 합 100) */
export type AccordRatio = { accord: string; percent: number };

export type BlendErrorCode = 'EMPTY_BLEND' | 'LAYER_LIMIT' | 'DUPLICATE_NOTE' | 'BAD_BLEND_NOTE' | 'NOTE_NOT_FOUND';

/** 조합이 규칙에 맞지 않을 때 */
export class BlendError extends Error {
  constructor(readonly code: BlendErrorCode) {
    super(code);
    this.name = 'BlendError';
  }
}
