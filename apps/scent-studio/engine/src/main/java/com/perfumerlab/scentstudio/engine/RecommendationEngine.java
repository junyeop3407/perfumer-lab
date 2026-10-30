package com.perfumerlab.scentstudio.engine;

import static com.perfumerlab.scentstudio.engine.EngineParams.*;

import com.perfumerlab.scentstudio.engine.CatalogModel.Accord;
import com.perfumerlab.scentstudio.engine.CatalogModel.Catalog;
import com.perfumerlab.scentstudio.engine.CatalogModel.Family;
import com.perfumerlab.scentstudio.engine.CatalogModel.Layer;
import com.perfumerlab.scentstudio.engine.CatalogModel.Note;
import com.perfumerlab.scentstudio.engine.CatalogModel.Perfume;
import com.perfumerlab.scentstudio.engine.CatalogModel.Tier;
import com.perfumerlab.scentstudio.engine.EngineTypes.AccordRatio;
import com.perfumerlab.scentstudio.engine.EngineTypes.BlendException;
import com.perfumerlab.scentstudio.engine.EngineTypes.BlendNote;
import com.perfumerlab.scentstudio.engine.EngineTypes.Grade;
import com.perfumerlab.scentstudio.engine.EngineTypes.MatchKind;
import com.perfumerlab.scentstudio.engine.EngineTypes.MatchedNote;
import com.perfumerlab.scentstudio.engine.EngineTypes.Recommendation;
import com.perfumerlab.scentstudio.engine.EngineTypes.SharedAccord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 엔진 (docs/design.md 8절). Python pipeline/engine_ref.py 와 계산 순서까지 똑같이 맞춘다
 * (골든 테스트가 점수 차 1e-9 이하를 확인한다). Spring·Android를 모른다.
 */
public final class RecommendationEngine {

    private final Catalog catalog;
    private final List<String> accords;
    private final Map<String, Integer> accordIndex = new HashMap<>();
    private final String[] accordFamily;
    private final Map<String, Integer> wheel = new HashMap<>();
    private final Map<String, Note> notes = new HashMap<>();
    private final List<Indexed> perfumes = new ArrayList<>();

    /** 향수마다 미리 계산해 두는 것 */
    private record Indexed(Perfume perfume, double[] vector, Set<String> top, Set<String> middle,
                           Set<String> base, Set<String> all) {
        Set<String> layer(Layer layer) {
            return switch (layer) {
                case TOP -> top;
                case MIDDLE -> middle;
                case BASE -> base;
            };
        }
    }

    public RecommendationEngine(Catalog catalog) {
        this.catalog = catalog;
        this.accords = catalog.accords().stream().map(Accord::key).toList();
        this.accordFamily = new String[accords.size()];
        for (int i = 0; i < accords.size(); i++) {
            accordIndex.put(accords.get(i), i);
            accordFamily[i] = catalog.accords().get(i).family();
        }
        for (Family f : catalog.families()) wheel.put(f.code(), f.wheelOrder());
        for (Note n : catalog.notes()) notes.put(n.key(), n);
        for (Perfume p : catalog.perfumes()) {
            Set<String> all = new HashSet<>(p.top());
            all.addAll(p.middle());
            all.addAll(p.base());
            perfumes.add(new Indexed(p, perfumeVector(p.accords()), new HashSet<>(p.top()),
                    new HashSet<>(p.middle()), new HashSet<>(p.base()), all));
        }
    }

    public Catalog catalog() { return catalog; }

    /** 8-1절: 목록 순서대로 5·4·3·2·1, 합 100 */
    public double[] perfumeVector(List<String> perfumeAccords) {
        int n = Math.min(perfumeAccords.size(), RANK_WEIGHTS.length);
        int total = 0;
        for (int i = 0; i < n; i++) total += RANK_WEIGHTS[i];
        double[] v = new double[accords.size()];
        for (int i = 0; i < n; i++) {
            v[accordIndex.get(perfumeAccords.get(i))] = 100.0 * RANK_WEIGHTS[i] / total;
        }
        return v;
    }

    public void validate(List<BlendNote> blend) {
        if (blend.isEmpty()) throw new BlendException("EMPTY_BLEND");
        for (Layer layer : Layer.values()) {
            long count = blend.stream().filter(b -> b.layer() == layer).count();
            if (count > MAX_PER_LAYER) throw new BlendException("LAYER_LIMIT");
        }
        if (blend.stream().map(BlendNote::noteKey).distinct().count() != blend.size()) {
            throw new BlendException("DUPLICATE_NOTE");
        }
        for (BlendNote b : blend) {
            if (b.layer() == null || Arrays.stream(INTENSITIES).noneMatch(x -> x == b.intensity())) {
                throw new BlendException("BAD_BLEND_NOTE");
            }
            Note note = notes.get(b.noteKey());
            if (note == null || note.tier() == Tier.HIDDEN) throw new BlendException("NOTE_NOT_FOUND");
        }
    }

    /** 8-3절: b = Σ 강도 × 층 가중 × 노트 프로필 */
    public double[] blendVector(List<BlendNote> blend) {
        double[] b = new double[accords.size()];
        for (BlendNote x : blend) {
            double[] prof = notes.get(x.noteKey()).profile();
            double s = x.intensity() * layerWeight(x.layer());
            for (int i = 0; i < prof.length; i++) b[i] += s * prof[i];
        }
        return b;
    }

    /** 화면용 비율: 반올림해서 1% 이상인 어코드만, 합 100이 되게 가장 큰 항목에서 맞춘다. 큰 순서 */
    public List<AccordRatio> displayRatios(List<BlendNote> blend) {
        double[] b = blendVector(blend);
        double total = sum(b);
        List<AccordRatio> shown = new ArrayList<>();
        if (total == 0) return shown;
        for (int i = 0; i < b.length; i++) {
            int p = (int) Math.floor(100.0 * b[i] / total + 0.5);
            if (p >= 1) shown.add(new AccordRatio(accords.get(i), p));
        }
        shown.sort(Comparator.comparingInt((AccordRatio r) -> -r.percent())
                .thenComparingInt(r -> accordIndex.get(r.accord())));
        int diff = 100 - shown.stream().mapToInt(AccordRatio::percent).sum();
        if (!shown.isEmpty() && diff != 0) {
            AccordRatio first = shown.get(0);
            shown.set(0, new AccordRatio(first.accord(), first.percent() + diff));
        }
        return shown;
    }

    /** 8-7절: EMPTY_LAYER:top|middle|base, ONE_FAMILY, DOMINANT, FAR_FAMILIES (이 순서) */
    public List<String> balanceHints(List<BlendNote> blend) {
        List<String> hints = new ArrayList<>();
        for (Layer layer : Layer.values()) {
            if (blend.stream().noneMatch(x -> x.layer() == layer)) hints.add("EMPTY_LAYER:" + layer.key());
        }
        Set<String> fams = new HashSet<>();
        for (BlendNote x : blend) fams.add(notes.get(x.noteKey()).family());
        if (blend.size() >= 2 && fams.size() == 1) hints.add("ONE_FAMILY");
        double[] b = blendVector(blend);
        double total = sum(b);
        if (total > 0 && Arrays.stream(b).max().orElse(0) / total >= DOMINANT_SHARE) hints.add("DOMINANT");
        if (fams.size() == 2) {
            String[] xy = fams.toArray(new String[0]);
            if (wheelDistance(xy[0], xy[1]) >= FAR_FAMILY_DISTANCE) hints.add("FAR_FAMILIES");
        }
        return hints;
    }

    public int wheelDistance(String x, String y) {
        int d = Math.abs(wheel.get(x) - wheel.get(y));
        return Math.min(d, wheel.size() - d);
    }

    /** 8-4~8-6절. disliked: 불호 노트 key, preferredFamilies: 선호 계열 code */
    public List<Recommendation> recommend(List<BlendNote> blend, Set<String> disliked,
                                          Set<String> preferredFamilies, int k) {
        validate(blend);
        double[] b = blendVector(blend);
        double bTotal = sum(b);
        int n = perfumes.size();
        Map<String, Double> weights = new HashMap<>();
        double wSum = 0;
        for (BlendNote x : blend) {
            double w = Math.log(1 + (double) n / notes.get(x.noteKey()).df());
            weights.put(x.noteKey(), w);
            wSum += w;
        }

        record Scored(Indexed p, double score, double accordSim, double noteMatch, List<MatchedNote> matched) {}
        List<Scored> scored = new ArrayList<>();
        for (Indexed p : perfumes) {
            if (!disjoint(p.all(), disliked)) continue;
            double accordSim = cosine(p.vector(), b);
            Set<String> fams = new HashSet<>();
            for (String key : p.all()) fams.add(notes.get(key).family());
            List<MatchedNote> matched = new ArrayList<>();
            double nm = 0;
            for (BlendNote x : blend) {
                double c;
                MatchKind kind;
                if (p.layer(x.layer()).contains(x.noteKey())) {
                    c = C_SAME_LAYER;
                    kind = MatchKind.SAME_LAYER;
                } else if (p.all().contains(x.noteKey())) {
                    c = C_OTHER_LAYER;
                    kind = MatchKind.OTHER_LAYER;
                } else if (fams.contains(notes.get(x.noteKey()).family())) {
                    c = C_SAME_FAMILY;
                    kind = MatchKind.SAME_FAMILY;
                } else {
                    c = 0;
                    kind = null;
                }
                nm += weights.get(x.noteKey()) * c;
                if (kind != null) matched.add(new MatchedNote(x.noteKey(), x.layer(), kind));
            }
            double noteMatch = nm / wSum;
            double score;
            if (!preferredFamilies.isEmpty()) {
                double pref = 0;
                double[] a = p.vector();
                for (int i = 0; i < a.length; i++) {
                    if (preferredFamilies.contains(accordFamily[i])) pref += a[i];
                }
                pref = pref / 100.0;
                score = W_ACCORD * accordSim + W_NOTE * noteMatch + W_PREF * pref;
            } else {
                score = (W_ACCORD * accordSim + W_NOTE * noteMatch) / (W_ACCORD + W_NOTE);
            }
            scored.add(new Scored(p, score, accordSim, noteMatch, matched));
        }

        scored.sort(Comparator.comparingLong((Scored s) -> -Math.round(s.score() * SCORE_QUANTUM))
                .thenComparingInt(s -> -s.p().perfume().ratingCount())
                .thenComparing(s -> s.p().perfume().key()));

        Set<String> blendKeys = new HashSet<>();
        for (BlendNote x : blend) blendKeys.add(x.noteKey());
        Map<String, Integer> perBrand = new HashMap<>();
        Set<String> groups = new HashSet<>();
        List<Recommendation> out = new ArrayList<>();
        for (Scored s : scored) {
            // 농도만 다른 같은 향수는 점수가 가장 높은 하나만
            if (groups.contains(s.p().perfume().group())) continue;
            String brand = s.p().perfume().brand();
            if (perBrand.getOrDefault(brand, 0) >= BRAND_CAP) continue;
            groups.add(s.p().perfume().group());
            perBrand.merge(brand, 1, Integer::sum);

            double[] a = s.p().vector();
            List<SharedAccord> shared = new ArrayList<>();
            for (int i = 0; i < a.length; i++) {
                shared.add(new SharedAccord(accords.get(i), a[i], 100.0 * b[i] / bTotal));
            }
            shared.sort(Comparator.comparingDouble((SharedAccord x) -> -Math.min(x.perfumePct(), x.blendPct()))
                    .thenComparingInt(x -> accordIndex.get(x.accord())));
            List<String> missing = new ArrayList<>();
            for (String key : s.p().all()) if (!blendKeys.contains(key)) missing.add(key);
            missing.sort(Comparator.comparingInt((String key) -> -notes.get(key).df())
                    .thenComparing(Comparator.naturalOrder()));

            out.add(new Recommendation(s.p().perfume().key(), s.score(), s.accordSim(), s.noteMatch(),
                    grade(s.score()), List.copyOf(shared.subList(0, Math.min(3, shared.size()))),
                    List.copyOf(s.matched()), List.copyOf(missing.subList(0, Math.min(3, missing.size())))));
            if (out.size() == k) break;
        }
        return out;
    }

    public static Grade grade(double score) {
        if (score >= GRADE_VERY_SIMILAR) return Grade.VERY_SIMILAR;
        if (score >= GRADE_SIMILAR) return Grade.SIMILAR;
        return Grade.REFERENCE;
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        for (double x : a) na += x * x;
        for (double y : b) nb += y * y;
        na = Math.sqrt(na);
        nb = Math.sqrt(nb);
        return na == 0 || nb == 0 ? 0.0 : dot / (na * nb);
    }

    private static double sum(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s;
    }

    private static double layerWeight(Layer layer) {
        return switch (layer) {
            case TOP -> LAYER_WEIGHT_TOP;
            case MIDDLE -> LAYER_WEIGHT_MIDDLE;
            case BASE -> LAYER_WEIGHT_BASE;
        };
    }

    private static boolean disjoint(Set<String> a, Set<String> b) {
        for (String x : b) if (a.contains(x)) return false;
        return true;
    }
}
