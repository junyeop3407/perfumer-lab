package com.perfumerlab.scentstudio.ops;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 카탈로그 배포: ./gradlew dbCatalogPublish -Pfile=<catalog.json.gz> -PminApp=1.0.0 [-Papply=true]
 * 기본은 "미리 보기"(아무것도 바꾸지 않고 버전·해시·크기·key 수만 보여 줌). -Papply=true일 때만 실제로:
 *   ① Storage 버킷 catalog가 없으면 비공개로 만든다. 있는데 공개면 멈춘다
 *   ② 전체 파일과 key 목록 파일을 올린다(이미 있으면 내용이 같을 때만 그대로, 다르면 멈춤) → 다시 받아 sha256 확인
 *   ③ 공개 주소(/object/public/)로는 받을 수 없는지 확인
 *   ④ app.catalog_releases에 줄을 넣고 지금 버전으로 바꾼다(마이그레이션 계정, 한 트랜잭션)
 * 서버 전용 키(SUPABASE_SECRET_KEY, sb_secret_…)는 .env에서만 읽고 절대 출력하지 않는다. Storage 응답 본문도 출력하지 않는다.
 */
final class CatalogPublish {

    static final String BUCKET = "catalog";
    static final Pattern VERSION = Pattern.compile("[0-9]{4}\\.(0[1-9]|1[0-2])\\.(0[1-9]|[12][0-9]|3[01])(\\.[1-9][0-9]{0,2})?");
    static final Pattern APP = Pattern.compile("(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,3})");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String base, key;

    CatalogPublish(String base, String key) {
        this.base = base;
        this.key = key;
    }

    static void run(Map<String, String> env, String file, String minApp, boolean apply) throws Exception {
        if (file == null || file.isBlank()) throw new IllegalArgumentException("-Pfile=<catalog.json.gz 경로>가 필요합니다");
        if (minApp == null || !APP.matcher(minApp).matches()) throw new IllegalArgumentException("-PminApp=숫자.숫자.숫자 가 필요합니다");
        byte[] full = Files.readAllBytes(Path.of(file));
        JsonNode root;
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(full))) {
            root = JsonMapper.builder().build().readTree(in);
        }
        String version = root.path("version").asString("");
        int engine = root.path("engineVersion").asInt(0);
        if (!VERSION.matcher(version).matches() || engine < 1) throw new IllegalStateException("카탈로그 version·engineVersion 모양이 틀립니다");
        byte[] keys = keysFile(root, version);
        String path = "catalog-" + version + ".json.gz", keysPath = "catalog-" + version + ".keys.json.gz";
        System.out.printf("버전 %s, 엔진 %d, 최소 앱 %s%n전체 %s: %,d바이트, sha256 %s%nkey 목록 %s: %,d바이트, sha256 %s%n",
                version, engine, minApp, path, full.length, sha256(full), keysPath, keys.length, sha256(keys));
        if (!apply) {
            System.out.println("미리 보기만 했습니다(아무것도 바꾸지 않음). 실제로 하려면 -Papply=true");
            return;
        }
        String ref = env.getOrDefault("DB_MIGRATION_USER", "").replaceFirst("^postgres\\.", "");
        String secret = env.getOrDefault("SUPABASE_SECRET_KEY", "");
        if (ref.isEmpty() || !secret.startsWith("sb_secret_")) throw new IllegalStateException(".env에 DB_MIGRATION_USER와 SUPABASE_SECRET_KEY(sb_secret_…)가 필요합니다");
        // Storage를 건드리기 전에 확인: DB 크기 상한, 이미 배포된 버전(같은 버전 다시 배포는 막음 — 버전을 올린다, 리뷰 묶음 6 QA-11)
        if (full.length > 50_000_000) throw new IllegalStateException("파일이 50,000,000바이트를 넘습니다");
        try (Connection c = DbOps.connect(env); PreparedStatement s = c.prepareStatement("select 1 from app.catalog_releases where version = ?")) {
            s.setString(1, version);
            if (s.executeQuery().next()) throw new IllegalStateException(version + "은(는) 이미 배포된 버전입니다. 카탈로그 버전을 올려 주세요");
        }
        CatalogPublish p = new CatalogPublish("https://" + ref + ".supabase.co", secret);
        p.privateBucket();
        p.upload(path, full);
        p.upload(keysPath, keys);
        p.publicBlocked(path);
        try (Connection c = DbOps.connect(env)) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement("insert into app.catalog_releases (version, engine_version, sha256, keys_sha256, size_bytes,"
                    + " storage_path, keys_storage_path, min_app_version) values (?, ?, ?, ?, ?, ?, ?, ?)")) {
                s.setString(1, version);
                s.setInt(2, engine);
                s.setString(3, sha256(full));
                s.setString(4, sha256(keys));
                s.setInt(5, full.length);
                s.setString(6, path);
                s.setString(7, keysPath);
                s.setString(8, minApp);
                s.executeUpdate();
            }
            try (PreparedStatement off = c.prepareStatement("update app.catalog_releases set is_current = false where is_current");
                 PreparedStatement on = c.prepareStatement("update app.catalog_releases set is_current = true where version = ?")) {
                off.executeUpdate();
                on.setString(1, version);
                on.executeUpdate();
            }
            c.commit();
        }
        System.out.println("배포 완료: " + version + "이(가) 지금 버전입니다");
    }

    /** {"version","notes":[note key],"families":[계열 code],"perfumes":[향수 key]} gzip. 정렬해서 매번 같은 바이트 */
    static byte[] keysFile(JsonNode root, String version) throws Exception {
        JsonMapper m = JsonMapper.builder().build();
        ObjectNode out = m.createObjectNode().put("version", version);
        out.set("notes", sorted(m, root.path("notes"), "key"));
        out.set("families", sorted(m, root.path("families"), "code"));
        out.set("perfumes", sorted(m, root.path("perfumes"), "key"));
        if (out.get("notes").isEmpty() || out.get("families").isEmpty() || out.get("perfumes").isEmpty()) throw new IllegalStateException("key 목록이 비었습니다");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(b)) {
            g.write(m.writeValueAsBytes(out));
        }
        return b.toByteArray();
    }

    private static ArrayNode sorted(JsonMapper m, JsonNode list, String field) {
        TreeSet<String> s = new TreeSet<>();
        for (JsonNode n : list) if (n.path(field).isString()) s.add(n.path(field).asString());
        ArrayNode a = m.createArrayNode();
        s.forEach(a::add);
        return a;
    }

    private HttpResponse<byte[]> call(String method, String path, byte[] body, String type, boolean withKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (type != null) b.header("Content-Type", type);
        if (withKey) b.header("apikey", key).header("Authorization", "Bearer " + key);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    void privateBucket() throws Exception {
        HttpResponse<byte[]> r = call("GET", "/storage/v1/bucket/" + BUCKET, null, null, true);
        if (r.statusCode() != 200) {
            HttpResponse<byte[]> made = call("POST", "/storage/v1/bucket", ("{\"id\":\"" + BUCKET + "\",\"name\":\"" + BUCKET + "\",\"public\":false}")
                    .getBytes(StandardCharsets.UTF_8), "application/json", true);
            if (made.statusCode() != 200) throw new IllegalStateException("버킷을 만들지 못했습니다: 상태 " + made.statusCode());
            System.out.println("비공개 버킷 catalog를 만들었습니다");
            r = call("GET", "/storage/v1/bucket/" + BUCKET, null, null, true);
        }
        JsonNode b = JsonMapper.builder().build().readTree(r.body());
        if (r.statusCode() != 200 || !b.path("public").isBoolean() || b.path("public").asBoolean()) {
            throw new IllegalStateException("버킷 catalog가 비공개가 아닙니다(또는 확인 실패). 멈춥니다");
        }
        System.out.println("버킷 catalog: 비공개 확인");
    }

    void upload(String path, byte[] bytes) throws Exception {
        HttpResponse<byte[]> r = call("POST", "/storage/v1/object/" + BUCKET + "/" + path, bytes, "application/gzip", true);
        if (r.statusCode() != 200) {
            HttpResponse<byte[]> have = call("GET", "/storage/v1/object/" + BUCKET + "/" + path, null, null, true);
            if (have.statusCode() == 200 && sha256(have.body()).equals(sha256(bytes))) {
                System.out.println(path + ": 이미 같은 내용이 있습니다");
                return;
            }
            throw new IllegalStateException(path + " 올리기 실패(상태 " + r.statusCode() + "). 이미 다른 내용이 있으면 버전을 올려 주세요");
        }
        HttpResponse<byte[]> back = call("GET", "/storage/v1/object/" + BUCKET + "/" + path, null, null, true);
        if (back.statusCode() != 200 || !sha256(back.body()).equals(sha256(bytes))) throw new IllegalStateException(path + " 다시 받아 본 sha256이 다릅니다");
        System.out.println(path + ": 올림, sha256 확인");
    }

    void publicBlocked(String path) throws Exception {
        int s = call("GET", "/storage/v1/object/public/" + BUCKET + "/" + path, null, null, false).statusCode();
        if (s == 200) throw new IllegalStateException("공개 주소로 받아집니다! 멈춥니다(버킷 설정 확인)");
        System.out.println("공개 주소로는 안 받아짐 확인(상태 " + s + ")");
    }

    static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
