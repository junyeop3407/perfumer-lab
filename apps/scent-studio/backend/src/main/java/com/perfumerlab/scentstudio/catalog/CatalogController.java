package com.perfumerlab.scentstudio.catalog;

import com.perfumerlab.scentstudio.limit.LimitFilters;
import com.perfumerlab.scentstudio.limit.RateLimiter;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import com.perfumerlab.scentstudio.web.InstallId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 카탈로그 (server_design.md 5절). 공개 주소 없이 서버를 거쳐서만 받는다.
 * - GET /catalog/latest: 지금 버전 정보(버전·엔진·sha256·크기·최소 앱 버전). 주소·파일 이름은 알려 주지 않는다
 * - GET /catalog/{version}: X-Install-Id 필요. IP 시간당 10회 + install_id 하루 3회(버전을 찍어 보는 것도 셈).
 *   처음 요청 때 Storage에서 받아 sha256이 맞으면 메모리에 보관(최근 2개 버전), 다음부터는 메모리에서 준다.
 * 알려진 한계: install_id는 앱이 지어내는 값이라 실제 방어는 IP 제한. 앱 안에도 카탈로그가 들어 있어 완전한 보호는 아니다.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    public record Latest(String version, int engineVersion, String sha256, int sizeBytes, String minAppVersion) {}

    private final CatalogStore store;
    private final RateLimiter limiter;
    // 보관한 파일(최근 2개). 읽기는 잠금 없이, 바꿀 때는 통째로 새 지도로(리뷰 묶음 6 Gemini-1: 받는 동안 보관본까지 기다리던 것)
    private volatile Map<String, byte[]> files = Map.of();
    private final Object fetching = new Object();
    // 받기에 실패한 파일은 30초 동안 다시 부르지 않고 바로 503(장애 중 요청마다 최대 30초씩 줄 서지 않게, 리뷰 묶음 6 QA-2)
    private final Map<String, Instant> failedUntil = new java.util.concurrent.ConcurrentHashMap<>();
    static final Duration RETRY = Duration.ofSeconds(30);
    private final Clock clock;

    public CatalogController(CatalogStore store, RateLimiter limiter, Clock clock) {
        this.store = store;
        this.limiter = limiter;
        this.clock = clock;
    }

    @GetMapping(path = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public Latest latest() {
        CatalogStore.Release r = store.current().orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
        return new Latest(r.version(), r.engineVersion(), r.sha256(), r.sizeBytes(), r.minAppVersion());
    }

    @GetMapping(path = "/{version}")
    public ResponseEntity<byte[]> file(HttpServletRequest req, @PathVariable("version") String version) {
        UUID install = InstallId.from(req);
        limiter.require(RateLimiter.Limit.CATALOG_IP, LimitFilters.ipKey(req));
        limiter.require(RateLimiter.Limit.CATALOG_INSTALL, install.toString());
        CatalogStore.Release r = store.byVersion(version).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
        String id = r.version() + "|" + r.sha256(); // 같은 버전을 다시 올려 해시가 바뀌면 새로 받음
        byte[] body = files.get(id);
        if (body == null) {
            try {
                body = fetch(id, r);
            } catch (ApiException e) {
                limiter.refund(RateLimiter.Limit.CATALOG_INSTALL, install.toString()); // 우리 쪽 문제는 하루 3회에서 빼 줌(IP 한도는 그대로, 리뷰 QA-5)
                throw e;
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/gzip"))
                .header("Cache-Control", "private, no-store")
                .eTag("\"" + r.sha256() + "\"")
                .body(body);
    }

    /** Storage에서 받아 보관. 처음 몇 요청이 한꺼번에 와도 Storage는 한 번만. 보관본 내려받기는 여기서 기다리지 않음 */
    private byte[] fetch(String id, CatalogStore.Release r) {
        Instant until = failedUntil.get(id);
        if (until != null && clock.instant().isBefore(until)) throw new ApiException(ApiError.UNAVAILABLE);
        synchronized (fetching) {
            byte[] body = files.get(id);
            if (body != null) return body;
            try {
                body = store.fetch(r.storagePath(), r.sha256(), r.sizeBytes());
            } catch (ApiException e) {
                failedUntil.put(id, clock.instant().plus(RETRY));
                throw e;
            }
            failedUntil.remove(id);
            Map<String, byte[]> next = new LinkedHashMap<>(files);
            next.put(id, body);
            while (next.size() > 2) next.remove(next.keySet().iterator().next());
            files = java.util.Collections.unmodifiableMap(next); // 넣은 순서 유지(오래된 것부터 뺌)
            return body;
        }
    }

}
