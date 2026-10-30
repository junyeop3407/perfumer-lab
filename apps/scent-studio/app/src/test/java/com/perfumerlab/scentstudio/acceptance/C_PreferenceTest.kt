package com.perfumerlab.scentstudio.acceptance

import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 C. 불호 노트와 취향, 로그인 없이도 (docs/tests.md) */
class C_PreferenceTest : AcceptanceTest() {

    @Test
    fun c01_불호_노트가_든_향수는_추천에서_빠진다() {
        launchApp()
        addDislikedNote("패출", "patchouli")
        buildA3Blend()
        goToSummary()
        showRecommendations()
        val names = recommendations().map { it.name }
        val leaked = names.filter { it in PATCHOULI_PERFUMES }
        assertTrue("패출리가 든 향수가 추천됨: $leaked", leaked.isEmpty())
    }

    @Test
    fun c02_불호_노트는_앱을_껐다_켜도_남는다() {
        launchApp()
        addDislikedNote("패출", "patchouli")
        restartApp()
        openTab(Tags.TAB_MY)
        waitForTag(Tags.dislikedChip("patchouli"))
    }

    @Test
    fun c03_선호_계열을_정하면_그_계열이_강한_향수가_위로() {
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        val before = recommendations().map { it.name }

        openTab(Tags.TAB_MY)
        click(Tags.preferredFamily("floral"))
        buildA3Blend()
        goToSummary()
        showRecommendations()
        val after = recommendations().map { it.name }

        assertNotEquals("선호 계열을 정해도 순위가 그대로", before, after)
        fun rankOf(list: List<String>, name: String) = list.indexOf(name).let { if (it < 0) 99 else it }
        val rose = FLORAL_FIRST_PERFUMES.any { rankOf(after, it) < rankOf(before, it) }
        assertTrue("플로럴이 1위 어코드인 향수 중 위로 온 것이 없음\n전: $before\n후: $after", rose)
    }
}
