package com.perfumerlab.scentstudio.engine;

/**
 * 추천 엔진 숫자 (docs/design.md 8-8절). Python pipeline/engine_ref.py 와 같은 값이어야 한다.
 * 바꿀 때는 Python·Java·골든 파일(tests/golden)을 함께 바꾸고 ENGINE_VERSION을 올린다.
 */
public final class EngineParams {
    private EngineParams() {}

    public static final int ENGINE_VERSION = 2;

    // 2026-10-30 사장 결정: 기획 60/30/10 → 45/45/10 (재현 9/20 → 15/20, docs/engine_report.md)
    public static final double W_ACCORD = 0.45;
    public static final double W_NOTE = 0.45;
    public static final double W_PREF = 0.1;

    public static final double C_SAME_LAYER = 1.0;
    public static final double C_OTHER_LAYER = 0.5;
    public static final double C_SAME_FAMILY = 0.3;

    /** 층 가중 (1차는 모두 1.0) */
    public static final double LAYER_WEIGHT_TOP = 1.0;
    public static final double LAYER_WEIGHT_MIDDLE = 1.0;
    public static final double LAYER_WEIGHT_BASE = 1.0;

    public static final int BRAND_CAP = 2;
    public static final double GRADE_VERY_SIMILAR = 0.85;
    public static final double GRADE_SIMILAR = 0.75;
    /** 정렬은 round(score × 1e9) 로 (차이 1e-9 이하는 같은 점수) */
    public static final double SCORE_QUANTUM = 1e9;
    public static final int[] RANK_WEIGHTS = {5, 4, 3, 2, 1};
    public static final int MAX_PER_LAYER = 3;
    public static final double[] INTENSITIES = {0.5, 1.0, 1.5};
    public static final double DOMINANT_SHARE = 0.6;
    public static final int FAR_FAMILY_DISTANCE = 4;
}
