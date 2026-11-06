package com.perfumerlab.scentstudio.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

/**
 * 묶음 1: health·측정 주소만 열고 나머지는 모두 401. 로그인 토큰 확인은 묶음 3에서 더한다.
 * 세션·쿠키·CSRF 없이(앱이 토큰을 보내는 API 서버).
 */
@Configuration
public class SecurityConfig {

    static final String UNAUTHORIZED = "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요해요\"}}";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/live").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/_debug/mem", "/api/v1/_debug/ip").permitAll() // 컨트롤러가 토큰 확인
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.getWriter().write(UNAUTHORIZED);
                }));
        return http.build();
    }

    /** Spring이 기본 사용자와 임시 비밀번호를 만들어 로그에 찍지 않게, 아무도 없는 사용자 목록을 둔다. */
    @Bean
    UserDetailsService noUsers() {
        return username -> {
            throw new UsernameNotFoundException("no users");
        };
    }
}
