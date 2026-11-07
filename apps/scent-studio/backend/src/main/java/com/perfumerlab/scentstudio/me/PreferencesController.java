package com.perfumerlab.scentstudio.me;

import com.perfumerlab.scentstudio.catalog.CatalogKeys;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 취향(불호 노트·선호 계열) GET·PUT과 로그인 직후 합치기 (db_design.md 5절, 승인 C1~C3·D6).
 * - 모양 검사(빈 값·중복·개수 200/10·key 글자 모양) → 400 BAD_REQUEST
 * - PUT: 카탈로그에 없는 key → 400 UNKNOWN_KEY (하나도 저장 안 함)
 * - 합치기: 기기 값의 모르는 key만 빼고 ignoredKeys로 알림(옛 카탈로그 key 때문에 로그인 합치기가 매번 실패하지 않게).
 *   불호는 서버 것 먼저 + 기기 것(겹치면 한 번), 200개까지(잘리면 truncated). 선호는 서버에 하나라도 있으면 서버 값.
 *   같은 사용자의 합치기·저장은 그 줄을 잠근 채 처리한다(여러 기기 동시 로그인).
 * - 쓰기는 지금 버전 동의 세 개가 있어야 한다(403 CONSENT_REQUIRED). 읽기는 동의 없어도 된다.
 * 서버에 이미 저장된 key는 다시 확인하지 않는다(나중 카탈로그에서 빠져도 그대로 둠).
 */
@RestController
@RequestMapping("/api/v1/me/preferences")
public class PreferencesController {

    static final int MAX_DISLIKED = 200, MAX_FAMILIES = 10, MAX_KEY = 100;
    static final Pattern KEY = Pattern.compile("[!-~]([ -~]*[!-~])?"); // DB app.valid_key와 같은 글자 규칙

    public record Prefs(List<String> dislikedNoteKeys, List<String> preferredFamilies) {}

    public record Merged(List<String> dislikedNoteKeys, List<String> preferredFamilies, boolean truncated, List<String> ignoredKeys) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Consents consents;
    private final CatalogKeys catalog;

    public PreferencesController(JdbcClient jdbc, TransactionTemplate tx, Consents consents, CatalogKeys catalog) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.consents = consents;
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Prefs get(JwtAuthenticationToken auth) {
        return load(MeController.userId(auth), false).orElse(new Prefs(List.of(), List.of()));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Prefs put(JwtAuthenticationToken auth, @RequestBody Prefs req) {
        UUID user = MeController.userId(auth);
        consents.requireComplete(user);
        checkShape(req);
        for (String k : req.dislikedNoteKeys()) if (!catalog.contains(CatalogKeys.Kind.NOTE, k)) throw new ApiException(ApiError.UNKNOWN_KEY);
        for (String k : req.preferredFamilies()) if (!catalog.contains(CatalogKeys.Kind.FAMILY, k)) throw new ApiException(ApiError.UNKNOWN_KEY);
        jdbc.sql("insert into app.user_preferences (user_id, preferred_families, disliked_note_keys) values (?, ?::text[], ?::text[])"
                        + " on conflict (user_id) do update set preferred_families = excluded.preferred_families, disliked_note_keys = excluded.disliked_note_keys")
                .params(user, req.preferredFamilies().toArray(String[]::new), req.dislikedNoteKeys().toArray(String[]::new)).update();
        return new Prefs(req.dislikedNoteKeys(), req.preferredFamilies());
    }

    @PostMapping(path = "/merge", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Merged merge(JwtAuthenticationToken auth, @RequestBody Prefs device) {
        UUID user = MeController.userId(auth);
        consents.requireComplete(user);
        checkShape(device);
        List<String> ignored = new ArrayList<>();
        List<String> deviceNotes = known(device.dislikedNoteKeys(), CatalogKeys.Kind.NOTE, ignored);
        List<String> deviceFamilies = known(device.preferredFamilies(), CatalogKeys.Kind.FAMILY, ignored);
        return tx.execute(s -> {
            jdbc.sql("insert into app.user_preferences (user_id) values (?) on conflict (user_id) do nothing").param(user).update();
            Prefs server = load(user, true).orElseThrow();
            LinkedHashSet<String> notes = new LinkedHashSet<>(server.dislikedNoteKeys());
            boolean truncated = false;
            for (String k : deviceNotes) {
                if (notes.contains(k)) continue;
                if (notes.size() >= MAX_DISLIKED) {
                    truncated = true;
                    break;
                }
                notes.add(k);
            }
            List<String> families = server.preferredFamilies().isEmpty() ? deviceFamilies : server.preferredFamilies();
            List<String> merged = List.copyOf(notes);
            if (merged.equals(server.dislikedNoteKeys()) && families.equals(server.preferredFamilies())) {
                return new Merged(merged, families, truncated, ignored); // 바뀐 게 없으면 쓰지 않는다(리뷰 묶음 4 QA-6)
            }
            jdbc.sql("update app.user_preferences set preferred_families = ?::text[], disliked_note_keys = ?::text[] where user_id = ?")
                    .params(families.toArray(String[]::new), merged.toArray(String[]::new), user).update();
            return new Merged(merged, families, truncated, ignored);
        });
    }

    private List<String> known(List<String> keys, CatalogKeys.Kind kind, List<String> ignored) {
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            if (catalog.contains(kind, k)) out.add(k);
            else ignored.add(k);
        }
        return out;
    }

    private Optional<Prefs> load(UUID user, boolean lock) {
        return jdbc.sql("select disliked_note_keys, preferred_families from app.user_preferences where user_id = ?" + (lock ? " for update" : ""))
                .param(user)
                .query((rs, n) -> new Prefs(List.of((String[]) rs.getArray(1).getArray()), List.of((String[]) rs.getArray(2).getArray())))
                .optional();
    }

    static void checkShape(Prefs p) {
        if (p == null || !keys(p.dislikedNoteKeys(), MAX_DISLIKED) || !keys(p.preferredFamilies(), MAX_FAMILIES)) {
            throw new ApiException(ApiError.BAD_REQUEST);
        }
    }

    private static boolean keys(List<String> l, int max) {
        if (l == null || l.size() > max) return false;
        Set<String> seen = new HashSet<>();
        for (String k : l) {
            if (k == null || k.length() > MAX_KEY || !KEY.matcher(k).matches() || !seen.add(k)) return false;
        }
        return true;
    }
}
