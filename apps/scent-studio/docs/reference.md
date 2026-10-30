# scent-studio 참고 자료

> 조사 날짜: 2026-10-30 · 이 문서는 **배경 자료**다. 기능·기술·계산 규칙은 `requirements.md`와 `design.md`가 기준이며, 이 문서와 다르면 그 두 문서를 따른다.
> 쓰는 곳: 데이터 파이프라인(1·2절), 노트 설명·궁합 힌트·계열 색 정하기(3절), 화면 문구(3절).

## 1. 데이터 소스
추천 엔진의 원재료는 "향수 1개 = 탑/미들/베이스 노트 목록 + 메인 어코드(강도)" 형태의 데이터다. Fragrantica에는 공식 API가 없으므로 공개 데이터셋으로 1차를 만들고, 공개 배포 전에 라이선스를 결정한다.

**1차 권장 조합:** FragDB로 노트 사전과 한글 표시명을 만들고, Parfumo CSV로 향수 데이터를 채운다.

| 소스 | 무엇이 있나 | 우리 용도 | 주의 |
|---|---|---|---|
| [FragDB fragrance-database](https://gh.r-mj.com/FragDB/fragrance-database) | Fragrantica 기반 향수 약 13만 건, 노트 2,550개(번역·노트 그룹·향 설명 포함), 어코드 92개, 23개 언어 라벨. 2026-06 갱신 | 노트 사전, 한글 표시명, 노트 계열 | 무료 샘플 외 전체는 구매. 라이선스 확인 필요 |
| [Parfumo 데이터셋 (TidyTuesday 2024-12-10)](https://rfordatascience.github.io/tidytuesday/data/2024/2024-12-10/readme.html) | Parfumo 커뮤니티 향수 데이터, 정리된 CSV 바로 다운로드 (원본 Kaggle) | 1차 향수 카탈로그 | 노트 표기 정규화 필요 |
| [Decant (Codeberg)](https://codeberg.org/decant/pages) | Fragrantica 전체 카탈로그(평점, 투표, 노트, 어코드, 조향사)를 Kaggle 데이터셋(CSV+JSONL)으로 공개 | 데이터가 부족할 때 보강 | 원본이 수집(스크래핑) 데이터라 상업 이용 전 확인 |
| [rdemarqui/perfume_recommender](https://github.com/rdemarqui/perfume_recommender) | 브랜드 2,570개, 향수 36,969개, 향 2,145종 + 추천 코드 | 추천 방식 참고 | 학습용 예제 |
| [arianabina/Perfume-Recommender-System](https://github.com/arianabina/Perfume-Recommender-System) | 어코드 최대 3개 선택 → 비슷한 향수 5개 추천 | 비슷한 UX 참고 | 학습용 예제 |
| [Fragrance-Recommendation-System (Hugging Face)](https://huggingface.co/spaces/jrkotun/Fragrance-Recommendation-System/blob/main/src/streamlit_app.py) | 노트 텍스트 임베딩 + 벡터 검색, LLM으로 추천 이유 설명 | 3차 "LLM 추천 이유" 참고 | — |
| [seccaz/PerfumAPI](https://github.com/seccaz/PerfumAPI) | 향수 REST API 예시, notes_top/middle/base 배열 스키마 | 테이블 설계 참고만 (우리 서버는 Spring) | 수집 윤리 항목 확인 |
| [The Good Scents Company](https://www.thegoodscentscompany.com/allodor.html) | 향 원료별 냄새 설명 사전 | 노트 설명 문구 보조 | 완성 향수가 아니라 원료 DB |

- Fragrantica를 직접 수집하는 도구(Apify 등)도 있지만 공식 API가 없고 약관 문제가 있어 쓰지 않는다. ([참고](https://decodo.com/scraping/web/fragrantica-scraper-api))
- 어코드 강도 값은 대부분 커뮤니티 투표로 만든 값이지 실제 처방 비율이 아니다. 화면에서는 "메인 테마 추정치"로 표기한다.
- 연구 참고: 향수 1만여 종 노트 네트워크 분석에서 머스크·바닐라처럼 널리 쓰이는 노트가 어코드를 가장 많이 강화했다. 가장 흔한 어코드와 평점을 올리는 어코드는 서로 달랐다. ([arXiv 1909.05726](https://ar5iv.arxiv.org/html/1909.05726))

## 2. 노트 정규화에서 자주 나오는 경우
- 같은 노트의 다른 이름: Bergamot / Calabrian Bergamot, Vanilla / Tahitian Vanilla / Mexican Vanilla
- 1차 방침: 산지·품종 접두어는 대표 노트로 합친다 (`note_aliases`). 단, 냄새가 확실히 다른 것은 합치지 않는다 (예: Bitter Orange ≠ Orange는 사람이 판단해 `unmapped_notes.csv`에서 결정)
- 계열이 애매한 노트는 FragDB 노트 그룹을 따른다

## 3. 조향 기초
### 노트 피라미드
| 층 | 지속 시간(대략) | 역할 | 대표 노트 |
|---|---|---|---|
| 탑 | 15~30분 | 첫인상, 가볍고 상쾌함 | 레몬, 베르가못, 오렌지, 자몽, 라벤더, 민트 |
| 미들 | 1~4시간 | 향의 성격·중심, 탑과 베이스를 잇는 다리 | 장미, 자스민, 일랑일랑, 제라늄, 네롤리, 시나몬 |
| 베이스 | 수 시간 이상 | 깊이·지속력, 잔향, 고정 역할 | 샌달우드, 시더우드, 바닐라, 앰버, 머스크, 패출리, 오크모스 |

지속 시간은 제품·피부·환경에 따라 다르므로 화면에서는 "대략"으로만 안내한다.

### 비율
- 가장 흔한 입문 비율: 탑 30 / 미들 50 / 베이스 20. 베이스가 무거운 향은 탑 20 / 미들 30 / 베이스 50처럼 가기도 한다
- 실제 조향사들은 "비율보다 코로 판단"을 강조한다. 원료마다 세기가 달라서다
- 앱에서는 비율을 규칙이 아니라 **균형 안내**로만 쓴다 (예: 베이스가 비면 "잔향이 가볍게 느껴질 수 있는 조합")

### 향 계열 (Michael Edwards 프래그런스 휠)
업계 표준 분류. 4대 계열 Fresh · Floral · Amber(구 Oriental) · Woody 아래 14개 하위 계열. 휠에서 이웃한 계열끼리 잘 어울린다.

| 계열 | 하위 계열 예 | 잘 어울리는 이웃 |
|---|---|---|
| Fresh | 시트러스, 워터(아쿠아틱), 그린, 프루티, 아로마틱(푸제르) | Floral, Woody |
| Floral | 플로럴, 소프트 플로럴, 플로럴 앰버 | Fresh, Amber |
| Amber | 소프트 앰버, 앰버, 우디 앰버 | Floral, Woody |
| Woody | 우즈, 모스 우즈(시프레), 드라이 우즈(레더) | Amber, Fresh |

`families` 테이블의 `wheel_order`는 이 순서를 따르고, 궁합 힌트와 균형 경고에서 "이웃 계열"을 판단할 때 쓴다.

### 잘 어울리는 조합 원칙 (노트 카드·균형 안내 문구 재료)
1. 베이스부터 정한다: 우디·앰버 계열로 뼈대를 잡는다
2. 이웃 계열에서 탑·미들을 고른다: 우디 베이스 + 시트러스 탑은 밝고 깔끔하다
3. 플로럴이나 바닐라 계열 미들은 날카로운 대비를 부드럽게 잇는다
4. 머스크·바닐라처럼 많이 쓰이는 노트는 대부분의 조합을 받쳐 준다
5. 어코드 = 여러 노트가 섞여 새 인상을 만드는 단위. 조합 결과는 노트 목록보다 어코드 비율로 보여 주는 게 이해하기 쉽다

궁합 예시 문구 (확정적인 정답이 아니라 제안으로 표현):
| 조합 | 예상 인상 |
|---|---|
| 베르가못 + 로즈 | 상큼함과 화사함이 함께 |
| 로즈 + 샌달우드 | 부드럽고 차분함 |
| 베르가못 + 샌달우드 | 산뜻함과 따뜻하고 묵직한 느낌의 대비 |

## 4. 더 볼 사이트
| 사이트 | 내용 |
|---|---|
| [Scento — 노트 페어링](https://www.scento.com/blog/perfume-note-pairings-how-build-signature-scent-from-scratch) | 30/50/20 규칙, 층별 지속 시간, 휠로 조합 찾기 |
| [Makesy — 향수 만들기](https://makesy.com/blogs/news/the-art-of-making-perfume) | 블렌딩 단계, 방울 수 비율 예시 |
| [Pepper Jane's — Formulator's Toolbox](https://pepperjanes.com/blogs/tips-and-recipes/formulator-s-toolbox-the-basics-of-blending-fragrances) | 시향지로 비율 맞춰 보기 |
| [PerfumersWorld — 노트 비율](https://www.perfumersworld.com/fragrance-notes.php) | 고정 비율의 한계, 조향사 관점 |
| [Basenotes 포럼](https://basenotes.com/goto/post?id=5757481) | 입문자 Q&A, 혼합 순서·희석 |
| [The Perfume Shop — Michael Edwards](https://www.theperfumeshop.com/michael-edwards) | 계열별 설명 |
| [Fragplace — 향 계열](https://fragplace.com/it/blog/understand-fragrance-families-to-enhance-your-collection.md) | 4대 계열·14개 하위 계열 |
| [Makeup.com — 향수 용어](https://www.makeup.com/perfume-terminology) | 어코드, 시프레, 드라이다운 용어집 |
| [Vetiver Aromatics — 직접 섞기](https://vetiveraromatics.com/pages/mixing-your-own-perfume) | 베이스 비중을 높인 비율 예시 |
