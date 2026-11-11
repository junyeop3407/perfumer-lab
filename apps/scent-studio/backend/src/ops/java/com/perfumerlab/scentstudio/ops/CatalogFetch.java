package com.perfumerlab.scentstudio.ops;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

/**
 * 실제 카탈로그 받기 (plan_stage5.md 1-2): ./gradlew dbCatalogFetch [-Papply=true] [-Pout=<경로>]
 * 기본은 "미리 보기"(catalog_releases의 지금 버전·해시·크기만 보여 주고 아무것도 받지 않음). -Papply=true일 때만:
 *   ① 서버 전용 키로 비공개 버킷 catalog에서 지금 버전 파일을 받는다(공개 주소·서버 공개 API는 쓰지 않음)
 *   ② catalog_releases의 sha256·크기, 파일 안 version과 비교한다. 다르거나 샘플이면 아무것도 쓰지 않고 멈춘다
 *   ③ app/assets/catalog/catalog.json.gz에 쓰고(임시 파일 → 바꿔치기), 옆에 catalog.release.json(버전·sha256·크기)을 남긴다.
 *      빌드 준비 스크립트(app/scripts/prepare-catalog.mjs, CATALOG_SOURCE=real)가 이 기록과 파일 해시를 비교한다.
 * 키는 .env에서만 읽고 출력하지 않는다. 받은 파일은 커밋하지 않는다(.gitignore).
 */
public final class CatalogFetch {

    public record Release(String version, String sha256, long size, String storagePath) {}

    static final String DEFAULT_OUT = "../app/assets/catalog/catalog.json.gz";
    static final long MAX_BYTES = 50_000_000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String base, key;
    private final Duration bodyTimeout;

    CatalogFetch(String base, String key) {
        this(base, key, Duration.ofSeconds(120));
    }

    /** bodyTimeout: 본문을 다 받을 때까지 기다리는 시간(요청 timeout은 응답 머리까지만 적용된다, 리뷰 5-1 QA 낮음-5) */
    CatalogFetch(String base, String key, Duration bodyTimeout) {
        this.base = base;
        this.key = key;
        this.bodyTimeout = bodyTimeout;
    }

    /** -Papply 값: 정확히 "true"일 때만 받는다(나머지는 미리 보기) */
    static boolean applyFlag(String v) {
        return "true".equals(v);
    }

    static void run(Map<String, String> env, String out, boolean apply) throws Exception {
        Path target = Path.of(out == null || out.isBlank() ? DEFAULT_OUT : out).toAbsolutePath().normalize();
        Release r;
        try (Connection c = DbOps.connect(env)) {
            r = require(current(c));
        }
        runWith(r, target, apply, () -> {
            String ref = env.getOrDefault("DB_MIGRATION_USER", "").replaceFirst("^postgres\\.", "");
            String secret = env.getOrDefault("SUPABASE_SECRET_KEY", "");
            if (ref.isEmpty() || !secret.startsWith("sb_secret_")) throw new IllegalStateException(".env에 DB_MIGRATION_USER와 SUPABASE_SECRET_KEY(sb_secret_…)가 필요합니다");
            return new CatalogFetch("https://" + ref + ".supabase.co", secret);
        });
    }

    /** 미리 보기를 보여 주고, apply일 때만 받는 도구를 만들어 받는다 */
    static void runWith(Release r, Path target, boolean apply, Supplier<CatalogFetch> maker) throws Exception {
        preview(r, target);
        if (!apply) {
            System.out.println("미리 보기만 했습니다(아무것도 받지 않음). 실제로 받으려면 -Papply=true");
            return;
        }
        maker.get().fetch(r, target);
    }

    /** catalog_releases의 지금 버전(is_current) 한 줄 */
    public static Optional<Release> current(Connection c) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("select version, sha256, size_bytes, storage_path from app.catalog_releases where is_current");
             ResultSet rs = s.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            return Optional.of(new Release(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
        }
    }

    static Release require(Optional<Release> r) {
        return r.orElseThrow(() -> new IllegalStateException("catalog_releases에 지금 버전(is_current)이 없습니다. 먼저 dbCatalogPublish로 배포해 주세요"));
    }

    static Path releaseNote(Path out) {
        return out.resolveSibling("catalog.release.json");
    }

    static void preview(Release r, Path out) {
        System.out.printf("지금 버전 %s: %s, %,d바이트, sha256 %s%n받을 자리: %s%n", r.version(), r.storagePath(), r.size(), r.sha256(), out);
    }

    void fetch(Release r, Path out) throws Exception {
        if (!CatalogPublish.VERSION.matcher(r.version()).matches()) throw new IllegalStateException("버전 모양이 틀립니다");
        if (!r.storagePath().equals("catalog-" + r.version() + ".json.gz")) throw new IllegalStateException("파일 경로가 버전과 맞지 않습니다");
        if (r.size() < 1 || r.size() > MAX_BYTES || !r.sha256().matches("[0-9a-f]{64}")) throw new IllegalStateException("기록된 크기·해시 모양이 틀립니다");

        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/storage/v1/object/" + CatalogPublish.BUCKET + "/" + r.storagePath()))
                .timeout(Duration.ofSeconds(120)).header("apikey", key).header("Authorization", "Bearer " + key).GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        byte[] body;
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) throw new IllegalStateException("받기 실패(상태 " + res.statusCode() + ")");
            // 기록보다 크면 거기서 멈춘다. 본문이 중간에 멈추면 bodyTimeout 뒤에 끝낸다
            CompletableFuture<byte[]> read = CompletableFuture.supplyAsync(() -> {
                try {
                    return in.readNBytes((int) r.size() + 1);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            try {
                body = read.get(bodyTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                read.cancel(true);
                throw new IllegalStateException("받기 시간 초과(" + bodyTimeout.toSeconds() + "초). 아무것도 쓰지 않았습니다");
            } catch (ExecutionException e) {
                throw new IllegalStateException("받는 중 연결이 끊겼습니다. 아무것도 쓰지 않았습니다");
            }
        }
        if (body.length != r.size()) throw new IllegalStateException("크기가 catalog_releases와 다릅니다. 아무것도 쓰지 않았습니다");
        if (!CatalogPublish.sha256(body).equals(r.sha256())) throw new IllegalStateException("sha256이 catalog_releases와 다릅니다. 아무것도 쓰지 않았습니다");
        JsonNode root;
        try (GZIPInputStream g = new GZIPInputStream(new ByteArrayInputStream(body))) {
            root = JsonMapper.builder().build().readTree(g);
        }
        if (!r.version().equals(root.path("version").asString(""))) throw new IllegalStateException("파일 안 version이 catalog_releases와 다릅니다");
        if (root.path("engineVersion").asInt(0) < 1) throw new IllegalStateException("engineVersion이 없습니다");
        if ("sample".equalsIgnoreCase(root.path("source").path("name").asString(""))) throw new IllegalStateException("샘플 카탈로그는 실제 카탈로그 자리에 받지 않습니다");

        Files.createDirectories(out.getParent());
        Path note = releaseNote(out);
        Files.deleteIfExists(note); // 바꾸는 도중 멈춰도 옛 기록이 새 파일을 통과시키지 않게
        replace(out, body);
        String json = "{\"version\":\"" + r.version() + "\",\"sha256\":\"" + r.sha256() + "\",\"size\":" + r.size() + "}\n";
        replace(note, json.getBytes(StandardCharsets.UTF_8));
        System.out.println("받음: " + r.version() + ", sha256·크기·버전 확인, " + out.getFileName() + "에 씀");
    }

    private static void replace(Path target, byte[] bytes) throws Exception {
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
