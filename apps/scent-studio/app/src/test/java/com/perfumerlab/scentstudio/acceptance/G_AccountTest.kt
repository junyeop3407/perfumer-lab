package com.perfumerlab.scentstudio.acceptance

import androidx.compose.ui.test.assertHasClickAction
import com.perfumerlab.scentstudio.acceptance.harness.AcceptanceTest
import com.perfumerlab.scentstudio.ui.Tags
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승인 목록 G. 계정 (docs/tests.md) */
class G_AccountTest : AcceptanceTest() {

    @Test
    fun g01_계정_삭제를_누르면_되돌릴_수_없다는_확인창() {
        auth.signInDirectly()
        launchApp()
        openTab(Tags.TAB_MY)
        click(Tags.DELETE_ACCOUNT)
        val dialog = textOf(Tags.DELETE_DIALOG)
        assertTrue(dialog, dialog.contains("저장한 조합·찜·시향 기록이 모두 지워지고"))
        assertTrue(dialog, dialog.contains("되돌릴 수 없어요"))
    }

    @Test
    fun g02_삭제하면_로그아웃되고_보관함이_빈다() {
        server.blends += Json.parseToJsonElement(
            """{"id":"b-1","name":"지워질 조합","families":["citrus"],
               "notes":[{"noteKey":"lemon","layer":"top","intensity":1.0}]}""",
        ).jsonObject
        auth.signInDirectly()
        launchApp()
        openTab(Tags.TAB_MY)
        click(Tags.DELETE_ACCOUNT)
        click(Tags.DELETE_CONFIRM)

        waitUntil { server.accountDeleted }
        waitUntil { auth.session.value == null }
        assertNull(auth.session.value)
        waitForTag(Tags.MY_LOGIN)

        openTab(Tags.TAB_LIBRARY)
        waitForTag(Tags.EMPTY_STATE)
        assertFalse(exists(Tags.libBlend("지워질 조합")))
    }

    @Test
    fun g03_개인정보처리방침과_웹_삭제_안내_링크() {
        launchApp()
        openTab(Tags.TAB_MY)
        assertTrue(textOf(Tags.PRIVACY_LINK).contains("개인정보처리방침"))
        node(Tags.PRIVACY_LINK).assertHasClickAction()
        assertTrue(textOf(Tags.DELETION_GUIDE_LINK).contains("계정 삭제"))
        node(Tags.DELETION_GUIDE_LINK).assertHasClickAction()
    }
}
