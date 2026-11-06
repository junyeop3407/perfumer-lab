package com.perfumerlab.scentstudio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Perfumer-LAB 서버. 로그인 사용자 데이터·카탈로그 배포·사용 기록만 맡는다(추천 계산은 앱 안에서). */
@SpringBootApplication
public class ScentStudioApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScentStudioApplication.class, args);
    }
}
