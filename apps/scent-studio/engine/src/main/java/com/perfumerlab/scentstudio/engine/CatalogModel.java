package com.perfumerlab.scentstudio.engine;

import java.util.List;

/** 카탈로그 파일(catalog.json.gz)의 내용 (docs/design.md 7절 11번). JSON 읽기는 앱·서버가 맡는다. */
public final class CatalogModel {
    private CatalogModel() {}

    public enum Layer {
        TOP, MIDDLE, BASE;

        /** 카탈로그·API의 표기 ("top" / "middle" / "base") */
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

        public static Layer of(String key) { return valueOf(key.toUpperCase(java.util.Locale.ROOT)); }
    }

    public enum Tier {
        BROWSE, SEARCH, HIDDEN;

        public static Tier of(String key) { return valueOf(key.toUpperCase(java.util.Locale.ROOT)); }
    }

    public record Family(String code, String nameKo, int wheelOrder) {}

    public record Accord(String key, String nameKo, String family) {}

    /** profile: 어코드 순서(Catalog.accords)대로, 합 1 */
    public record Note(String key, String nameEn, String nameKo, String family, int df, Tier tier,
                       double[] profile, List<String> pairings, String description) {}

    /**
     * accords: 진한 순서 (최대 5개).
     * group: 농도만 다른 같은 향수끼리 같은 값 (추천에서는 묶음마다 하나만, design.md 8-5절)
     */
    public record Perfume(String key, String brand, String name, Integer year, String concentration,
                          Double rating, int ratingCount, List<String> accords,
                          List<String> top, List<String> middle, List<String> base, String group) {
        public List<String> layer(Layer layer) {
            return switch (layer) {
                case TOP -> top;
                case MIDDLE -> middle;
                case BASE -> base;
            };
        }
    }

    public record Catalog(String version, int engineVersion, String sourceName, String sourceNote,
                          List<Family> families, List<Accord> accords, List<Note> notes, List<Perfume> perfumes) {}
}
