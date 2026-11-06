package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** 묶음 1: DB가 안 되면 health 503(서버는 뜬다), live는 200, 오류 내용·비밀번호는 응답·로그에 없음. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/nowhere",
        "spring.datasource.username=scent_server",
        "spring.datasource.password=" + HealthDbDownTest.FAKE_PASSWORD,
        "spring.datasource.hikari.connection-timeout=500"
})
@ExtendWith(OutputCaptureExtension.class)
class HealthDbDownTest extends ApiTestBase {

    static final String FAKE_PASSWORD = "downDbPasswordShouldNeverAppear1234567";

    @Test
    void healthIs503AndServerStillUp(CapturedOutput output) throws Exception {
        HttpResponse<String> r = get("/api/v1/health");
        assertEquals(503, r.statusCode());
        assertTrue(r.body().contains("\"db\":\"down\""), r.body());
        assertFalse(r.body().contains(FAKE_PASSWORD));
        assertFalse(r.body().contains("jdbc:"));
        assertEquals(200, get("/api/v1/health/live").statusCode());
        assertFalse(output.getAll().contains(FAKE_PASSWORD), "DB 비밀번호가 로그에 나오면 안 된다");
        assertFalse(output.getAll().contains("generated security password"), "Spring 기본 임시 비밀번호를 만들면 안 된다");
    }
}
