/**
 * 화면 요소 이름표(testID). 승인된 테스트(docs/tests.md)가 이 이름으로 요소를 찾는다.
 * 웹에서는 data-testid 속성이 된다(react-native-web).
 * 이름을 바꾸면 승인된 테스트가 요소를 못 찾으므로, 바꾸기 전에 사장 승인을 다시 받는다.
 * (예전 Kotlin 앱의 Tags.kt 이름표를 그대로 옮겼다 — git 0a2788a)
 *
 * 값 규칙: 노트는 note key(정리된 영문 이름, 예 "lemon"), 향수는 perfume key(예 "T001"),
 * 층은 "top" / "middle" / "base", 강도는 "weak" / "medium" / "strong",
 * 계열은 code(예 "citrus"), 어코드는 영문 소문자(예 "citrus", "fougère").
 */
export type Layer = 'top' | 'middle' | 'base';
export type IntensityLevel = 'weak' | 'medium' | 'strong';

export const TestIds = {
  // 하단 탭 4개 (PC 크기에서는 왼쪽 메뉴)
  TAB_HOME: 'tab:home',
  TAB_SEARCH: 'tab:search',
  TAB_LIBRARY: 'tab:library',
  TAB_MY: 'tab:my',

  // 홈
  NEW_BLEND: 'home:newBlend',
  RESUME_BLEND: 'home:resumeBlend',

  // 새 조합 흐름: 계열 선택 → 노트 선택
  family: (code: string) => `family:${code}`,
  NEXT: 'flow:next',
  layerTab: (layer: Layer) => `layer:${layer}`,
  NOTE_LIST: 'notes:list',
  NOTE_SEARCH: 'notes:search',
  noteItem: (noteKey: string) => `note:${noteKey}`,
  noteInfo: (noteKey: string) => `noteInfo:${noteKey}`,
  intensity: (noteKey: string, level: IntensityLevel) => `intensity:${noteKey}:${level}`,
  /** 고른 노트 칩. 강도가 글자로 함께 보인다 (예 "레몬 · 강") */
  selectedNote: (layer: Layer, noteKey: string) => `selected:${layer}:${noteKey}`,
  LAYER_LIMIT_MESSAGE: 'notes:layerLimit',
  TO_SUMMARY: 'flow:toSummary',

  // 노트 카드 (폰: 아래에서 올라오는 시트, PC: 옆 패널)
  NOTE_CARD: 'noteCard',
  NOTE_CARD_FAMILY: 'noteCard:family',
  NOTE_CARD_DESCRIPTION: 'noteCard:description',
  NOTE_CARD_PAIRING: 'noteCard:pairing',

  // 조합 요약
  SUMMARY_SCREEN: 'summary',
  ACCORD_BAR_PREFIX: 'accordBar:',
  accordBar: (accord: string) => `accordBar:${accord}`,
  BALANCE_HINT: 'summary:hint',
  EDIT_NOTES: 'summary:edit',
  SHOW_RECS: 'summary:recs',
  SAVE_BLEND: 'summary:save',
  BLEND_NAME_FIELD: 'save:name',
  CONFIRM_SAVE: 'save:confirm',

  // 추천 결과 (rank는 1부터)
  REC_LIST: 'recs:list',
  rec: (rank: number) => `rec:${rank}`,
  recName: (rank: number) => `rec:${rank}:name`,
  recBrand: (rank: number) => `rec:${rank}:brand`,
  recScore: (rank: number) => `rec:${rank}:score`,
  recGrade: (rank: number) => `rec:${rank}:grade`,
  recSharedAccords: (rank: number) => `rec:${rank}:shared`,
  recMatchedNotes: (rank: number) => `rec:${rank}:matched`,

  // 향수 상세
  DETAIL_SCREEN: 'detail',
  pyramid: (layer: Layer) => `pyramid:${layer}`,
  COMPARE_BAR_PREFIX: 'compareBar:',
  PERFUME_ACCORD_PREFIX: 'perfumeAccord:',
  MATCHED_NOTES: 'detail:matched',
  ONLY_IN_PERFUME_NOTES: 'detail:onlyInPerfume',
  FAVORITE_TOGGLE: 'detail:favorite',
  WRITE_FEEDBACK: 'detail:feedback',

  // 공통
  SIMILARITY_DISCLAIMER: 'disclaimer',
  DATA_SOURCE: 'dataSource',
  EMPTY_STATE: 'emptyState',
  ERROR_MESSAGE: 'error:message',
  ERROR_RETRY: 'error:retry',
  SLOW_SERVER_HINT: 'server:slow',

  // 로그인
  LOGIN_SCREEN: 'login',
  LOGIN_GOOGLE: 'login:google',

  // 시향 피드백
  satisfaction: (score: number) => `feedback:satisfaction:${score}`,
  similarity: (score: number) => `feedback:similarity:${score}`,
  FEEDBACK_MEMO: 'feedback:memo',
  FEEDBACK_SUBMIT: 'feedback:submit',

  // 보관함
  LIB_BLENDS: 'library:blends',
  LIB_FAVORITES: 'library:favorites',
  LIB_FEEDBACK: 'library:feedback',
  libBlend: (name: string) => `library:blend:${name}`,
  libFavorite: (perfumeKey: string) => `library:favorite:${perfumeKey}`,
  libFeedback: (perfumeKey: string) => `library:feedback:${perfumeKey}`,

  // 향수 찾기
  PERFUME_SEARCH: 'search:field',
  searchResult: (perfumeKey: string) => `search:result:${perfumeKey}`,

  // 마이
  MY_LOGIN: 'my:login',
  DISLIKE_SEARCH: 'my:dislikeSearch',
  dislikeOption: (noteKey: string) => `my:dislikeOption:${noteKey}`,
  dislikedChip: (noteKey: string) => `my:disliked:${noteKey}`,
  preferredFamily: (code: string) => `my:preferred:${code}`,
  DELETE_ACCOUNT: 'my:deleteAccount',
  DELETE_DIALOG: 'my:deleteDialog',
  DELETE_CONFIRM: 'my:deleteConfirm',
  PRIVACY_LINK: 'my:privacy',
  DELETION_GUIDE_LINK: 'my:deletionGuide',
} as const;
