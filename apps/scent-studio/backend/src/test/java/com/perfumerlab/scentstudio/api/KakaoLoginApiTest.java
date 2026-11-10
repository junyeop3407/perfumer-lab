package com.perfumerlab.scentstudio.api;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 카카오 로그인 서버 쪽 (plan_stage4.md 남은 것 10, 사장 OK 2026-11-10, kakao_setup.md).
 * - 카카오 토큰은 구글과 같은 Supabase 토큰(ES256·iss·aud). 다른 점: 이메일이 없을 수 있고(이메일 동의 안 받음), provider가 kakao
 * - GET /me: email 칸이 없거나 빈 값·공백·문자열이 아니면 200 + "email": null
 * - 구글·카카오 계정은 서로 다른 user id = 다른 계정. 합치지 않고 서로의 데이터가 안 보인다
 */
class KakaoLoginApiTest extends MeApiTestBase {

    /** 카카오 로그인 토큰 모양: 이메일 없음 + app_metadata.provider kakao (edit로 더 바꿈) */
    static Consumer<JWTClaimsSet.Builder> kakao(Consumer<JWTClaimsSet.Builder> edit) {
        return b -> {
            b.claim("email", null).claim("phone", "")
                    .claim("app_metadata", Map.of("provider", "kakao", "providers", List.of("kakao")))
                    .claim("user_metadata", Map.of("provider_id", "1234567890", "sub", "1234567890"));
            edit.accept(b);
        };
    }

    static String bearer(TestAuth.User u, Consumer<JWTClaimsSet.Builder> edit) {
        return "Bearer " + TestAuth.token(u, kakao(edit));
    }

    HttpResponse<String> getMe(TestAuth.User u, Consumer<JWTClaimsSet.Builder> edit) throws Exception {
        return get("/api/v1/me", "Authorization", bearer(u, edit), "X-Forwarded-For", ip());
    }

    @Test
    void kakaoTokenWithoutEmailGetsMeWithNullEmail() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = getMe(u, b -> { });
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().startsWith("{\"userId\":\"" + u.id() + "\",\"email\":null,"), "email 칸은 있고 null: " + r.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "　"})
    void blankEmailIsNull(String email) throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = getMe(u, b -> b.claim("email", email));
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"email\":null"), r.body());
    }

    @Test
    void nonStringEmailIsNull() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = getMe(u, b -> b.claim("email", 12345));
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"email\":null"), r.body());
    }

    @Test
    void kakaoAccountWithEmailShowsIt() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = getMe(u, b -> b.claim("email", "kakao-user@example.invalid"));
        assertTrue(r.body().contains("\"email\":\"kakao-user@example.invalid\""), r.body());
    }

    @Test
    void kakaoUserCanConsentAndSave() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        String auth = bearer(u, b -> { });
        assertEquals(200, post("/api/v1/me/consents", MeApiTest.ALL, "Authorization", auth, "X-Forwarded-For", ip()).statusCode());
        assertEquals(204, send("PUT", "/api/v1/favorites/Brand:perfume", null, "Authorization", auth, "X-Forwarded-For", ip()).statusCode());
        assertEquals(201, post("/api/v1/blends", BlendsApiTest.blend("\"x\"", "[]", BlendsApiTest.ONE_NOTE), "Authorization", auth, "X-Forwarded-For", ip()).statusCode());
    }

    @Test
    void googleAndKakaoAccountsAreSeparate() throws Exception {
        // 같은 사람이 구글·카카오로 따로 가입 = Supabase에서 다른 user id. 서버는 합치지 않는다
        TestAuth.User google = consentedUser(), kakaoUser = consentedUser();
        String kakaoAuth = bearer(kakaoUser, b -> { });
        assertEquals(204, putAs(google, "/api/v1/favorites/Brand:perfume", null).statusCode());
        String blend = BlendsApiTest.idOf(postAs(google, "/api/v1/blends", BlendsApiTest.blend("\"구글 조합\"", "[]", BlendsApiTest.ONE_NOTE)));

        HttpResponse<String> me = get("/api/v1/me", "Authorization", kakaoAuth, "X-Forwarded-For", ip());
        assertTrue(me.body().contains("\"userId\":\"" + kakaoUser.id() + "\""), me.body());
        assertFalse(get("/api/v1/favorites", "Authorization", kakaoAuth, "X-Forwarded-For", ip()).body().contains("Brand:perfume"), "구글 찜 안 보임");
        assertFalse(get("/api/v1/blends", "Authorization", kakaoAuth, "X-Forwarded-For", ip()).body().contains(blend), "구글 조합 안 보임");
        assertEquals(404, get("/api/v1/blends/" + blend, "Authorization", kakaoAuth, "X-Forwarded-For", ip()).statusCode());
        assertEquals(404, send("DELETE", "/api/v1/blends/" + blend, null, "Authorization", kakaoAuth, "X-Forwarded-For", ip()).statusCode());
        assertEquals(1, dbCount("select count(*) from app.blends where id = ?::uuid", blend), "구글 조합 그대로");
    }

    @Test
    void userIdComesFromSubNotFromKakaoProviderId() throws Exception {
        // user_metadata.sub·provider_id(카카오 회원번호)를 바꿔도 사용자는 토큰 sub(Supabase user id)
        TestAuth.User a = TestAuth.newUser();
        HttpResponse<String> r = getMe(a, b -> b.claim("user_metadata", Map.of("sub", UUID.randomUUID().toString(), "provider_id", "999")));
        assertTrue(r.body().contains("\"userId\":\"" + a.id() + "\""), r.body());
    }

    @Test
    void kakaoTokenStillNeedsValidSignatureAndSession() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertError(get("/api/v1/me", "Authorization", "Bearer " + TestAuth.sign(TestAuth.OTHER_KEY, u, kakao(b -> { })), "X-Forwarded-For", ip()),
                401, "UNAUTHORIZED");
        TestAuth.deleteSession(u.session());
        assertError(getMe(u, b -> { }), 401, "UNAUTHORIZED");
    }
}
