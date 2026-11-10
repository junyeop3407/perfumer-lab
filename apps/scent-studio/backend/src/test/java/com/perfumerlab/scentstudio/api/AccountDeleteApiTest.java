package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 8: 계정 삭제 DELETE /api/v1/me (db_design.md 4-1, server_design.md 2·4절, plan_stage4.md 묶음 8, 승인 G1·G2).
 * - Supabase 관리 API로 계정 삭제 → DB 외래 키 규칙이 정리 → 서버가 "남은 줄 0" 확인 → 204
 * - 관리 API가 실패하면 아무것도 안 지워지고 503(다시 시도 가능). 키는 응답·로그에 안 나옴
 * - 삭제가 끝나면 그 사용자의 세션 확인 보관(30초)을 바로 지움 → 같은 토큰 바로 401 (사장 지시 2026-11-07)
 * - 동의 없어도 지울 수 있음, 시간당 3회
 * - 대시보드에서 지운 계정의 토큰이 30초 보관 중에 저장해도 500이 아니라 401 (남은 것 4)
 */
@ExtendWith(OutputCaptureExtension.class)
class AccountDeleteApiTest extends MeApiTestBase {

    static final String PATH = "/api/v1/me";

    @DynamicPropertySource
    static void admin(DynamicPropertyRegistry r) {
        r.add("scent.admin.url", FakeAuthAdmin::url);
        r.add("scent.admin.secret-key", () -> FakeStorage.SECRET);
        r.add("scent.admin.timeout", () -> "2s");
    }

    @BeforeEach
    void reset() {
        FakeAuthAdmin.reset();
    }

    /** 표마다 이 사용자 줄 수 (관리자 계정으로 셈) */
    static long rows(UUID u) throws Exception {
        return dbCount("select (select count(*) from app.user_preferences where user_id = ?) + (select count(*) from app.blends where user_id = ?)"
                + " + (select count(*) from app.favorites where user_id = ?) + (select count(*) from app.tasting_feedback where user_id = ?)"
                + " + (select count(*) from app.consents where user_id = ?) + (select count(*) from app.error_reports where user_id = ?)"
                + " + (select count(*) from app.events where user_id = ?)", u, u, u, u, u, u, u);
    }

    static long authUsers(UUID u) throws Exception {
        return dbCount("select count(*) from auth.users where id = ?", u);
    }

    /** 표마다 하나씩 데이터를 만든다(API로) */
    TestAuth.User userWithData() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(200, putAs(u, "/api/v1/me/preferences", prefs("[\"rose\"]", "[\"floral\"]")).statusCode());
        assertEquals(201, postAs(u, "/api/v1/blends", BlendsApiTest.blend("\"x\"", "[]", BlendsApiTest.ONE_NOTE)).statusCode());
        assertEquals(204, putAs(u, "/api/v1/favorites/Brand:perfume", null).statusCode());
        assertEquals(201, postAs(u, "/api/v1/feedback", FeedbackApiTest.MIN).statusCode());
        assertEquals(204, postAs(u, "/api/v1/reports", ReportsApiTest.MIN).statusCode());
        assertEquals(200, post("/api/v1/events", EventsApiTest.batch(EventsApiTest.ok(UUID.randomUUID())),
                "X-Install-Id", UUID.randomUUID().toString(), "Authorization", u.bearer(), "X-Forwarded-For", ip()).statusCode());
        assertTrue(rows(u.id()) >= 9, "동의 3 + 표마다 1");
        return u;
    }

    @Test
    void deletesAccountAndAllData() throws Exception {
        TestAuth.User u = userWithData(), other = userWithData();
        long otherRows = rows(other.id());
        long events = dbCount("select count(*) from app.events");
        HttpResponse<String> r = deleteAs(u, PATH);
        assertEquals(204, r.statusCode(), r.body());
        assertEquals(0, rows(u.id()), "내 줄 0");
        assertEquals(0, authUsers(u.id()));
        assertEquals(events, dbCount("select count(*) from app.events"), "사용 기록은 user_id만 비우고 남음(db_design.md 4-2)");
        assertEquals(otherRows, rows(other.id()), "남의 데이터는 그대로");
        assertEquals(1, authUsers(other.id()));
    }

    @Test
    void callsAdminApiWithServerKey() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(204, deleteAs(u, PATH).statusCode());
        assertEquals(1, FakeAuthAdmin.seen.size());
        FakeAuthAdmin.Seen s = FakeAuthAdmin.seen.get(0);
        assertEquals("DELETE", s.method());
        assertEquals("/auth/v1/admin/users/" + u.id(), s.path(), "토큰의 사용자만 지움");
        assertEquals(FakeStorage.SECRET, s.apikey());
        assertEquals("Bearer " + FakeStorage.SECRET, s.authorization());
    }

    @Test
    void sameTokenIs401RightAfter() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(200, getAs(u, PATH).statusCode(), "세션 확인이 보관된 상태에서");
        assertEquals(204, deleteAs(u, PATH).statusCode());
        // 시계를 돌리지 않고 바로
        assertError(getAs(u, PATH), 401, "UNAUTHORIZED");
        assertError(putAs(u, "/api/v1/favorites/Brand:perfume", null), 401, "UNAUTHORIZED");
        assertError(deleteAs(u, PATH), 401, "UNAUTHORIZED");
        assertEquals(1, FakeAuthAdmin.seen.size(), "두 번째 삭제는 관리 API까지 가지 않음");
    }

    @Test
    void noConsentNeeded() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertEquals(204, deleteAs(u, PATH).statusCode());
        assertEquals(0, authUsers(u.id()));
    }

    @Test
    void needsLogin() throws Exception {
        assertError(send("DELETE", PATH, null, "X-Forwarded-For", ip()), 401, "UNAUTHORIZED");
        assertTrue(FakeAuthAdmin.seen.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 401, 403, 429, 0, -1})
    void adminFailureDeletesNothing(int status, CapturedOutput out) throws Exception {
        TestAuth.User u = userWithData();
        long before = rows(u.id());
        FakeAuthAdmin.status = status;
        HttpResponse<String> r = deleteAs(u, PATH);
        assertError(r, 503, "UNAVAILABLE");
        assertEquals(before, rows(u.id()), "아무것도 안 지워짐");
        assertEquals(1, authUsers(u.id()));
        FakeAuthAdmin.status = 200;
        assertEquals(200, getAs(u, PATH).statusCode(), "계정은 그대로 쓸 수 있음");
        assertFalse(r.body().contains(FakeStorage.SECRET) || r.headers().toString().contains(FakeStorage.SECRET), "응답에 키 없음");
        assertFalse(out.getAll().contains(FakeStorage.SECRET), "로그에 키 없음");
        assertEquals(204, deleteAs(u, PATH).statusCode(), "다시 시도하면 됨");
        assertEquals(0, rows(u.id()));
    }

    @Test
    void alreadyGoneAtSupabaseStillChecksRows() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(200, getAs(u, PATH).statusCode());
        // 대시보드에서 먼저 지워졌고(세션 확인은 아직 보관 중) 관리 API는 404
        TestAuth.admin("delete from auth.users where id = ?", u.id());
        FakeAuthAdmin.status = 404;
        assertEquals(204, deleteAs(u, PATH).statusCode(), "이미 없음 = 지워진 상태, 남은 줄 0 확인 뒤 성공");
        assertEquals(0, rows(u.id()));
    }

    @Test
    void rowsLeftAfterDeleteIs500(CapturedOutput out) throws Exception {
        TestAuth.User u = consentedUser();
        FakeAuthAdmin.status = 1; // 관리 API는 성공이라고 답했지만 실제로는 안 지워짐
        assertError(deleteAs(u, PATH), 500, "INTERNAL");
        assertTrue(out.getAll().contains("남은 줄"), "로그로 알림");
        assertFalse(out.getAll().contains(u.id().toString()), "로그에 사용자 id 없음");
    }

    @Test
    void threePerHour() throws Exception {
        TestAuth.User u = consentedUser();
        FakeAuthAdmin.status = 500;
        for (int i = 0; i < 3; i++) assertError(deleteAs(u, PATH), 503, "UNAVAILABLE");
        HttpResponse<String> r = deleteAs(u, PATH);
        assertError(r, 429, "RATE_LIMITED");
        assertTrue(r.headers().firstValue("Retry-After").isPresent());
        assertEquals(3, FakeAuthAdmin.seen.size());
    }

    @Test
    void bodyIsIgnoredAndOnlyTokenUserIsDeleted() throws Exception {
        TestAuth.User u = consentedUser(), other = consentedUser();
        HttpResponse<String> r = send("DELETE", PATH, "{\"userId\":\"" + other.id() + "\"}", "Authorization", u.bearer(), "X-Forwarded-For", ip());
        assertEquals(204, r.statusCode(), r.body());
        assertEquals(0, authUsers(u.id()));
        assertEquals(1, authUsers(other.id()));
    }

    @Test
    void savingWithTokenOfAccountDeletedElsewhereIs401Not500() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(200, getAs(u, PATH).statusCode(), "세션 확인 보관(30초)");
        TestAuth.admin("delete from auth.users where id = ?", u.id()); // 대시보드 삭제 → 이 서버 보관은 모름
        // 동의가 필요한 저장은 동의도 같이 지워져 403에서 멈춘다(500 아님)
        for (HttpResponse<String> r : new HttpResponse[]{
                postAs(u, "/api/v1/blends", BlendsApiTest.blend("\"x\"", "[]", BlendsApiTest.ONE_NOTE)),
                putAs(u, "/api/v1/favorites/Brand:perfume", null),
                postAs(u, "/api/v1/reports", ReportsApiTest.MIN),
                putAs(u, "/api/v1/me/preferences", prefs("[]", "[]"))}) {
            assertTrue(r.statusCode() == 401 || r.statusCode() == 403, r.statusCode() + " " + r.body());
        }
        // 동의 기록은 사용자 줄이 없어 외래 키에 걸림 → 500이 아니라 401
        assertError(postAs(u, "/api/v1/me/consents", MeApiTest.ALL), 401, "UNAUTHORIZED");
        // 사용 기록은 동의가 없으니 사용자와 잇지 않고 익명으로 저장(원래 규칙) → 200
        UUID install = UUID.randomUUID();
        assertEquals(200, post("/api/v1/events", EventsApiTest.batch(EventsApiTest.ok(UUID.randomUUID())),
                "X-Install-Id", install.toString(), "Authorization", u.bearer(), "X-Forwarded-For", ip()).statusCode());
        assertEquals(1, dbCount("select count(*) from app.events where install_id = ? and user_id is null", install));
    }
}
