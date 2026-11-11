package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 9: OpenAPI 문서. 운영(Render)에서는 꺼져 있다(HealthApiTest: 401).
 * 켜진 서버에서 문서를 만들어 저장소의 docs/openapi.json과 같은지 본다(앱 TypeScript 타입은 이 파일로 만든다).
 * 파일을 새로 쓰려면: ./gradlew test --tests '*OpenApiDocTest*' -Dopenapi.write=true
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"springdoc.api-docs.enabled=true", "scent.debug.mem=false", "scent.debug.ip=false", "scent.debug.token=", "scent.debug.until="})
class OpenApiDocTest extends ApiTestBase {

    static final Path DOC_FILE = Path.of("..", "docs", "openapi.json");
    static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    static final String ERROR_REF = "#/components/schemas/ApiError";

    /** 앱이 부르는 주소 전부 (server_design.md 2절 표, 컨트롤러가 실제로 돌려주는 성공 코드) */
    record Op(String method, String path, boolean login, String success, Set<String> errors) {
        String id() { return method + " " + path; }
    }

    static final List<Op> OPS = List.of(
            new Op("get", "/api/v1/health", false, "200", Set.of("503")),
            new Op("get", "/api/v1/health/live", false, "200", Set.of()),
            new Op("get", "/api/v1/catalog/latest", false, "200", Set.of("401", "404", "429", "503")),
            new Op("get", "/api/v1/catalog/{version}", false, "200", Set.of("400", "401", "404", "429", "503")),
            new Op("post", "/api/v1/events", false, "200", Set.of("400", "401", "413", "415", "429")),
            new Op("get", "/api/v1/me", true, "200", Set.of("401", "429")),
            new Op("delete", "/api/v1/me", true, "204", Set.of("401", "429", "503")),
            new Op("post", "/api/v1/me/consents", true, "200", Set.of("400", "401", "409", "429")),
            new Op("get", "/api/v1/me/preferences", true, "200", Set.of("401", "429")),
            new Op("put", "/api/v1/me/preferences", true, "200", Set.of("400", "401", "403", "429")),
            new Op("post", "/api/v1/me/preferences/merge", true, "200", Set.of("400", "401", "403", "429")),
            new Op("get", "/api/v1/blends", true, "200", Set.of("401", "429")),
            new Op("post", "/api/v1/blends", true, "201", Set.of("400", "401", "403", "409", "429")),
            new Op("get", "/api/v1/blends/{id}", true, "200", Set.of("401", "404", "429")),
            new Op("put", "/api/v1/blends/{id}", true, "200", Set.of("400", "401", "403", "404", "429")),
            new Op("delete", "/api/v1/blends/{id}", true, "204", Set.of("401", "404", "429")),
            new Op("get", "/api/v1/favorites", true, "200", Set.of("401", "429")),
            new Op("put", "/api/v1/favorites/{perfumeKey}", true, "204", Set.of("400", "401", "403", "409", "429")),
            new Op("delete", "/api/v1/favorites/{perfumeKey}", true, "204", Set.of("401", "429")),
            new Op("get", "/api/v1/feedback", true, "200", Set.of("401", "429")),
            new Op("post", "/api/v1/feedback", true, "201", Set.of("400", "401", "403", "404", "409", "429")),
            new Op("delete", "/api/v1/feedback/{id}", true, "204", Set.of("401", "404", "429")),
            new Op("post", "/api/v1/reports", true, "204", Set.of("400", "401", "403", "429")));

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
    }

    JsonNode doc() throws Exception {
        HttpResponse<String> r = get("/api/v1/openapi");
        assertEquals(200, r.statusCode(), "켜면 로그인 없이 읽힌다: " + r.body());
        HealthApiTest.assertNoSecrets(r.body());
        return JSON.readTree(r.body());
    }

    static JsonNode op(JsonNode d, Op o) {
        return d.path("paths").path(o.path()).path(o.method());
    }

    @Test
    void listsExactlyTheAppEndpointsWithMethods() throws Exception {
        JsonNode paths = doc().get("paths");
        Map<String, List<String>> actual = new TreeMap<>();
        for (Map.Entry<String, JsonNode> p : paths.properties()) {
            List<String> methods = new ArrayList<>();
            for (Map.Entry<String, JsonNode> m : p.getValue().properties()) if (!m.getKey().equals("parameters")) methods.add(m.getKey());
            methods.sort(null);
            actual.put(p.getKey(), methods);
        }
        Map<String, List<String>> expected = new TreeMap<>();
        for (Op o : OPS) expected.computeIfAbsent(o.path(), k -> new ArrayList<>()).add(o.method());
        expected.values().forEach(l -> l.sort(null));
        assertEquals(expected, actual, "측정 주소(_debug)·/error는 빠지고 앱 주소는 모두 있어야 한다");
    }

    @Test
    void loginEndpointsDeclareBearerAndPublicOnesDoNot() throws Exception {
        JsonNode d = doc();
        JsonNode scheme = d.at("/components/securitySchemes/bearer");
        assertEquals("http", scheme.path("type").asString());
        assertEquals("bearer", scheme.path("scheme").asString());
        assertEquals("JWT", scheme.path("bearerFormat").asString());
        assertTrue(d.path("security").isMissingNode() || d.path("security").isEmpty(), "문서 전체 기본 보안은 두지 않는다(주소마다)");
        for (Op o : OPS) {
            JsonNode sec = op(d, o).path("security");
            boolean bearer = sec.isArray() && sec.size() == 1 && sec.get(0).has("bearer");
            assertEquals(o.login(), bearer, o.id());
        }
    }

    @Test
    void successCodeMatchesController() throws Exception {
        JsonNode d = doc();
        for (Op o : OPS) {
            JsonNode responses = op(d, o).path("responses");
            assertTrue(responses.has(o.success()), o.id() + " 성공 " + o.success() + ": " + responses.propertyNames());
            for (String other : new String[]{"200", "201", "204"}) {
                if (!other.equals(o.success())) assertFalse(responses.has(other), o.id() + " 성공 코드는 하나: " + other);
            }
            if (o.success().equals("204")) assertTrue(responses.path("204").path("content").isMissingNode() || responses.path("204").path("content").isEmpty(), o.id() + " 204는 본문 없음");
        }
    }

    @Test
    void errorResponsesUseApiErrorShape() throws Exception {
        JsonNode d = doc();
        JsonNode err = d.at("/components/schemas/ApiError/properties/error/properties");
        assertTrue(err.has("code") && err.has("message"), "오류 모양 {error:{code, message}}: " + err);
        assertTrue(err.path("code").path("enum").toString().contains("CONSENT_REQUIRED"), "code 목록이 있어야 앱이 타입으로 쓴다");
        for (Op o : OPS) {
            JsonNode responses = op(d, o).path("responses");
            for (String status : o.errors()) {
                String ref = responses.path(status).at("/content/application~1json/schema/$ref").asString("");
                assertEquals(ERROR_REF, ref, o.id() + " " + status);
            }
        }
    }

    @Test
    void installIdHeaderIsRequiredWhereServerNeedsIt() throws Exception {
        JsonNode d = doc();
        for (Op o : OPS) {
            boolean needs = o.path().equals("/api/v1/catalog/{version}") || o.path().equals("/api/v1/events");
            boolean found = false;
            for (JsonNode p : op(d, o).path("parameters")) {
                if (p.path("in").asString().equals("header") && p.path("name").asString().equals("X-Install-Id")) {
                    found = true;
                    assertTrue(p.path("required").asBoolean(), o.id());
                    assertEquals("uuid", p.at("/schema/format").asString(), o.id());
                }
            }
            assertEquals(needs, found, o.id() + " X-Install-Id");
        }
    }

    @Test
    void catalogFileIsGzipBinaryAndEventsAreAnArray() throws Exception {
        JsonNode d = doc();
        JsonNode ok = d.at("/paths/~1api~1v1~1catalog~1{version}/get/responses/200/content");
        assertEquals(List.of("application/gzip"), List.copyOf(ok.propertyNames()), "카탈로그 파일은 gzip 바이너리");
        JsonNode s = ok.path("application/gzip").path("schema");
        assertTrue(s.path("format").asString().equals("binary") || s.path("contentMediaType").asString().equals("application/octet-stream"), s.toString());
        assertFalse(d.at("/components/schemas").has("JsonNode"), "Jackson 내부 모양(nodeType 등)이 문서에 나오면 안 된다");
        String eventsRef = d.at("/components/schemas/EventsRequest/properties/events/type").asString("");
        assertEquals("array", eventsRef, "events는 배열");
    }

    @Test
    void docHasNoServerAddressOrSecretNames() throws Exception {
        String text = JSON.writeValueAsString(doc());
        assertFalse(text.contains("127.0.0.1") || text.contains("localhost"), "실행한 컴퓨터 주소가 문서에 들어가면 안 된다(파일이 컴퓨터마다 달라짐)");
        for (String bad : new String[]{"sb_secret_", "supabase.co", "X-Debug-Token", "_debug", "password"}) {
            assertFalse(text.toLowerCase().contains(bad.toLowerCase()), bad);
        }
    }

    @Test
    void committedFileMatchesServer() throws Exception {
        JsonNode d = doc();
        String generated = JSON.writeValueAsString(d).replace("\r\n", "\n") + "\n";
        if (Boolean.getBoolean("openapi.write")) {
            Files.createDirectories(DOC_FILE.getParent());
            Files.writeString(DOC_FILE, generated, StandardCharsets.UTF_8);
        }
        assertTrue(Files.exists(DOC_FILE), "docs/openapi.json이 없다 → -Dopenapi.write=true로 만든다");
        String committed = Files.readString(DOC_FILE, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(generated, committed, "서버 API가 바뀌었는데 docs/openapi.json이 옛것이다 → -Dopenapi.write=true로 다시 만든다");
    }
}
