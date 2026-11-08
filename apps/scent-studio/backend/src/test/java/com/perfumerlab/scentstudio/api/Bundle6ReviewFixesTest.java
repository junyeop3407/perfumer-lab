package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 6 리뷰(QA·Gemini)에서 맞다고 판단한 지적의 회귀 테스트. 고치기 전에 실패를 확인했다(docs/review.md "묶음 6").
 * CatalogApiTest와 같은 서버 설정(가짜 Storage)을 쓴다.
 */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class Bundle6ReviewFixesTest extends CatalogTestBase {

    /** Gemini-1: Storage에서 새 버전을 받는 동안(최대 30초) 이미 메모리에 있는 버전 내려받기까지 같이 기다리면 안 된다 */
    @Test
    void slowFetchDoesNotBlockKeptFile() throws Exception {
        String kept = uniq(), slow = uniq();
        release(kept, FILE, KEYS, false);
        release(slow, FakeStorage.gzip("{\"version\":\"" + slow + "\"}"), KEYS, true);
        assertEquals(200, download(kept, UUID.randomUUID(), ip()).statusCode(), "먼저 메모리에 보관");
        FakeStorage.delays.put("/storage/v1/object/catalog/catalog-" + slow + ".json.gz", 2500L); // 마감 4초 안
        CompletableFuture<HttpResponse<byte[]>> waiting = CompletableFuture.supplyAsync(() -> {
            try {
                return download(slow, UUID.randomUUID(), ip());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(300); // 느린 받기가 시작되게
        long t = System.nanoTime();
        HttpResponse<byte[]> r = download(kept, UUID.randomUUID(), ip());
        long ms = (System.nanoTime() - t) / 1_000_000;
        assertEquals(200, r.statusCode());
        assertTrue(ms < 2000, "보관된 파일은 바로 나와야 함: " + ms + "ms");
        assertEquals(200, waiting.get(30, TimeUnit.SECONDS).statusCode());
    }

    static String path(String v) {
        return "/storage/v1/object/catalog/catalog-" + v + ".json.gz";
    }

    /** QA-1: Storage가 헤더만 보내고 본문을 멈추면, 전체 마감 시간 뒤 503으로 끝나야 한다(무한 대기 → 스레드·잠금 묶임) */
    @Test
    void stalledBodyEndsWith503() throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        FakeStorage.stalls.add(path(v));
        long t = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> r = CompletableFuture.supplyAsync(() -> {
            try {
                return download(v, UUID.randomUUID(), ip());
            } catch (Exception e) {
                throw new IllegalStateException(e); // 15초 안에 답이 없으면 여기로
            }
        });
        assertError(r.get(30, TimeUnit.SECONDS), 503, "UNAVAILABLE");
        assertTrue((System.nanoTime() - t) / 1_000_000 < 10_000, "마감 시간(테스트 4초) 근처에서 끝남");
    }

    /** QA-2: 받기에 실패하면 30초 동안은 Storage를 다시 부르지 않고 바로 503(장애 중 요청마다 최대 30초씩 줄 서지 않게) */
    @Test
    void failureIsRememberedFor30Seconds() throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 500, new byte[0]);
        assertError(download(v, UUID.randomUUID(), ip()), 503, "UNAVAILABLE");
        assertError(download(v, UUID.randomUUID(), ip()), 503, "UNAVAILABLE");
        assertEquals(1, FakeStorage.seen.stream().filter(x -> x.path().equals(path(v))).count(), "두 번째는 Storage를 부르지 않음");
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 200, FILE);
        clock.advance(java.time.Duration.ofSeconds(31));
        assertEquals(200, download(v, UUID.randomUUID(), ip()).statusCode(), "30초 뒤 다시 받아 봄");
    }

    /** QA-5: 우리 쪽 문제(503)로 실패한 내려받기는 설치 id 하루 3회에서 빼 준다(복구돼도 하루 동안 429가 되지 않게) */
    @Test
    void failedDownloadsDoNotUseDailyQuota() throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 500, new byte[0]);
        UUID install = UUID.randomUUID();
        for (int i = 0; i < 3; i++) assertError(download(v, install, ip()), 503, "UNAVAILABLE");
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 200, FILE);
        clock.advance(java.time.Duration.ofSeconds(31));
        assertEquals(200, download(v, install, ip()).statusCode(), "실패 3번 뒤에도 같은 설치 id로 받을 수 있음");
    }

    /** QA-3: 서버가 켜져 있는 동안 새 카탈로그를 배포하면 10분 안에 새 key 목록을 쓴다(다시 켜질 때만 바뀌지 않게) */
    @Test
    void keyListFollowsNewRelease() throws Exception {
        clock.advance(java.time.Duration.ofMinutes(11));
        TestAuth.User u = TestAuth.newUser();
        for (String t : new String[]{"terms", "privacy", "age_14"}) {
            TestAuth.admin("insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, ?, 'draft-1', '1.0.0')", u.id(), t);
        }
        String v1 = uniq(), v2 = uniq();
        release(v1, FILE, FakeStorage.gzip(KEYS_JSON.replace(V, v1)), true);
        String fav = "/api/v1/favorites/Fake_Maison:456-new-scent";
        assertEquals(400, send("PUT", fav, null, "Authorization", bearer(u), "X-Forwarded-For", ip()).statusCode(), "v1에는 없는 향수");
        TestAuth.admin("update app.catalog_releases set is_current = false");
        release(v2, FILE, FakeStorage.gzip(KEYS_JSON.replace(V, v2).replace("\"perfumes\":[", "\"perfumes\":[\"Fake_Maison:456-new-scent\",")), true);
        clock.advance(java.time.Duration.ofMinutes(11));
        HttpResponse<String> r = send("PUT", fav, null, "Authorization", bearer(u), "X-Forwarded-For", ip());
        assertEquals(204, r.statusCode(), "새 배포의 향수: " + r.body());
    }

    String bearer(TestAuth.User u) {
        return "Bearer " + TestAuth.token(u, c -> c.issueTime(java.util.Date.from(clock.instant()))
                .expirationTime(java.util.Date.from(clock.instant().plus(java.time.Duration.ofHours(1)))));
    }

    /** QA-4: 설치 id를 요청마다 바꿔도 한 IP의 사용 기록 묶음은 분당 30회까지 */
    @Test
    void eventsCappedPerIpEvenWithNewInstallIds() throws Exception {
        String sameIp = ip();
        String body = "{\"appVersion\":\"1.0.0\",\"events\":[{\"clientEventId\":\"" + UUID.randomUUID()
                + "\",\"name\":\"app_open\",\"props\":{},\"occurredAt\":\"" + java.time.Instant.now().minusSeconds(60) + "\"}]}";
        for (int i = 0; i < 30; i++) {
            assertEquals(200, post("/api/v1/events", body, "X-Install-Id", UUID.randomUUID().toString(), "X-Forwarded-For", sameIp).statusCode(), "요청 " + (i + 1));
        }
        HttpResponse<String> r = post("/api/v1/events", body, "X-Install-Id", UUID.randomUUID().toString(), "X-Forwarded-For", sameIp);
        assertEquals(429, r.statusCode(), r.body());
    }

    /** QA-10: 파일 이름이 catalog-<버전>(.keys).json.gz 모양이 아니면 Storage 주소를 만들지 않는다(이중 방어) */
    @Test
    void fetchRefusesOddPaths() {
        var store = new com.perfumerlab.scentstudio.catalog.CatalogStore(null, FakeStorage.url(), FakeStorage.SECRET, java.time.Duration.ofSeconds(4));
        for (String p : new String[]{"../catalog-2026.11.05.json.gz", "catalog-2026.11.05.json.gz?x=1", "other.json.gz", "catalog-x.json.gz"}) {
            assertThrows(com.perfumerlab.scentstudio.web.ApiException.class, () -> store.fetch(p, "0".repeat(64), 0), p);
        }
        assertTrue(FakeStorage.seen.isEmpty(), "Storage를 부르지 않음");
    }

    /** QA-12: Storage가 다른 곳으로 보내도(302) 따라가지 않는다(키가 다른 주소로 가지 않게) */
    @Test
    void redirectIsNotFollowed(org.springframework.boot.test.system.CapturedOutput out) throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        FakeStorage.redirects.put(path(v), FakeStorage.url() + "/elsewhere");
        assertError(download(v, UUID.randomUUID(), ip()), 503, "UNAVAILABLE");
        assertTrue(FakeStorage.seen.stream().noneMatch(x -> x.path().equals("/elsewhere")), "옮긴 주소로 가지 않음");
        assertFalse(out.getAll().contains(FakeStorage.SECRET));
    }
}
