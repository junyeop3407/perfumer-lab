/**
 * 추천 엔진 숫자 (docs/design.md 8-8절). Python pipeline/engine_ref.py 와 같은 값이어야 한다.
 * 바꿀 때는 Python·TypeScript·골든 파일(tests/golden)을 함께 바꾸고 ENGINE_VERSION을 올린다.
 */
export const EngineParams = {
  // 2026-10-31 v3: 프로필 특징 강조(PPMI, 파이프라인), 강 1.5 → 2.0, 등급 0.85/0.75 → 0.87/0.79
  // 2026-10-31 v4: 어코드 0.45 → 0.35, 노트 0.45 → 0.55 (재현 15 → 17/20, docs/engine_weights.md). 강 값·등급은 사장 결정 대기
  // 2026-10-31 v5: 노트 프로필을 "함께 든 노트의 몫 빼기"로(파이프라인), 원본 복사 오류 제외. 엔진 숫자는 v4와 같음
  // 2026-10-31 v6: 강 2.0 → 3.0 (어코드 쪽에만, 사장 결정)
  ENGINE_VERSION: 6,

  // 2026-10-30 사장 결정: 기획 60/30/10 → 45/45/10 (재현 9/20 → 15/20). 2026-10-31 v4: 35/55/10
  W_ACCORD: 0.35,
  W_NOTE: 0.55,
  W_PREF: 0.1,

  C_SAME_LAYER: 1.0,
  C_OTHER_LAYER: 0.5,
  C_SAME_FAMILY: 0.3,

  /** 층 가중 (1차는 모두 1.0) */
  LAYER_WEIGHT: { top: 1.0, middle: 1.0, base: 1.0 },

  BRAND_CAP: 2,
  /** "그 향수의 노트를 그대로 넣었을 때 그 향수 자신의 점수" 중앙값·하위 25% (docs/engine_variants.md) */
  GRADE_VERY_SIMILAR: 0.87,
  GRADE_SIMILAR: 0.79,
  /** 정렬은 round(score × 1e9) 로 (차이 1e-9 이하는 같은 점수) */
  SCORE_QUANTUM: 1e9,
  RANK_WEIGHTS: [5, 4, 3, 2, 1],
  MAX_PER_LAYER: 3,
  /** 약 · 중 · 강 */
  INTENSITIES: { weak: 0.5, medium: 1.0, strong: 3.0 },
  DOMINANT_SHARE: 0.6,
  FAR_FAMILY_DISTANCE: 4,
} as const;
