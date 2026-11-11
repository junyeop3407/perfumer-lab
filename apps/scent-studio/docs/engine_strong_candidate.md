# '강' 값 시험

> **2026-11-11 공개용 정리:** 향수 이름과 향수별 값(평점·평가 수·어코드·조향사·주소·점수)은 비공개로 옮겼다. 검증 방법·표본 수·결과 숫자는 그대로다. 원본은 비공개 저장소 `pipeline-data-out/private_docs/engine_strong_candidate.md`.


> `py pipeline/experiments/strong_value_test.py` (2026-10-31). 어코드 0.35 : 노트 0.55, 후보 프로필(함께 든 노트 몫 빼기, `note_profile_check.py`). 강 1.0 = 모두 '중'(기준선).
> '강 어코드 1위' = Top 5 중 1위 어코드가 '강' 노트의 대표 어코드인 향수 수. '중 어코드 남음' = Top 5 중 '중' 노트의 대표 어코드를 (목록 어디에든) 가진 향수 수. 0/5에 가까우면 '강' 노트가 조합을 덮은 것.
> 머스크는 이 데이터에서 대표 어코드가 플로럴로 나온다(머스크가 든 향수에 플로럴이 많아서).

## 요약

| 조합 | 성격 | 강 노트 → 대표 어코드 | 강 1.0 | 강 2.0 | 강 2.5 | 강 3.0 |
|---|---|---|---|---|---|---|
| 바닐라 강 · 베르가못 · 자스민 | 센 노트 | 바닐라 → 스위트 | 강 1위 0/5 · 중 남음 프레시 5/5 · 플로럴 5/5 | 강 1위 5/5 · 중 남음 프레시 1/5 · 플로럴 5/5 | 강 1위 5/5 · 중 남음 프레시 1/5 · 플로럴 5/5 | 강 1위 5/5 · 중 남음 프레시 1/5 · 플로럴 5/5 |
| 패출리 강 · 만다린 · 장미 | 센 노트 | 패출리 → 스파이시 | 강 1위 0/5 · 중 남음 프루티 5/5 · 플로럴 5/5 | 강 1위 0/5 · 중 남음 프루티 5/5 · 플로럴 5/5 | 강 1위 1/5 · 중 남음 프루티 2/5 · 플로럴 5/5 | 강 1위 2/5 · 중 남음 프루티 1/5 · 플로럴 5/5 |
| 우드(오드) 강 · 베르가못 · 장미 | 센 노트 | 우드 → 우디 | 강 1위 0/5 · 중 남음 프레시 0/5 · 플로럴 5/5 | 강 1위 3/5 · 중 남음 프레시 0/5 · 플로럴 5/5 | 강 1위 4/5 · 중 남음 프레시 0/5 · 플로럴 5/5 | 강 1위 4/5 · 중 남음 프레시 0/5 · 플로럴 5/5 |
| 레몬 강 · 오스만투스 · 샌달우드 약 (맹점, 기존) | 약한 노트 | 레몬 → 시트러스 | 강 1위 0/5 · 중 남음 플로럴 5/5 | 강 1위 0/5 · 중 남음 플로럴 5/5 | 강 1위 1/5 · 중 남음 플로럴 5/5 | 강 1위 2/5 · 중 남음 플로럴 4/5 |
| 녹차 강 · 베르가못 · 샌달우드 | 약한 노트 | 녹차 → 프레시 | 강 1위 4/5 · 중 남음 프레시 5/5 · 우디 4/5 | 강 1위 3/5 · 중 남음 프레시 5/5 · 우디 4/5 | 강 1위 3/5 · 중 남음 프레시 5/5 · 우디 4/5 | 강 1위 3/5 · 중 남음 프레시 5/5 · 우디 4/5 |
| 머스크 강 · 만다린 · 아이리스 | 약한 노트 | 머스크 → 프레시 | 강 1위 0/5 · 중 남음 프루티 1/5 · 파우더리 5/5 | 강 1위 2/5 · 중 남음 프루티 1/5 · 파우더리 5/5 | 강 1위 2/5 · 중 남음 프루티 1/5 · 파우더리 5/5 | 강 1위 2/5 · 중 남음 프루티 1/5 · 파우더리 5/5 |

## 조합별 Top 5

### 바닐라 강 · 베르가못 · 자스민

- 강: 바닐라 → 스위트 · 중: 베르가못 → 프레시, 재스민 → 플로럴

**강 1.0** — 조합 비율: 스위트 23%, 프레시 10%, 플로럴 26% — 강 어코드 1위 0/5 · 중 어코드 남음 프레시 5/5 · 플로럴 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.0** — 조합 비율: 스위트 32%, 프레시 7%, 플로럴 19% — 강 어코드 1위 5/5 · 중 어코드 남음 프레시 1/5 · 플로럴 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.5** — 조합 비율: 스위트 35%, 프레시 6%, 플로럴 17% — 강 어코드 1위 5/5 · 중 어코드 남음 프레시 1/5 · 플로럴 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 3.0** — 조합 비율: 스위트 35%, 프레시 6%, 플로럴 16% — 강 어코드 1위 5/5 · 중 어코드 남음 프레시 1/5 · 플로럴 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

### 패출리 강 · 만다린 · 장미

- 강: 패출리 → 스파이시 · 중: 만다린 오렌지 → 프루티, 장미 → 플로럴

**강 1.0** — 조합 비율: 스파이시 12%, 프루티 10%, 플로럴 30% — 강 어코드 1위 0/5 · 중 어코드 남음 프루티 5/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 4 | Gianfranco Ferré — GFF Uomo | 플로럴 · 스파이시 · 프루티 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.0** — 조합 비율: 스파이시 17%, 프루티 8%, 플로럴 24% — 강 어코드 1위 0/5 · 중 어코드 남음 프루티 5/5 · 플로럴 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.5** — 조합 비율: 스파이시 19%, 프루티 8%, 플로럴 20% — 강 어코드 1위 1/5 · 중 어코드 남음 프루티 2/5 · 플로럴 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 3.0** — 조합 비율: 스파이시 20%, 프루티 7%, 플로럴 18% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 플로럴 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

### 우드(오드) 강 · 베르가못 · 장미

- 강: 우드 → 우디 · 중: 베르가못 → 프레시, 장미 → 플로럴

**강 1.0** — 조합 비율: 우디 17%, 프레시 9%, 플로럴 31% — 강 어코드 1위 0/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.0** — 조합 비율: 우디 23%, 프레시 7%, 플로럴 22% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.5** — 조합 비율: 우디 28%, 프레시 6%, 플로럴 19% — 강 어코드 1위 4/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 2 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 3.0** — 조합 비율: 우디 28%, 프레시 6%, 플로럴 17% — 강 어코드 1위 4/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 3 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

### 레몬 강 · 오스만투스 · 샌달우드 약 (맹점, 기존)

- 강: 레몬 → 시트러스 · 중: 오스만투스 → 플로럴

**강 1.0** — 조합 비율: 시트러스 23%, 플로럴 28% — 강 어코드 1위 0/5 · 중 어코드 남음 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.0** — 조합 비율: 시트러스 33%, 플로럴 18% — 강 어코드 1위 0/5 · 중 어코드 남음 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 2 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.5** — 조합 비율: 시트러스 37%, 플로럴 16% — 강 어코드 1위 1/5 · 중 어코드 남음 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 2 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 3.0** — 조합 비율: 시트러스 39%, 플로럴 14% — 강 어코드 1위 2/5 · 중 어코드 남음 플로럴 4/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 5 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |

> (향수 4개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

### 녹차 강 · 베르가못 · 샌달우드

- 강: 녹차 → 프레시 · 중: 베르가못 → 프레시, 샌달우드 → 우디

**강 1.0** — 조합 비율: 프레시 22%, 프레시 22%, 우디 23% — 강 어코드 1위 4/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.0** — 조합 비율: 프레시 25%, 프레시 25%, 우디 16% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.5** — 조합 비율: 프레시 26%, 프레시 26%, 우디 15% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 3.0** — 조합 비율: 프레시 31%, 프레시 31%, 우디 13% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

### 머스크 강 · 만다린 · 아이리스

- 강: 머스크 → 프레시 · 중: 만다린 오렌지 → 프루티, 아이리스 → 파우더리

**강 1.0** — 조합 비율: 프레시 21%, 프루티 9%, 파우더리 20% — 강 어코드 1위 0/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.0** — 조합 비율: 프레시 26%, 프루티 7%, 파우더리 19% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 2.5** — 조합 비율: 프레시 27%, 프루티 6%, 파우더리 18% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)

**강 3.0** — 조합 비율: 프레시 28%, 프루티 6%, 파우더리 18% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

> (향수 5개 표는 비공개 — 원본 `pipeline/data/out/private_docs/engine_strong_candidate.md`, plan_b_portfolio.md 1-4)
