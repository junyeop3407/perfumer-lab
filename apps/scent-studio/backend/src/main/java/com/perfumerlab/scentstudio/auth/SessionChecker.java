package com.perfumerlab.scentstudio.auth;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 토큰의 세션이 아직 살아 있나 (server_design.md 3-1). DB 함수 app.session_alive만 부르고, 결과를 30초 보관한다
 * → 로그아웃·계정 삭제 뒤 늦어도 30초 안에 옛 토큰이 막힌다. 보관은 최대 1만 개(넘으면 지난 것부터, 그래도 넘으면 비움).
 */
@Component
public class SessionChecker {

    static final long KEEP_MS = 30_000;
    static final int MAX_ENTRIES = 10_000;

    private record Entry(boolean alive, long until) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public SessionChecker(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean alive(UUID sessionId, UUID userId) {
        String key = sessionId + ":" + userId;
        long now = clock.millis();
        Entry e = cache.get(key);
        if (e != null && e.until() > now) return e.alive();
        boolean alive;
        try {
            alive = Boolean.TRUE.equals(jdbc.sql("select app.session_alive(?, ?)").params(sessionId, userId).query(Boolean.class).single());
        } catch (DataAccessException ex) {
            throw new Unavailable(); // 401이 아니라 503 (DB가 잠시 안 될 뿐, 로그아웃시키지 않는다)
        }
        if (cache.size() >= MAX_ENTRIES) cache.values().removeIf(x -> x.until() <= now);
        if (cache.size() >= MAX_ENTRIES) cache.clear();
        cache.put(key, new Entry(alive, now + KEEP_MS));
        return alive;
    }

    /** 세션을 확인할 수 없음(DB 안 됨) → 503 */
    public static class Unavailable extends AuthenticationException {
        public Unavailable() {
            super("session check unavailable");
        }
    }
}
