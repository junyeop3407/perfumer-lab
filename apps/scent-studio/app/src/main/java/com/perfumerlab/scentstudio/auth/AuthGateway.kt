package com.perfumerlab.scentstudio.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** 로그인한 사용자. accessToken은 서버 요청의 Bearer 토큰으로 쓴다. */
data class AuthSession(val userId: String, val accessToken: String)

/**
 * 구글 로그인 창구. 실제 앱은 Credential Manager + Supabase로 만들고,
 * 테스트는 가짜로 바꿔 끼운다 (구글 로그인은 자동 테스트가 안 됨).
 */
interface AuthGateway {
    val session: StateFlow<AuthSession?>

    /** 로그인 화면의 "Google로 로그인" 버튼. 성공하면 true. */
    suspend fun signIn(activity: Activity): Boolean

    suspend fun signOut()
}
