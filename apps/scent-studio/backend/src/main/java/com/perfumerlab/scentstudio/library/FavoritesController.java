package com.perfumerlab.scentstudio.library;

import com.perfumerlab.scentstudio.catalog.CatalogKeys;
import com.perfumerlab.scentstudio.me.Consents;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 찜 (db_design.md ③, 승인 D3). 향수는 key로(예 Brand:perfume).
 * - PUT: 이미 찜했으면 오류 없이 그대로 성공(204, 한도도 세지 않음). 카탈로그에 없는 key → 400 UNKNOWN_KEY
 * - DELETE: 없어도 204. 카탈로그에서 빠진 향수도 풀 수 있게 카탈로그는 확인하지 않는다(모양만)
 * - 1인당 300개(사용자 단위 잠금 안에서 세고 넣음) → 넘으면 409 LIMIT_REACHED
 */
@RestController
@RequestMapping("/api/v1/favorites")
public class FavoritesController {

    static final int LIMIT = 300, MAX_KEY = 200;

    public record Favorite(String perfumeKey, String createdAt) {}

    public record Favorites(List<Favorite> favorites) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Consents consents;
    private final CatalogKeys catalog;

    public FavoritesController(JdbcClient jdbc, TransactionTemplate tx, Consents consents, CatalogKeys catalog) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.consents = consents;
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Favorites list(JwtAuthenticationToken auth) {
        return new Favorites(jdbc.sql("select perfume_key, created_at from app.favorites where user_id = ? order by created_at desc, perfume_key")
                .param(UUID.fromString(auth.getName()))
                .query((rs, n) -> new Favorite(rs.getString("perfume_key"), Library.time(rs, "created_at"))).list());
    }

    @PutMapping(path = "/{perfumeKey}")
    public ResponseEntity<Void> add(JwtAuthenticationToken auth, @PathVariable("perfumeKey") String key) {
        UUID user = UUID.fromString(auth.getName());
        consents.requireComplete(user);
        if (!Library.key(key, MAX_KEY)) throw new ApiException(ApiError.BAD_REQUEST);
        if (!catalog.contains(CatalogKeys.Kind.PERFUME, key)) throw new ApiException(ApiError.UNKNOWN_KEY);
        tx.executeWithoutResult(s -> {
            Library.lock(jdbc, Library.Kind.FAVORITES, user);
            boolean already = jdbc.sql("select 1 from app.favorites where user_id = ? and perfume_key = ?").params(user, key)
                    .query(Integer.class).optional().isPresent();
            if (already) return;
            Library.requireRoom(jdbc, "favorites", user, LIMIT);
            jdbc.sql("insert into app.favorites (user_id, perfume_key) values (?, ?)").params(user, key).update();
        });
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/{perfumeKey}")
    public ResponseEntity<Void> remove(JwtAuthenticationToken auth, @PathVariable("perfumeKey") String key) {
        UUID user = UUID.fromString(auth.getName());
        consents.requireComplete(user);
        if (!Library.key(key, MAX_KEY)) throw new ApiException(ApiError.BAD_REQUEST);
        jdbc.sql("delete from app.favorites where user_id = ? and perfume_key = ?").params(user, key).update();
        return ResponseEntity.noContent().build();
    }
}
