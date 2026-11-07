package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 5: 찜 (db_design.md ③·8절, 승인 D3). 향수는 key로 가리킨다(Parfumo 주소의 /Perfumes/ 뒤, 예 Brand:perfume).
 * - PUT /favorites/{key}: 찜. 이미 찜했으면 오류 없이 그대로 성공(204). 카탈로그에 없는 key → 400 UNKNOWN_KEY
 * - DELETE /favorites/{key}: 찜 풀기. 없어도 204(카탈로그에서 빠진 향수도 풀 수 있게 카탈로그는 확인하지 않음)
 * - 1인당 300개(사용자 단위 잠금), 넘으면 409 LIMIT_REACHED. 이미 찜한 것을 다시 찜하는 건 가득 차도 성공
 */
class FavoritesApiTest extends MeApiTestBase {

    static final String PATH = "/api/v1/favorites";

    static void prefill(UUID user, int n) {
        TestAuth.admin("insert into app.favorites (user_id, perfume_key) select ?, 'Old:perfume-' || g from generate_series(1, ?) g", user, n);
    }

    static long count(UUID user) throws Exception {
        return dbCount("select count(*) from app.favorites where user_id = ?", user);
    }

    static String key(int i) {
        return String.format("Brand:perfume-%03d", i);
    }

    @Test
    void favoriteThenList() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals("{\"favorites\":[]}", getAs(u, PATH).body());
        HttpResponse<String> r = putAs(u, PATH + "/Le_Re_Noir:455-tabac-ecarlate", null);
        assertEquals(204, r.statusCode(), r.body());
        String list = getAs(u, PATH).body();
        assertTrue(list.startsWith("{\"favorites\":[{\"perfumeKey\":\"Le_Re_Noir:455-tabac-ecarlate\",\"createdAt\":\""), list);
    }

    @Test
    void favoritingTwiceIsStillSuccess() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(204, putAs(u, PATH + "/Brand:perfume", null).statusCode());
        assertEquals(204, putAs(u, PATH + "/Brand:perfume", null).statusCode(), "다시 찜해도 오류 없음");
        assertEquals(1, count(u.id()));
    }

    @Test
    void listIsNewestFirst() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH + "/" + key(1), null);
        TestAuth.admin("update app.favorites set created_at = created_at - interval '1 minute' where user_id = ?", u.id());
        putAs(u, PATH + "/" + key(2), null);
        String body = getAs(u, PATH).body();
        assertTrue(body.indexOf(key(2)) < body.indexOf(key(1)), body);
    }

    @Test
    void unfavorite() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH + "/Brand:perfume", null);
        assertEquals(204, deleteAs(u, PATH + "/Brand:perfume").statusCode());
        assertEquals("{\"favorites\":[]}", getAs(u, PATH).body());
        assertEquals(204, deleteAs(u, PATH + "/Brand:perfume").statusCode(), "없어도 그대로 성공");
    }

    @Test
    void canUnfavoriteKeyThatLeftTheCatalog() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 1); // Old:perfume-1 (카탈로그에 없음)
        assertTrue(getAs(u, PATH).body().contains("Old:perfume-1"), "목록에는 그대로 보임");
        assertEquals(204, deleteAs(u, PATH + "/Old:perfume-1").statusCode());
        assertEquals(0, count(u.id()));
    }

    @Test
    void unknownPerfumeIs400() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(putAs(u, PATH + "/Brand:no-such-perfume", null), 400, "UNKNOWN_KEY");
        assertError(putAs(u, PATH + "/rose", null), 400, "UNKNOWN_KEY"); // 노트 key는 향수가 아님
        assertEquals(0, count(u.id()));
    }

    @ParameterizedTest(name = "잘못된 key 모양: {0}")
    @ValueSource(strings = {"%20Brand:perfume", "Brand:perfume%20", "Brand:%E2%9C%93", "TOO_LONG"})
    void badKeyShapeIs400(String k) throws Exception {
        TestAuth.User u = consentedUser();
        String path = PATH + "/" + (k.equals("TOO_LONG") ? "a".repeat(201) : k);
        assertError(putAs(u, path, null), 400, "BAD_REQUEST");
        assertError(deleteAs(u, path), 400, "BAD_REQUEST");
    }

    @Test
    void othersAreSeparate() throws Exception {
        TestAuth.User a = consentedUser(), b = consentedUser();
        putAs(a, PATH + "/Brand:perfume", null);
        assertEquals("{\"favorites\":[]}", getAs(b, PATH).body(), "남의 찜은 안 보임");
        assertEquals(204, deleteAs(b, PATH + "/Brand:perfume").statusCode());
        assertEquals(1, count(a.id()), "b가 풀어도 a의 찜은 그대로");
    }

    @Test
    void writesNeedConsentReadsDoNot() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        prefill(u.id(), 1);
        assertError(putAs(u, PATH + "/Brand:perfume", null), 403, "CONSENT_REQUIRED");
        assertError(deleteAs(u, PATH + "/Old:perfume-1"), 403, "CONSENT_REQUIRED");
        assertEquals(200, getAs(u, PATH).statusCode());
        assertEquals(1, count(u.id()));
    }

    // ---------- 1인당 300개 ----------

    @Test
    void limit300() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 299);
        assertEquals(204, putAs(u, PATH + "/" + key(1), null).statusCode(), "300번째");
        assertError(putAs(u, PATH + "/" + key(2), null), 409, "LIMIT_REACHED");
        assertEquals(204, putAs(u, PATH + "/" + key(1), null).statusCode(), "가득 차도 이미 찜한 것은 다시 찜해도 성공");
        assertEquals(300, count(u.id()));
        assertEquals(204, deleteAs(u, PATH + "/Old:perfume-1").statusCode());
        assertEquals(204, putAs(u, PATH + "/" + key(2), null).statusCode(), "하나 풀면 다시");
    }

    @Test
    void twentyAtOnceNeverExceed300() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 290);
        List<HttpResponse<String>> rs = together(20, i -> () -> putAs(u, PATH + "/" + key(i + 1), null));
        assertEquals(10, countStatus(rs, 204), "10개만 들어감");
        assertEquals(10, countStatus(rs, 409));
        rs.stream().filter(r -> r.statusCode() == 409).forEach(r -> assertError(r, 409, "LIMIT_REACHED"));
        assertEquals(300, count(u.id()));
    }

    @Test
    void sameKeyTwentyAtOnceAllSucceed() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 299);
        List<HttpResponse<String>> rs = together(20, i -> () -> putAs(u, PATH + "/Brand:perfume", null));
        assertEquals(20, countStatus(rs, 204), "같은 향수 동시 찜: 모두 성공, 한 줄만");
        assertEquals(300, count(u.id()));
    }
}
