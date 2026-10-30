package com.perfumerlab.scentstudio.acceptance

import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 E. 끊기지 않게 (docs/tests.md) */
class E_ResilienceTest : AcceptanceTest() {

    @Test
    fun e01_앱이_꺼졌다_켜져도_만들던_조합을_이어_한다() {
        launchApp()
        startNewBlend("citrus", "floral")
        pickNote("top", "lemon", "strong")
        pickNote("middle", "rose", "medium")
        restartApp()

        click(Tags.RESUME_BLEND)
        waitForTag(Tags.NOTE_LIST)
        assertTrue(textOf(Tags.selectedNote("top", "lemon")).contains("강"))
        assertTrue(textOf(Tags.selectedNote("middle", "rose")).contains("중"))
    }

    @Test
    fun e02_인터넷이_없어도_조합_추천_상세_검색이_된다() {
        server.offline = true
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        assertEquals(10, recommendations().size)
        click(Tags.rec(1))
        waitForTag(Tags.DETAIL_SCREEN)

        openTab(Tags.TAB_SEARCH)
        type(Tags.PERFUME_SEARCH, "Verte")
        waitForTag(Tags.searchResult("T003"))
    }

    @Test
    fun e03_인터넷이_없을_때_저장하면_안내와_다시_시도() {
        auth.signInDirectly()
        server.offline = true
        launchApp()
        buildA3Blend()
        goToSummary()
        click(Tags.SAVE_BLEND)
        type(Tags.BLEND_NAME_FIELD, "오프라인 조합")
        click(Tags.CONFIRM_SAVE)

        waitForTag(Tags.ERROR_MESSAGE)
        assertTrue(textOf(Tags.ERROR_MESSAGE).contains("인터넷 연결을 확인해 주세요"))
        assertTrue(exists(Tags.ERROR_RETRY))
        assertTrue("앱이 요약 화면에 그대로 있어야 함", exists(Tags.SUMMARY_SCREEN))

        server.offline = false
        click(Tags.ERROR_RETRY)
        waitUntil { server.blends.isNotEmpty() }
    }

    @Test
    fun e04_서버가_늦으면_깨우는_중_안내_후_저장된다() {
        auth.signInDirectly()
        launchApp()
        buildA3Blend()
        goToSummary()
        server.slowMillis = 4_500
        click(Tags.SAVE_BLEND)
        type(Tags.BLEND_NAME_FIELD, "느린 서버 조합")
        click(Tags.CONFIRM_SAVE)

        waitForTag(Tags.SLOW_SERVER_HINT, timeoutMillis = 4_000)
        assertTrue(textOf(Tags.SLOW_SERVER_HINT).contains("서버를 깨우는 중이에요"))
        waitUntil(timeoutMillis = 10_000) { server.blends.isNotEmpty() }

        server.slowMillis = 0
        openTab(Tags.TAB_LIBRARY)
        click(Tags.LIB_BLENDS)
        waitForTag(Tags.libBlend("느린 서버 조합"))
    }
}
