package com.perfumerlab.scentstudio.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 모든 오류 응답의 모양: {"error":{"code":"...","message":"..."}} (server_design.md 1절). 오류 원인·내부 내용은 넣지 않는다. */
public enum ApiError {
    BAD_REQUEST(400, "요청 내용이 올바르지 않아요"),
    UNKNOWN_KEY(400, "목록에 없는 항목이 있어요"),
    UNAUTHORIZED(401, "로그인이 필요해요"),
    FORBIDDEN(403, "이 작업을 할 수 없어요"),
    CONSENT_REQUIRED(403, "약관·방침 동의가 필요해요"),
    NOT_FOUND(404, "찾을 수 없어요"),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청이에요"),
    NOT_ACCEPTABLE(406, "지원하지 않는 응답 형식이에요"),
    STALE_CONSENT_VERSION(409, "약관·방침이 바뀌었어요. 새 내용을 확인해 주세요"),
    LIMIT_REACHED(409, "저장할 수 있는 개수를 다 채웠어요"),
    PAYLOAD_TOO_LARGE(413, "요청이 너무 커요"),
    UNSUPPORTED_MEDIA_TYPE(415, "지원하지 않는 형식이에요"),
    RATE_LIMITED(429, "요청이 너무 많아요. 잠시 후 다시 시도해 주세요"),
    INTERNAL(500, "서버에 문제가 생겼어요"),
    UNAVAILABLE(503, "잠시 후 다시 시도해 주세요");

    public final int status;
    public final String message;

    ApiError(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public String json() {
        return "{\"error\":{\"code\":\"" + name() + "\",\"message\":\"" + message + "\"}}";
    }

    public void write(HttpServletResponse res) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(json());
    }

    /** 상태 번호에 맞는 오류. 표에 없는 4xx는 BAD_REQUEST, 5xx는 INTERNAL */
    public static ApiError of(int status) {
        for (ApiError e : values()) if (e.status == status) return e;
        return status >= 500 ? INTERNAL : BAD_REQUEST;
    }
}
