package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 5 리뷰(QA·Gemini)에서 맞다고 판단한 지적의 회귀 테스트. 고치기 전에 실패를 확인했다(docs/review.md "묶음 5").
 */
class Bundle5ReviewFixesTest extends MeApiTestBase {

    /**
     * QA-1: 한 사용자의 저장이 자기 잠금을 기다리며 DB 연결(풀 4개)을 모두 붙잡으면, 다른 사용자 요청이 연결을 못 얻어 실패한다.
     * → 잠금은 짧게만 기다리고(lock_timeout) 못 잡으면 503으로 끝내 연결을 돌려준다.
     */
    @Test
    void waitingForOwnLockDoesNotStarveOtherUsers() throws Exception {
        TestAuth.User a = consentedUser(), b = consentedUser();
        assertEquals(200, getAs(b, BlendsApiTest.PATH).statusCode(), "b 세션 확인을 미리(보관됨)");
        List<CompletableFuture<HttpResponse<String>>> waiting = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(TestDb.jdbcUrl())) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("select pg_advisory_xact_lock(5101, hashtext(?))")) {
                p.setString(1, a.id().toString());
                p.execute();
            }
            for (int i = 0; i < 4; i++) { // 풀 크기만큼 a의 저장이 잠금을 기다림
                waiting.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return postAs(a, BlendsApiTest.PATH, BlendsApiTest.blend("\"x\"", "[]", BlendsApiTest.ONE_NOTE));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));
            }
            Thread.sleep(500);
            HttpResponse<String> other = getAs(b, BlendsApiTest.PATH); // 연결이 필요한 다른 사용자 읽기
            assertEquals(200, other.statusCode(), "다른 사용자는 a의 잠금 대기 때문에 실패하면 안 됨: " + other.body());
            for (CompletableFuture<HttpResponse<String>> f : waiting) {
                HttpResponse<String> r = f.get(30, TimeUnit.SECONDS);
                assertError(r, 503, "UNAVAILABLE"); // 잠금을 못 잡은 저장은 "잠시 후 다시"
            }
            c.rollback();
        }
        assertEquals(0, dbCount("select count(*) from app.blends where user_id = ?", a.id()));
    }

    /**
     * QA-2: 조합 이름 검사를 Java와 DB가 따로 해서, Java는 통과시키고 DB 검사가 거절하면 500이 된다.
     * 공백·서식 문자(유니코드 Zs·Zl·Zp·Cf, 그리고 Java·DB 목록 경계 글자) 하나로 된 이름을 모두 보내 500이 하나도 없어야 한다.
     */
    @Test
    void nameRuleNeverGives500() throws Exception {
        List<Integer> chars = new ArrayList<>();
        for (int c = 0x20; c <= 0xFFFF; c++) {
            if (c >= 0xD800 && c <= 0xDFFF) continue;
            int t = Character.getType(c);
            if (t == Character.SPACE_SEPARATOR || t == Character.LINE_SEPARATOR || t == Character.PARAGRAPH_SEPARATOR
                    || t == Character.FORMAT || Character.isWhitespace(c)) chars.add(c);
        }
        for (int c : new int[]{0x180e, 0x2065, 0x2800, 0x3164, 0xffa0, 0x115f, 0x1160}) chars.add(c);
        List<String> fiveHundreds = new ArrayList<>();
        TestAuth.User u = null;
        for (int i = 0; i < chars.size(); i++) {
            if (i % 25 == 0) u = consentedUser(); // 쓰기 분당 30회 제한과 섞이지 않게
            String name = new String(Character.toChars(chars.get(i)));
            String escaped = String.format("\\u%04x", chars.get(i));
            HttpResponse<String> r = postAs(u, BlendsApiTest.PATH, BlendsApiTest.blend("\"" + escaped + "\"", "[]", BlendsApiTest.ONE_NOTE));
            if (r.statusCode() >= 500) fiveHundreds.add(String.format("U+%04X", chars.get(i)));
            assertTrue(r.statusCode() == 201 || r.statusCode() == 400 || r.statusCode() >= 500, name + " " + r.body());
        }
        assertEquals(List.of(), fiveHundreds, "500이 난 글자");
    }
}
