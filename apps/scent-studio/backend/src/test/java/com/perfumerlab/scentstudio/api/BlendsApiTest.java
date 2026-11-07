package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 5: 조합 CRUD (db_design.md ②·8절, server_design.md 2·8절, 승인 D2·D5).
 * - 남의 조합 id(또는 없는 id, uuid 모양이 아닌 id)로 읽기·바꾸기·지우기 → 404 NOT_FOUND (있는지 여부를 알려 주지 않음)
 * - 카탈로그에 없는 노트·계열 key → 400 UNKNOWN_KEY, 모양이 틀리면 400 BAD_REQUEST
 * - 1인당 100개. 사용자 단위 잠금 트랜잭션 안에서 세고 넣는다 → 동시에 20번 보내도 100개를 넘지 않음. 넘으면 409 LIMIT_REACHED
 * - 쓰기는 지금 버전 동의가 있어야(403 CONSENT_REQUIRED), 읽기는 동의 없어도 된다.
 */
class BlendsApiTest extends MeApiTestBase {

    static final String PATH = "/api/v1/blends";
    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    static String blend(String name, String families, String notes) {
        return "{\"name\":" + name + ",\"families\":" + families + ",\"notes\":" + notes + "}";
    }

    static String note(String key, String layer, String strength) {
        return "{\"noteKey\":\"" + key + "\",\"layer\":\"" + layer + "\",\"strength\":\"" + strength + "\"}";
    }

    static final String ONE_NOTE = "[" + note("rose", "top", "medium") + "]";

    static String idOf(HttpResponse<String> r) {
        Matcher m = ID.matcher(r.body());
        assertTrue(m.find(), r.body());
        return m.group(1);
    }

    /** DB에 조합 n개를 바로 넣는다(요청 제한과 섞이지 않게) */
    static void prefill(UUID user, int n) {
        TestAuth.admin("insert into app.blends (user_id, name, notes) select ?, 'b' || g,"
                + " '[{\"note_key\":\"rose\",\"layer\":\"top\",\"strength\":\"weak\"}]'::jsonb from generate_series(1, ?) g", user, n);
    }

    static long count(UUID user) throws Exception {
        return dbCount("select count(*) from app.blends where user_id = ?", user);
    }

    // ---------- 만들기·읽기·바꾸기·지우기 ----------

    @Test
    void createThenReadListAndOne() throws Exception {
        TestAuth.User u = consentedUser();
        String notes = "[" + note("bergamot", "top", "strong") + "," + note("rose", "middle", "medium") + "," + note("musk", "base", "weak") + "]";
        HttpResponse<String> r = postAs(u, PATH, blend("\"첫 조합\"", "[\"floral\",\"woody\"]", notes));
        assertEquals(201, r.statusCode(), r.body());
        String id = idOf(r);
        assertTrue(r.body().contains("\"name\":\"첫 조합\""), r.body());
        assertTrue(r.body().contains("\"families\":[\"floral\",\"woody\"]"), r.body());
        assertTrue(r.body().contains("\"notes\":" + notes), "노트는 보낸 순서·모양 그대로: " + r.body());
        assertTrue(r.body().contains("\"createdAt\":") && r.body().contains("\"updatedAt\":"), r.body());

        HttpResponse<String> one = getAs(u, PATH + "/" + id);
        assertEquals(200, one.statusCode(), one.body());
        assertEquals(r.body(), one.body(), "하나 읽기 = 만들 때 응답");

        HttpResponse<String> list = getAs(u, PATH);
        assertEquals(200, list.statusCode());
        assertEquals("{\"blends\":[" + r.body() + "]}", list.body());
    }

    @Test
    void emptyListBeforeAnything() throws Exception {
        HttpResponse<String> r = getAs(TestAuth.newUser(), PATH);
        assertEquals(200, r.statusCode());
        assertEquals("{\"blends\":[]}", r.body());
    }

    @Test
    void listIsNewestFirst() throws Exception {
        TestAuth.User u = consentedUser();
        String a = idOf(postAs(u, PATH, blend("\"A\"", "[]", ONE_NOTE)));
        String b = idOf(postAs(u, PATH, blend("\"B\"", "[]", ONE_NOTE)));
        String body = getAs(u, PATH).body();
        assertTrue(body.indexOf(b) < body.indexOf(a), "나중에 만든 것이 먼저: " + body);
    }

    @Test
    void updateReplacesEverything() throws Exception {
        TestAuth.User u = consentedUser();
        String id = idOf(postAs(u, PATH, blend("\"처음\"", "[\"floral\"]", ONE_NOTE)));
        String notes = "[" + note("vanilla", "base", "strong") + "]";
        HttpResponse<String> r = putAs(u, PATH + "/" + id, blend("\"바꿈\"", "[]", notes));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals(id, idOf(r));
        assertTrue(r.body().contains("\"name\":\"바꿈\",\"families\":[],\"notes\":" + notes), r.body());
        assertEquals(r.body(), getAs(u, PATH + "/" + id).body());
        assertEquals(1, count(u.id()), "새 줄이 생기지 않음");
    }

    @Test
    void deleteThenGone() throws Exception {
        TestAuth.User u = consentedUser();
        String id = idOf(postAs(u, PATH, blend("\"지울 것\"", "[]", ONE_NOTE)));
        assertEquals(204, deleteAs(u, PATH + "/" + id).statusCode());
        assertError(getAs(u, PATH + "/" + id), 404, "NOT_FOUND");
        assertError(deleteAs(u, PATH + "/" + id), 404, "NOT_FOUND");
        assertEquals(0, count(u.id()));
    }

    // ---------- 남의 id → 404 ----------

    @Test
    void othersBlendIs404ForReadUpdateDelete() throws Exception {
        TestAuth.User a = consentedUser(), b = consentedUser();
        HttpResponse<String> made = postAs(a, PATH, blend("\"A의 조합\"", "[]", ONE_NOTE));
        String id = idOf(made);
        HttpResponse<String> notMine = getAs(b, PATH + "/" + id);
        assertError(notMine, 404, "NOT_FOUND");
        assertError(putAs(b, PATH + "/" + id, blend("\"뺏기\"", "[]", ONE_NOTE)), 404, "NOT_FOUND");
        assertError(deleteAs(b, PATH + "/" + id), 404, "NOT_FOUND");
        assertEquals("{\"blends\":[]}", getAs(b, PATH).body(), "목록에도 안 보임");
        assertEquals(made.body(), getAs(a, PATH + "/" + id).body(), "A의 조합은 그대로");
        // 없는 id와 응답이 같다(있는지 여부를 알 수 없음)
        assertEquals(notMine.body(), getAs(b, PATH + "/" + UUID.randomUUID()).body());
    }

    @ParameterizedTest(name = "uuid가 아닌 id: {0}")
    @ValueSource(strings = {"abc", "1", "00000000-0000-0000-0000-00000000000g", "%27%20or%201=1"})
    void malformedIdIs404(String id) throws Exception {
        TestAuth.User u = consentedUser();
        assertError(getAs(u, PATH + "/" + id), 404, "NOT_FOUND");
        assertError(putAs(u, PATH + "/" + id, blend("\"x\"", "[]", ONE_NOTE)), 404, "NOT_FOUND");
        assertError(deleteAs(u, PATH + "/" + id), 404, "NOT_FOUND");
    }

    // ---------- 검사 ----------

    @Test
    void unknownKeysAre400() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, blend("\"x\"", "[]", "[" + note("no-such-note", "top", "weak") + "]")), 400, "UNKNOWN_KEY");
        assertError(postAs(u, PATH, blend("\"x\"", "[\"jazz\"]", ONE_NOTE)), 400, "UNKNOWN_KEY");
        assertError(postAs(u, PATH, blend("\"x\"", "[]", "[" + note("floral", "top", "weak") + "]")), 400, "UNKNOWN_KEY"); // 계열 key는 노트가 아님
        assertError(postAs(u, PATH, blend("\"x\"", "[\"rose\"]", ONE_NOTE)), 400, "UNKNOWN_KEY"); // 노트 key는 계열이 아님
        String id = idOf(postAs(u, PATH, blend("\"x\"", "[]", ONE_NOTE)));
        assertError(putAs(u, PATH + "/" + id, blend("\"x\"", "[]", "[" + note("no-such-note", "top", "weak") + "]")), 400, "UNKNOWN_KEY");
        assertEquals(1, count(u.id()), "모르는 key로는 아무것도 저장 안 됨");
    }

    @ParameterizedTest(name = "잘못된 조합: {0}")
    @ValueSource(strings = {
            "{\"name\":\"\",\"families\":[],\"notes\":NOTE1}",                    // 빈 이름
            "{\"name\":\"   \",\"families\":[],\"notes\":NOTE1}",                 // 공백만
            "{\"name\":\"\\u3000\\u200b\",\"families\":[],\"notes\":NOTE1}",      // 보이지 않는 글자만
            "{\"name\":\"a\\nb\",\"families\":[],\"notes\":NOTE1}",               // 제어 문자
            "{\"name\":\"a\\u0000b\",\"families\":[],\"notes\":NOTE1}",           // NUL
            "{\"name\":\"a\\ud800b\",\"families\":[],\"notes\":NOTE1}",           // 짝 없는 서로게이트
            "{\"name\":\"NAME41\",\"families\":[],\"notes\":NOTE1}",              // 41자
            "{\"name\":null,\"families\":[],\"notes\":NOTE1}",
            "{\"families\":[],\"notes\":NOTE1}",
            "{\"name\":1,\"families\":[],\"notes\":NOTE1}",
            "{\"name\":\"x\",\"families\":[],\"notes\":[]}",                      // 노트 0개
            "{\"name\":\"x\",\"families\":[],\"notes\":null}",
            "{\"name\":\"x\",\"families\":[]}",
            "{\"name\":\"x\",\"families\":[],\"notes\":\"rose\"}",
            "{\"name\":\"x\",\"families\":[],\"notes\":[null]}",
            "{\"name\":\"x\",\"families\":[],\"notes\":TEN}",                     // 10개
            "{\"name\":\"x\",\"families\":[],\"notes\":FOUR_TOP}",                // 한 층 4개
            "{\"name\":\"x\",\"families\":[],\"notes\":DUP}",                     // 같은 노트 두 번
            "{\"name\":\"x\",\"families\":[],\"notes\":[{\"noteKey\":\"rose\",\"layer\":\"heart\",\"strength\":\"weak\"}]}",
            "{\"name\":\"x\",\"families\":[],\"notes\":[{\"noteKey\":\"rose\",\"layer\":\"top\",\"strength\":\"huge\"}]}",
            "{\"name\":\"x\",\"families\":[],\"notes\":[{\"noteKey\":\"rose\",\"layer\":\"top\"}]}",
            "{\"name\":\"x\",\"families\":[],\"notes\":[{\"noteKey\":null,\"layer\":\"top\",\"strength\":\"weak\"}]}",
            "{\"name\":\"x\",\"families\":[],\"notes\":[{\"noteKey\":\" rose\",\"layer\":\"top\",\"strength\":\"weak\"}]}",
            "{\"name\":\"x\",\"families\":ELEVEN,\"notes\":NOTE1}",               // 계열 11개
            "{\"name\":\"x\",\"families\":[\"floral\",\"floral\"],\"notes\":NOTE1}",
            "{\"name\":\"x\",\"families\":null,\"notes\":NOTE1}",
            "{\"name\":\"x\",\"families\":[null],\"notes\":NOTE1}",
            "[]", "not json"
    })
    void badShapesAre400(String template) throws Exception {
        String body = template.replace("NOTE1", ONE_NOTE)
                .replace("NAME41", "가".repeat(41))
                .replace("TEN", notesJson(10))
                .replace("FOUR_TOP", "[" + note("rose", "top", "weak") + "," + note("musk", "top", "weak") + ","
                        + note("vanilla", "top", "weak") + "," + note("bergamot", "top", "weak") + "]")
                .replace("DUP", "[" + note("rose", "top", "weak") + "," + note("rose", "base", "weak") + "]")
                .replace("ELEVEN", "[\"citrus\",\"fruity\",\"floral\",\"soft\",\"sweet\",\"spicy\",\"amber\",\"woody\",\"green\",\"fresh\",\"x\"]");
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, body), 400, "BAD_REQUEST");
        assertEquals(0, count(u.id()));
    }

    /** 층마다 3개씩 9개 + 10번째 */
    static String notesJson(int n) {
        String[] layers = {"top", "middle", "base"};
        StringBuilder s = new StringBuilder("[");
        for (int i = 1; i <= n; i++) s.append(i > 1 ? "," : "").append(note(String.format("note-%03d", i), layers[(i - 1) % 3], "medium"));
        return s.append("]").toString();
    }

    @Test
    void edgesThatAreAllowed() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(201, postAs(u, PATH, blend("\"" + "가".repeat(40) + "\"", "[]", ONE_NOTE)).statusCode(), "한글 40자");
        assertEquals(201, postAs(u, PATH, blend("\"" + "😀".repeat(40) + "\"", "[]", ONE_NOTE)).statusCode(), "이모지 40자(글자 수로 셈)");
        assertEquals(201, postAs(u, PATH, blend("\"x\"", "[]", notesJson(9))).statusCode(), "노트 9개(층마다 3개)");
        String ten = "[\"citrus\",\"fruity\",\"floral\",\"soft\",\"sweet\",\"spicy\",\"amber\",\"woody\",\"green\",\"fresh\"]";
        assertEquals(201, postAs(u, PATH, blend("\" 앞뒤 공백 \"", ten, ONE_NOTE)).statusCode(), "계열 10개, 이름 안 공백");
        assertError(postAs(u, PATH, blend("\"" + "😀".repeat(41) + "\"", "[]", ONE_NOTE)), 400, "BAD_REQUEST");
    }

    // ---------- 동의 ----------

    @Test
    void writesNeedConsentReadsDoNot() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        prefill(u.id(), 1);
        String id = TestDbId.blend(u.id());
        assertError(postAs(u, PATH, blend("\"x\"", "[]", ONE_NOTE)), 403, "CONSENT_REQUIRED");
        assertError(putAs(u, PATH + "/" + id, blend("\"x\"", "[]", ONE_NOTE)), 403, "CONSENT_REQUIRED");
        assertError(deleteAs(u, PATH + "/" + id), 403, "CONSENT_REQUIRED");
        assertEquals(200, getAs(u, PATH).statusCode());
        assertEquals(200, getAs(u, PATH + "/" + id).statusCode());
        assertEquals(1, count(u.id()));
    }

    // ---------- 1인당 100개 ----------

    @Test
    void limit100() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 99);
        assertEquals(201, postAs(u, PATH, blend("\"백 번째\"", "[]", ONE_NOTE)).statusCode());
        assertError(postAs(u, PATH, blend("\"백한 번째\"", "[]", ONE_NOTE)), 409, "LIMIT_REACHED");
        assertEquals(100, count(u.id()));
        String any = TestDbId.blend(u.id());
        assertEquals(200, putAs(u, PATH + "/" + any, blend("\"고치기는 됨\"", "[]", ONE_NOTE)).statusCode(), "가득 차도 고치기는 됨");
        assertEquals(204, deleteAs(u, PATH + "/" + any).statusCode());
        assertEquals(201, postAs(u, PATH, blend("\"하나 지우면 다시\"", "[]", ONE_NOTE)).statusCode());
    }

    @Test
    void limitIsPerUser() throws Exception {
        TestAuth.User full = consentedUser(), other = consentedUser();
        prefill(full.id(), 100);
        assertEquals(201, postAs(other, PATH, blend("\"x\"", "[]", ONE_NOTE)).statusCode(), "남이 가득 차도 나는 됨");
    }

    @Test
    void twentyAtOnceNeverExceed100() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 90);
        List<HttpResponse<String>> rs = together(20, i -> () -> postAs(u, PATH, blend("\"동시 " + i + "\"", "[]", ONE_NOTE)));
        assertEquals(10, countStatus(rs, 201), "10개만 들어감");
        assertEquals(10, countStatus(rs, 409), "나머지는 LIMIT_REACHED");
        rs.stream().filter(r -> r.statusCode() == 409).forEach(r -> assertError(r, 409, "LIMIT_REACHED"));
        assertEquals(100, count(u.id()));
    }

    @Test
    void twentyAtOnceAt99OnlyOneWins() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 99);
        List<HttpResponse<String>> rs = together(20, i -> () -> postAs(u, PATH, blend("\"동시 " + i + "\"", "[]", ONE_NOTE)));
        assertEquals(1, countStatus(rs, 201));
        assertEquals(19, countStatus(rs, 409));
        assertEquals(100, count(u.id()));
    }

    /** 테스트 DB에서 그 사용자의 조합 id 하나 */
    static final class TestDbId {
        static String blend(UUID user) throws Exception {
            return Bundle4ReviewFixesTest.TestDbQuery.text("select id::text from app.blends where user_id = ? limit 1", user);
        }
    }
}
