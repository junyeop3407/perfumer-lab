# 추천 엔진 합격 기준표

> 카탈로그 `2026.10.31` · 엔진 v6 · 후보 향수 13,461개 · `py pipeline/evaluate.py`로 만든 문서
> 기준은 docs/design.md 14절. 블라인드 비교는 `engine_blind.md`.

> ⏳ **블라인드 비교 답을 기다리는 중.** 지금 엔진의 추천 목록이 보이는 표(2절 표, 4절)는 답을 받은 뒤에 나옵니다.

## 1. 재현 (기준: 20개 중 15개 이상이 Top 3, 2026-10-30 사장 결정으로 16→15)

**결과: 20 / 20 ✅** · 고유 노트 기준(클론은 1개로): 20 / 20

- 넣는 노트는 화면 규칙대로 층마다 앞 3개까지. 고유 노트 기준은 노트 데이터가 완전히 같은 향수(클론)를 하나로 세고, 그중 하나라도 맞히면 맞힌 것으로 본다
- 이 20개 안의 클론: 없음

어려운 버전(참고, 2026-10-31 — 기본이 거의 만점이라 변별력을 보려고):

| 입력 | 20개 | 100개 |
|---|---|---|
| 기본: 층마다 3개 | 20/20 | 99/100 |
| (a) 층마다 2개만 | 20/20 | 98/100 |
| (b) 노트 하나를 무작위로 뺌 (고정 난수 20261031) | 20/20 | 97/100 |

<details><summary>향수별 순위</summary>

| 향수 | 순위 |
|---|---|
| Joop! — Joop! Homme Joop! 1989 Eau de Toilette | 1 |
| Paco Rabanne — Million Paco Rabanne 2008 Eau de Toilette | 1 |
| Calvin Klein — CK One Calvin Klein 1994 Eau de Toilette | 1 |
| Montblanc — Individuel Montblanc 2003 Eau de Toilette | 1 |
| Lancôme — La Vie est Belle L'Eau de Parfum | 2 |
| Nikos — Sculpture Homme Nikos 1995 Eau de Toilette | 1 |
| Issey Miyake — L'Eau d'Issey pour Homme Issey Miyake 1994 Eau de Toilette | 1 |
| Giorgio Armani — Emporio Armani - Stronger With You | 1 |
| Gisada — Ambassador for Men | 1 |
| Mugler — Angel Mugler 1992 Eau de Parfum | 1 |
| Versace — Blue Jeans | 3 |
| Nautica — Voyage Nautica 2006 Eau de Toilette | 1 |
| Fragrance One — Office for Men | 1 |
| Jil Sander — Sun Jil Sander 1989 Eau de Toilette | 1 |
| Dolce & Gabbana — The One for Men Dolce & Gabbana 2008 Eau de Toilette | 1 |
| Parfums de Marly — Kalan | 1 |
| Dior — Dior Homme Dior 2020 Eau de Toilette | 1 |
| Amouage — Sunshine Man | 1 |
| Davidoff — Cool Water Intense | 1 |
| Bvlgari — Aqva Amara Bvlgari 2014 Eau de Toilette | 1 |

</details>

## 2. 맹점 조합 (기준: Top 5 중 4개 이상이 시트러스와 플로럴을 둘 다 가짐)

**결과: 4 / 5 ✅**

- 판정 방법: 향수의 어코드 목록 **전체(최대 5개)**를 계열로 바꿔, 시트러스 계열과 플로럴 계열이 둘 다 있으면 통과. design.md 14절은 "둘 다 가짐"이라고만 적었고, 0절 보정 설명은 "추천 상위 5개 모두에 시트러스가 들어간다"(순위 무관)라서 전체 목록 기준이 의도에 맞다
- 참고: 상위 3개 어코드만 보면 1 / 5
- 조합 어코드: 시트러스 39%, 프레시 19%, 플로럴 15%, 프루티 7%
- Top 5 중 1위 어코드가 시트러스인 향수: 2개 (참고: 레몬을 '강'으로 골랐을 때 시트러스가 앞서는지)

## 3. 블라인드 비교 (기준: 지금 엔진 쪽을 12개 중 8개 이상 고름)

`engine_blind.md`에서 답을 받은 뒤 채웁니다.

## 4. 눈으로 평가 (기준: 평균 3.5점 이상)

블라인드 비교 답을 받은 뒤, 조합마다 지금 엔진의 Top 5를 보여 드리고 1~5점을 받습니다.

## 5. 다양성 (기준: 조합 12개의 Top 10 전체에서 서로 다른 브랜드 40개 이상)

**결과: 97개 ✅**

## 6. 점수 분포 (등급 기준값 정하기용)

- 1위 점수: 평균 0.956 · 최소 0.884 · 최대 0.990
- 10위 점수: 평균 0.902 · 최소 0.771 · 최대 0.978
- 지금 기준(engine_ref.py): 0.87 이상 매우 비슷 · 0.79 이상 비슷 · 그 아래 참고

## 7. 계산 시간

- Python 참조 엔진 1회 평균 142ms (참고값. 기준 300ms는 앱의 TypeScript 엔진으로 잰다)
