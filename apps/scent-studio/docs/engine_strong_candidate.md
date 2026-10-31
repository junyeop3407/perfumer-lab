# '강' 값 시험

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

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Jil Sander — Sun Summer Edition 2019 | 플로럴 · 스위트 · 프레시 |
| 2 | Spezierie Palazzo Vecchio / I Profumi di Firenze — Cashmere Il Fiume e il Salice | 플로럴 · 스위트 · 프레시 |
| 3 | Naomi Campbell — Naomagic | 플로럴 · 스위트 · 파우더리 |
| 4 | Marc Gebauer — Amorist | 플로럴 · 스위트 · 프레시 |
| 5 | Jil Sander — Style Summer | 플로럴 · 스위트 · 스파이시 |

**강 2.0** — 조합 비율: 스위트 32%, 프레시 7%, 플로럴 19% — 강 어코드 1위 5/5 · 중 어코드 남음 프레시 1/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Elizabeth Arden — th Avenue Nights | 스위트 · 플로럴 · 파우더리 |
| 2 | Spezierie Palazzo Vecchio / I Profumi di Firenze — Acqua Mirabile Odorosa di Firenze N°1 Spezierie Palazzo Vecchio / I Profumi di Firenze Eau de Parfum | 스위트 · 플로럴 · 시트러스 |
| 3 | Yves Saint Laurent — Manifesto L'Eclat | 스위트 · 플로럴 · 파우더리 |
| 4 | Naomi Campbell — Winter Kiss | 스위트 · 플로럴 · 파우더리 |
| 5 | Fragonard — Miranda Fragonard 2005 Eau de Toilette | 스위트 · 플로럴 · 구르망 |

**강 2.5** — 조합 비율: 스위트 35%, 프레시 6%, 플로럴 17% — 강 어코드 1위 5/5 · 중 어코드 남음 프레시 1/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Elizabeth Arden — th Avenue Nights | 스위트 · 플로럴 · 파우더리 |
| 2 | Spezierie Palazzo Vecchio / I Profumi di Firenze — Acqua Mirabile Odorosa di Firenze N°1 Spezierie Palazzo Vecchio / I Profumi di Firenze Eau de Parfum | 스위트 · 플로럴 · 시트러스 |
| 3 | Fragonard — Miranda Fragonard 2005 Eau de Toilette | 스위트 · 플로럴 · 구르망 |
| 4 | Yves Saint Laurent — Manifesto L'Eclat | 스위트 · 플로럴 · 파우더리 |
| 5 | Naomi Campbell — Winter Kiss | 스위트 · 플로럴 · 파우더리 |

**강 3.0** — 조합 비율: 스위트 35%, 프레시 6%, 플로럴 16% — 강 어코드 1위 5/5 · 중 어코드 남음 프레시 1/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Elizabeth Arden — th Avenue Nights | 스위트 · 플로럴 · 파우더리 |
| 2 | Fragonard — Miranda Fragonard 2005 Eau de Toilette | 스위트 · 플로럴 · 구르망 |
| 3 | Yves Saint Laurent — Manifesto L'Eclat | 스위트 · 플로럴 · 파우더리 |
| 4 | Naomi Campbell — Winter Kiss | 스위트 · 플로럴 · 파우더리 |
| 5 | Spezierie Palazzo Vecchio / I Profumi di Firenze — Acqua Mirabile Odorosa di Firenze N°1 Spezierie Palazzo Vecchio / I Profumi di Firenze Eau de Parfum | 스위트 · 플로럴 · 시트러스 |

### 패출리 강 · 만다린 · 장미

- 강: 패출리 → 스파이시 · 중: 만다린 오렌지 → 프루티, 장미 → 플로럴

**강 1.0** — 조합 비율: 스파이시 12%, 프루티 10%, 플로럴 30% — 강 어코드 1위 0/5 · 중 어코드 남음 프루티 5/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Bois 1920 — Fondentarancio | 플로럴 · 스위트 · 스파이시 |
| 2 | Clive Christian — X for Women | 플로럴 · 프루티 · 스위트 |
| 3 | Salvador Dali — Le Roy Soleil Salvador Dali 1997 Eau de Toilette | 플로럴 · 프루티 · 스파이시 |
| 4 | Gianfranco Ferré — GFF Uomo | 플로럴 · 스파이시 · 프루티 |
| 5 | Roberto Verino — VV Man Roberto Verino 2004 Eau de Toilette | 플로럴 · 스파이시 · 프루티 |

**강 2.0** — 조합 비율: 스파이시 17%, 프루티 8%, 플로럴 24% — 강 어코드 1위 0/5 · 중 어코드 남음 프루티 5/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Roberto Verino — VV Man Roberto Verino 2004 Eau de Toilette | 플로럴 · 스파이시 · 프루티 |
| 2 | Kenzo — Ça Sent Beau Kenzo Kenzo 1988 Eau de Toilette | 플로럴 · 스파이시 · 스위트 |
| 3 | Bois 1920 — Fondentarancio | 플로럴 · 스위트 · 스파이시 |
| 4 | XerJoff — XerJoff | 스위트 · 플로럴 · 스파이시 |
| 5 | Bath & Body Works — Summer Vanillas - Berry | 플로럴 · 스파이시 · 프루티 |

**강 2.5** — 조합 비율: 스파이시 19%, 프루티 8%, 플로럴 20% — 강 어코드 1위 1/5 · 중 어코드 남음 프루티 2/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | XerJoff — XerJoff | 스위트 · 플로럴 · 스파이시 |
| 2 | 06130 - Zéro Six Cent-Trente — Matéo | 우디 · 스파이시 · 플로럴 |
| 3 | Rancé 1795 — François Charles Rancé 1795 2016 Eau de Parfum | 우디 · 스파이시 · 플로럴 |
| 4 | Rancé 1795 — Laetitia | 스파이시 · 플로럴 · 우디 |
| 5 | Roberto Verino — VV Man Roberto Verino 2004 Eau de Toilette | 플로럴 · 스파이시 · 프루티 |

**강 3.0** — 조합 비율: 스파이시 20%, 프루티 7%, 플로럴 18% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | 06130 - Zéro Six Cent-Trente — Matéo | 우디 · 스파이시 · 플로럴 |
| 2 | Rancé 1795 — François Charles Rancé 1795 2016 Eau de Parfum | 우디 · 스파이시 · 플로럴 |
| 3 | s.Oliver — Selection Men s.Oliver 2007 Eau de Toilette | 스파이시 · 스위트 · 우디 |
| 4 | Rancé 1795 — Laetitia | 스파이시 · 플로럴 · 우디 |
| 5 | XerJoff — XerJoff | 스위트 · 플로럴 · 스파이시 |

### 우드(오드) 강 · 베르가못 · 장미

- 강: 우드 → 우디 · 중: 베르가못 → 프레시, 장미 → 플로럴

**강 1.0** — 조합 비율: 우디 17%, 프레시 9%, 플로럴 31% — 강 어코드 1위 0/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |
| 2 | Roja Parfums — Taif Aoud Roja Parfums 2014 Parfum | 플로럴 · 오리엔탈 · 우디 |
| 3 | Rasasi — Qasamat Morhaf | 플로럴 · 오리엔탈 · 우디 |
| 4 | Penhaligon's — Oud de Nil | 플로럴 · 오리엔탈 · 우디 |
| 5 | Nabeel — Nasaem Nabeel Eau de Parfum | 플로럴 · 스파이시 · 우디 |

**강 2.0** — 조합 비율: 우디 23%, 프레시 7%, 플로럴 22% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |
| 2 | Roja Parfums — Aoud | 우디 · 오리엔탈 · 플로럴 |
| 3 | Tiziana Terenzi — Gold Rose Oudh Tiziana Terenzi 2012 Extrait de Parfum | 우디 · 플로럴 · 스파이시 |
| 4 | Mancera — Black to Black | 우디 · 플로럴 · 스파이시 |
| 5 | Roja Parfums — Taif Aoud Roja Parfums 2014 Parfum | 플로럴 · 오리엔탈 · 우디 |

**강 2.5** — 조합 비율: 우디 28%, 프레시 6%, 플로럴 19% — 강 어코드 1위 4/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Roja Parfums — Aoud | 우디 · 오리엔탈 · 플로럴 |
| 2 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |
| 3 | Tiziana Terenzi — Gold Rose Oudh Tiziana Terenzi 2012 Extrait de Parfum | 우디 · 플로럴 · 스파이시 |
| 4 | Mancera — Black to Black | 우디 · 플로럴 · 스파이시 |
| 5 | Penhaligon's — As Sawira | 우디 · 오리엔탈 · 스파이시 |

**강 3.0** — 조합 비율: 우디 28%, 프레시 6%, 플로럴 17% — 강 어코드 1위 4/5 · 중 어코드 남음 프레시 0/5 · 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Roja Parfums — Aoud | 우디 · 오리엔탈 · 플로럴 |
| 2 | Penhaligon's — As Sawira | 우디 · 오리엔탈 · 스파이시 |
| 3 | Al Haramain / الحرمين — Haneen Al Haramain / الحرمين Perfume | 플로럴 · 우디 · 오리엔탈 |
| 4 | Tiziana Terenzi — Gold Rose Oudh Tiziana Terenzi 2012 Extrait de Parfum | 우디 · 플로럴 · 스파이시 |
| 5 | Mancera — Black to Black | 우디 · 플로럴 · 스파이시 |

### 레몬 강 · 오스만투스 · 샌달우드 약 (맹점, 기존)

- 강: 레몬 → 시트러스 · 중: 오스만투스 → 플로럴

**강 1.0** — 조합 비율: 시트러스 23%, 플로럴 28% — 강 어코드 1위 0/5 · 중 어코드 남음 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |
| 2 | Clive Christian — for Women | 플로럴 · 프레시 · 그린 |
| 3 | Elizabeth Arden — Sunflowers Elizabeth Arden 1993 Eau de Toilette | 플로럴 · 프레시 · 프루티 |
| 4 | Balenciaga — Talisman Balenciaga 1996 Eau Transparente | 플로럴 · 프루티 · 그린 |
| 5 | Alfred Sung — Sung Alfred Sung 1986 Eau de Toilette | 플로럴 · 그린 · 프레시 |

**강 2.0** — 조합 비율: 시트러스 33%, 플로럴 18% — 강 어코드 1위 0/5 · 중 어코드 남음 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Clive Christian — for Women | 플로럴 · 프레시 · 그린 |
| 2 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |
| 3 | Elizabeth Arden — Sunflowers Elizabeth Arden 1993 Eau de Toilette | 플로럴 · 프레시 · 프루티 |
| 4 | Ormonde Jayne — Gatsby 22 | 프레시 · 시트러스 · 플로럴 |
| 5 | Balenciaga — Talisman Balenciaga 1996 Eau Transparente | 플로럴 · 프루티 · 그린 |

**강 2.5** — 조합 비율: 시트러스 37%, 플로럴 16% — 강 어코드 1위 1/5 · 중 어코드 남음 플로럴 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Clive Christian — for Women | 플로럴 · 프레시 · 그린 |
| 2 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |
| 3 | Ormonde Jayne — Gatsby 22 | 프레시 · 시트러스 · 플로럴 |
| 4 | Elizabeth Arden — Sunflowers Elizabeth Arden 1993 Eau de Toilette | 플로럴 · 프레시 · 프루티 |
| 5 | Carolina Herrera — Mad World | 시트러스 · 프레시 · 아쿠아틱 |

**강 3.0** — 조합 비율: 시트러스 39%, 플로럴 14% — 강 어코드 1위 2/5 · 중 어코드 남음 플로럴 4/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Clive Christian — for Women | 플로럴 · 프레시 · 그린 |
| 2 | Ormonde Jayne — Gatsby 22 | 프레시 · 시트러스 · 플로럴 |
| 3 | Carolina Herrera — Mad World | 시트러스 · 프레시 · 아쿠아틱 |
| 4 | Sisley — Eau de Sisley 3 | 시트러스 · 프레시 · 스파이시 |
| 5 | Kuschel J / クシェルヨット — Freude Feder フロイデフェーダァ | 플로럴 · 프레시 · 프루티 |

### 녹차 강 · 베르가못 · 샌달우드

- 강: 녹차 → 프레시 · 중: 베르가못 → 프레시, 샌달우드 → 우디

**강 1.0** — 조합 비율: 프레시 22%, 프레시 22%, 우디 23% — 강 어코드 1위 4/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Afnan Perfumes — Supremacy in Heaven | 프레시 · 시트러스 · 프루티 |
| 2 | Creed — Asian Green Tea | 그린 · 프레시 · 시트러스 |
| 3 | Tommy Hilfiger — Impact Spark | 프레시 · 우디 · 프루티 |
| 4 | Banana Republic — Metal Rain | 프레시 · 그린 · 우디 |
| 5 | La Maison de la Vanille — Vanille Fleurie de Tahiti | 프레시 · 신세틱 · 시트러스 |

**강 2.0** — 조합 비율: 프레시 25%, 프레시 25%, 우디 16% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Creed — Asian Green Tea | 그린 · 프레시 · 시트러스 |
| 2 | Afnan Perfumes — Supremacy in Heaven | 프레시 · 시트러스 · 프루티 |
| 3 | Banana Republic — Metal Rain | 프레시 · 그린 · 우디 |
| 4 | Elizabeth Arden — Green Tea Bamboo | 그린 · 프레시 · 시트러스 |
| 5 | M. Asam — M. Asam No. 1 | 프레시 · 그린 · 시트러스 |

**강 2.5** — 조합 비율: 프레시 26%, 프레시 26%, 우디 15% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Creed — Asian Green Tea | 그린 · 프레시 · 시트러스 |
| 2 | Afnan Perfumes — Supremacy in Heaven | 프레시 · 시트러스 · 프루티 |
| 3 | Elizabeth Arden — Green Tea Bamboo | 그린 · 프레시 · 시트러스 |
| 4 | Banana Republic — Metal Rain | 프레시 · 그린 · 우디 |
| 5 | M. Asam — M. Asam No. 1 | 프레시 · 그린 · 시트러스 |

**강 3.0** — 조합 비율: 프레시 31%, 프레시 31%, 우디 13% — 강 어코드 1위 3/5 · 중 어코드 남음 프레시 5/5 · 우디 4/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Creed — Asian Green Tea | 그린 · 프레시 · 시트러스 |
| 2 | Afnan Perfumes — Supremacy in Heaven | 프레시 · 시트러스 · 프루티 |
| 3 | Elizabeth Arden — Green Tea Bamboo | 그린 · 프레시 · 시트러스 |
| 4 | M. Asam — M. Asam No. 1 | 프레시 · 그린 · 시트러스 |
| 5 | Jardin de France — Eau de Cologne 1920: Ame de Thé vert | 프레시 · 그린 · 시트러스 |

### 머스크 강 · 만다린 · 아이리스

- 강: 머스크 → 프레시 · 중: 만다린 오렌지 → 프루티, 아이리스 → 파우더리

**강 1.0** — 조합 비율: 프레시 21%, 프루티 9%, 파우더리 20% — 강 어코드 1위 0/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Réminiscence — Love Rose Réminiscence 2014 Eau de Parfum | 플로럴 · 파우더리 · 프레시 |
| 2 | Jeanne en Provence — Iris & Néroli | 플로럴 · 파우더리 · 프레시 |
| 3 | Bvlgari — Omnia Crystalline L'Eau de Parfum | 플로럴 · 파우더리 · 프레시 |
| 4 | Guerlain — Eau de Cashmere | 파우더리 · 플로럴 · 프레시 |
| 5 | Jean Paul Gaultier — Classique Summer Fragrance 2013 | 플로럴 · 프레시 · 시트러스 |

**강 2.0** — 조합 비율: 프레시 26%, 프루티 7%, 파우더리 19% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Réminiscence — Love Rose Réminiscence 2014 Eau de Parfum | 플로럴 · 파우더리 · 프레시 |
| 2 | Jeanne en Provence — Iris & Néroli | 플로럴 · 파우더리 · 프레시 |
| 3 | Bvlgari — Omnia Crystalline L'Eau de Parfum | 플로럴 · 파우더리 · 프레시 |
| 4 | Lacoste — L.12.12 Rose Eau Intense | 프레시 · 플로럴 · 파우더리 |
| 5 | Clubman / Edouard Pinaud — Bouquet de l'Empereur Napoléon III | 프레시 · 플로럴 · 파우더리 |

**강 2.5** — 조합 비율: 프레시 27%, 프루티 6%, 파우더리 18% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Réminiscence — Love Rose Réminiscence 2014 Eau de Parfum | 플로럴 · 파우더리 · 프레시 |
| 2 | Jeanne en Provence — Iris & Néroli | 플로럴 · 파우더리 · 프레시 |
| 3 | Lacoste — L.12.12 Rose Eau Intense | 프레시 · 플로럴 · 파우더리 |
| 4 | Clubman / Edouard Pinaud — Bouquet de l'Empereur Napoléon III | 프레시 · 플로럴 · 파우더리 |
| 5 | Bvlgari — Omnia Crystalline L'Eau de Parfum | 플로럴 · 파우더리 · 프레시 |

**강 3.0** — 조합 비율: 프레시 28%, 프루티 6%, 파우더리 18% — 강 어코드 1위 2/5 · 중 어코드 남음 프루티 1/5 · 파우더리 5/5

| 순위 | 향수 | 상위 어코드 |
|---|---|---|
| 1 | Réminiscence — Love Rose Réminiscence 2014 Eau de Parfum | 플로럴 · 파우더리 · 프레시 |
| 2 | Jeanne en Provence — Iris & Néroli | 플로럴 · 파우더리 · 프레시 |
| 3 | Lacoste — L.12.12 Rose Eau Intense | 프레시 · 플로럴 · 파우더리 |
| 4 | Clubman / Edouard Pinaud — Bouquet de l'Empereur Napoléon III | 프레시 · 플로럴 · 파우더리 |
| 5 | Lancaster — Aquasun | 플로럴 · 프레시 · 스위트 |
