package com.perfumerlab.scentstudio.acceptance

import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 I. 사용 기록 (docs/tests.md, docs/design.md 13절) */
class I_EventsTest : AcceptanceTest() {

    private val flow = listOf("blend_started", "blend_summary_viewed", "recs_viewed")

    @Test
    fun i01_조합_시작_요약_추천_기록이_서버로_간다() {
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        waitUntil { server.receivedEvents.containsAll(flow) }
    }

    @Test
    fun i02_인터넷이_끊겼다가_연결되면_밀린_기록이_간다() {
        server.offline = true
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        assertTrue("끊긴 동안 받은 기록이 있으면 안 됨", server.receivedEvents.isEmpty())

        server.offline = false
        waitUntil { server.receivedEvents.containsAll(flow) }
    }
}
