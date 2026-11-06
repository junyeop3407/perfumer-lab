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
 * 로그인 없이 부르는 주소라, 몰아 불러도 DB 연결(최대 4개)을 묶지 않게 결과를 5초 동안 보관하고
 * 동시에 와도 DB에는 한 번만 묻는다(리뷰 묶음 1 QA-1).
 * /health/live: 서버 프로세스만 (Render 상태 확인용, DB가 잠시 안 돼도 서버를 다시 띄우지 않게).
 * 오류 내용·접속 정보는 응답에 넣지 않는다.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    static final long CACHE_MS = 5_000;

    private final JdbcClient jdbc;
    private volatile boolean lastOk;
    private volatile long lastCheckedAt = 0; // 0이면 첫 요청에서 바로 확인 (MIN_VALUE는 빼기가 넘쳐서 안 됨)

    public HealthController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        boolean ok = dbOk();
        return ok ? ResponseEntity.ok(Map.of("status", "ok", "db", "ok"))
                  : ResponseEntity.status(503).body(Map.of("status", "unavailable", "db", "down"));
    }

    private boolean dbOk() {
        long now = System.currentTimeMillis();
        if (now - lastCheckedAt < CACHE_MS) return lastOk;
        synchronized (this) {
            if (System.currentTimeMillis() - lastCheckedAt < CACHE_MS) return lastOk; // 기다리는 동안 다른 요청이 확인함
            boolean ok;
            try {
                Integer one = jdbc.sql("select 1").query(Integer.class).single();
                ok = one != null && one == 1;
            } catch (DataAccessException e) {
                ok = false; // 내용은 응답·로그에 남기지 않는다(접속 정보가 섞일 수 있음)
            }
            lastOk = ok;
            lastCheckedAt = System.currentTimeMillis();
            return ok;
        }
    }

    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "ok");
    }
}
