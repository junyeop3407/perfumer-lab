package com.perfumerlab.scentstudio.web;

/** 컨트롤러·서비스에서 정해진 오류로 답할 때 던진다. 응답은 ApiExceptionHandler가 같은 모양으로 만든다. */
public class ApiException extends RuntimeException {
    public final ApiError error;

    public ApiException(ApiError error) {
        super(error.name(), null, false, false); // 스택을 만들지 않는다(흔한 400을 싸게)
        this.error = error;
    }
}
