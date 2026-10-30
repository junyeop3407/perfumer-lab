package com.perfumerlab.scentstudio.ui

/**
 * 화면 요소 이름표(testTag). 승인된 테스트(docs/tests.md)가 이 이름으로 요소를 찾는다.
 * 이름을 바꾸면 승인된 테스트가 요소를 못 찾으므로, 바꾸기 전에 사장 승인을 다시 받는다.
 *
 * 값 규칙: 노트는 note key(정리된 영문 이름, 예 "lemon"), 향수는 perfume key(예 "T001"),
 * 층은 "top" / "middle" / "base", 강도는 "weak" / "medium" / "strong",
 * 계열은 code(예 "citrus"), 어코드는 영문 소문자(예 "citrus", "fougère").
 */
object Tags {
    // 하단 탭 4개
    const val TAB_HOME = "tab:home"
    const val TAB_SEARCH = "tab:search"
    const val TAB_LIBRARY = "tab:library"
    const val TAB_MY = "tab:my"

    // 홈
    const val NEW_BLEND = "home:newBlend"
    const val RESUME_BLEND = "home:resumeBlend"

    // 새 조합 흐름: 계열 선택 → 노트 선택
    fun family(code: String) = "family:$code"
    const val NEXT = "flow:next"
    fun layerTab(layer: String) = "layer:$layer"
    const val NOTE_LIST = "notes:list"
    const val NOTE_SEARCH = "notes:search"
    fun noteItem(noteKey: String) = "note:$noteKey"
    fun noteInfo(noteKey: String) = "noteInfo:$noteKey"
    fun intensity(noteKey: String, level: String) = "intensity:$noteKey:$level"
    /** 고른 노트 칩. 강도가 글자로 함께 보인다 (예 "레몬 · 강") */
    fun selectedNote(layer: String, noteKey: String) = "selected:$layer:$noteKey"
    const val LAYER_LIMIT_MESSAGE = "notes:layerLimit"
    const val TO_SUMMARY = "flow:toSummary"

    // 노트 카드 (아래에서 올라오는 시트)
    const val NOTE_CARD = "noteCard"
    const val NOTE_CARD_FAMILY = "noteCard:family"
    const val NOTE_CARD_DESCRIPTION = "noteCard:description"
    const val NOTE_CARD_PAIRING = "noteCard:pairing"

    // 조합 요약
    const val SUMMARY_SCREEN = "summary"
    const val ACCORD_BAR_PREFIX = "accordBar:"
    fun accordBar(accord: String) = "$ACCORD_BAR_PREFIX$accord"
    const val BALANCE_HINT = "summary:hint"
    const val EDIT_NOTES = "summary:edit"
    const val SHOW_RECS = "summary:recs"
    const val SAVE_BLEND = "summary:save"
    const val BLEND_NAME_FIELD = "save:name"
    const val CONFIRM_SAVE = "save:confirm"

    // 추천 결과 (rank는 1부터)
    const val REC_LIST = "recs:list"
    fun rec(rank: Int) = "rec:$rank"
    fun recName(rank: Int) = "rec:$rank:name"
    fun recBrand(rank: Int) = "rec:$rank:brand"
    fun recScore(rank: Int) = "rec:$rank:score"
    fun recGrade(rank: Int) = "rec:$rank:grade"
    fun recSharedAccords(rank: Int) = "rec:$rank:shared"
    fun recMatchedNotes(rank: Int) = "rec:$rank:matched"

    // 향수 상세
    const val DETAIL_SCREEN = "detail"
    fun pyramid(layer: String) = "pyramid:$layer"
    const val COMPARE_BAR_PREFIX = "compareBar:"
    const val PERFUME_ACCORD_PREFIX = "perfumeAccord:"
    const val MATCHED_NOTES = "detail:matched"
    const val ONLY_IN_PERFUME_NOTES = "detail:onlyInPerfume"
    const val FAVORITE_TOGGLE = "detail:favorite"
    const val WRITE_FEEDBACK = "detail:feedback"

    // 공통
    const val SIMILARITY_DISCLAIMER = "disclaimer"
    const val DATA_SOURCE = "dataSource"
    const val EMPTY_STATE = "emptyState"
    const val ERROR_MESSAGE = "error:message"
    const val ERROR_RETRY = "error:retry"
    const val SLOW_SERVER_HINT = "server:slow"

    // 로그인
    const val LOGIN_SCREEN = "login"
    const val LOGIN_GOOGLE = "login:google"

    // 시향 피드백
    fun satisfaction(score: Int) = "feedback:satisfaction:$score"
    fun similarity(score: Int) = "feedback:similarity:$score"
    const val FEEDBACK_MEMO = "feedback:memo"
    const val FEEDBACK_SUBMIT = "feedback:submit"

    // 보관함
    const val LIB_BLENDS = "library:blends"
    const val LIB_FAVORITES = "library:favorites"
    const val LIB_FEEDBACK = "library:feedback"
    fun libBlend(name: String) = "library:blend:$name"
    fun libFavorite(perfumeKey: String) = "library:favorite:$perfumeKey"
    fun libFeedback(perfumeKey: String) = "library:feedback:$perfumeKey"

    // 향수 찾기
    const val PERFUME_SEARCH = "search:field"
    fun searchResult(perfumeKey: String) = "search:result:$perfumeKey"

    // 마이
    const val MY_LOGIN = "my:login"
    const val DISLIKE_SEARCH = "my:dislikeSearch"
    fun dislikeOption(noteKey: String) = "my:dislikeOption:$noteKey"
    fun dislikedChip(noteKey: String) = "my:disliked:$noteKey"
    fun preferredFamily(code: String) = "my:preferred:$code"
    const val DELETE_ACCOUNT = "my:deleteAccount"
    const val DELETE_DIALOG = "my:deleteDialog"
    const val DELETE_CONFIRM = "my:deleteConfirm"
    const val PRIVACY_LINK = "my:privacy"
    const val DELETION_GUIDE_LINK = "my:deletionGuide"
}
