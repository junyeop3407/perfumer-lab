# 등급 기준값 비교 · 클론 표본

> **2026-11-11 공개용 정리:** 향수 이름과 향수별 값(평점·평가 수·어코드·조향사·주소·점수)은 비공개로 옮겼다. 검증 방법·표본 수·결과 숫자는 그대로다. 원본은 비공개 저장소 `pipeline-data-out/private_docs/engine_grades_clones.md`.


> `py pipeline/experiments/grades_clones_check.py` (2026-10-31). 엔진 v4 (어코드 0.35 : 노트 0.55, 강 2.0).

## 등급 분포 (블라인드 비교 조합 10개)

| 기준 (매우 비슷 / 비슷) | Top 5 (50개): 매우 비슷 / 비슷 / 참고 | Top 10 (100개): 매우 비슷 / 비슷 / 참고 | 매우 비슷이 없는 조합 |
|---|---|---|---|
| 0.87 / 0.79 | 43 / 7 / 0 | 74 / 23 / 3 | 0 |
| 0.9 / 0.84 | 37 / 10 / 3 | 66 / 21 / 13 | 2 |

## 클론 표본 (브랜드가 다른 묶음 226개 중 무작위 20개, 고정 난수 20261031)

- 노트 수 = 층별 노트 개수 합. 원본 순서 = 원본 CSV의 노트 문자열이 글자까지 같은지(사람이 따로 적었다면 순서·표기가 조금씩 다르기 쉽다).
- 판단: 서로 다른 향수인데 **평가 수와 어코드까지 똑같으면 수집 오류 의심**(한 향수의 칸이 다른 향수에 복사된 것으로 보임 — 진짜 클론이면 평가 수가 같을 리 없다). 평가 수가 다르고 한쪽이 평가 많은 '원작'이면 **클론·오마주 향수로 보임**, 그 밖은 **확인 필요**.
- 전체(브랜드가 다른 226묶음): 평가 수·어코드까지 모두 같음 199묶음 · 일부만 같음 4묶음 · 다름 23묶음

### 1. 노트 8개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 2. 노트 9개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 3. 노트 8개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 4. 노트 12개 · 원본 순서 다름 · 확인 필요

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 5. 노트 6개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 6. 노트 6개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 7. 노트 9개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 8. 노트 9개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 9. 노트 10개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 10. 노트 16개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 11. 노트 7개 · 원본 순서 같음 · 일부 수집 오류 의심 (평가 수·어코드가 같은 향수가 섞임)

> (향수 3개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 12. 노트 10개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 3개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 13. 노트 14개 · 원본 순서 다름 · 확인 필요

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 14. 노트 10개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 15. 노트 19개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 16. 노트 12개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 17. 노트 9개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 18. 노트 8개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 19. 노트 8개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)

### 20. 노트 8개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

> (향수 2개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_grades_clones.md`, plan_b_portfolio.md 1-4)
