package com.perfumerlab.scentstudio.acceptance

import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 F. 향수 찾기 (docs/tests.md) */
class F_SearchTest : AcceptanceTest() {

    @Test
    fun f01_이름이나_브랜드로_검색해_상세를_연다() {
        launchApp()
        openTab(Tags.TAB_SEARCH)
        type(Tags.PERFUME_SEARCH, "Lumière")
        waitForTag(Tags.searchResult("T001"))

        type(Tags.PERFUME_SEARCH, "Noirwood")
        listOf("T002", "T022", "T023", "T036").forEach { waitForTag(Tags.searchResult(it)) }

        type(Tags.PERFUME_SEARCH, "Lumière")
        click(Tags.searchResult("T001"))
        waitForTag(Tags.DETAIL_SCREEN)
    }

    @Test
    fun f02_조합_없이_연_상세는_비교막대_없이_어코드와_피라미드만() {
        launchApp()
        openPerfumeBySearch("Lumière", "T001")
        assertTrue("비교 막대가 있으면 안 됨", nodesWithTagPrefix(Tags.COMPARE_BAR_PREFIX).isEmpty())
        assertTrue("향수 어코드가 없음", nodesWithTagPrefix(Tags.PERFUME_ACCORD_PREFIX).isNotEmpty())
        listOf("top", "middle", "base").forEach { assertTrue("피라미드 $it", exists(Tags.pyramid(it))) }
    }
}
