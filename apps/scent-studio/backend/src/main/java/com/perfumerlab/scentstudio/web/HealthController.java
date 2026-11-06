package com.perfumerlab.scentstudio.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * /health: 서버와 DB가 살아 있나 (일시 정지 방지 예약 작업이 부름). DB가 안 되면 503.
 * /health/live: 서버 프로세스만 (Render 상태 확인용, DB가 잠시 안 돼도 서버를 다시 띄우지 않게).
 * 오류 내용·접속 정보는 응답에 넣지 않는다.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcClient jdbc;

    public HealthController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        try {
            Integer one = jdbc.sql("select 1").query(Integer.class).single();
            if (one != null && one == 1) return ResponseEntity.ok(Map.of("status", "ok", "db", "ok"));
        } catch (DataAccessException e) {
            // 내용은 응답·로그에 남기지 않는다(접속 정보가 섞일 수 있음)
        }
        return ResponseEntity.status(503).body(Map.of("status", "unavailable", "db", "down"));
    }

    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "ok");
    }
}
