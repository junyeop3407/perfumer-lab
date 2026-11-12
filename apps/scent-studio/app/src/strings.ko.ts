/** 화면 글자 (한국어). 승인 테스트가 읽는 문구는 docs/tests.md와 같아야 한다. 글자는 글꼴 서브셋 안에서만(tests/unit/fonts.test.ts). */
export const S = {
  appName: 'Perfumer-LAB',
  tabHome: '홈',
  tabSearch: '향수 찾기',
  tabLibrary: '보관함',
  tabMy: '마이',
  newBlend: '새 조합 만들기',
  resumeBlend: '만들던 조합 이어 하기',
  back: '뒤로',
  sampleBanner: '가상 데이터 · 실제 향수 정보가 아니에요',

  // 홈
  homeTitle: '새 조합을\n만들어 볼까요',
  homeLead: '탑 · 미들 · 베이스에서 노트를 골라 조합하면, 어코드 비중이 가장 비슷한 향수를 보여 드려요.',
  homeLayersTitle: '탑 · 미들 · 베이스가 뭔가요?',
  homeLayersBody: '뿌린 직후 먼저 나는 향이 탑, 한두 시간 이어지는 중심이 미들, 마지막까지 남는 잔향이 베이스예요.',
  catalogLoading: '향수 데이터를 준비하는 중이에요',
  catalogError: '향수 데이터를 읽지 못했어요. 다시 시도해 주세요. 계속되면 앱을 다시 설치해 주세요.',
  retry: '다시 시도',

  // 계열 고르기
  familiesTitle: '계열 고르기',
  familiesLead: '끌리는 계열을 골라 주세요. 여러 개 골라도 되고, 고른 계열의 노트를 먼저 보여 드려요.',
  next: '다음',

  // 노트 고르기
  notesTitle: '노트 고르기',
  familiesPicked: '계열: ',
  familiesNone: '계열: 고르지 않음',
  noteSearchPlaceholder: '노트 이름으로 찾기 (예: 베르가, berg)',
  noteSearchLabel: '노트 검색',
  pickedTitle: '고른 노트 · 층마다 3개까지',
  pickedEmpty: '아직 고른 노트가 없어요',
  layerLimit: '층마다 3개까지 고를 수 있어요',
  inOtherLayer: '이미 다른 층에 고른 노트예요: ',
  oftenUsed: ' · 많이 쓰는 노트',
  searchResults: '검색 결과',
  searchEmpty: '맞는 노트가 없어요. 다른 이름으로 찾아보세요.',
  removeNote: '조합에서 빼기',
  toSummary: '조합 요약 보기',
  noteInfo: ' 설명 보기',

  // 노트 카드
  noteCardFamily: '계열 · ',
  noteCardPairings: '잘 어울리는 노트',
  pairingFilled: '같은 계열 추천',
  noteCardAdd: '이 층에 넣기',
  noteCardClose: '닫기',

  // 요약
  summaryTitle: '조합 요약',
  myBlend: '내 조합',
  noteCount: '노트 ',
  noteCountUnit: '개',
  layerEmpty: '비어 있음',
  accordsTitle: '이 조합의 어코드',
  accordsDisclaimer: '메인 테마 추정치예요. 실제로 섞은 향을 예측한 값은 아니에요.',
  editNotes: '노트 수정',
  summaryEmpty: '고른 노트가 없어요.',
} as const;
