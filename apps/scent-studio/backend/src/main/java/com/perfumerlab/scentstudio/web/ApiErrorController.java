package com.perfumerlab.scentstudio.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러 밖에서 난 오류(없는 주소 404, 허용 안 된 메서드 405, 처리 중 예외 500 등)도 같은 모양으로 답한다.
 * Spring 기본 오류 응답(timestamp·path·error)을 대신한다. 원인·경로는 돌려주지 않는다.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.ALL_VALUE)
    public ResponseEntity<String> error(HttpServletRequest req) {
        Object code = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = code instanceof Integer i ? i : 404; // 누가 /error를 직접 부르면 없는 주소처럼
        if (status < 400) status = 500;
        ApiError e = ApiError.of(status);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(e.json());
    }
}
