package com.perfumerlab.scentstudio.limit;

import com.perfumerlab.scentstudio.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Collections;

/**
 * 요청 제한 필터 두 개 (보안 필터 사슬 안에 SecurityConfig가 넣는다. 일반 서블릿 필터로 따로 등록하지 않음).
 * ① IpFilter: 토큰 확인 전에. 요청한 사람 IP를 정해 요청 속성에 둔다(막힌 IP의 위조 토큰 429는 SecurityConfig.tokenFailed).
 * ② LimitFilter: 토큰 확인 뒤에. 로그인했으면 사용자 id 바구니(주소·메서드별), 아니면 IP 바구니(분당 60회).
 * /health/live(Render 상태 확인)는 제한하지 않는다.
 */
public final class LimitFilters {

    public static final String IP_ATTR = LimitFilters.class.getName() + ".ip";
    static final String LIVE = "/api/v1/health/live";

    private LimitFilters() {}

    /** 요청한 사람의 IP 바구니 열쇠 (IpFilter가 정한 값) */
    public static String ipKey(HttpServletRequest req) {
        Object v = req.getAttribute(IP_ATTR);
        return v instanceof String s ? s : ClientIp.limitKey(req.getRemoteAddr());
    }

    public static void tooMany(HttpServletResponse res, long seconds) throws IOException {
        res.setHeader("Retry-After", Long.toString(seconds));
        ApiError.RATE_LIMITED.write(res);
    }

    /** 요청한 사람 IP를 정해 요청 속성에 둔다. 막지 않는다(막힌 IP의 위조 토큰 429는 SecurityConfig.tokenFailed) */
    public static final class IpFilter extends OncePerRequestFilter {
        private final int hops;

        public IpFilter(int hops) {
            this.hops = hops;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            String ip = ClientIp.resolve(Collections.list(req.getHeaders("X-Forwarded-For")), req.getRemoteAddr(), hops);
            String key = ClientIp.limitKey(ip);
            req.setAttribute(IP_ATTR, key);
            // 막힌 IP라도 여기서 통째로 막지 않는다. 위조 토큰만 SecurityConfig.tokenFailed가 429(레드팀 묶음 9 1번)
            chain.doFilter(req, res);
        }
    }

    public static final class LimitFilter extends OncePerRequestFilter {
        private static final UrlPathHelper PATHS = new UrlPathHelper(); // 기본: 디코딩 + ;뒤 글자 제거
        private final RateLimiter limiter;

        public LimitFilter(RateLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            // 분류는 서버가 실제로 고르는 주소 기준으로(%65 같은 인코딩·;뒤 글자를 풀어서). 날 주소로 비교하면 인코딩으로 한도를 피함(보안 검사·남은 것 3)
            String path = PATHS.getPathWithinApplication(req);
            if (!LIVE.equals(path)) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                RateLimiter.Result r = auth instanceof JwtAuthenticationToken
                        ? limiter.tryConsume(classify(req.getMethod(), path), RateLimiter.USER + auth.getName())
                        : limiter.tryConsume(RateLimiter.Limit.ANON_IP, ipKey(req));
                if (!r.allowed()) {
                    tooMany(res, r.retryAfterSeconds());
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }

    /** 로그인한 사용자 요청이 어느 한도에 드는지 (server_design.md 4절 표) */
    static RateLimiter.Limit classify(String method, String path) {
        return switch (method) {
            case "GET", "HEAD", "OPTIONS" -> RateLimiter.Limit.READ;
            default -> {
                if ("DELETE".equals(method) && "/api/v1/me".equals(path)) yield RateLimiter.Limit.DELETE_ACCOUNT;
                if ("POST".equals(method) && "/api/v1/me/preferences/merge".equals(path)) yield RateLimiter.Limit.MERGE;
                if ("POST".equals(method) && "/api/v1/reports".equals(path)) yield RateLimiter.Limit.REPORT;
                yield RateLimiter.Limit.WRITE;
            }
        };
    }
}
