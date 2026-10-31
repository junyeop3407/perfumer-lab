/**
 * 추천 엔진 숫자 (docs/design.md 8-8절). Python pipeline/engine_ref.py 와 같은 값이어야 한다.
 * 바꿀 때는 Python·TypeScript·골든 파일(tests/golden)을 함께 바꾸고 ENGINE_VERSION을 올린다.
 */
export const EngineParams = {
  ENGINE_VERSION: 2,

  // 2026-10-30 사장 결정: 기획 60/30/10 → 45/45/10 (재현 9/20 → 15/20, docs/engine_report.md)
  W_ACCORD: 0.45,
  W_NOTE: 0.45,
  W_PREF: 0.1,

  C_SAME_LAYER: 1.0,
  C_OTHER_LAYER: 0.5,
  C_SAME_FAMILY: 0.3,

  /** 층 가중 (1차는 모두 1.0) */
  LAYER_WEIGHT: { top: 1.0, middle: 1.0, base: 1.0 },

  BRAND_CAP: 2,
  GRADE_VERY_SIMILAR: 0.85,
  GRADE_SIMILAR: 0.75,
  /** 정렬은 round(score × 1e9) 로 (차이 1e-9 이하는 같은 점수) */
  SCORE_QUANTUM: 1e9,
  RANK_WEIGHTS: [5, 4, 3, 2, 1],
  MAX_PER_LAYER: 3,
  /** 약 · 중 · 강 */
  INTENSITIES: { weak: 0.5, medium: 1.0, strong: 1.5 },
  DOMINANT_SHARE: 0.6,
  FAR_FAMILY_DISTANCE: 4,
} as const;
