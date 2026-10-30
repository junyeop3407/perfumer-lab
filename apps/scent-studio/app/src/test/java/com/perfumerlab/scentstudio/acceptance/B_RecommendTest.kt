package com.perfumerlab.scentstudio.acceptance

import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 B. 추천 보기 (docs/tests.md) */
class B_RecommendTest : AcceptanceTest() {

    @Test
    fun b01_추천_10개가_점수_높은_순() {
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        val recs = recommendations()
        assertEquals(10, recs.size)
        recs.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.rank}위(${a.score}) < ${b.rank}위(${b.score})", a.score >= b.score)
        }
    }

    @Test
    fun b02_향수마다_이름_점수_등급_공통어코드_일치노트() {
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        recommendations().forEach { r ->
            assertTrue("${r.rank}위 이름", r.name.isNotBlank())
            assertTrue("${r.rank}위 브랜드", r.brand.isNotBlank())
            assertTrue("${r.rank}위 점수 '${r.scoreText}'", r.scoreText.contains("%"))
            assertTrue("${r.rank}위 등급 '${r.grade}'", r.grade in GRADES)
            assertTrue("${r.rank}위 공통 어코드", r.sharedAccords.isNotBlank())
            assertTrue("${r.rank}위 일치 노트", r.matchedNotes.isNotBlank())
        }
    }

    @Test
    fun b03_한_브랜드는_최대_2개() {
        // 가짜 데이터의 Maison Verte는 이 조합과 비슷한 향수가 6개 있다
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        recommendations().groupingBy { it.brand }.eachCount().forEach { (brand, count) ->
            assertTrue("$brand 가 ${count}개", count <= 2)
        }
    }

    @Test
    fun b04_맹점_조합에서_가벼운_향수가_무거운_향수보다_위() {
        launchApp()
        buildBlindSpotBlend()
        goToSummary()
        showRecommendations()
        val names = recommendations().map { it.name }
        assertEquals("가벼운 시트러스·플로럴 향수가 1위", "Lumière d'Agrumes", names.first())
        assertNotEquals("무거운 우디 향수가 1위", "Santal Profond", names.first())
    }

    @Test
    fun b05_향수_상세에_피라미드_비교막대_일치_차이_노트() {
        launchApp()
        buildBlindSpotBlend()
        goToSummary()
        showRecommendations()
        click(Tags.rec(1))
        waitForTag(Tags.DETAIL_SCREEN)
        listOf("top", "middle", "base").forEach { assertTrue("피라미드 $it", exists(Tags.pyramid(it))) }
        val bars = nodesWithTagPrefix(Tags.COMPARE_BAR_PREFIX).map(::allText)
        assertTrue("비교 막대 없음", bars.isNotEmpty())
        bars.forEach { assertTrue("비교 막대에 % 글자 없음: $it", it.contains("%")) }
        assertTrue(exists(Tags.MATCHED_NOTES))
        assertTrue(exists(Tags.ONLY_IN_PERFUME_NOTES))
    }

    @Test
    fun b06_데이터상_유사도와_출처가_보인다() {
        launchApp()
        buildA3Blend()
        goToSummary()
        showRecommendations()
        assertTrue(textOf(Tags.SIMILARITY_DISCLAIMER).contains("데이터상 유사도"))
        assertTrue(textOf(Tags.DATA_SOURCE).contains("Parfumo"))
        click(Tags.rec(1))
        waitForTag(Tags.DETAIL_SCREEN)
        assertTrue(textOf(Tags.SIMILARITY_DISCLAIMER).contains("데이터상 유사도"))
        assertTrue(textOf(Tags.DATA_SOURCE).contains("Parfumo"))
    }
}
