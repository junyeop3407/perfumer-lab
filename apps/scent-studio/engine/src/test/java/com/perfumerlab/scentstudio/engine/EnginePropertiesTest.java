package com.perfumerlab.scentstudio.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.perfumerlab.scentstudio.engine.CatalogModel.Layer;
import com.perfumerlab.scentstudio.engine.CatalogModel.Perfume;
import com.perfumerlab.scentstudio.engine.EngineTypes.AccordRatio;
import com.perfumerlab.scentstudio.engine.EngineTypes.BlendException;
import com.perfumerlab.scentstudio.engine.EngineTypes.BlendNote;
import com.perfumerlab.scentstudio.engine.EngineTypes.Recommendation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** 엔진 성질 테스트 (QA팀 추가 테스트, docs/design.md 14절) */
public class EnginePropertiesTest {

    private final RecommendationEngine engine = new RecommendationEngine(TestCatalogs.fixtureCatalog());
    private final Map<String, Perfume> byKey = new HashMap<>();

    {
        for (Perfume p : engine.catalog().perfumes()) byKey.put(p.key(), p);
    }

    private static final List<BlendNote> A3 = List.of(
            new BlendNote("lemon", Layer.TOP, 1.5),
            new BlendNote("rose", Layer.MIDDLE, 1.0),
            new BlendNote("sandalwood", Layer.BASE, 0.5));

    @Test
    public void 화면_비율의_합은_100() {
        assertEquals(100, engine.displayRatios(A3).stream().mapToInt(AccordRatio::percent).sum());
    }

    @Test
    public void 불호_노트가_든_향수는_절대_나오지_않는다() {
        for (String disliked : List.of("patchouli", "musk", "sandalwood", "rose")) {
            for (Recommendation r : engine.recommend(A3, Set.of(disliked), Set.of(), 100)) {
                Perfume p = byKey.get(r.perfumeKey());
                assertFalse(disliked + " 가 든 " + p.name(),
                        p.top().contains(disliked) || p.middle().contains(disliked) || p.base().contains(disliked));
            }
        }
    }

    @Test
    public void 같은_입력이면_같은_결과() {
        assertEquals(engine.recommend(A3, Set.of(), Set.of(), 10), engine.recommend(A3, Set.of(), Set.of(), 10));
    }

    @Test
    public void 한_브랜드는_최대_2개() {
        Map<String, Integer> count = new HashMap<>();
        for (Recommendation r : engine.recommend(A3, Set.of(), Set.of(), 100)) {
            count.merge(byKey.get(r.perfumeKey()).brand(), 1, Integer::sum);
        }
        count.forEach((brand, n) -> assertTrue(brand + " " + n + "개", n <= EngineParams.BRAND_CAP));
    }

    @Test
    public void 농도만_다른_같은_향수는_하나만() {
        // 가짜 데이터의 T041은 T001(Lumière d'Agrumes)의 EDP 버전이다
        List<String> keys = engine.recommend(A3, Set.of(), Set.of(), 100).stream().map(Recommendation::perfumeKey).toList();
        assertTrue("T001이 있어야 함", keys.contains("T001"));
        assertFalse("T041이 함께 나오면 안 됨", keys.contains("T041"));
        Set<String> groups = new java.util.HashSet<>();
        for (String k : keys) assertTrue("묶음 중복: " + k, groups.add(byKey.get(k).group()));
    }

    @Test
    public void 점수는_내림차순이고_0과_1_사이() {
        List<Recommendation> recs = engine.recommend(A3, Set.of(), Set.of("floral"), 100);
        for (int i = 0; i < recs.size(); i++) {
            assertTrue(recs.get(i).score() >= 0 && recs.get(i).score() <= 1);
            if (i > 0) assertTrue(recs.get(i - 1).score() >= recs.get(i).score() - 1e-9);
        }
    }

    @Test(expected = BlendException.class)
    public void 노트가_없으면_오류() {
        engine.recommend(List.of(), Set.of(), Set.of(), 10);
    }

    @Test
    public void 실제_크기_카탈로그에서도_빠르다() {
        // 가짜 카탈로그를 1.4만 개로 늘려 계산 시간을 잰다 (기준: 폰 300ms, PC에서는 훨씬 빨라야 함)
        var c = engine.catalog();
        var many = new java.util.ArrayList<Perfume>();
        for (int i = 0; i < 400; i++) {
            for (Perfume p : c.perfumes()) {
                many.add(new Perfume(p.key() + "-" + i, p.brand() + i, p.name(), p.year(), p.concentration(),
                        p.rating(), p.ratingCount(), p.accords(), p.top(), p.middle(), p.base(), p.group() + i));
            }
        }
        var big = new RecommendationEngine(new CatalogModel.Catalog(c.version(), c.engineVersion(), c.sourceName(),
                c.sourceNote(), c.families(), c.accords(), c.notes(), many));
        big.recommend(A3, Set.of(), Set.of(), 10); // 데우기
        long t = System.nanoTime();
        big.recommend(A3, Set.of("patchouli"), Set.of("floral"), 10);
        long ms = (System.nanoTime() - t) / 1_000_000;
        assertTrue(many.size() + "개 계산에 " + ms + "ms", ms < 150);
    }
}
