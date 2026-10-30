# 추천 엔진 합격 기준표

> 카탈로그 `2026.10.30` · 엔진 v2 · 후보 향수 13,786개 · `py pipeline/evaluate.py`로 만든 문서
> 기준은 docs/design.md 14절. 블라인드 비교는 `engine_blind.md`.

> ⏳ **블라인드 비교 답을 기다리는 중.** 지금 엔진의 추천 목록이 보이는 표(2절 표, 4절)는 답을 받은 뒤에 나옵니다.

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

- 조합 어코드: 시트러스 22%, 플로럴 22%, 프레시 13%, 프루티 7%
- Top 5 중 1위 어코드가 시트러스인 향수: 0개 (참고: 레몬을 '강'으로 골랐을 때 시트러스가 앞서는지)

## 3. 블라인드 비교 (기준: 지금 엔진 쪽을 10개 중 6개 이상 고름)

`engine_blind.md`에서 답을 받은 뒤 채웁니다.

## 4. 눈으로 평가 (기준: 평균 3.5점 이상)

블라인드 비교 답을 받은 뒤, 조합마다 지금 엔진의 Top 5를 보여 드리고 1~5점을 받습니다.

## 5. 다양성 (기준: 조합 10개의 Top 10 전체에서 서로 다른 브랜드 40개 이상)

**결과: 82개 ✅**

## 6. 점수 분포 (등급 기준값 정하기용)

- 1위 점수: 평균 0.941 · 최소 0.875 · 최대 0.986
- 10위 점수: 평균 0.878 · 최소 0.796 · 최대 0.948
- 지금 기준: 0.85 이상 매우 비슷 · 0.75 이상 비슷 · 그 아래 참고

## 7. 계산 시간

- Python 참조 엔진 1회 평균 156ms (참고값. 기준 300ms는 폰의 Java 엔진으로 잰다)
