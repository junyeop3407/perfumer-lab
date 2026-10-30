package com.perfumerlab.scentstudio.acceptance

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 A. 향 조합 만들기 (docs/tests.md) */
class A_BlendTest : AcceptanceTest() {

    @Test
    fun a01_앱을_켜면_탭4개와_새조합만들기() {
        launchApp()
        listOf(
            Tags.TAB_HOME to "홈", Tags.TAB_SEARCH to "향수 찾기",
            Tags.TAB_LIBRARY to "보관함", Tags.TAB_MY to "마이",
        ).forEach { (tag, label) -> assertTrue("탭 '$label'", textOf(tag).contains(label)) }
        assertTrue(textOf(Tags.NEW_BLEND).contains("새 조합 만들기"))
    }

    @Test
    fun a02_계열을_고르면_층탭이_있고_고른_계열_노트가_먼저() {
        launchApp()
        startNewBlend("citrus", "woody")
        listOf("top", "middle", "base").forEach { assertTrue("층 탭 $it", exists(Tags.layerTab(it))) }
        val firstNote = nodesWithTagPrefix("note:").first()
        val text = allText(firstNote)
        assertTrue("첫 노트가 시트러스·우디 계열이 아님: $text", text.contains("시트러스") || text.contains("우디"))
    }

    @Test
    fun a03_노트마다_고른_강도가_보인다() {
        launchApp()
        buildA3Blend()
        assertTrue(textOf(Tags.selectedNote("top", "lemon")).contains("강"))
        assertTrue(textOf(Tags.selectedNote("middle", "rose")).contains("중"))
        assertTrue(textOf(Tags.selectedNote("base", "sandalwood")).contains("약"))
    }

    @Test
    fun a04_층마다_3개까지만() {
        launchApp()
        startNewBlend("citrus")
        pickNote("top", "lemon")
        pickNote("top", "bergamot")
        pickNote("top", "grapefruit")
        click(Tags.noteItem("mandarin orange"))
        assertFalse("4번째 노트가 들어감", exists(Tags.selectedNote("top", "mandarin orange")))
        assertTrue(textOf(Tags.LAYER_LIMIT_MESSAGE).contains("층마다 3개까지 고를 수 있어요"))
    }

    @Test
    fun a05_한글과_영문으로_노트를_검색한다() {
        launchApp()
        startNewBlend("citrus")
        type(Tags.NOTE_SEARCH, "베르가")
        waitForTag(Tags.noteItem("bergamot"))
        assertTrue(textOf(Tags.noteItem("bergamot")).contains("베르가못"))
        type(Tags.NOTE_SEARCH, "berg")
        waitForTag(Tags.noteItem("bergamot"))
        assertTrue(textOf(Tags.noteItem("bergamot")).contains("베르가못"))
    }

    @Test
    fun a06_목록에_없는_드문_노트도_검색으로_고른다() {
        launchApp()
        startNewBlend("woody")
        click(Tags.layerTab("middle"))
        val inBrowseList = runCatching {
            node(Tags.NOTE_LIST).performScrollToNode(hasTestTag(Tags.noteItem("hinoki")))
        }.isSuccess
        assertFalse("히노키는 목록에 없어야 함(검색 전용)", inBrowseList)

        type(Tags.NOTE_SEARCH, "히노키")
        click(Tags.noteItem("hinoki"))
        waitForTag(Tags.selectedNote("middle", "hinoki"))
    }

    @Test
    fun a07_노트_카드에_설명_계열_궁합노트3개() {
        launchApp()
        startNewBlend("citrus")
        click(Tags.noteInfo("lemon"))
        waitForTag(Tags.NOTE_CARD)
        assertTrue(textOf(Tags.NOTE_CARD_DESCRIPTION).contains("밝고 산뜻하게 톡 쏘는 첫인상"))
        assertTrue(textOf(Tags.NOTE_CARD_FAMILY).contains("시트러스"))
        assertEquals(3, compose.onAllNodes(hasTestTag(Tags.NOTE_CARD_PAIRING), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun a08_요약에_층별_노트와_합100인_어코드_막대() {
        launchApp()
        buildA3Blend()
        goToSummary()
        assertTrue(exists(Tags.selectedNote("top", "lemon")))
        assertTrue(exists(Tags.selectedNote("middle", "rose")))
        assertTrue(exists(Tags.selectedNote("base", "sandalwood")))

        val bars = nodesWithTagPrefix(Tags.ACCORD_BAR_PREFIX).map(::allText)
        assertTrue("어코드 막대가 없음", bars.isNotEmpty())
        val percents = bars.map { bar ->
            assertTrue("막대에 %가 글자로 없음: $bar", bar.contains("%"))
            Regex("""(\d+)\s*%""").find(bar)!!.groupValues[1].toInt()
        }
        assertEquals("막대 % 합", 100, percents.sum())
    }

    @Test
    fun a09_베이스가_비면_안내() {
        launchApp()
        startNewBlend("citrus", "floral")
        pickNote("top", "lemon")
        pickNote("middle", "rose")
        goToSummary()
        assertTrue(textOf(Tags.BALANCE_HINT).contains("베이스가 비어 있어"))
    }

    @Test
    fun a10_노트_수정으로_돌아가도_고른_노트가_그대로() {
        launchApp()
        buildA3Blend()
        goToSummary()
        click(Tags.EDIT_NOTES)
        waitForTag(Tags.NOTE_LIST)
        assertTrue(textOf(Tags.selectedNote("top", "lemon")).contains("강"))
        assertTrue(textOf(Tags.selectedNote("middle", "rose")).contains("중"))
        assertTrue(textOf(Tags.selectedNote("base", "sandalwood")).contains("약"))
    }
}
