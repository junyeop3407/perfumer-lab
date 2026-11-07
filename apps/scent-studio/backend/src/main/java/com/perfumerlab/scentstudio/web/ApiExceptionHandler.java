package com.perfumerlab.scentstudio.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 컨트롤러에서 난 오류를 {"error":{code,message}} 모양으로. 원인·내용은 응답에 넣지 않는다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static ResponseEntity<String> of(ApiError e) {
        return ResponseEntity.status(e.status).contentType(MediaType.APPLICATION_JSON).body(e.json());
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<String> api(ApiException e) {
        return of(e.error);
    }

    /** JSON 모양이 틀림(깨진 JSON, 타입 다름, 본문 없음). 본문이 너무 커서 읽다 멈춘 경우는 413 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<String> unreadable(HttpMessageNotReadableException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BodySizeFilter.TooLarge) return of(ApiError.PAYLOAD_TOO_LARGE);
        }
        return of(ApiError.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<String> mediaType() {
        return of(ApiError.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<String> method() {
        return of(ApiError.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<String> notFound() {
        return of(ApiError.NOT_FOUND);
    }

    /** DB에 붙지 못함 → 503 (잠시 뒤 다시) */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<String> dbDown() {
        return of(ApiError.UNAVAILABLE);
    }

    /** DB 검사(check 제약)에 걸림 = 서버 검사가 놓친 잘못된 입력 → 500이 아니라 400 (리뷰 묶음 5 QA-2). 그 밖의 무결성 오류는 아래로 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<String> integrity(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException s && "23514".equals(s.getSQLState())) return of(ApiError.BAD_REQUEST);
        }
        return other(e);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<String> other(Exception e) {
        // Spring 표준 오류(406 등)는 원래 상태 번호 그대로(리뷰 묶음 4 QA-5, 묶음 1 회귀 테스트가 잡음)
        if (e instanceof ErrorResponse er && er.getStatusCode().value() < 500) {
            int status = er.getStatusCode().value();
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ApiError.of(status).json());
        }
        log.error("처리 중 오류: {}", e.getClass().getName()); // 메시지는 남기지 않는다(입력·접속 정보가 섞일 수 있음)
        return of(ApiError.INTERNAL);
    }
}
