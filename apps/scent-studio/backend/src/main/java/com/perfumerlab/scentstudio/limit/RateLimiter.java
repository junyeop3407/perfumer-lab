package com.perfumerlab.scentstudio.limit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 요청 횟수 제한 (server_design.md 4절 표, Bucket4j + 서버 메모리. Render 1대라 충분).
 * 바구니는 창(1분·1시간)이 끝나면 한꺼번에 다시 찬다. 바구니·막은 IP 목록은 각각 최대 5만 개까지만 기억한다
 * (IP를 바꿔 가며 메모리를 채우지 못하게, 넘으면 가장 오래 안 쓴 것부터 버림).
 */
@Component
public class RateLimiter {

    public enum Limit {
        WRITE(30, Duration.ofMinutes(1)),          // 저장·찜·시향 기록·취향 바꾸기
        MERGE(5, Duration.ofMinutes(1)),           // 취향 합치기
        REPORT(20, Duration.ofHours(1)),           // 오류 신고
        DELETE_ACCOUNT(3, Duration.ofHours(1)),    // 계정 삭제
        READ(120, Duration.ofMinutes(1)),          // 읽기
        ANON_IP(60, Duration.ofMinutes(1)),        // 로그인 없이 (IP)
        AUTH_FAIL(20, Duration.ofMinutes(1)),      // 토큰 확인 실패 (IP), 다 쓰면 5분 막음
        EVENTS(10, Duration.ofMinutes(1)),         // 사용 기록 묶음 (install_id + IP 짝)
        EVENTS_IP(30, Duration.ofMinutes(1)),      // 사용 기록 묶음 (IP만) — 설치 id를 바꿔 가며 보내도(리뷰 묶음 6 QA-4)
        CATALOG_INSTALL(3, Duration.ofDays(1)),    // 카탈로그 파일 (install_id)
        CATALOG_IP(10, Duration.ofHours(1));       // 카탈로그 파일 (IP) — install_id는 앱이 지어내는 값이라 실제 방어는 이쪽

        final int count;
        final Duration window;

        Limit(int count, Duration window) {
            this.count = count;
            this.window = window;
        }
    }

    static final Duration BLOCK = Duration.ofMinutes(5);
    static final int MAX_KEYS = 50_000;     // 지도마다. 바구니 하나 약 0.3KB(추정) → 지도 하나 최대 약 15MB
    public static final String USER = "user:"; // 사용자 바구니 열쇠 앞머리

    /** 허용이면 0, 아니면 다시 시도할 때까지 초(1 이상) */
    public record Result(long retryAfterSeconds) {
        public boolean allowed() {
            return retryAfterSeconds == 0;
        }
    }

    private final Clock clock;
    private final TimeMeter time;
    // 사용자 바구니와 IP 바구니를 따로 둔다: 로그인 없는 요청이 IP를 바꿔 가며 몰려와도 사용자 한도가 밀려나 풀리지 않게(리뷰 묶음 3 QA-2)
    private final Map<String, Bucket> userBuckets = lru();
    private final Map<String, Bucket> ipBuckets = lru();
    private final Map<String, Long> blockedUntil = lru();

    public RateLimiter(Clock clock) {
        this.clock = clock;
        this.time = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return clock.millis() * 1_000_000L;
            }

            @Override
            public boolean isWallClockBased() {
                return true;
            }
        };
    }

    private static <V> Map<String, V> lru() {
        return new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > MAX_KEYS;
            }
        };
    }

    private Bucket bucket(Limit limit, String id) {
        Map<String, Bucket> buckets = id.startsWith(USER) ? userBuckets : ipBuckets;
        synchronized (buckets) {
            return buckets.computeIfAbsent(limit.name() + ":" + id, k -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(limit.count).refillIntervally(limit.count, limit.window).build())
                    .withCustomTimePrecision(time)
                    .build());
        }
    }

    public Result tryConsume(Limit limit, String id) {
        ConsumptionProbe p = bucket(limit, id).tryConsumeAndReturnRemaining(1);
        return p.isConsumed() ? new Result(0) : new Result(seconds(p.getNanosToWaitForRefill()));
    }

    /** 한도 안이면 그대로, 넘으면 429(Retry-After) 예외 */
    public void require(Limit limit, String id) {
        Result r = tryConsume(limit, id);
        if (!r.allowed()) throw new ApiException(ApiError.RATE_LIMITED, r.retryAfterSeconds());
    }

    /** 우리 쪽 문제로 실패한 요청은 한 번 돌려준다(예: 카탈로그 503이면 설치 id 하루 3회에서 빼 줌, 리뷰 묶음 6 QA-5) */
    public void refund(Limit limit, String id) {
        bucket(limit, id).addTokens(1);
    }

    /** 토큰 확인 실패 1번 기록. 1분에 20번째 실패면 그 IP를 5분 막는다. */
    public void recordAuthFailure(String ipKey) {
        ConsumptionProbe p = bucket(Limit.AUTH_FAIL, ipKey).tryConsumeAndReturnRemaining(1);
        if (!p.isConsumed() || p.getRemainingTokens() == 0) {
            synchronized (blockedUntil) {
                blockedUntil.put(ipKey, clock.millis() + BLOCK.toMillis());
            }
        }
    }

    /** 막힌 IP면 남은 초, 아니면 0 */
    public long blockedSeconds(String ipKey) {
        Long until;
        synchronized (blockedUntil) {
            until = blockedUntil.get(ipKey);
        }
        if (until == null) return 0;
        long left = until - clock.millis();
        return left <= 0 ? 0 : Math.max(1, (left + 999) / 1000);
    }

    private static long seconds(long nanos) {
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }
}
