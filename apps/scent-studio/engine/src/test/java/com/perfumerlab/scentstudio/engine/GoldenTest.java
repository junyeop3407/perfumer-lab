package com.perfumerlab.scentstudio.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.perfumerlab.scentstudio.engine.CatalogModel.Layer;
import com.perfumerlab.scentstudio.engine.EngineTypes.AccordRatio;
import com.perfumerlab.scentstudio.engine.EngineTypes.BlendException;
import com.perfumerlab.scentstudio.engine.EngineTypes.BlendNote;
import com.perfumerlab.scentstudio.engine.EngineTypes.MatchedNote;
import com.perfumerlab.scentstudio.engine.EngineTypes.Recommendation;
import com.perfumerlab.scentstudio.engine.EngineTypes.SharedAccord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

/**
 * 골든 테스트 (QA팀 추가 테스트): Python 참조 엔진(pipeline/engine_ref.py)이 만든 기대 결과
 * (tests/golden/recommend_cases.json)와 순위가 같고 점수 차가 1e-9 이하인지 확인한다.
 */
public class GoldenTest {

    private static final double EPS = 1e-9;
    private final RecommendationEngine engine = new RecommendationEngine(TestCatalogs.fixtureCatalog());
    private final JsonObject golden = TestCatalogs.golden();

    @Test
    public void 추천_결과가_Python과_같다() {
        for (JsonElement e : golden.getAsJsonArray("cases")) {
            JsonObject c = e.getAsJsonObject();
            String name = c.get("name").getAsString();
            List<Recommendation> recs = engine.recommend(blend(c), new HashSet<>(TestCatalogs.strings(c.getAsJsonArray("disliked"))),
                    new HashSet<>(TestCatalogs.strings(c.getAsJsonArray("preferred"))), c.get("k").getAsInt());
            var expected = c.getAsJsonArray("recommendations");
            assertEquals(name + ": 개수", expected.size(), recs.size());
            for (int i = 0; i < recs.size(); i++) {
                JsonObject x = expected.get(i).getAsJsonObject();
                Recommendation r = recs.get(i);
                String at = name + " " + (i + 1) + "위";
                assertEquals(at + " 향수", x.get("perfumeKey").getAsString(), r.perfumeKey());
                assertEquals(at + " 점수", x.get("score").getAsDouble(), r.score(), EPS);
                assertEquals(at + " 어코드 유사도", x.get("accordSim").getAsDouble(), r.accordSim(), EPS);
                assertEquals(at + " 노트 일치도", x.get("noteMatch").getAsDouble(), r.noteMatch(), EPS);
                assertEquals(at + " 등급", x.get("grade").getAsString(), r.grade().name());

                var shared = x.getAsJsonArray("sharedAccords");
                assertEquals(at + " 공통 어코드 수", shared.size(), r.sharedAccords().size());
                for (int j = 0; j < shared.size(); j++) {
                    JsonObject s = shared.get(j).getAsJsonObject();
                    SharedAccord a = r.sharedAccords().get(j);
                    assertEquals(at + " 공통 어코드", s.get("accord").getAsString(), a.accord());
                    assertEquals(at + " 향수 %", s.get("perfumePct").getAsDouble(), a.perfumePct(), EPS);
                    assertEquals(at + " 조합 %", s.get("blendPct").getAsDouble(), a.blendPct(), EPS);
                }
                List<String> matched = new ArrayList<>();
                for (MatchedNote m : r.matchedNotes()) {
                    matched.add(m.noteKey() + "/" + m.layer().key() + "/" + m.match().name().toLowerCase(Locale.ROOT));
                }
                List<String> expectedMatched = new ArrayList<>();
                for (JsonElement m : x.getAsJsonArray("matchedNotes")) {
                    JsonObject o = m.getAsJsonObject();
                    expectedMatched.add(o.get("noteKey").getAsString() + "/" + o.get("layer").getAsString() + "/" + o.get("match").getAsString());
                }
                assertEquals(at + " 일치 노트", expectedMatched, matched);
                assertEquals(at + " 빠진 노트", TestCatalogs.strings(x.getAsJsonArray("missingNotes")), r.missingNotes());
            }
        }
    }

    @Test
    public void 화면_비율과_균형_안내가_Python과_같다() {
        for (JsonElement e : golden.getAsJsonArray("cases")) {
            JsonObject c = e.getAsJsonObject();
            String name = c.get("name").getAsString();
            List<String> expected = new ArrayList<>();
            for (JsonElement r : c.getAsJsonArray("displayRatios")) {
                expected.add(r.getAsJsonObject().get("accord").getAsString() + "=" + r.getAsJsonObject().get("percent").getAsInt());
            }
            List<String> actual = new ArrayList<>();
            for (AccordRatio r : engine.displayRatios(blend(c))) actual.add(r.accord() + "=" + r.percent());
            assertEquals(name + ": 화면 비율", expected, actual);
            assertEquals(name + ": 균형 안내", TestCatalogs.strings(c.getAsJsonArray("balanceHints")), engine.balanceHints(blend(c)));
        }
    }

    @Test
    public void 잘못된_조합은_같은_오류() {
        for (JsonElement e : golden.getAsJsonArray("errors")) {
            JsonObject c = e.getAsJsonObject();
            try {
                engine.recommend(blend(c), new HashSet<>(), new HashSet<>(), 10);
                fail(c.get("name").getAsString() + ": 오류가 나지 않음");
            } catch (BlendException ex) {
                assertEquals(c.get("name").getAsString(), c.get("error").getAsString(), ex.code());
            }
        }
    }

    static List<BlendNote> blend(JsonObject c) {
        List<BlendNote> out = new ArrayList<>();
        for (JsonElement e : c.getAsJsonArray("blend")) {
            JsonObject b = e.getAsJsonObject();
            out.add(new BlendNote(b.get("noteKey").getAsString(), Layer.of(b.get("layer").getAsString()), b.get("intensity").getAsDouble()));
        }
        return out;
    }
}
