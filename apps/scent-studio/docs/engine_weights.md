# 어코드 : 노트 가중치 비교

> `py pipeline/experiments/weight_grid.py` (2026-10-31). 선호 계열 0.1은 그대로, 어코드 + 노트 = 0.9. 엔진 v3 프로필·강 2.0 그대로.

첫 줄이 지금 엔진 v3. '블라인드 조합 Top 5 변화' = 블라인드 비교 조합 10개의 Top 5(50개) 중 v3에 없던 향수 수.

| 어코드 : 노트 | 강 | 재현 20 (기준 15, Top 3) | 재현 100 (Top 3) | 재현 100 (Top 10) | 맹점 (전체 / 상위 3) | 맹점 시트러스 1위 | 블라인드 조합 Top 5 변화 | 다양성 브랜드 (기준 40) | 노트만 방식과 Top 5 겹침 | 등급 기준(자기 점수 중앙값 / 하위 25%) |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.45 : 0.45 | 2.0 | 15/20 | 87/100 | 94/100 | 5/5 · 3/5 | 2 | 0/50 | 81 | 32% | 0.87 / 0.79 |
| 0.40 : 0.50 | 2.0 | 15/20 | 92/100 | 96/100 | 5/5 · 2/5 | 1 | 2/50 | 81 | 36% | 0.89 / 0.81 |
| 0.35 : 0.55 | 2.0 | 17/20 | 96/100 | 96/100 | 5/5 · 1/5 | 0 | 2/50 | 82 | 36% | 0.9 / 0.84 |
| 0.35 : 0.55 | 2.5 | 17/20 | 96/100 | 96/100 | 5/5 · 1/5 | 1 | 3/50 | 82 | 36% | 0.9 / 0.84 |
| 0.35 : 0.55 | 3.0 | 17/20 | 96/100 | 96/100 | 4/5 · 1/5 | 2 | 3/50 | 83 | 36% | 0.9 / 0.84 |
| 0.30 : 0.60 | 2.0 | 17/20 | 96/100 | 96/100 | 5/5 · 1/5 | 0 | 3/50 | 81 | 36% | 0.91 / 0.86 |
| 0.25 : 0.65 | 2.0 | 17/20 | 96/100 | 99/100 | 4/5 · 0/5 | 0 | 3/50 | 82 | 36% | 0.93 / 0.88 |
| 0.20 : 0.70 | 2.0 | 17/20 | 96/100 | 99/100 | 4/5 · 0/5 | 0 | 4/50 | 82 | 38% | 0.94 / 0.91 |

## 재현 20에서 실패한 향수

- 0.45 : 0.45 · 강 2.0 — Joop! Homme Joop! 1989 Eau de Toilette, Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal, Blue Jeans
- 0.40 : 0.50 · 강 2.0 — Joop! Homme Joop! 1989 Eau de Toilette, Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal, Blue Jeans
- 0.35 : 0.55 · 강 2.0 — Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal
- 0.35 : 0.55 · 강 2.5 — Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal
- 0.35 : 0.55 · 강 3.0 — Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal
- 0.30 : 0.60 · 강 2.0 — Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal
- 0.25 : 0.65 · 강 2.0 — Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal
- 0.20 : 0.70 · 강 2.0 — Sculpture Homme Nikos 1995 Eau de Toilette, Passionate, The Royal
