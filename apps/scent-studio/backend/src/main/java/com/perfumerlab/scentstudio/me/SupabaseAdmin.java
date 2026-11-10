package com.perfumerlab.scentstudio.me;

import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Supabase 관리 API로 계정 삭제 (db_design.md 4-1). 서버 전용 키(render-server, sb_secret_…)는 환경 변수로만 받고
 * 응답·로그·오류 메시지에 남기지 않는다. 관리 API의 응답 본문도 읽지 않는다(키를 되돌려 적는 경우가 있어도 새지 않게).
 */
@Component
public class SupabaseAdmin {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAdmin.class);

    private final String base;
    private final String key;
    private final Duration timeout;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    public SupabaseAdmin(@Value("${scent.admin.url:}") String url, @Value("${scent.admin.secret-key:}") String key,
                         @Value("${scent.admin.timeout:10s}") Duration timeout) {
        this.base = url.strip().replaceAll("/+$", "");
        this.timeout = timeout;
        String k = key.strip();
        if (!k.isEmpty() && !k.startsWith("sb_secret_")) {
            log.warn("관리 API 서버 키 모양이 sb_secret_가 아니어서 쓰지 않습니다(render-server 키를 넣어 주세요)");
            k = "";
        }
        if (k.isEmpty() || base.isEmpty()) log.warn("SUPABASE_URL·SUPABASE_SECRET_KEY가 없어 계정 삭제를 할 수 없습니다(503)");
        this.key = k;
    }

    /** 계정 삭제. 지웠거나 이미 없으면 정상 종료, 그 밖(실패·시간 초과·키 없음)은 503 — 이때 Supabase는 지우지 않은 것으로 본다 */
    public void deleteUser(UUID user) {
        if (key.isEmpty() || base.isEmpty()) throw new ApiException(ApiError.UNAVAILABLE);
        CompletableFuture<HttpResponse<Void>> call = null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/auth/v1/admin/users/" + user))
                    .timeout(timeout).DELETE()
                    .header("apikey", key).header("Authorization", "Bearer " + key).build();
            call = http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
            int status = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS).statusCode();
            if (status == 200 || status == 204 || status == 404) return; // 404 = 이미 없음(대시보드에서 먼저 지움)
            log.warn("관리 API 계정 삭제 실패: 상태 {}", status);
            throw new ApiException(ApiError.UNAVAILABLE);
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiError.UNAVAILABLE);
        } catch (Exception e) {
            log.warn("관리 API 계정 삭제 중 오류: {}", e.getClass().getSimpleName()); // 메시지는 남기지 않음
            throw new ApiException(ApiError.UNAVAILABLE);
        } finally {
            if (call != null && !call.isDone()) call.cancel(true);
        }
    }
}
