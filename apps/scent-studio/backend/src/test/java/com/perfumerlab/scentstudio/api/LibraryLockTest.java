package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 5: 한도 저장이 정말 "사용자 단위 잠금" 안에서 도는지 확실하게 본다.
 * 동시 20번 테스트는 타이밍에 따라 경쟁이 안 생길 수도 있어서(잠금을 빼 봤을 때 가끔 통과), 여기서는
 * DB에서 그 사용자의 잠금을 먼저 잡아 두고 → 저장 요청이 잠금이 풀릴 때까지 기다리는지 → 풀면 끝나는지 확인한다.
 * 다른 사용자의 저장은 기다리지 않는다(사용자 단위). 잠금 대기는 2초까지라(리뷰 묶음 5 QA-1) 0.7초만 기다려 본다.
 */
class LibraryLockTest extends MeApiTestBase {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "조합,5101,/api/v1/blends,POST",
            "찜,5102,/api/v1/favorites/Brand:perfume,PUT",
            "시향 기록,5103,/api/v1/feedback,POST"
    })
    void saveWaitsForUserLock(String what, int lockClass, String path, String method) throws Exception {
        TestAuth.User u = consentedUser(), other = consentedUser();
        String body = switch (method) {
            case "PUT" -> null;
            default -> path.endsWith("blends") ? BlendsApiTest.blend("\"x\"", "[]", BlendsApiTest.ONE_NOTE) : FeedbackApiTest.MIN;
        };
        try (Connection c = DriverManager.getConnection(TestDb.jdbcUrl())) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("select pg_advisory_xact_lock(?, hashtext(?))")) {
                p.setInt(1, lockClass);
                p.setString(2, u.id().toString());
                p.execute();
            }
            CompletableFuture<HttpResponse<String>> mine = CompletableFuture.supplyAsync(() -> call(method, path, body, u));
            assertThrows(TimeoutException.class, () -> mine.get(700, TimeUnit.MILLISECONDS), what + ": 잠금이 풀릴 때까지 기다려야 함");
            HttpResponse<String> others = call(method, path, body, other);
            assertTrue(others.statusCode() / 100 == 2, "다른 사용자는 기다리지 않음: " + others.body());
            c.rollback(); // 잠금 풀기
            HttpResponse<String> r = mine.get(15, TimeUnit.SECONDS);
            assertTrue(r.statusCode() / 100 == 2, r.body());
        }
    }

    private HttpResponse<String> call(String method, String path, String body, TestAuth.User u) {
        try {
            return send(method, path, body, "Authorization", u.bearer(), "X-Forwarded-For", ip());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
