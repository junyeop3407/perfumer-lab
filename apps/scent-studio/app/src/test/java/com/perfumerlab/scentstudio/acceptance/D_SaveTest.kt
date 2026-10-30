package com.perfumerlab.scentstudio.acceptance

import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 D. 저장 · 찜 · 시향 피드백 (docs/tests.md) */
class D_SaveTest : AcceptanceTest() {

    @Test
    fun d01_로그인_안하고_저장이나_찜을_누르면_로그인_화면() {
        launchApp()
        buildA3Blend()
        goToSummary()
        click(Tags.SAVE_BLEND)
        waitForTag(Tags.LOGIN_SCREEN)

        launchApp()
        openPerfumeBySearch("Lumière", "T001")
        click(Tags.FAVORITE_TOGGLE)
        waitForTag(Tags.LOGIN_SCREEN)
    }

    @Test
    fun d02_조합에_이름을_붙여_저장하면_보관함에_보인다() {
        auth.signInDirectly()
        launchApp()
        buildA3Blend()
        goToSummary()
        click(Tags.SAVE_BLEND)
        type(Tags.BLEND_NAME_FIELD, "봄 산책")
        click(Tags.CONFIRM_SAVE)
        waitUntil { server.blends.isNotEmpty() }

        openTab(Tags.TAB_LIBRARY)
        click(Tags.LIB_BLENDS)
        waitForTag(Tags.libBlend("봄 산책"))
    }

    @Test
    fun d03_찜하면_보관함에_생기고_풀면_사라진다() {
        auth.signInDirectly()
        launchApp()
        openPerfumeBySearch("Lumière", "T001")
        click(Tags.FAVORITE_TOGGLE)
        waitUntil { "T001" in server.favorites }
        openTab(Tags.TAB_LIBRARY)
        click(Tags.LIB_FAVORITES)
        waitForTag(Tags.libFavorite("T001"))

        openPerfumeBySearch("Lumière", "T001")
        click(Tags.FAVORITE_TOGGLE)
        waitUntil { "T001" !in server.favorites }
        openTab(Tags.TAB_LIBRARY)
        click(Tags.LIB_FAVORITES)
        waitUntil { !exists(Tags.libFavorite("T001")) }
    }

    @Test
    fun d04_시향_피드백이_보관함_시향_기록에_보인다() {
        auth.signInDirectly()
        launchApp()
        openPerfumeBySearch("Lumière", "T001")
        click(Tags.WRITE_FEEDBACK)
        click(Tags.satisfaction(4))
        click(Tags.similarity(3))
        type(Tags.FEEDBACK_MEMO, "첫 향이 상큼했어요")
        click(Tags.FEEDBACK_SUBMIT)
        waitUntil { server.feedback.isNotEmpty() }

        openTab(Tags.TAB_LIBRARY)
        click(Tags.LIB_FEEDBACK)
        val item = textOf(Tags.libFeedback("T001"))
        assertTrue("메모 없음: $item", item.contains("첫 향이 상큼했어요"))
        assertTrue("만족도 4 없음: $item", item.contains("4"))
        assertTrue("비슷했나 3 없음: $item", item.contains("3"))
    }

    @Test
    fun d05_저장한_조합을_누르면_요약이_열리고_추천을_다시_본다() {
        server.blends += Json.parseToJsonElement(
            """{"id":"b-1","name":"저장된 조합","families":["citrus","woody"],
               "notes":[{"noteKey":"lemon","layer":"top","intensity":1.5},
                        {"noteKey":"rose","layer":"middle","intensity":1.0},
                        {"noteKey":"sandalwood","layer":"base","intensity":0.5}]}""",
        ).jsonObject
        auth.signInDirectly()
        launchApp()
        openTab(Tags.TAB_LIBRARY)
        click(Tags.LIB_BLENDS)
        click(Tags.libBlend("저장된 조합"))
        waitForTag(Tags.SUMMARY_SCREEN)
        assertTrue(exists(Tags.selectedNote("top", "lemon")))
        showRecommendations()
        assertFalse(recommendations().isEmpty())
    }

    @Test
    fun d06_로그인하면_폰의_불호와_서버의_불호가_합쳐진다() {
        server.dislikedNoteKeys = listOf("oakmoss")
        launchApp()
        addDislikedNote("패출", "patchouli")
        click(Tags.MY_LOGIN)
        click(Tags.LOGIN_GOOGLE)
        openTab(Tags.TAB_MY)
        waitForTag(Tags.dislikedChip("patchouli"))
        waitForTag(Tags.dislikedChip("oakmoss"))
    }
}
