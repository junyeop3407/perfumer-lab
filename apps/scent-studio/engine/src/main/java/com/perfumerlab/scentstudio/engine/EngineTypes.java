package com.perfumerlab.scentstudio.engine;

import com.perfumerlab.scentstudio.engine.CatalogModel.Layer;
import java.util.List;

/** 엔진의 입력과 출력 (docs/design.md 8절) */
public final class EngineTypes {
    private EngineTypes() {}

    /** 고른 노트 하나. intensity: 0.5(약) / 1.0(중) / 1.5(강) */
    public record BlendNote(String noteKey, Layer layer, double intensity) {}

    public enum Grade { VERY_SIMILAR, SIMILAR, REFERENCE }

    public enum MatchKind { SAME_LAYER, OTHER_LAYER, SAME_FAMILY }

    public record MatchedNote(String noteKey, Layer layer, MatchKind match) {}

    /** perfumePct·blendPct: 0~100 */
    public record SharedAccord(String accord, double perfumePct, double blendPct) {}

    public record Recommendation(String perfumeKey, double score, double accordSim, double noteMatch,
                                 Grade grade, List<SharedAccord> sharedAccords,
                                 List<MatchedNote> matchedNotes, List<String> missingNotes) {}

    /** 화면용 어코드 비율 (정수 %, 합 100) */
    public record AccordRatio(String accord, int percent) {}

    /** 조합이 규칙에 맞지 않을 때. code: EMPTY_BLEND / LAYER_LIMIT / DUPLICATE_NOTE / BAD_BLEND_NOTE / NOTE_NOT_FOUND */
    public static final class BlendException extends IllegalArgumentException {
        private final String code;

        public BlendException(String code) {
            super(code);
            this.code = code;
        }

        public String code() { return code; }
    }
}
