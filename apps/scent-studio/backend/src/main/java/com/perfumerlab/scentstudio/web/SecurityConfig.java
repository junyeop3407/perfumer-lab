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
 * 순서: IP 정하기·막힌 IP 429 → 토큰 확인(서명·클레임) → 세션 확인(30초 보관) → 요청 제한(사용자/IP) → 주소 권한.
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
                            @Value("${scent.ip.trusted-proxy-hops:1}") int hops) throws Exception {
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
        AuthenticationFailureHandler tokenFailed = (req, res, ex) -> {
            if (!serverSide(ex)) limiter.recordAuthFailure(LimitFilters.ipKey(req));
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
                .addFilterBefore(new LimitFilters.BlockFilter(limiter, hops), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new LimitFilters.LimitFilter(limiter), BearerTokenAuthenticationFilter.class);
        return http.build();
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
            if (!sessions.alive(session, user)) throw new BadCredentialsException("session ended");
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
