package com.perfumerlab.scentstudio.catalog;

import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 카탈로그 배포 정보(app.catalog_releases)와 비공개 Storage 버킷에서 파일 받기 (server_design.md 5절).
 * - Storage는 서버 전용 Secret key(render-server, sb_secret_로 시작)로만 부른다. 공개 주소(/object/public/)는 쓰지 않는다.
 * - 받은 파일은 catalog_releases의 크기·sha256과 맞을 때만 돌려준다. 다르거나 못 받으면 503(UNAVAILABLE).
 * - 키는 응답·로그·오류 메시지에 절대 넣지 않는다. Storage가 돌려준 본문도 로그에 남기지 않는다(키를 되돌려 적을 수 있음).
 */
@Component
public class CatalogStore {

    private static final Logger log = LoggerFactory.getLogger(CatalogStore.class);
    static final String BUCKET = "catalog";
    /** catalog_releases.version 검사와 같은 모양. 이 모양이 아니면 DB도 부르지 않는다 */
    static final Pattern VERSION = Pattern.compile("[0-9]{4}\\.(0[1-9]|1[0-2])\\.(0[1-9]|[12][0-9]|3[01])(\\.[1-9][0-9]{0,2})?");
    /** Storage에서 받을 수 있는 파일 이름(DB 검사와 같은 모양). 이중 방어: 이 모양이 아니면 주소를 만들지 않는다(리뷰 묶음 6 QA-10) */
    static final Pattern FILE = Pattern.compile("catalog-" + VERSION.pattern() + "(\\.keys)?\\.json\\.gz");

    public record Release(String version, int engineVersion, String sha256, String keysSha256, int sizeBytes,
                          String storagePath, String keysStoragePath, String minAppVersion) {}

    private static final String COLS = "version, engine_version, sha256, keys_sha256, size_bytes, storage_path, keys_storage_path, min_app_version";
    private static final RowMapper<Release> ROWS = (rs, n) -> new Release(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4),
            rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8));

    private final JdbcClient jdbc;
    private final String base;
    private final String key;
    private final Duration timeout;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    public CatalogStore(JdbcClient jdbc, @Value("${scent.storage.url:}") String url, @Value("${scent.storage.secret-key:}") String key,
                        @Value("${scent.storage.timeout:30s}") Duration timeout) {
        this.jdbc = jdbc;
        this.timeout = timeout;
        this.base = url.strip().replaceAll("/+$", "");
        String k = key.strip();
        if (k.isEmpty()) {
            log.warn("Storage 서버 키(SUPABASE_SECRET_KEY)가 없어 카탈로그 파일·key 목록을 Storage에서 받을 수 없습니다(503)");
            k = "";
        } else if (!k.startsWith("sb_secret_")) {
            // 옛 방식(JWT, eyJ…) service_role 키나 공개 키를 잘못 넣은 경우 → 쓰지 않는다(값은 남기지 않음)
            log.warn("Storage 서버 키 모양이 sb_secret_가 아니어서 쓰지 않습니다(render-server 키를 넣어 주세요)");
            k = "";
        }
        this.key = k;
    }

    public Optional<Release> current() {
        return jdbc.sql("select " + COLS + " from app.catalog_releases where is_current").query(ROWS).optional();
    }

    public Optional<Release> byVersion(String version) {
        if (version == null || !VERSION.matcher(version).matches()) return Optional.empty();
        return jdbc.sql("select " + COLS + " from app.catalog_releases where version = ?").param(version).query(ROWS).optional();
    }

    /** Storage에서 받아 크기(알면)·sha256을 확인한 파일. 하나라도 틀리면 503 */
    public byte[] fetch(String path, String sha256, int expectedSize) {
        if (key.isEmpty() || base.isEmpty() || path == null || !FILE.matcher(path).matches()) throw new ApiException(ApiError.UNAVAILABLE);
        int limit = expectedSize > 0 ? expectedSize : 20_000_000;
        // 상태가 200이 아니거나 적힌 길이가 한도를 넘으면 본문을 받지 않는다
        HttpResponse.BodyHandler<byte[]> handler = info -> info.statusCode() != 200 || info.headers().firstValueAsLong("content-length").orElse(0) > limit
                ? HttpResponse.BodySubscribers.replacing(null) : HttpResponse.BodySubscribers.ofByteArray();
        CompletableFuture<HttpResponse<byte[]>> call = null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/storage/v1/object/" + BUCKET + "/" + path))
                    .timeout(timeout).GET()
                    .header("apikey", key).header("Authorization", "Bearer " + key).build();
            call = http.sendAsync(req, handler);
            // 헤더뿐 아니라 본문까지 전체 마감 시간(본문 중간에 멈추면 무한 대기하던 것, 리뷰 묶음 6 QA-1)
            HttpResponse<byte[]> res = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            byte[] body = res.body();
            if (res.statusCode() != 200 || body == null) {
                log.warn("Storage에서 {}를 받지 못했습니다: 상태 {}", path, res.statusCode());
                throw new ApiException(ApiError.UNAVAILABLE);
            }
            if ((expectedSize > 0 && body.length != expectedSize) || body.length > limit) {
                log.warn("Storage의 {} 크기가 배포 기록과 다릅니다. 내주지 않습니다", path);
                throw new ApiException(ApiError.UNAVAILABLE);
            }
            if (!sha256(body).equals(sha256)) {
                log.warn("Storage의 {} sha256이 배포 기록과 다릅니다. 내주지 않습니다", path);
                throw new ApiException(ApiError.UNAVAILABLE);
            }
            return body;
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiError.UNAVAILABLE);
        } catch (Exception e) {
            log.warn("Storage에서 {}를 받는 중 오류: {}", path, e.getClass().getSimpleName()); // 메시지는 남기지 않음
            throw new ApiException(ApiError.UNAVAILABLE);
        } finally {
            if (call != null && !call.isDone()) call.cancel(true); // 마감이 지나면 연결을 끊는다
        }
    }

    static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
