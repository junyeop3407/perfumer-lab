package com.perfumerlab.scentstudio.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI 문서 (묶음 9). 운영에서는 꺼져 있고(OPENAPI_ENABLED 없음), 테스트가 만든 문서를 docs/openapi.json으로 둔다.
 * 앱 TypeScript 타입은 그 파일로 만든다(server_design.md 2절). 주소마다 로그인 여부·성공 코드·오류 응답을 컨트롤러 동작대로 적는다.
 * 열쇠는 "메서드 주소"(SecurityConfig처럼 메서드까지 본다). 주소를 더하면 OpenApiDocTest 표도 같이 고친다.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearer";
    static final String ERROR_REF = "#/components/schemas/ApiError";

    /** 로그인 없이 부르는 주소(SecurityConfig와 같게). 나머지는 모두 로그인 필요 */
    static final Set<String> PUBLIC = Set.of("get /api/v1/health", "get /api/v1/health/live",
            "get /api/v1/catalog/latest", "get /api/v1/catalog/{version}", "post /api/v1/events");
    /** 토큰을 아예 읽지 않는 주소(SecurityConfig.PUBLIC_GET) → 401 없음 */
    static final Set<String> NO_TOKEN = Set.of("get /api/v1/health", "get /api/v1/health/live");
    /** 요청 제한·DB와 상관없는 주소 (LimitFilters: Render 상태 확인) */
    static final String LIVE = "get /api/v1/health/live";
    /** X-Install-Id 헤더가 꼭 있어야 하는 주소 (InstallId.from) */
    static final Set<String> INSTALL_ID = Set.of("get /api/v1/catalog/{version}", "post /api/v1/events");
    /** 200이 아닌 성공 코드 */
    static final Map<String, String> SUCCESS = Map.ofEntries(
            Map.entry("post /api/v1/blends", "201"), Map.entry("post /api/v1/feedback", "201"),
            Map.entry("delete /api/v1/me", "204"), Map.entry("delete /api/v1/blends/{id}", "204"),
            Map.entry("put /api/v1/favorites/{perfumeKey}", "204"), Map.entry("delete /api/v1/favorites/{perfumeKey}", "204"),
            Map.entry("delete /api/v1/feedback/{id}", "204"), Map.entry("post /api/v1/reports", "204"));
    /** 동의가 없어도 되는 쓰기 (server_design.md 2절) */
    static final Set<String> NO_CONSENT = Set.of("post /api/v1/me/consents", "delete /api/v1/me");
    /** 404: 내 것이 아님·없음(id), 카탈로그 배포 없음, 시향 기록의 남의 조합 */
    static final Set<String> NOT_FOUND = Set.of("get /api/v1/catalog/latest", "get /api/v1/catalog/{version}",
            "get /api/v1/blends/{id}", "put /api/v1/blends/{id}", "delete /api/v1/blends/{id}",
            "delete /api/v1/feedback/{id}", "post /api/v1/feedback");
    /** 409: 개수 한도(LIMIT_REACHED), 동의 버전이 옛것(STALE_CONSENT_VERSION) */
    static final Set<String> CONFLICT = Set.of("post /api/v1/blends", "post /api/v1/feedback",
            "put /api/v1/favorites/{perfumeKey}", "post /api/v1/me/consents");
    /** 본문은 없지만 400이 나는 곳(설치 id 헤더, 목록에 없는 향수 key) */
    static final Set<String> BAD_REQUEST_NO_BODY = Set.of("get /api/v1/catalog/{version}", "put /api/v1/favorites/{perfumeKey}");

    @Bean
    OpenAPI scentOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Perfumer-LAB API").version("v1"))
                .servers(List.of(new Server().url("/"))) // 실행한 컴퓨터 주소가 문서에 들어가지 않게
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer scentOperations() {
        return api -> {
            // 여기서 넣는다: 처음 문서(위 Bean)에 넣으면 springdoc이 "안 쓰는 스키마"로 보고 지운다
            api.getComponents().addSchemas("ApiError", apiErrorSchema());
            // EventsRequest.events는 Jackson JsonNode라 springdoc이 내부 모양(nodeType…)을 적는다 → 객체 배열로
            Schema<?> events = api.getComponents().getSchemas().get("EventsRequest");
            if (events != null) events.addProperty("events", new ArraySchema().items(new ObjectSchema()));
            api.getComponents().getSchemas().remove("JsonNode");

            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((httpMethod, op) -> {
                String key = httpMethod.name().toLowerCase() + " " + path;
                boolean login = !PUBLIC.contains(key);
                boolean write = !httpMethod.name().equals("GET");
                boolean body = op.getRequestBody() != null;
                op.setSecurity(login ? List.of(new SecurityRequirement().addList(BEARER)) : null);

                String success = SUCCESS.get(key);
                if (success != null) {
                    ApiResponse ok = op.getResponses().remove("200");
                    if (ok == null) ok = new ApiResponse();
                    if (success.equals("204")) ok.setContent(null);
                    ok.setDescription(success.equals("201") ? "Created" : "No Content");
                    op.getResponses().addApiResponse(success, ok);
                }
                if (INSTALL_ID.contains(key)) {
                    op.addParametersItem(new HeaderParameter().name(InstallId.HEADER).required(true)
                            .description("앱 설치 때 만든 무작위 uuid(소문자)").schema(new UUIDSchema()));
                }
                if (key.equals("get /api/v1/catalog/{version}")) {
                    op.getResponses().get("200").setContent(new Content().addMediaType("application/gzip",
                            new MediaType().schema(new StringSchema().format("binary"))));
                }

                if (body || BAD_REQUEST_NO_BODY.contains(key)) addError(op, "400", ApiError.BAD_REQUEST.message);
                if (!NO_TOKEN.contains(key)) addError(op, "401", ApiError.UNAUTHORIZED.message);
                if (login && write && !NO_CONSENT.contains(key)) addError(op, "403", ApiError.CONSENT_REQUIRED.message);
                if (NOT_FOUND.contains(key)) addError(op, "404", ApiError.NOT_FOUND.message);
                if (CONFLICT.contains(key)) addError(op, "409", "개수 한도(LIMIT_REACHED) 또는 옛 동의 버전(STALE_CONSENT_VERSION)");
                if (body) addError(op, "413", ApiError.PAYLOAD_TOO_LARGE.message);
                if (body) addError(op, "415", ApiError.UNSUPPORTED_MEDIA_TYPE.message);
                if (!key.equals(LIVE)) addError(op, "429", ApiError.RATE_LIMITED.message);
                if (!key.equals(LIVE)) addError(op, "503", ApiError.UNAVAILABLE.message);
            }));
        };
    }

    /** {"error":{"code":"...","message":"..."}} — code는 ApiError 이름 목록 */
    static Schema<?> apiErrorSchema() {
        List<String> codes = Arrays.stream(ApiError.values()).map(Enum::name).toList();
        Schema<?> error = new ObjectSchema()
                .addProperty("code", new StringSchema()._enum(codes))
                .addProperty("message", new StringSchema());
        error.setRequired(List.of("code", "message"));
        Schema<?> apiError = new ObjectSchema().addProperty("error", error);
        apiError.setRequired(List.of("error"));
        return apiError;
    }

    static void addError(Operation op, String status, String description) {
        if (op.getResponses() == null) op.setResponses(new ApiResponses());
        op.getResponses().addApiResponse(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref(ERROR_REF)))));
    }
}
