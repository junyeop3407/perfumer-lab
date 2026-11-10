package com.perfumerlab.scentstudio.reports;

import com.perfumerlab.scentstudio.catalog.CatalogKeys;
import com.perfumerlab.scentstudio.library.BlendsController;
import com.perfumerlab.scentstudio.library.BlendsController.NoteItem;
import com.perfumerlab.scentstudio.library.Library;
import com.perfumerlab.scentstudio.me.Consents;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 오류 신고 "추천이 이상해요" (server_design.md 2·4절, V3 app.error_reports, 승인 K3·K4).
 * - 로그인한 사람만(사장 결정 2026-11-06), 동의 전 403. 시간당 20회는 요청 제한 필터(RateLimiter.Limit.REPORT)
 * - 향수나 노트 key 중 하나는 꼭, 둘 다 지금 카탈로그에 있어야 함(없으면 400 UNKNOWN_KEY)
 * - 신고 당시 조합 notes는 없어도 됨(사장 결정 2026-11-09). 있으면 조합 저장과 같은 검사(BlendsController.checkNotes*)
 * - 서버는 신고를 넣기만 한다(읽기 권한 없음) → 성공은 204. 처리 상태는 사장이 대시보드에서
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    static final Set<String> KINDS = Set.of("not_similar", "wrong_info", "note_name", "other");
    static final int MAX_MEMO = 500, MAX_KEY = 200;
    static final Pattern CATALOG_VERSION = Pattern.compile("[0-9A-Za-z.-]{1,32}");
    static final Pattern APP_VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,31}");

    public record ReportRequest(String kind, String perfumeKey, String noteKey, String memo, Integer rank, Double score,
                                Integer engineVersion, String catalogVersion, String appVersion, List<NoteItem> notes) {}

    private final JdbcClient jdbc;
    private final Consents consents;
    private final CatalogKeys catalog;

    public ReportsController(JdbcClient jdbc, Consents consents, CatalogKeys catalog) {
        this.jdbc = jdbc;
        this.consents = consents;
        this.catalog = catalog;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> create(JwtAuthenticationToken auth, @RequestBody ReportRequest req) {
        UUID user = UUID.fromString(auth.getName());
        consents.requireComplete(user);
        check(req);
        if (req.perfumeKey() != null && !catalog.contains(CatalogKeys.Kind.PERFUME, req.perfumeKey())) throw new ApiException(ApiError.UNKNOWN_KEY);
        if (req.noteKey() != null && !catalog.contains(CatalogKeys.Kind.NOTE, req.noteKey())) throw new ApiException(ApiError.UNKNOWN_KEY);
        if (req.notes() != null) BlendsController.checkNotesKnown(req.notes(), catalog);
        String notes = req.notes() == null ? null : BlendsController.notesJson(req.notes());
        jdbc.sql("insert into app.error_reports (user_id, kind, perfume_key, note_key, memo, rank, score, engine_version, catalog_version, app_version, notes)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)")
                .params(user, req.kind(), req.perfumeKey(), req.noteKey(), req.memo(), req.rank(), req.score(),
                        req.engineVersion(), req.catalogVersion(), req.appVersion(), notes)
                .update();
        return ResponseEntity.noContent().build();
    }

    private static void check(ReportRequest r) {
        if (r == null || r.kind() == null || !KINDS.contains(r.kind())
                || (r.perfumeKey() == null && r.noteKey() == null)
                || (r.perfumeKey() != null && !Library.key(r.perfumeKey(), MAX_KEY))
                || (r.noteKey() != null && !Library.key(r.noteKey(), MAX_KEY))
                || (r.memo() != null && (Library.length(r.memo()) < 0 || Library.length(r.memo()) > MAX_MEMO))
                || (r.rank() != null && (r.rank() < 1 || r.rank() > 10))
                || (r.score() != null && !(r.score() >= 0 && r.score() <= 1))
                || r.engineVersion() == null || r.engineVersion() < 1
                || r.catalogVersion() == null || !CATALOG_VERSION.matcher(r.catalogVersion()).matches()
                || r.appVersion() == null || !APP_VERSION.matcher(r.appVersion()).matches()) {
            throw new ApiException(ApiError.BAD_REQUEST);
        }
        if (r.notes() != null) BlendsController.checkNotesShape(r.notes());
    }
}
