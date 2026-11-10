package com.perfumerlab.scentstudio.api;

import com.perfumerlab.scentstudio.auth.SessionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** 묶음 8 리뷰 회귀 테스트 (docs/review.md "묶음 8") */
class Bundle8ReviewFixesTest extends MeApiTestBase {

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

    /** QA-1: 삭제 중에 먼저 출발한 요청이 "살아 있음"을 보관에 다시 넣어도, 지운 사용자는 30초 동안 막힌다 */
    @Test
    void deletedUserStaysBlockedEvenIfCacheIsRefilled() {
        TestAuth.User u = TestAuth.newUser(); // DB에는 아직 살아 있음 = 삭제 직전에 확인을 마친 늦은 요청과 같은 상황
        Instant[] now = {Instant.parse("2026-11-10T00:00:00Z")};
        Clock clock = new Clock() {
            public Instant instant() { return now[0]; }
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
        };
        SessionChecker s = new SessionChecker(JdbcClient.create(new DriverManagerDataSource(TestDb.jdbcUrl())), clock);
        assertTrue(s.alive(u.session(), u.id()));
        s.deleted(u.id());
        assertFalse(s.alive(u.session(), u.id()), "보관이 있어도 지워짐 표시가 이김");
        now[0] = now[0].plus(Duration.ofSeconds(59));
        assertFalse(s.alive(u.session(), u.id()), "60초 안에는(늦게 넣은 30초 보관이 끝날 때까지) 막음");
        s.forget(u.id());
        assertFalse(s.alive(u.session(), u.id()), "보관만 지우는 forget은 지워짐 표시를 없애지 않음");
    }

    /** QA-2: Supabase는 지웠는데 답을 못 받아 503이어도, 보관은 지워서 같은 토큰이 바로 401(다시 시도하면 401 = 이미 지워짐) */
    @Test
    void deletedButConnectionDroppedStillBlocksTokenRightAway() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(200, getAs(u, "/api/v1/me").statusCode(), "세션 확인 보관");
        FakeAuthAdmin.status = 2;
        assertError(deleteAs(u, "/api/v1/me"), 503, "UNAVAILABLE");
        assertEquals(0, dbCount("select count(*) from auth.users where id = ?", u.id()));
        assertError(getAs(u, "/api/v1/me"), 401, "UNAUTHORIZED");
        assertError(deleteAs(u, "/api/v1/me"), 401, "UNAUTHORIZED");
    }

    /** QA-2 반대쪽: 정말 실패한 503 뒤에는 계정을 그대로 쓸 수 있다(지워짐 표시를 하지 않음) */
    @Test
    void realFailureDoesNotLockUserOut() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(200, getAs(u, "/api/v1/me").statusCode());
        FakeAuthAdmin.status = 500;
        assertError(deleteAs(u, "/api/v1/me"), 503, "UNAVAILABLE");
        assertEquals(200, getAs(u, "/api/v1/me").statusCode());
    }

    /** QA·Gemini: 외래 키 → 401 규칙은 이름(*_user_id_fkey)에 기대므로, auth.users를 가리키는 외래 키 이름을 고정한다 */
    @Test
    void foreignKeysToUsersAreNamedUserIdFkey() throws Exception {
        assertEquals(0, dbCount("select count(*) from pg_constraint where contype = 'f' and confrelid = 'auth.users'::regclass"
                + " and connamespace = 'app'::regnamespace and conname !~ '^[a-z_]+_user_id_fkey$'"));
        assertEquals(7, dbCount("select count(*) from pg_constraint where contype = 'f' and confrelid = 'auth.users'::regclass"
                + " and connamespace = 'app'::regnamespace"), "app 표 7개 모두 user_id 외래 키");
    }
}
