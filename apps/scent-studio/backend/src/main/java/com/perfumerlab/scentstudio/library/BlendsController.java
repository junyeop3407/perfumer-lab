package com.perfumerlab.scentstudio.library;

import com.perfumerlab.scentstudio.catalog.CatalogKeys;
import com.perfumerlab.scentstudio.me.Consents;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 저장한 조합 (db_design.md ②, server_design.md 2절, 승인 D2·D5).
 * - 내 것만: 남의 id·없는 id·uuid가 아닌 id는 모두 404 NOT_FOUND(있는지 알려 주지 않음). 쿼리마다 user_id 조건을 같이 건다.
 * - 이름 1~40자, 노트 1~9개(층마다 3개, 같은 노트 한 번, 강도 weak/medium/strong), 계열 10개 → 틀리면 400 BAD_REQUEST
 * - 카탈로그에 없는 노트·계열 key → 400 UNKNOWN_KEY
 * - 1인당 100개(사용자 단위 잠금 안에서 세고 넣음) → 넘으면 409 LIMIT_REACHED
 * - 쓰기는 지금 버전 동의가 있어야 한다(403 CONSENT_REQUIRED).
 */
@RestController
@RequestMapping("/api/v1/blends")
public class BlendsController {

    static final int LIMIT = 100, MAX_NOTES = 9, PER_LAYER = 3, MAX_FAMILIES = 10;
    static final Set<String> LAYERS = Set.of("top", "middle", "base"), STRENGTHS = Set.of("weak", "medium", "strong");
    private static final String COLS = "id, name, families, notes::text as notes, created_at, updated_at";
    private static final TypeReference<List<Map<String, String>>> NOTES_JSON = new TypeReference<>() {};

    public record NoteItem(String noteKey, String layer, String strength) {}

    public record BlendRequest(String name, List<String> families, List<NoteItem> notes) {}

    public record Blend(String id, String name, List<String> families, List<NoteItem> notes, String createdAt, String updatedAt) {}

    public record Blends(List<Blend> blends) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Consents consents;
    private final CatalogKeys catalog;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final RowMapper<Blend> rows;

    public BlendsController(JdbcClient jdbc, TransactionTemplate tx, Consents consents, CatalogKeys catalog) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.consents = consents;
        this.catalog = catalog;
        this.rows = (rs, n) -> {
            List<NoteItem> notes = new ArrayList<>();
            for (Map<String, String> m : JSON.readValue(rs.getString("notes"), NOTES_JSON)) {
                notes.add(new NoteItem(m.get("note_key"), m.get("layer"), m.get("strength")));
            }
            return new Blend(rs.getString("id"), rs.getString("name"), List.of((String[]) rs.getArray("families").getArray()), notes,
                    Library.time(rs, "created_at"), Library.time(rs, "updated_at"));
        };
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Blends list(JwtAuthenticationToken auth) {
        return new Blends(jdbc.sql("select " + COLS + " from app.blends where user_id = ? order by created_at desc, id")
                .param(user(auth)).query(rows).list());
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Blend one(JwtAuthenticationToken auth, @PathVariable("id") String id) {
        return jdbc.sql("select " + COLS + " from app.blends where id = ? and user_id = ?").params(Library.id(id), user(auth))
                .query(rows).optional().orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Blend> create(JwtAuthenticationToken auth, @RequestBody BlendRequest req) {
        UUID user = user(auth);
        consents.requireComplete(user);
        String notes = check(req);
        Blend made = tx.execute(s -> {
            Library.lock(jdbc, Library.Kind.BLENDS, user);
            Library.requireRoom(jdbc, "blends", user, LIMIT);
            return jdbc.sql("insert into app.blends (user_id, name, families, notes) values (?, ?, ?::text[], ?::jsonb) returning " + COLS)
                    .params(user, req.name(), req.families().toArray(String[]::new), notes).query(rows).single();
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(made);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Blend update(JwtAuthenticationToken auth, @PathVariable("id") String id, @RequestBody BlendRequest req) {
        UUID user = user(auth);
        consents.requireComplete(user);
        UUID blend = Library.id(id);
        String notes = check(req);
        return jdbc.sql("update app.blends set name = ?, families = ?::text[], notes = ?::jsonb where id = ? and user_id = ? returning " + COLS)
                .params(req.name(), req.families().toArray(String[]::new), notes, blend, user)
                .query(rows).optional().orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> delete(JwtAuthenticationToken auth, @PathVariable("id") String id) {
        UUID user = user(auth);
        consents.requireComplete(user);
        int n = jdbc.sql("delete from app.blends where id = ? and user_id = ?").params(Library.id(id), user).update();
        if (n == 0) throw new ApiException(ApiError.NOT_FOUND);
        return ResponseEntity.noContent().build();
    }

    private static UUID user(JwtAuthenticationToken auth) {
        return UUID.fromString(auth.getName());
    }

    /** 모양 검사 → 카탈로그 확인. 통과하면 DB에 넣을 노트 JSON(note_key 이름으로) */
    private String check(BlendRequest req) {
        if (req == null || !Library.name(req.name()) || !Library.keys(req.families(), MAX_FAMILIES)) throw new ApiException(ApiError.BAD_REQUEST);
        checkNotesShape(req.notes());
        for (String f : req.families()) if (!catalog.contains(CatalogKeys.Kind.FAMILY, f)) throw new ApiException(ApiError.UNKNOWN_KEY);
        checkNotesKnown(req.notes(), catalog);
        return notesJson(req.notes());
    }

    /** 조합 노트 모양 검사(조합 저장·신고 당시 조합 공통, DB app.valid_blend_notes와 같은 규칙). 틀리면 400 BAD_REQUEST */
    public static void checkNotesShape(List<NoteItem> notes) {
        if (notes == null || notes.isEmpty() || notes.size() > MAX_NOTES) throw new ApiException(ApiError.BAD_REQUEST);
        Set<String> seen = new HashSet<>();
        Map<String, Integer> perLayer = new HashMap<>();
        for (NoteItem n : notes) {
            if (n == null || !Library.key(n.noteKey(), 100) || n.layer() == null || !LAYERS.contains(n.layer()) // Set.of는 null을 물으면 예외
                    || n.strength() == null || !STRENGTHS.contains(n.strength())
                    || !seen.add(n.noteKey()) || perLayer.merge(n.layer(), 1, Integer::sum) > PER_LAYER) {
                throw new ApiException(ApiError.BAD_REQUEST);
            }
        }
    }

    /** 노트가 모두 지금 카탈로그에 있는지. 없으면 400 UNKNOWN_KEY */
    public static void checkNotesKnown(List<NoteItem> notes, CatalogKeys catalog) {
        for (NoteItem n : notes) if (!catalog.contains(CatalogKeys.Kind.NOTE, n.noteKey())) throw new ApiException(ApiError.UNKNOWN_KEY);
    }

    /** DB에 넣을 노트 JSON [{note_key, layer, strength}] (검사를 통과한 노트만) */
    public static String notesJson(List<NoteItem> notes) {
        List<Map<String, String>> out = new ArrayList<>();
        for (NoteItem n : notes) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("note_key", n.noteKey());
            m.put("layer", n.layer());
            m.put("strength", n.strength());
            out.add(m);
        }
        return JSON.writeValueAsString(out);
    }
}
