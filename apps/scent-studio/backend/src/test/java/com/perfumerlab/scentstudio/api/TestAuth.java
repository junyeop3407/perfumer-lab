package com.perfumerlab.scentstudio.api;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 서버 테스트용 가짜 Supabase 로그인: EC P-256 키로 ES256 토큰을 만들고, 공개 키 목록(JWKS)을 이 컴퓨터 안의 작은 HTTP 서버로 내준다.
 * 사용자·세션은 테스트 DB(auth.users, auth.sessions)에 넣는다. 실제 Supabase 토큰과 같은 모양(iss·aud·role·session_id·is_anonymous).
 */
final class TestAuth {
    static final String ISSUER = "https://testref.supabase.co/auth/v1";
    static final String KID = "test-key-1";
    static final ECKey KEY = newKey(KID);
    static final ECKey OTHER_KEY = newKey(KID); // 같은 kid, 다른 키 (서명 다름)
    static final AtomicInteger JWKS_HITS = new AtomicInteger(); // 서버가 키 목록을 몇 번 받으러 왔나
    private static HttpServer jwks;

    private TestAuth() {}

    static ECKey newKey(String kid) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static synchronized String jwksUri() {
        if (jwks == null) {
            try {
                jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                jwks.createContext("/auth/v1/.well-known/jwks.json", ex -> {
                    JWKS_HITS.incrementAndGet();
                    ex.getResponseHeaders().add("Content-Type", "application/json");
                    ex.sendResponseHeaders(200, body.length);
                    ex.getResponseBody().write(body);
                    ex.close();
                });
                jwks.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return "http://127.0.0.1:" + jwks.getAddress().getPort() + "/auth/v1/.well-known/jwks.json";
    }

    /** 로그인한 사용자 한 명 (DB에 사용자 + 살아 있는 세션) */
    record User(UUID id, UUID session) {
        String token() {
            return TestAuth.token(this, c -> { });
        }

        String bearer() {
            return "Bearer " + token();
        }
    }

    static User newUser() {
        UUID id = UUID.randomUUID(), session = UUID.randomUUID();
        admin("insert into auth.users (id, email) values (?, ?)", id, id + "@test.invalid");
        admin("insert into auth.sessions (id, user_id, not_after) values (?, ?, null)", session, id);
        return new User(id, session);
    }

    static void deleteSession(UUID session) {
        admin("delete from auth.sessions where id = ?", session);
    }

    static void admin(String sql, Object... args) {
        try (Connection c = DriverManager.getConnection(TestDb.jdbcUrl()); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            p.execute();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 실제 Supabase와 같은 모양의 클레임. edit로 하나씩 바꿔 잘못된 토큰을 만든다. */
    static JWTClaimsSet.Builder claims(User u) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject(u.id().toString()).audience("authenticated")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("role", "authenticated").claim("aal", "aal1").claim("session_id", u.session().toString())
                .claim("is_anonymous", false).claim("email", u.id() + "@test.invalid");
    }

    static String token(User u, Consumer<JWTClaimsSet.Builder> edit) {
        return sign(KEY, u, edit);
    }

    static String sign(ECKey key, User u, Consumer<JWTClaimsSet.Builder> edit) {
        try {
            JWTClaimsSet.Builder b = claims(u);
            edit.accept(b);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), b.build());
            jwt.sign(new ECDSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hs256(User u) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KID).type(JOSEObjectType.JWT).build(), claims(u).build());
            jwt.sign(new MACSigner("hs256-secret-that-is-long-enough-0123456789".getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String none(User u) {
        return new PlainJWT(claims(u).build()).serialize();
    }
}
