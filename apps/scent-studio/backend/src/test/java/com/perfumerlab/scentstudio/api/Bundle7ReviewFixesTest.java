package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** 묶음 7 리뷰 반영 (docs/review.md "묶음 7"). 승인 목록과 섞지 않는다. */
class Bundle7ReviewFixesTest extends MeApiTestBase {

    /** QA-8d: 순위·점수 경계값(1·10, 0·1)은 받는다 */
    @Test
    void rankAndScoreBoundariesAreAccepted() throws Exception {
        TestAuth.User u = consentedUser();
        for (String f : new String[]{"\"rank\":1,\"score\":0", "\"rank\":10,\"score\":1", "\"rank\":5,\"score\":0.0"}) {
            HttpResponse<String> r = postAs(u, ReportsApiTest.PATH, ReportsApiTest.rep("\"kind\":\"not_similar\",\"perfumeKey\":\"Brand:perfume\"," + f));
            assertEquals(204, r.statusCode(), f + " " + r.body());
        }
        assertEquals(3, ReportsApiTest.count(u.id()));
    }

    /** QA-8a: 시간당 20회를 넘긴 21번째 신고는 429이고 저장되지 않는다 */
    @Test
    void reportOverTheHourlyLimitIsNotStored() throws Exception {
        TestAuth.User u = consentedUser();
        for (int i = 0; i < 20; i++) assertEquals(204, postAs(u, ReportsApiTest.PATH, ReportsApiTest.MIN).statusCode(), "신고 " + (i + 1));
        assertError(postAs(u, ReportsApiTest.PATH, ReportsApiTest.MIN), 429, "RATE_LIMITED");
        assertEquals(20, ReportsApiTest.count(u.id()));
    }
}
