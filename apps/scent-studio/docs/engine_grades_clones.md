# 등급 기준값 비교 · 클론 표본

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

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Ayala Moriel — Magnolia Petal | 2002 | 37 | fresh · aquatic · citrus | NA | https://www.parfumo.com/Perfumes/Ayala_Moriel/Magnolia_Petal |
| La Rive — Aqua Bella | 2014 | 37 | fresh · aquatic · citrus | NA | https://www.parfumo.com/Perfumes/La_Rive/Aqua_Bella |

### 2. 노트 9개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Auphorie — Impromptu N°4 | 2010 | 156 | woody · spicy · floral | Daniela Andrier | https://www.parfumo.com/Perfumes/Auphorie/impromptu-n-4 |
| Etat Libre d'Orange — Une Amourette - Roland Mouret | 2017 | 156 | woody · spicy · floral | Daniela Andrier | https://www.parfumo.com/Perfumes/Etat_Libre_d_Orange/Une_Amourette__Roland_Mouret |

### 3. 노트 8개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| James Bond 007 — for Women III | 2017 | 37 | fruity · sweet · synthetic | NA | https://www.parfumo.com/Perfumes/James_Bond_007/007_for_Women_III |
| s.Oliver — Black Label Eau Légère Women s.Oliver 2003 Eau de Toilette | 2003 | 37 | fruity · sweet · synthetic | NA | https://www.parfumo.com/Perfumes/s_Oliver/black-label-eau-legere-women-eau-de-toilette |

### 4. 노트 12개 · 원본 순서 다름 · 확인 필요

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Grossmith — Hasu-no-Hana Grossmith 1888 Eau de Parfum | 1888 | 234 | chypre · floral · spicy | NA | https://www.parfumo.com/Perfumes/Grossmith/Hasu_no_Hana_Eau_de_Parfum |
| XerJoff — White On White - One | ? | 14 | oriental · floral · woody | NA | https://www.parfumo.com/Perfumes/Xerjoff/white-on-white-one |

### 5. 노트 6개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Benetton — Energizing Pitanga | 2010 | 37 | green · fresh · citrus | Nathalie Gracia-Cetto | https://www.parfumo.com/Perfumes/Benetton/Energizing_Pitanga |
| Carolina Herrera — Virgin Mint | 2017 | 37 | green · fresh · citrus | Nathalie Gracia-Cetto | https://www.parfumo.com/Perfumes/Carolina_Herrera/virgin-mint |

### 6. 노트 6개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Acqua di Parma — Sakura | 2019 | 127 | floral · fresh · citrus | Nicolas Bonneville, François Demachy | https://www.parfumo.com/Perfumes/Acqua_di_Parma/Sakura |
| Amir Oud — Arab Oud | 2006 | 127 | floral · fresh · citrus | Nicolas Bonneville, François Demachy | https://www.parfumo.com/Perfumes/amir-oud/arab-oud |

### 7. 노트 9개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Carbaline — Cotone Cotton | 2007 | 10 | woody · spicy · fresh | NA | https://www.parfumo.com/Perfumes/Carbaline/Cotone_Cotton |
| Eisenberg — Happiness - Young | 2022 | 10 | woody · spicy · fresh | NA | https://www.parfumo.com/Perfumes/Eisenberg/happiness-young |

### 8. 노트 9개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| 4711 — Shahi 4711 2006 Eau de Toilette | 2006 | 64 | leathery · powdery · floral | Delphine Thierry | https://www.parfumo.com/Perfumes/4711/Shahi_Eau_de_Toilette |
| Lubin — Condottiere | 2019 | 64 | leathery · powdery · floral | Delphine Thierry | https://www.parfumo.com/Perfumes/Lubin/Condottiere |

### 9. 노트 10개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| ArteOlfatto - Luxury Perfumes — Calar del Sole | 2002 | 66 | woody · fruity · spicy | Harry Frémont | https://www.parfumo.com/Perfumes/ArteOlfatto_Luxury_Perfumes/calar-del-sole |
| Ralph Lauren — Purple Label | 2003 | 66 | woody · fruity · spicy | Harry Frémont | https://www.parfumo.com/Perfumes/Ralph_Lauren/Purple_Label |

### 10. 노트 16개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Ava Luxe — Black Coconut Ava Luxe 2019 Eau de Parfum | 2019 | 34 | fruity · sweet · floral | Silvia Martinelli | https://www.parfumo.com/Perfumes/Ava_Luxe/black-coconut-eau-de-parfum |
| Giardini di Toscana — Scintilla | 2023 | 34 | fruity · sweet · floral | Silvia Martinelli | https://www.parfumo.com/Perfumes/Giardini_di_Toscana/scintilla |

### 11. 노트 7개 · 원본 순서 같음 · 일부 수집 오류 의심 (평가 수·어코드가 같은 향수가 섞임)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Narciso Rodriguez — Narciso Narciso Rodriguez 2014 Eau de Parfum | 2014 | 499 | floral · powdery · woody | Aurélien Guichard | https://www.parfumo.com/Perfumes/Narciso_Rodriguez/Narciso |
| C. B. Woodworth & Sons Co. — German Cologne | 2002 | 13 | powdery · floral · woody | Aurélien Guichard | https://www.parfumo.com/Perfumes/C_B_Woodworth__Sons_Co_/German_Cologne |
| Narciso Rodriguez — Narciso Musc Narciso Rodriguez 2014 Oil Parfum | 2014 | 13 | powdery · floral · woody | Aurélien Guichard | https://www.parfumo.com/Perfumes/Narciso_Rodriguez/Narciso_Musc_Oil_Parfum_Huile_Parfum |

### 12. 노트 10개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Atelier Cologne — Sous Le Toit de Paris | 2002 | 68 | spicy · sweet · gourmand | Fabrizio Tagliacarne Brand owner, Perfume maker | https://www.parfumo.com/Perfumes/Atelier_Cologne/Sous_le_toit_de_Paris |
| Avon — Elite Gentleman In Black | 2002 | 68 | spicy · sweet · gourmand | Fabrizio Tagliacarne Brand owner, Perfume maker | https://www.parfumo.com/Perfumes/Avon/Elite_Gentleman_In_Black |
| Omnia Profumi — Bronzo | 2014 | 68 | spicy · sweet · gourmand | Fabrizio Tagliacarne Brand owner, Perfume maker | https://www.parfumo.com/Perfumes/Omnia_Profumi/Bronzo |

### 13. 노트 14개 · 원본 순서 다름 · 확인 필요

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Floris — No. 89 Floris 1951 Eau de Toilette | 1951 | 191 | floral · fresh · woody | NA | https://www.parfumo.com/Perfumes/Floris/No_89_Eau_de_Toilette |
| Duftanker MGO Duftmanufaktur — Chypre Classico | ? | 14 | spicy · chypre · citrus | Hans Georg Staudt | https://www.parfumo.com/Perfumes/Duftanker_MGO_Duftmanufaktur/chypre-classico |

### 14. 노트 10개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Angel Schlesser — Femme Angel Schlesser 2014 Eau de Parfum | 2014 | 40 | fresh · floral · creamy | Alberto Morillas | https://www.parfumo.com/Perfumes/Angel_Schlesser/Angel_Schlesser_Femme_Eau_de_Parfum |
| Colornoise — Birthday Cake. | 2002 | 40 | fresh · floral · creamy | Alberto Morillas | https://www.parfumo.com/Perfumes/Colornoise/Birthday_Cake_ |

### 15. 노트 19개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Aimée de Mars — Eau 21 | 2008 | 10 | fresh · floral · green | NA | https://www.parfumo.com/Perfumes/Aimee_de_Mars/Les_Elements__Eau_21 |
| Ormonde Jayne — Montabaco Flor | 2021 | 10 | fresh · floral · green | NA | https://www.parfumo.com/Perfumes/Ormonde_Jayne/montabaco-flor |

### 16. 노트 12개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Kenneth Cole — Kenneth Cole Signature Kenneth Cole 2005 Eau de Toilette | 2005 | 35 | spicy · aquatic · fresh | Frank Voelkl, Symrise | https://www.parfumo.com/Perfumes/Kenneth_Cole/Kenneth_Cole_Signature_Eau_de_Toilette |
| Calyx — Bound | 1996 | 35 | spicy · aquatic · fresh | Frank Voelkl, Symrise | https://www.parfumo.com/Perfumes/calyx/bound |

### 17. 노트 9개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| CnR Create — Fire Aries | 2001 | 10 | floral · fresh · green | NA | https://www.parfumo.com/Perfumes/CnR_Create/Fire_Aries |
| Spezierie Palazzo Vecchio / I Profumi di Firenze — Jeunesse - Il Giorno e La Notte Spezierie Palazzo Vecchio / I Profumi di Firenze 2010 Eau de Parfum | 2010 | 10 | floral · fresh · green | NA | https://www.parfumo.com/Perfumes/Spezierie-Palazzo-Vecchio/Jeunesse__Il_Giorno_e_La_Notte_Eau_de_Parfum |

### 18. 노트 8개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| The Body Shop — Nigritella - Oriental Orchid | 2016 | 34 | floral · sweet · oriental | NA | https://www.parfumo.com/Perfumes/The_Body_Shop/Nigritella__Oriental_Orchid |
| Fine'ry — Magnetic Candy | 2015 | 34 | floral · sweet · oriental | NA | https://www.parfumo.com/Perfumes/fine-ry/magnetic-candy |

### 19. 노트 8개 · 원본 순서 다름 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Jaguar — Classic Chromite | 2017 | 72 | synthetic · woody · fresh | Michel Almairac | https://www.parfumo.com/Perfumes/Jaguar/Classic_Chromite |
| Mancera — Black Line | 2003 | 72 | synthetic · woody · fresh | Michel Almairac | https://www.parfumo.com/Perfumes/Mancera/Black_Line |

### 20. 노트 8개 · 원본 순서 같음 · 수집 오류 의심 (평가 수·어코드까지 같음)

| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |
|---|---|---|---|---|---|
| Corday — Jet for Jentlemen Corday 1998 After Shave Lotion | 1998 | 146 | green · fresh · spicy | Céline Ellena | https://www.parfumo.com/Perfumes/Corday/Jet_for_Jentlemen_After_Shave_Lotion |
| The Different Company — Charmes & Feuilles Un Parfum de Charmes & Feuilles | 2006 | 146 | green · fresh · spicy | Céline Ellena | https://www.parfumo.com/Perfumes/The_Different_Company/Charmes__Feuilles_Un_Parfum_de_Charmes__Feuilles |
