package com.perfumerlab.scentstudio.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Supabase 로그인 토큰 확인 (server_design.md 3절). 직접 해독하지 않고 Spring Security + Nimbus로.
 * - 서명: 공개 키 목록(JWKS)의 ES256만 (none·HS256·RS256 등 거부, 알고리즘은 서버가 고정)
 * - iss = https://<참조값>.supabase.co/auth/v1, aud에 authenticated, exp 필수(시계 오차 30초)
 * - role = authenticated, is_anonymous = false(값이 꼭 있어야 함), sub·session_id는 uuid 모양
 * 세션이 살아 있는지는 SessionChecker가 따로 본다(토큰 → 로그인 정보로 바꿀 때).
 */
@Configuration
public class SupabaseJwt {

    private static final Logger log = LoggerFactory.getLogger(SupabaseJwt.class);
    private static final Pattern LOCAL = Pattern.compile("http://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/.*");
    private static final Pattern SUPABASE_URL = Pattern.compile("https://[a-z0-9]{1,40}\\.supabase\\.co");
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    public JwtDecoder jwtDecoder(@Value("${scent.auth.supabase-url:}") String supabaseUrl,
                          @Value("${scent.auth.jwks-uri:}") String jwksUriOverride,
                          Clock clock) {
        String url = supabaseUrl.strip().replaceAll("/+$", "");
        if (!SUPABASE_URL.matcher(url).matches()) {
            // 서버는 뜨게 두고(health는 됨) 로그인 토큰은 모두 거절한다
            log.warn("SUPABASE_URL이 없거나 https://<참조값>.supabase.co 모양이 아니라 로그인 토큰을 모두 거절합니다");
            return token -> {
                throw new BadJwtException("auth not configured");
            };
        }
        String issuer = url + "/auth/v1";
        String jwksUri = issuer + "/.well-known/jwks.json";
        if (!jwksUriOverride.isBlank()) {
            // 테스트용(이 컴퓨터 안의 가짜 키 서버)만 허용. 운영에서 다른 서버의 키를 믿게 되는 실수를 막는다(리뷰 묶음 3)
            if (!LOCAL.matcher(jwksUriOverride.strip()).matches()) throw new IllegalStateException("scent.auth.jwks-uri는 테스트(127.0.0.1)에서만 바꿀 수 있습니다");
            jwksUri = jwksUriOverride.strip();
        }
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor(jwksUri));
        decoder.setJwtValidator(validator(issuer, clock));
        return decoder;
    }

    /**
     * 키 목록(JWKS): 10분 보관, 다시 받기는 1분에 한 번까지(모르는 kid 토큰을 몰아 보내 Supabase 호출을 쏟아내지 못하게, 리뷰 묶음 3 QA-4).
     * 1분 안에 또 모르는 kid가 오면 받으러 가지 않고 "맞는 키 없음" → 401. 키 목록을 못 읽는 동안은 503(로그아웃시키지 않음),
     * 이미 받아 둔 목록이 있으면 다시 받기가 실패해도 1시간까지 그 목록을 쓴다.
     * 서명 알고리즘은 ES256만. 클레임 검사는 아래 validator가 한다.
     */
    static DefaultJWTProcessor<SecurityContext> processor(String jwksUri) {
        try {
            JWKSource<SecurityContext> keys = JWKSourceBuilder.create(new URL(jwksUri), new DefaultResourceRetriever(3_000, 3_000, 50 * 1024))
                    .cache(Duration.ofMinutes(10).toMillis(), 3_000)
                    .refreshAheadCache(false)
                    .rateLimited(Duration.ofMinutes(1).toMillis())
                    .outageTolerant(Duration.ofHours(1).toMillis()) // 다시 받기가 실패하면 갖고 있던 목록을 1시간까지 계속 씀
                    .retrying(false)
                    .build();
            JWSVerificationKeySelector<SecurityContext> es256 = new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, keys);
            DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
            AtomicBoolean lastOk = new AtomicBoolean(false); // 마지막으로 키 목록을 제대로 읽었나
            p.setJWSKeySelector((header, ctx) -> {
                try {
                    List<java.security.Key> found = es256.selectJWSKeys(header, ctx);
                    lastOk.set(true);
                    return found;
                } catch (RateLimitReachedException e) {
                    if (lastOk.get()) return List.of(); // 키 목록은 멀쩡한데 모르는 kid → 맞는 키 없음(401)
                    throw e;                            // 키 목록을 못 읽는 중 → 503 (로그아웃시키지 않음)
                } catch (KeySourceException e) {
                    lastOk.set(false);
                    throw e;
                }
            });
            p.setJWTClaimsSetVerifier((claims, ctx) -> { }); // 클레임은 Spring validator가
            return p;
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("키 목록 주소 모양이 잘못됐습니다", e);
        }
    }

    static OAuth2TokenValidator<Jwt> validator(String issuer, Clock clock) {
        JwtTimestampValidator time = new JwtTimestampValidator(CLOCK_SKEW);
        time.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> all = List.of(
                time,
                new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<Object>(JwtClaimNames.AUD, aud -> aud instanceof Collection<?> c && c.contains("authenticated")),
                new JwtClaimValidator<Object>("role", "authenticated"::equals),
                new JwtClaimValidator<Object>("is_anonymous", Boolean.FALSE::equals),
                uuidClaim(JwtClaimNames.SUB),
                uuidClaim("session_id"));
        return new DelegatingOAuth2TokenValidator<>(all);
    }

    static OAuth2TokenValidator<Jwt> uuidClaim(String name) {
        return jwt -> uuidOrNull(jwt.getClaims().get(name)) != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    static UUID uuidOrNull(Object v) {
        if (!(v instanceof String s) || s.length() != 36) return null;
        try {
            UUID u = UUID.fromString(s);
            return u.toString().equalsIgnoreCase(s) ? u : null; // "1-1-1-1-1" 같은 느슨한 모양 거부
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
