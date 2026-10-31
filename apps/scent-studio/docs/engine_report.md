# 추천 엔진 합격 기준표

> 카탈로그 `2026.10.31` · 엔진 v3 · 후보 향수 13,786개 · `py pipeline/evaluate.py`로 만든 문서
> 기준은 docs/design.md 14절. 블라인드 비교는 `engine_blind.md`.

## 1. 재현 (기준: 20개 중 15개 이상이 Top 3, 2026-10-30 사장 결정으로 16→15)

**결과: 15 / 20 ✅**

<details><summary>향수별 순위</summary>

| 향수 | 순위 |
|---|---|
| Joop! — Joop! Homme Joop! 1989 Eau de Toilette | 10위 밖 |
| Paco Rabanne — Million Paco Rabanne 2008 Eau de Toilette | 1 |
| Calvin Klein — CK One Calvin Klein 1994 Eau de Toilette | 1 |
| Montblanc — Individuel Montblanc 2003 Eau de Toilette | 3 |
| Lancôme — La Vie est Belle L'Eau de Parfum | 2 |
| Nikos — Sculpture Homme Nikos 1995 Eau de Toilette | 10위 밖 |
| Boadicea the Victorious — Passionate | 10위 밖 |
| ann fragrance — The Royal | 10위 밖 |
| Issey Miyake — L'Eau d'Issey pour Homme Issey Miyake 1994 Eau de Toilette | 1 |
| Giorgio Armani — Emporio Armani - Stronger With You | 1 |
| Gisada — Ambassador for Men | 1 |
| Mugler — Angel Mugler 1992 Eau de Parfum | 1 |
| Versace — Blue Jeans | 10 |
| Nautica — Voyage Nautica 2006 Eau de Toilette | 1 |
| Fragrance One — Office for Men | 1 |
| Jil Sander — Sun Jil Sander 1989 Eau de Toilette | 1 |
| Dolce & Gabbana — The One for Men Dolce & Gabbana 2008 Eau de Toilette | 1 |
| Parfums de Marly — Kalan | 1 |
| Dior — Dior Homme Dior 2020 Eau de Toilette | 1 |
| Amouage — Sunshine Man | 1 |

</details>

## 2. 맹점 조합 (기준: Top 5 중 4개 이상이 시트러스와 플로럴을 둘 다 가짐)

**결과: 5 / 5 ✅**

- 판정 방법: 향수의 어코드 목록 **전체(최대 5개)**를 계열로 바꿔, 시트러스 계열과 플로럴 계열이 둘 다 있으면 통과. design.md 14절은 "둘 다 가짐"이라고만 적었고, 0절 보정 설명은 "추천 상위 5개 모두에 시트러스가 들어간다"(순위 무관)라서 전체 목록 기준이 의도에 맞다
- 참고: 상위 3개 어코드만 보면 3 / 5
- 조합 어코드: 시트러스 34%, 플로럴 19%, 프레시 13%, 그린 7%
- Top 5 중 1위 어코드가 시트러스인 향수: 2개 (참고: 레몬을 '강'으로 골랐을 때 시트러스가 앞서는지)

| 순위 | 향수 | 상위 어코드 | 시트러스 계열 비중 | 플로럴 계열 비중 | 점수 |
|---|---|---|---|---|---|
| 1 | Clive Christian — for Women | 플로럴 · 프레시 · 그린 | 13% (4번째) | 33% (1번째) | 0.890 |
| 2 | Annette Neuffer — Elixir Solaire | 시트러스 · 플로럴 · 프루티 | 33% (1번째) | 27% (2번째) | 0.866 |
| 3 | Ormonde Jayne — Gatsby 22 | 프레시 · 시트러스 · 플로럴 | 27% (2번째) | 20% (3번째) | 0.864 |
| 4 | Maître Parfumeur et Gantier — Jardin du Néroli | 시트러스 · 플로럴 · 그린 | 33% (1번째) | 27% (2번째) | 0.863 |
| 5 | Balenciaga — Talisman Balenciaga 1996 Eau Transparente | 플로럴 · 프루티 · 그린 | 13% (4번째) | 33% (1번째) | 0.846 |

비중 = 향수 어코드 벡터(목록 순서대로 5·4·3·2·1, 합 100)에서 그 계열 어코드가 차지하는 몫.

## 3. 블라인드 비교 (기준: 지금 엔진 쪽을 10개 중 6개 이상 고름)

> ⚠️ **임시 답안 (source: claude-provisional)**: 사람이 아니라 Claude가 향을 맡지 않고 노트 피라미드 지식과 어코드 라벨로 고른 답이다. 어코드 엔진 쪽에 유리하게 치우쳤을 수 있다. **사장 답안이 오면 그걸 기준으로 한다.**

**결과: 9.5 / 10 ✅** (조합마다 남은 줄에서 엔진 쪽을 더 많이 고르면 1, 같으면 0.5)

- 줄 단위: 엔진 쪽 36 / 44줄 (82%)
- 뺀 줄 — 양쪽이 같은 향수: 6-4, 6-5, 8-1, 8-5 · 자리만 바뀐 같은 쌍(뒤 줄은 세지 않음): 6-2/6-3, 7-1/7-2
- 자리만 바뀐 쌍의 판단 일관성: 모두 같은 향수를 골랐음
- 채점 기준: `engine_blind_v3_pairs.json`

| 조합 | 지금 엔진 쪽 | 엔진 : 다른 쪽 | 판정 |
|---|---|---|---|
| 1. 핑크 페퍼 · 장미 · 패출리 | B | 4 : 1 | 승 |
| 2. 베르가못 · 장미 · 머스크 | B | 5 : 0 | 승 |
| 3. 자몽 · 라벤더 · 베티버 | B | 4 : 1 | 승 |
| 4. 만다린 · 자스민 · 바닐라 | A | 4 : 1 | 승 |
| 5. 핑크 페퍼 · 아이리스 · 시더우드 | B | 4 : 1 | 승 |
| 6. 레몬 · 녹차 · 화이트 머스크 | B | 1 : 1 | 동점 |
| 7. 카다멈 · 타바코 · 우드 · 앰버 | A | 3 : 1 | 승 |
| 8. 복숭아 · 작약 · 머스크 | A | 3 : 0 | 승 |
| 9. 마린 노트 · 세이지 · 시더우드 약 | A | 3 : 2 | 승 |
| 10. 시나몬 · 통카 빈 · 바닐라 · 샌달우드 | A | 5 : 0 | 승 |

## 4. 눈으로 평가 (기준: 평균 3.5점 이상)

> ⚠️ **임시 점수 (source: claude-provisional)**: 사람이 아니라 Claude가 향을 맡지 않고 노트 지식과 어코드 라벨로 매긴 점수다. **사장 점수가 오면 그걸로 덮어쓴다.**

**결과: 평균 3.7점 ✅**

**1. 핑크 페퍼 · 장미 · 패출리** — 조합 어코드: 플로럴 29%, 스파이시 25%, 우디 10%, 오리엔탈 8% — 점수: 4점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Sospiro — Duetto | 플로럴 · 스파이시 · 우디 | 0.978 | VERY_SIMILAR |
| 2 | Agent Provocateur — Miss AP | 플로럴 · 스파이시 · 오리엔탈 | 0.965 | VERY_SIMILAR |
| 3 | Nishane — Favonius | 플로럴 · 스파이시 · 오리엔탈 | 0.958 | VERY_SIMILAR |
| 4 | Agent Provocateur — L'Agent Agent Provocateur 2011 Eau de Parfum | 스파이시 · 플로럴 · 오리엔탈 | 0.952 | VERY_SIMILAR |
| 5 | Bella Bellissima — Emerald Dorée | 스파이시 · 플로럴 · 오리엔탈 | 0.952 | VERY_SIMILAR |

**2. 베르가못 · 장미 · 머스크** — 조합 어코드: 플로럴 36%, 시트러스 12%, 시프레 9%, 프레시 9% — 점수: 4점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Molton Brown — Orange & Bergamot Molton Brown 2019 Eau de Parfum | 플로럴 · 시트러스 · 파우더리 | 0.938 | VERY_SIMILAR |
| 2 | Floris — Royal Arms Diamond Edition | 플로럴 · 프레시 · 파우더리 | 0.937 | VERY_SIMILAR |
| 3 | Versace — Yellow Jeans | 플로럴 · 시트러스 · 프레시 | 0.928 | VERY_SIMILAR |
| 4 | Penhaligon's — Luna Penhaligon's 2016 Eau de Toilette | 플로럴 · 프레시 · 시트러스 | 0.927 | VERY_SIMILAR |
| 5 | Floris — Special No. 127 Original Gentlemen's Cologne | 플로럴 · 프레시 · 시트러스 | 0.927 | VERY_SIMILAR |

**3. 자몽 · 라벤더 · 베티버** — 조합 어코드: 시트러스 19%, 프레시 19%, 스파이시 18%, 우디 13% — 점수: 3점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Eisenberg — Eau Fraîche | 프레시 · 스파이시 · 시트러스 | 0.972 | VERY_SIMILAR |
| 2 | Angel Schlesser — Sélection de Mémoires - Esprit de Gingembre pour Homme | 스파이시 · 시트러스 · 프레시 | 0.966 | VERY_SIMILAR |
| 3 | Dior — Dior Homme Sport Dior 2008 Eau de Toilette | 시트러스 · 프레시 · 스파이시 | 0.962 | VERY_SIMILAR |
| 4 | Azzaro — Azzaro pour Homme L'Eau | 시트러스 · 프레시 · 스파이시 | 0.962 | VERY_SIMILAR |
| 5 | Acqua di Parma — Colonia Futura | 시트러스 · 프레시 · 그린 | 0.954 | VERY_SIMILAR |

**4. 만다린 · 자스민 · 바닐라** — 조합 어코드: 플로럴 30%, 스위트 14%, 시트러스 10%, 프루티 10% — 점수: 4점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Estée Lauder — Bronze Goddess Capri | 플로럴 · 스위트 · 프루티 | 0.959 | VERY_SIMILAR |
| 2 | Dior — J'adore Dior 2002 Eau de Toilette | 플로럴 · 스위트 · 시트러스 | 0.954 | VERY_SIMILAR |
| 3 | Tesori d'Oriente — Tiaré delle Indie | 플로럴 · 스위트 · 프루티 | 0.953 | VERY_SIMILAR |
| 4 | DKNY / Donna Karan — DKNY Women Gold | 플로럴 · 프루티 · 스위트 | 0.952 | VERY_SIMILAR |
| 5 | Juicy Couture — Viva La Juicy La Fleur Juicy Couture 2012 Eau de Toilette | 플로럴 · 스위트 · 프루티 | 0.949 | VERY_SIMILAR |

**5. 핑크 페퍼 · 아이리스 · 시더우드** — 조합 어코드: 스파이시 21%, 플로럴 19%, 우디 17%, 파우더리 14% — 점수: 4점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Ormonde Jayne — Orris Noir Ormonde Jayne 2006 Eau de Parfum | 스파이시 · 플로럴 · 우디 | 0.980 | VERY_SIMILAR |
| 2 | La Perla — My Day | 플로럴 · 우디 · 파우더리 | 0.952 | VERY_SIMILAR |
| 3 | Ormonde Jayne — Vanille d'Iris Ormonde Jayne 2015 Eau de Parfum | 파우더리 · 플로럴 · 스파이시 | 0.945 | VERY_SIMILAR |
| 4 | Clive Christian — X for Men | 스파이시 · 우디 · 플로럴 | 0.927 | VERY_SIMILAR |
| 5 | Giorgio Armani — Armani Mania pour Femme | 플로럴 · 파우더리 · 우디 | 0.909 | VERY_SIMILAR |

**6. 레몬 · 녹차 · 화이트 머스크** — 조합 어코드: 시트러스 27%, 프레시 24%, 그린 16%, 플로럴 11% — 점수: 3점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Carthusia — Mediterraneo Carthusia Profumo | 시트러스 · 프레시 · 그린 | 0.988 | VERY_SIMILAR |
| 2 | Ffern — Autumn 21 | 시트러스 · 프레시 · 그린 | 0.988 | VERY_SIMILAR |
| 3 | Les Parfums de Rosine — Un Zest de Rose | 플로럴 · 시트러스 · 프레시 | 0.933 | VERY_SIMILAR |
| 4 | Bath & Body Works — Sonoma Weekend Escape | 프레시 · 플로럴 · 시트러스 | 0.925 | VERY_SIMILAR |
| 5 | Lanvin — Éclat d'Arpège Gourmandise | 프레시 · 플로럴 · 시트러스 | 0.925 | VERY_SIMILAR |

**7. 카다멈 · 타바코 · 우드 · 앰버** — 조합 어코드: 스파이시 27%, 우디 23%, 오리엔탈 20%, 레진 8% — 점수: 3점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Nabeel — The Spirit of Dubai - Baz | 스파이시 · 오리엔탈 · 레더 | 0.916 | VERY_SIMILAR |
| 2 | Nabeel — Dubai - Meydan Nabeel 2015 Eau de Parfum | 레더 · 스파이시 · 우디 | 0.900 | VERY_SIMILAR |
| 3 | Chopard — Oud Malaki | 우디 · 스파이시 · 오리엔탈 | 0.890 | VERY_SIMILAR |
| 4 | Anfas — Shaghaf | 스파이시 · 애니멀릭 · 오리엔탈 | 0.870 | VERY_SIMILAR |
| 5 | Al Haramain / الحرمين — Urbanist Prive Gold | 레진 · 스파이시 · 우디 | 0.847 | SIMILAR |

**8. 복숭아 · 작약 · 머스크** — 조합 어코드: 플로럴 44%, 프루티 27%, 프레시 10%, 파우더리 6% — 점수: 4점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Ex Nihilo — Fleur Narcotique Ex Nihilo 2013 Eau de Parfum | 플로럴 · 프루티 · 프레시 | 0.968 | VERY_SIMILAR |
| 2 | V Canto — Ricina | 플로럴 · 프루티 · 프레시 | 0.968 | VERY_SIMILAR |
| 3 | Grès — Sphinx - Hommage à Greta Garbo | 플로럴 · 프루티 · 프레시 | 0.960 | VERY_SIMILAR |
| 4 | Salvatore Ferragamo — Incanto | 플로럴 · 프루티 · 스위트 | 0.960 | VERY_SIMILAR |
| 5 | Roja Parfums — Elysium pour Femme | 플로럴 · 프루티 · 스위트 | 0.959 | VERY_SIMILAR |

**9. 마린 노트 · 세이지 · 시더우드 약** — 조합 어코드: 아쿠아틱 35%, 프레시 23%, 시트러스 10%, 스파이시 9% — 점수: 4점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Davidoff — Cool Water Into the Ocean | 아쿠아틱 · 프레시 · 시트러스 | 0.890 | VERY_SIMILAR |
| 2 | Fendi — Fan di Fendi pour Homme Acqua | 아쿠아틱 · 프레시 · 시트러스 | 0.865 | SIMILAR |
| 3 | Giorgio Armani — Acqua di Giò Profondo Lights | 아쿠아틱 · 프레시 · 시트러스 | 0.831 | SIMILAR |
| 4 | Kenzo — Kenzo Homme Kenzo 2016 Eau de Parfum | 프레시 · 아쿠아틱 · 신세틱 | 0.830 | SIMILAR |
| 5 | Giorgio Armani — Acqua di Giò Profondo Giorgio Armani 2024 Eau de Parfum | 아쿠아틱 · 프레시 · 시트러스 | 0.829 | SIMILAR |

**10. 시나몬 · 통카 빈 · 바닐라 · 샌달우드** — 조합 어코드: 스위트 23%, 스파이시 18%, 오리엔탈 13%, 우디 10% — 점수: 4점

| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |
|---|---|---|---|---|
| 1 | Nobile 1942 — Il Capriccio del Maestro | 스위트 · 스파이시 · 구르망 | 0.899 | VERY_SIMILAR |
| 2 | Joop! — Joop! Homme Joop! 2021 Eau de Parfum | 스위트 · 스파이시 · 오리엔탈 | 0.884 | VERY_SIMILAR |
| 3 | Brecourt — Mauvais Garçon | 스위트 · 스파이시 · 우디 | 0.883 | VERY_SIMILAR |
| 4 | Kayali — Invite Only Amber \| 23 | 스위트 · 스파이시 · 오리엔탈 | 0.875 | VERY_SIMILAR |
| 5 | Otto Kern — Beyond Horizon Otto Kern 2022 Eau de Toilette | 스파이시 · 스위트 · 오리엔탈 | 0.865 | SIMILAR |

## 5. 다양성 (기준: 조합 10개의 Top 10 전체에서 서로 다른 브랜드 40개 이상)

**결과: 81개 ✅**

## 6. 점수 분포 (등급 기준값 정하기용)

- 1위 점수: 평균 0.949 · 최소 0.890 · 최대 0.988
- 10위 점수: 평균 0.884 · 최소 0.780 · 최대 0.944
- 지금 기준(engine_ref.py): 0.87 이상 매우 비슷 · 0.79 이상 비슷 · 그 아래 참고

## 7. 계산 시간

- Python 참조 엔진 1회 평균 146ms (참고값. 기준 300ms는 앱의 TypeScript 엔진으로 잰다)
