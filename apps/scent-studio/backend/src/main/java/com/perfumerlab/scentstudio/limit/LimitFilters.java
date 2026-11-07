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

import java.io.IOException;
import java.util.Collections;

/**
 * 요청 제한 필터 두 개 (보안 필터 사슬 안에 SecurityConfig가 넣는다. 일반 서블릿 필터로 따로 등록하지 않음).
 * ① BlockFilter: 토큰 확인 전에. 요청한 사람 IP를 정해 요청 속성에 두고, 401이 잦아 막힌 IP면 바로 429.
 * ② LimitFilter: 토큰 확인 뒤에. 로그인했으면 사용자 id 바구니(주소·메서드별), 아니면 IP 바구니(분당 60회).
 * /health/live(Render 상태 확인)는 제한하지 않는다.
 */
public final class LimitFilters {

    public static final String IP_ATTR = LimitFilters.class.getName() + ".ip";
    static final String LIVE = "/api/v1/health/live";

    private LimitFilters() {}

    /** 요청한 사람의 IP 바구니 열쇠 (BlockFilter가 정한 값) */
    public static String ipKey(HttpServletRequest req) {
        Object v = req.getAttribute(IP_ATTR);
        return v instanceof String s ? s : ClientIp.limitKey(req.getRemoteAddr());
    }

    static void tooMany(HttpServletResponse res, long seconds) throws IOException {
        res.setHeader("Retry-After", Long.toString(seconds));
        ApiError.RATE_LIMITED.write(res);
    }

    public static final class BlockFilter extends OncePerRequestFilter {
        private final RateLimiter limiter;
        private final int hops;

        public BlockFilter(RateLimiter limiter, int hops) {
            this.limiter = limiter;
            this.hops = hops;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            String ip = ClientIp.resolve(Collections.list(req.getHeaders("X-Forwarded-For")), req.getRemoteAddr(), hops);
            String key = ClientIp.limitKey(ip);
            req.setAttribute(IP_ATTR, key);
            if (!LIVE.equals(req.getRequestURI())) {
                long blocked = limiter.blockedSeconds(key);
                if (blocked > 0) {
                    tooMany(res, blocked);
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }

    public static final class LimitFilter extends OncePerRequestFilter {
        private final RateLimiter limiter;

        public LimitFilter(RateLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            String path = req.getRequestURI();
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
