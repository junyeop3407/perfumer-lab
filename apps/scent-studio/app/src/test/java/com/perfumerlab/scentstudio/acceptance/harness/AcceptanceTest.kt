package com.perfumerlab.scentstudio.acceptance.harness

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.perfumerlab.scentstudio.AppConfig
import com.perfumerlab.scentstudio.MainActivity
import com.perfumerlab.scentstudio.ScentApplication
import com.perfumerlab.scentstudio.ui.Tags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 승인 목록(docs/tests.md) 테스트의 공통 준비.
 * 가짜 향수 데이터(tests/fixtures → app/src/test/resources/catalog/test_catalog.json.gz),
 * 가짜 서버(FakeUserServer), 가짜 로그인(FakeAuthGateway)으로 앱을 켠다.
 * 기본 화면은 큰 폰(411dp) 라이트 모드.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class AcceptanceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    protected val server = FakeUserServer()
    protected val auth = FakeAuthGateway()
    private var scenario: ActivityScenario<MainActivity>? = null

    protected val app: ScentApplication
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun configureApp() {
        app.configOverride = AppConfig(
            openCatalog = {
                javaClass.getResourceAsStream("/catalog/test_catalog.json.gz")
                    ?: error("테스트 카탈로그가 없습니다. pipeline으로 tests/fixtures에서 만든다 (docs/design.md 7절)")
            },
            apiBaseUrl = "https://fake.scent.test/api/v1/",
            auth = auth,
            eventFlushIntervalMillis = 200,
            httpInterceptors = listOf(server),
        )
    }

    @After
    fun closeApp() {
        scenario?.close()
    }

    // ---------- 앱 켜기 ----------

    protected fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag(Tags.TAB_HOME)
    }

    /** 시스템이 앱을 정리했다가 다시 켠 것처럼 (폰에 저장한 것만 남음) */
    protected fun restartApp() {
        scenario?.close()
        app.simulateProcessRestart()
        launchApp()
    }

    /** 화면 크기·모드를 바꿔 켠다. 예: "w360dp-h640dp-night" */
    protected fun launchAppWith(qualifiers: String, fontScale: Float = 1f) {
        scenario?.close()
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        launchApp()
    }

    // ---------- 찾기 · 누르기 · 읽기 ----------

    protected fun node(tag: String): SemanticsNodeInteraction =
        compose.onNodeWithTag(tag, useUnmergedTree = true)

    protected fun exists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun waitForTag(tag: String, timeoutMillis: Long = 5_000) {
        compose.waitUntil(timeoutMillis) { exists(tag) }
    }

    protected fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis, condition)
    }

    protected fun click(tag: String) {
        waitForTag(tag)
        val n = node(tag)
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    protected fun type(tag: String, text: String) {
        waitForTag(tag)
        node(tag).performTextClearance()
        node(tag).performTextInput(text)
        compose.waitForIdle()
    }

    /** 요소와 그 안의 글자를 모두 이어 붙인 것 (보이는 글자 + 화면 읽기 설명) */
    protected fun textOf(tag: String): String {
        waitForTag(tag)
        val n = compose.onNodeWithTag(tag).fetchSemanticsNode()
        return allText(n)
    }

    protected fun allText(n: SemanticsNode): String {
        val own = buildList {
            n.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
            n.config.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { add(it) }
        }
        return (own + n.children.map(::allText)).filter { it.isNotBlank() }.joinToString(" ")
    }

    protected fun hasTagPrefix(prefix: String) = SemanticsMatcher("testTag가 $prefix 로 시작") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    protected fun nodesWithTagPrefix(prefix: String): List<SemanticsNode> =
        compose.onAllNodes(hasTagPrefix(prefix), useUnmergedTree = true).fetchSemanticsNodes()

    protected fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        compose.waitUntil(timeoutMillis) { allText(compose.onRoot().fetchSemanticsNode()).contains(text) }
    }

    protected fun screenText(): String = allText(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())

    // ---------- 자주 하는 동작 ----------

    protected fun openTab(tabTag: String) = click(tabTag)

    /** 홈 → 새 조합 → 계열 고르기 → 다음 */
    protected fun startNewBlend(vararg familyCodes: String) {
        openTab(Tags.TAB_HOME)
        click(Tags.NEW_BLEND)
        familyCodes.forEach { click(Tags.family(it)) }
        click(Tags.NEXT)
        waitForTag(Tags.NOTE_LIST)
    }

    /** 층 탭을 고르고 목록에서 노트를 골라 강도를 정한다. level: weak / medium / strong */
    protected fun pickNote(layer: String, noteKey: String, level: String? = null) {
        click(Tags.layerTab(layer))
        node(Tags.NOTE_LIST).performScrollToNode(hasTestTag(Tags.noteItem(noteKey)))
        click(Tags.noteItem(noteKey))
        waitForTag(Tags.selectedNote(layer, noteKey))
        if (level != null) click(Tags.intensity(noteKey, level))
    }

    /** 승인 목록 A3의 조합: 시트러스·우디 / 탑 레몬(강) · 미들 장미(중) · 베이스 샌달우드(약) */
    protected fun buildA3Blend() {
        startNewBlend("citrus", "woody")
        pickNote("top", "lemon", "strong")
        pickNote("middle", "rose", "medium")
        pickNote("base", "sandalwood", "weak")
    }

    /** 기획서의 맹점 조합: 탑 레몬(강) · 미들 오스만투스(중) · 베이스 샌달우드(약) */
    protected fun buildBlindSpotBlend() {
        startNewBlend("citrus", "floral", "woody")
        pickNote("top", "lemon", "strong")
        pickNote("middle", "osmanthus", "medium")
        pickNote("base", "sandalwood", "weak")
    }

    protected fun goToSummary() {
        click(Tags.TO_SUMMARY)
        waitForTag(Tags.SUMMARY_SCREEN)
    }

    protected fun showRecommendations() {
        click(Tags.SHOW_RECS)
        waitForTag(Tags.REC_LIST)
    }

    /** 추천 목록을 위에서부터 끝까지 내려가며 읽는다 */
    protected fun recommendations(): List<Rec> {
        val recs = mutableListOf<Rec>()
        for (rank in 1..20) {
            val found = runCatching {
                node(Tags.REC_LIST).performScrollToNode(hasTestTag(Tags.rec(rank)))
            }.isSuccess
            if (!found) break
            recs += Rec(
                rank = rank,
                name = textOf(Tags.recName(rank)),
                brand = textOf(Tags.recBrand(rank)),
                scoreText = textOf(Tags.recScore(rank)),
                grade = textOf(Tags.recGrade(rank)),
                sharedAccords = textOf(Tags.recSharedAccords(rank)),
                matchedNotes = textOf(Tags.recMatchedNotes(rank)),
            )
        }
        return recs
    }

    data class Rec(
        val rank: Int,
        val name: String,
        val brand: String,
        val scoreText: String,
        val grade: String,
        val sharedAccords: String,
        val matchedNotes: String,
    ) {
        /** "82%" → 82 */
        val score: Int get() = Regex("""\d+""").find(scoreText)?.value?.toInt() ?: error("점수 숫자 없음: $scoreText")
    }

    /** 마이에서 불호 노트를 검색해 더한다 */
    protected fun addDislikedNote(searchText: String, noteKey: String) {
        openTab(Tags.TAB_MY)
        type(Tags.DISLIKE_SEARCH, searchText)
        click(Tags.dislikeOption(noteKey))
        waitForTag(Tags.dislikedChip(noteKey))
    }

    /** 향수 찾기에서 검색해 상세를 연다 */
    protected fun openPerfumeBySearch(query: String, perfumeKey: String) {
        openTab(Tags.TAB_SEARCH)
        type(Tags.PERFUME_SEARCH, query)
        click(Tags.searchResult(perfumeKey))
        waitForTag(Tags.DETAIL_SCREEN)
    }

    // ---------- 보기 편하게(H) 검사 ----------

    /** 글자가 말줄임(…)이나 넘침으로 잘리지 않았는지 */
    protected fun assertNoClippedText(where: String) {
        val clipped = compose.onAllNodes(SemanticsMatcher("글자 있음") {
            it.config.getOrNull(SemanticsProperties.Text) != null
        }, useUnmergedTree = true).fetchSemanticsNodes().filter { n ->
            val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return@filter false
            val results = mutableListOf<TextLayoutResult>()
            action.action?.invoke(results)
            results.any { it.hasVisualOverflow }
        }
        if (clipped.isNotEmpty()) {
            fail("$where: 잘린 글자 ${clipped.map(::allText)}")
        }
    }

    /** 누를 수 있는 것끼리 겹치지 않는지 (안에 든 것끼리는 제외) */
    protected fun assertNoOverlappingTargets(where: String) {
        val targets = clickableNodes()
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val a = targets[i]
            val b = targets[j]
            if (isAncestor(a, b) || isAncestor(b, a)) continue
            val overlap = a.boundsInRoot.intersect(b.boundsInRoot)
            if (overlap.width > 1f && overlap.height > 1f) {
                fail("$where: 겹친 버튼 '${allText(a)}' / '${allText(b)}'")
            }
        }
    }

    /** 누를 수 있는 것은 모두 48dp 이상 */
    protected fun assertTouchTargetsAtLeast48dp(where: String) {
        val density = app.resources.displayMetrics.density
        val min = 48 * density - 0.5f
        clickableNodes().forEach { n ->
            val r: Rect = n.touchBoundsInRoot
            assertTrue(
                "$where: '${allText(n)}' 누르는 영역이 ${r.width / density}x${r.height / density}dp",
                r.width >= min && r.height >= min,
            )
        }
    }

    protected fun capture(name: String) {
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun clickableNodes(): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher("누를 수 있음") {
            it.config.getOrNull(SemanticsActions.OnClick) != null
        }, useUnmergedTree = false).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }

    private fun isAncestor(maybeAncestor: SemanticsNode, node: SemanticsNode): Boolean {
        var p = node.parent
        while (p != null) {
            if (p.id == maybeAncestor.id) return true
            p = p.parent
        }
        return false
    }

    protected companion object {
        /** 가짜 데이터에서 패출리가 어느 층에든 든 향수 (tests/fixtures/synthetic_parfumo.csv) */
        val PATCHOULI_PERFUMES = setOf(
            "Santal Profond", "Rose Patchouli", "Citron Terre", "Jardin Sombre",
            "Cuir Fumé", "Épices d'Or", "Mousse de Chêne",
        )

        /** 가짜 데이터에서 어코드 1위가 플로럴인 향수 */
        val FLORAL_FIRST_PERFUMES = setOf(
            "Verte No.2", "Verte No.5", "Rose Patchouli", "Pivoine Blanche",
            "Rose d'Été", "Fleur de Thé", "Osmanthe Doré", "Jasmin Nuit",
        )

        val GRADES = setOf("매우 비슷", "비슷", "참고")
    }
}
