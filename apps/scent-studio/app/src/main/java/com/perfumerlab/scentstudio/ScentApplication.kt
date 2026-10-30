package com.perfumerlab.scentstudio

import android.app.Application
import com.perfumerlab.scentstudio.auth.AuthGateway
import okhttp3.Interceptor
import java.io.InputStream

/**
 * 앱을 조립할 때 바깥에서 정해 주는 값. 실제 앱은 빌드 설정값으로,
 * 테스트는 가짜 카탈로그·가짜 서버·가짜 로그인으로 채운다.
 */
data class AppConfig(
    /** catalog.json.gz 를 여는 방법 (docs/design.md 7절 11번) */
    val openCatalog: () -> InputStream,
    /** 사용자 데이터 서버 주소. 예: http://10.0.2.2:8080/api/v1/ */
    val apiBaseUrl: String,
    val auth: AuthGateway,
    /** 이 시간이 지나도 서버가 대답하지 않으면 "서버를 깨우는 중이에요"를 보여 준다 */
    val slowServerHintMillis: Long = 3_000,
    /** 사용 기록을 서버로 보내는 간격 */
    val eventFlushIntervalMillis: Long = 30_000,
    /** 서버 요청에 끼우는 OkHttp 인터셉터. 테스트는 여기에 가짜 서버를 끼운다 */
    val httpInterceptors: List<Interceptor> = emptyList(),
)

class ScentApplication : Application() {

    /** 테스트가 앱을 켜기 전에 넣는다. null이면 실제 설정으로 조립한다(4단계). */
    var configOverride: AppConfig? = null

    /**
     * 시스템이 앱을 정리했다가 다시 켠 것처럼, 메모리에 있던 것을 모두 버리고 다시 조립한다.
     * 폰 안에 저장한 것(Room 등)은 남는다.
     */
    fun simulateProcessRestart() {
        TODO("4단계에서 구현")
    }
}
