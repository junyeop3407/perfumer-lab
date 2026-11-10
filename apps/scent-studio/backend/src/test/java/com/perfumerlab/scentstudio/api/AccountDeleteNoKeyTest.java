package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** 묶음 8: 서버 전용 키가 없거나 모양이 틀리면(sb_secret_가 아님) 계정 삭제는 503, 관리 API를 부르지 않고 아무것도 안 지움 */
class AccountDeleteNoKeyTest extends MeApiTestBase {

    @DynamicPropertySource
    static void admin(DynamicPropertyRegistry r) {
        r.add("scent.admin.url", FakeAuthAdmin::url);
        r.add("scent.admin.secret-key", () -> "eyJhbGciOiJIUzI1NiJ9.old-service-role-style"); // 옛 JWT 모양 키 → 쓰지 않음
    }

    @Test
    void wrongOrMissingKeyIs503AndNothingDeleted() throws Exception {
        FakeAuthAdmin.reset();
        TestAuth.User u = consentedUser();
        assertError(deleteAs(u, "/api/v1/me"), 503, "UNAVAILABLE");
        assertTrue(FakeAuthAdmin.seen.isEmpty(), "관리 API를 부르지 않음");
        assertEquals(1, dbCount("select count(*) from auth.users where id = ?", u.id()));
        assertEquals(3, dbCount("select count(*) from app.consents where user_id = ?", u.id()));
    }
}
