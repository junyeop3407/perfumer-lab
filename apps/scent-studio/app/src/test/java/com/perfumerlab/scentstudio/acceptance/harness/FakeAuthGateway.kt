package com.perfumerlab.scentstudio.acceptance.harness

import android.app.Activity
import com.perfumerlab.scentstudio.auth.AuthGateway
import com.perfumerlab.scentstudio.auth.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 구글 로그인 흉내. "Google로 로그인"을 누르면 바로 로그인된다. */
class FakeAuthGateway(
    private val userId: String = FakeUserServer.TEST_USER_ID,
) : AuthGateway {
    private val state = MutableStateFlow<AuthSession?>(null)
    override val session: StateFlow<AuthSession?> = state

    override suspend fun signIn(activity: Activity): Boolean {
        state.value = AuthSession(userId, FakeUserServer.TEST_TOKEN)
        return true
    }

    override suspend fun signOut() {
        state.value = null
    }

    /** 테스트 준비용: 화면을 거치지 않고 로그인된 상태로 시작 */
    fun signInDirectly() {
        state.value = AuthSession(userId, FakeUserServer.TEST_TOKEN)
    }
}
