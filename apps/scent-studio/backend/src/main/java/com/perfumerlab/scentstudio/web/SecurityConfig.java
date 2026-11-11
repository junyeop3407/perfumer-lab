package com.perfumerlab.scentstudio.web;

import com.perfumerlab.scentstudio.auth.SessionChecker;
import com.perfumerlab.scentstudio.limit.LimitFilters;
import com.perfumerlab.scentstudio.limit.RateLimiter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 로그인·요청 제한 (server_design.md 3·4절). 세션·쿠키·CSRF 없음(앱이 토큰을 보내는 API 서버).
 * 순서: IP 정하기 → 토큰 확인(서명·클레임, 막힌 IP의 위조 토큰은 429) → 세션 확인(30초 보관) → 요청 제한(사용자/IP) → 주소 권한.
 * 토큰 실패는 이유와 상관없이 같은 401 응답. 보낸 토큰이 틀린 것만 IP별로 세어 1분 20번이면 5분 막는다. DB·키 목록을 못 읽으면 503(실패로 안 셈).
 */
@Configuration
public class SecurityConfig {

    static final Set<String> PUBLIC_GET = Set.of("/api/v1/health", "/api/v1/health/live", "/api/v1/_debug/mem", "/api/v1/_debug/ip");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, RateLimiter limiter, SessionChecker sessions,
                            @Value("${scent.ip.trusted-proxy-hops:1}") int hops,
                            @Value("${springdoc.api-docs.enabled:false}") boolean openapi) throws Exception {
        if (hops < 1 || hops > 5) throw new IllegalStateException("TRUSTED_PROXY_HOPS는 1~5");
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> {
            if (serverSide(ex)) {
                ApiError.UNAVAILABLE.write(res); // 우리 쪽 문제(DB·키 목록) → 로그아웃시키지 않음
                return;
            }
            res.setHeader("WWW-Authenticate", "Bearer");
            ApiError.UNAUTHORIZED.write(res);
        };
        // 보낸 토큰이 확인에 실패했을 때만 IP 실패로 센다. 토큰 없이 보호 주소를 부른 401은 세지 않는다
        // (다른 사이트가 방문자 브라우저로 불러 같은 공유기 사용자들을 막게 만들 수 있음, 리뷰 묶음 3 QA-1)
        // 막힌 IP에서도 맞는 토큰·토큰 없는 요청은 받는다. 막는 것은 위조·깨진 토큰뿐(통신사 공유 IP·공용 와이파이의 정상 사용자를
        // 같이 막지 않게, 레드팀 묶음 9 1번·사장 결정 2026-11-11). 서명이 맞는 토큰의 만료·로그아웃은 세지 않고 401 그대로(앱이 새로 로그인)
        AuthenticationFailureHandler tokenFailed = (req, res, ex) -> {
            String ip = LimitFilters.ipKey(req);
            if (forged(ex)) {
                long blocked = limiter.blockedSeconds(ip);
                if (blocked > 0) {
                    LimitFilters.tooMany(res, blocked);
                    return;
                }
                limiter.recordAuthFailure(ip);
            } else if (!serverSide(ex)) {
                // 세지 않는 실패(만료·로그아웃)도 로그인 없는 요청처럼 IP 분당 60을 쓴다(로그아웃 토큰으로 DB 세션 조회를 쏟지 못하게, QA 묶음 9)
                RateLimiter.Result r = limiter.tryConsume(RateLimiter.Limit.ANON_IP, ip);
                if (!r.allowed()) {
                    LimitFilters.tooMany(res, r.retryAfterSeconds());
                    return;
                }
            }
            entryPoint.commence(req, res, ex);
        };

        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .anonymous(a -> { })
                .authorizeHttpRequests(a -> a
                        // 이미 처리 중인 요청의 오류 응답(406·500 등)이 401로 바뀌지 않게. 오류 내용은 숨김(리뷰 묶음 1 QA-2)
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/live").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/_debug/mem", "/api/v1/_debug/ip").permitAll() // 컨트롤러가 토큰 확인
                        // 로그인 없이도 부름(설치 id로). 토큰을 보내면 확인하고, 틀린 토큰은 401(익명으로 넘기지 않음)
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/latest", "/api/v1/catalog/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/events").permitAll()
                        // OpenAPI 문서: 켰을 때(테스트·내 컴퓨터)만 로그인 없이. 운영은 꺼져 있어 401 그대로(묶음 9)
                        .requestMatchers(req -> openapi && "GET".equals(req.getMethod()) && "/api/v1/openapi".equals(req.getRequestURI())).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(resolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(j -> j.jwtAuthenticationConverter(sessionChecked(sessions)))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                // 기본은 서버 쪽 실패를 다시 던져 500이 된다 → 직접 처리(실패 세기 + 401/503)
                                filter.setAuthenticationFailureHandler(tokenFailed);
                                return filter;
                            }
                        }))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new LimitFilters.IpFilter(hops), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new LimitFilters.LimitFilter(limiter), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** 위조·깨진 토큰인가: 서버 쪽 문제(503)도, 서명은 맞는데 클레임(만료 등)이 틀린 것도, 로그아웃(세션 끝남)도 아닌 것 */
    static boolean forged(Exception ex) {
        if (serverSide(ex) || ex instanceof SessionEnded) return false;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof JwtValidationException) return false; // 서명 확인을 통과한 뒤 클레임 검사에서 떨어짐 = Supabase가 만든 토큰
        }
        return true;
    }

    /** 서명·클레임은 맞지만 세션이 끝난 토큰(로그아웃·계정 삭제) */
    static final class SessionEnded extends BadCredentialsException {
        SessionEnded() {
            super("session ended");
        }
    }

    static boolean serverSide(Exception ex) {
        return ex instanceof SessionChecker.Unavailable || ex instanceof AuthenticationServiceException;
    }

    /** Authorization 헤더만 읽는다(주소의 access_token은 안 읽음). 로그인 없이 부르는 주소에서는 토큰을 무시한다. */
    static BearerTokenResolver resolver() {
        DefaultBearerTokenResolver d = new DefaultBearerTokenResolver();
        return (HttpServletRequest req) -> PUBLIC_GET.contains(req.getRequestURI()) && "GET".equals(req.getMethod()) ? null : d.resolve(req);
    }

    /** 서명·클레임이 맞은 토큰 → 세션이 살아 있을 때만 로그인으로 인정. 이름 = 사용자 id(sub) */
    static Converter<Jwt, AbstractAuthenticationToken> sessionChecked(SessionChecker sessions) {
        return jwt -> {
            UUID user = UUID.fromString(jwt.getSubject());
            UUID session = UUID.fromString(jwt.getClaimAsString("session_id"));
            if (!sessions.alive(session, user)) throw new SessionEnded();
            return new JwtAuthenticationToken(jwt, List.of(), user.toString());
        };
    }

    /** Spring이 기본 사용자와 임시 비밀번호를 만들어 로그에 찍지 않게, 아무도 없는 사용자 목록을 둔다. */
    @Bean
    UserDetailsService noUsers() {
        return username -> {
            throw new UsernameNotFoundException("no users");
        };
    }
}
