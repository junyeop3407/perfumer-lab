package com.perfumerlab.scentstudio.me;

import com.perfumerlab.scentstudio.auth.SessionChecker;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * DELETE /me: 계정 삭제 (db_design.md 4-1, server_design.md 2절, 승인 G1·G2, 묶음 8).
 * 1. Supabase 관리 API로 토큰의 사용자 계정을 지운다(실패하면 아무것도 안 지워지고 503, 다시 시도 가능)
 * 2. DB 외래 키 규칙이 내 데이터를 한 번에 정리한다(사용 기록은 user_id만 비움)
 * 3. 그 사용자의 세션 확인 보관을 바로 지운다 → 같은 토큰이 바로 401 (사장 지시 2026-11-07)
 * 4. 서버가 읽을 수 있는 표에서 "남은 줄 0"을 확인하고 204
 * 동의 없어도 지울 수 있다. 시간당 3회는 요청 제한 필터(RateLimiter.Limit.DELETE_ACCOUNT).
 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final SupabaseAdmin admin;
    private final SessionChecker sessions;
    private final JdbcClient jdbc;

    public AccountController(SupabaseAdmin admin, SessionChecker sessions, JdbcClient jdbc) {
        this.admin = admin;
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(JwtAuthenticationToken auth) {
        UUID user = UUID.fromString(auth.getName());
        try {
            admin.deleteUser(user);
        } catch (ApiException e) {
            sessions.forget(user); // Supabase가 지웠는데 답만 못 받았을 수도 있다 → 다음 요청은 DB에 다시 묻게(리뷰 묶음 8 QA-2)
            throw e;
        }
        sessions.deleted(user);
        long left = jdbc.sql("select (select count(*) from app.user_preferences where user_id = :u) + (select count(*) from app.blends where user_id = :u)"
                        + " + (select count(*) from app.favorites where user_id = :u) + (select count(*) from app.tasting_feedback where user_id = :u)"
                        + " + (select count(*) from app.consents where user_id = :u)")
                .param("u", user).query(Long.class).single();
        if (left != 0) {
            log.error("계정 삭제 뒤 남은 줄이 있습니다: {}줄", left); // 사용자 id는 남기지 않음
            throw new ApiException(ApiError.INTERNAL);
        }
        return ResponseEntity.noContent().build();
    }
}
