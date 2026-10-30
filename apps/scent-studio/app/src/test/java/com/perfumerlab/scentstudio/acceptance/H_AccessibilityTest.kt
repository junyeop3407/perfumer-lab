package com.perfumerlab.scentstudio.acceptance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 H. 보기 편하게: 화면 크기 · 글자 크기 · 화면 읽기 (docs/tests.md) */
class H_AccessibilityTest : AcceptanceTest() {

    private val small = "w360dp-h640dp"
    private val large = "w411dp-h891dp"

    @Test
    fun h01_작은폰_큰폰_라이트_다크에서_잘리거나_겹치지_않는다() {
        for (size in listOf(small, large)) for (mode in listOf("notnight", "night")) {
            val variant = "$size-$mode"
            launchAppWith("$size-$mode-xxhdpi")
            visitMainScreens(variant) { screen ->
                assertNoClippedText("$variant/$screen")
                assertNoOverlappingTargets("$variant/$screen")
                capture("H1_${variant}_$screen")
            }
        }
    }

    @Test
    fun h02_글자_200퍼센트에서도_잘리지_않고_버튼을_누를_수_있다() {
        // visitMainScreens 는 각 화면의 주요 버튼을 스크롤해서 눌러 다음 화면으로 간다
        launchAppWith("$small-notnight-xxhdpi", fontScale = 2f)
        visitMainScreens("font200") { screen ->
            assertNoClippedText("글자200%/$screen")
            capture("H2_font200_$screen")
        }
    }

    @Test
    fun h03_화면_읽기가_어코드_막대를_이름과_퍼센트로_읽는다() {
        launchApp()
        buildA3Blend()
        goToSummary()
        val bars = nodesWithTagPrefix(Tags.ACCORD_BAR_PREFIX)
        assertTrue("어코드 막대 없음", bars.isNotEmpty())
        bars.forEach { bar ->
            val spoken = bar.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            assertTrue("화면 읽기 문구가 '이름 NN퍼센트'가 아님: '$spoken'", Regex("""^\S.* \d+퍼센트$""").matches(spoken))
        }
    }

    @Test
    fun h04_누를_수_있는_것은_모두_48dp_이상() {
        launchApp()
        visitMainScreens("touch") { screen -> assertTouchTargetsAtLeast48dp(screen) }
    }

    /** 홈 · 노트 선택 · 요약 · 추천 · 상세 · 보관함 · 마이를 차례로 열며 check를 부른다 */
    private fun visitMainScreens(variant: String, check: (screen: String) -> Unit) {
        seedLibrary()
        auth.signInDirectly()
        openTab(Tags.TAB_HOME)
        check("home")
        buildA3Blend()
        check("notes")
        goToSummary()
        check("summary")
        showRecommendations()
        check("recs")
        click(Tags.rec(1))
        waitForTag(Tags.DETAIL_SCREEN)
        check("detail")
        openTab(Tags.TAB_LIBRARY)
        click(Tags.LIB_BLENDS)
        waitForTag(Tags.libBlend("저장된 조합"))
        check("library")
        openTab(Tags.TAB_MY)
        check("my")
    }

    private fun seedLibrary() {
        if (server.blends.isNotEmpty()) return
        server.blends += Json.parseToJsonElement(
            """{"id":"b-1","name":"저장된 조합","families":["citrus","woody"],
               "notes":[{"noteKey":"lemon","layer":"top","intensity":1.5},
                        {"noteKey":"sandalwood","layer":"base","intensity":0.5}]}""",
        ).jsonObject
        server.favorites += "T001"
    }
}
