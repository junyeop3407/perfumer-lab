package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 4: 취향 GET·PUT, 로그인 직후 합치기 POST /me/preferences/merge (db_design.md 5절 표, 승인 D6).
 * 불호 노트는 합집합(서버 것 먼저, 200개까지, 잘리면 truncated), 선호 계열은 서버에 하나라도 있으면 서버 값.
 * 카탈로그에 없는 key: PUT은 400 UNKNOWN_KEY, 합치기는 기기 값의 모르는 key만 빼고 ignoredKeys로 알려 줌.
 */
class PreferencesApiTest extends MeApiTestBase {

    static final String PATH = "/api/v1/me/preferences", MERGE = "/api/v1/me/preferences/merge";

    @Test
    void emptyBeforeAnything() throws Exception {
        HttpResponse<String> r = getAs(TestAuth.newUser(), PATH);
        assertEquals(200, r.statusCode());
        assertEquals("{\"dislikedNoteKeys\":[],\"preferredFamilies\":[]}", r.body());
    }

    @Test
    void putThenGet() throws Exception {
        TestAuth.User u = consentedUser();
        HttpResponse<String> r = putAs(u, PATH, prefs("[\"patchouli\",\"rose\"]", "[\"floral\"]"));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("{\"dislikedNoteKeys\":[\"patchouli\",\"rose\"],\"preferredFamilies\":[\"floral\"]}", r.body());
        assertEquals(r.body(), getAs(u, PATH).body());
        assertEquals(200, putAs(u, PATH, prefs("[]", "[]")).statusCode(), "다시 바꾸기(비우기)");
        assertEquals("{\"dislikedNoteKeys\":[],\"preferredFamilies\":[]}", getAs(u, PATH).body());
    }

    @Test
    void othersCannotSeeMine() throws Exception {
        TestAuth.User a = consentedUser(), b = consentedUser();
        putAs(a, PATH, prefs("[\"patchouli\"]", "[\"woody\"]"));
        assertEquals("{\"dislikedNoteKeys\":[],\"preferredFamilies\":[]}", getAs(b, PATH).body());
    }

    @Test
    void unknownKeysAreRejectedOnPut() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(putAs(u, PATH, prefs("[\"no-such-note\"]", "[]")), 400, "UNKNOWN_KEY");
        assertError(putAs(u, PATH, prefs("[]", "[\"jazz\"]")), 400, "UNKNOWN_KEY");
        assertError(putAs(u, PATH, prefs("[\"floral\"]", "[]")), 400, "UNKNOWN_KEY"); // 계열 key는 노트가 아님
        assertEquals("{\"dislikedNoteKeys\":[],\"preferredFamilies\":[]}", getAs(u, PATH).body(), "아무것도 저장 안 됨");
    }

    @ParameterizedTest(name = "잘못된 취향: {0}")
    @ValueSource(strings = {
            "{\"dislikedNoteKeys\":[\"rose\",\"rose\"],\"preferredFamilies\":[]}",      // 중복
            "{\"dislikedNoteKeys\":[],\"preferredFamilies\":[\"floral\",\"floral\"]}",
            "{\"dislikedNoteKeys\":null,\"preferredFamilies\":[]}",
            "{\"preferredFamilies\":[]}",
            "{\"dislikedNoteKeys\":[]}",
            "{\"dislikedNoteKeys\":[null],\"preferredFamilies\":[]}",
            "{\"dislikedNoteKeys\":[1],\"preferredFamilies\":[]}",
            "{\"dislikedNoteKeys\":\"rose\",\"preferredFamilies\":[]}",
            "[]", "not json"
    })
    void badShapesAreRejected(String body) throws Exception {
        assertError(putAs(consentedUser(), PATH, body), 400, "BAD_REQUEST");
    }

    @Test
    void limits200And10() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(200, putAs(u, PATH, prefs(notes(1, 200), "[]")).statusCode(), "200개까지");
        assertError(putAs(u, PATH, prefs(notes(1, 201), "[]")), 400, "BAD_REQUEST");
        String ten = "[\"citrus\",\"fruity\",\"floral\",\"soft\",\"sweet\",\"spicy\",\"amber\",\"woody\",\"green\",\"fresh\"]";
        assertEquals(200, putAs(u, PATH, prefs("[]", ten)).statusCode(), "계열 10개(전부)까지");
    }

    // ---------- 합치기 (db_design.md 5절) ----------

    @Test
    void d6_unionOfDislikes() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH, prefs("[\"oakmoss\"]", "[]"));                 // 서버에 오크모스
        HttpResponse<String> r = postAs(u, MERGE, prefs("[\"patchouli\"]", "[]")); // 기기에 패출리
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().startsWith("{\"dislikedNoteKeys\":[\"oakmoss\",\"patchouli\"],\"preferredFamilies\":[]"), r.body());
        assertTrue(r.body().contains("\"truncated\":false"), r.body());
        assertTrue(getAs(u, PATH).body().contains("[\"oakmoss\",\"patchouli\"]"), "합친 값이 저장됨");
    }

    @Test
    void mergeKeepsServerOrderAndDropsDuplicates() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH, prefs("[\"rose\",\"musk\"]", "[]"));
        HttpResponse<String> r = postAs(u, MERGE, prefs("[\"musk\",\"bergamot\",\"rose\"]", "[]"));
        assertTrue(r.body().startsWith("{\"dislikedNoteKeys\":[\"rose\",\"musk\",\"bergamot\"]"), r.body());
    }

    @Test
    void preferredFamiliesServerWinsIfAny() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH, prefs("[]", "[\"woody\"]"));
        assertTrue(postAs(u, MERGE, prefs("[]", "[\"floral\",\"citrus\"]")).body().contains("\"preferredFamilies\":[\"woody\"]"));
    }

    @Test
    void preferredFamiliesFromDeviceIfServerEmpty() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH, prefs("[\"rose\"]", "[]"));
        assertTrue(postAs(u, MERGE, prefs("[]", "[\"floral\",\"citrus\"]")).body().contains("\"preferredFamilies\":[\"floral\",\"citrus\"]"));
    }

    @Test
    void mergeWithNothingOnServer() throws Exception {
        TestAuth.User u = consentedUser();
        HttpResponse<String> r = postAs(u, MERGE, prefs("[\"patchouli\"]", "[\"amber\"]"));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("{\"dislikedNoteKeys\":[\"patchouli\"],\"preferredFamilies\":[\"amber\"]}", getAs(u, PATH).body());
    }

    @Test
    void mergeCapsAt200ServerFirst() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH, prefs(notes(1, 150), "[]"));
        HttpResponse<String> r = postAs(u, MERGE, prefs(notes(101, 300), "[]")); // 새로 더할 것 151~300 = 150개 → 50개만 들어감
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"truncated\":true"), r.body());
        String saved = getAs(u, PATH).body();
        assertTrue(saved.contains("\"note-001\"") && saved.contains("\"note-150\"") && saved.contains("\"note-200\""), "서버 것 모두 + 기기 것 앞에서부터");
        assertFalse(saved.contains("\"note-201\""), "200개 넘는 것은 안 들어감");
    }

    @Test
    void fullServerWithOnlyKnownDeviceKeysIsNotTruncated() throws Exception {
        TestAuth.User u = consentedUser();
        putAs(u, PATH, prefs(notes(1, 200), "[]"));
        HttpResponse<String> r = postAs(u, MERGE, prefs(notes(1, 50), "[]")); // 이미 서버에 있는 것만
        assertTrue(r.body().contains("\"truncated\":false"), "잘린 게 없으면 false: " + r.body().substring(r.body().length() - 60));
    }

    @Test
    void mergeIgnoresUnknownDeviceKeys() throws Exception {
        TestAuth.User u = consentedUser();
        HttpResponse<String> r = postAs(u, MERGE, prefs("[\"rose\",\"gone-from-catalog\"]", "[\"jazz\",\"floral\"]"));
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"dislikedNoteKeys\":[\"rose\"]"), r.body());
        assertTrue(r.body().contains("\"preferredFamilies\":[\"floral\"]"), r.body());
        assertTrue(r.body().contains("\"ignoredKeys\":[\"gone-from-catalog\",\"jazz\"]"), r.body());
    }

    @Test
    void mergeRejectsBadShapes() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, MERGE, prefs(notes(1, 201), "[]")), 400, "BAD_REQUEST");
        assertError(postAs(u, MERGE, "{\"dislikedNoteKeys\":[\"rose\",\"rose\"],\"preferredFamilies\":[]}"), 400, "BAD_REQUEST");
        assertError(postAs(u, MERGE, "{\"dislikedNoteKeys\":[" + "\"" + "x".repeat(101) + "\"],\"preferredFamilies\":[]}"), 400, "BAD_REQUEST");
    }

    @Test
    void concurrentMergesFromManyDevicesKeepEverything() throws Exception {
        TestAuth.User u = consentedUser();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {  // 합치기 한도 분당 5회
                String note = String.format("\"note-%03d\"", i);
                calls.add(() -> postAs(u, MERGE, prefs("[" + note + "]", "[]")));
            }
            for (Future<HttpResponse<String>> f : pool.invokeAll(calls)) assertEquals(200, f.get().statusCode(), f.get().body());
        } finally {
            pool.shutdown();
        }
        String saved = getAs(u, PATH).body();
        for (int i = 1; i <= 5; i++) assertTrue(saved.contains(String.format("\"note-%03d\"", i)), "동시에 합쳐도 잃어버리지 않음: " + saved);
    }
}
