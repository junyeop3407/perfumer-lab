"""9단계 눈으로 평가 부품 (규칙: docs/engine_eye_v8_rules.md). 조합 만들기와 시트 문서. 추천 계산은 하지 않는다."""
from __future__ import annotations

from collections import Counter

import blind_v8 as BL

SEED = 20261106
GOURMAND = "구르망"
STRENGTH_KO = {"strong": " (강)", "weak": " (약)"}


def combo_bucket(note_keys: list[str], family_of_note: dict[str, str]) -> str:
    """스위트 계열 노트가 2개 이상이면 구르망, 아니면 2개 이상인 분류, 없으면 혼합."""
    count = Counter(BL.BUCKET_OF[family_of_note[k]] for k in note_keys)
    if count[GOURMAND] >= 2:
        return GOURMAND
    best = max(BL.BUCKETS, key=lambda b: count[b])
    return best if count[best] >= 2 else "혼합"


def gourmand_combos(cat: dict, n: int, used: set[frozenset], seed: int = SEED) -> list[list]:
    """블라인드와 같은 방법으로 만들어 구르망만 쓴다. 노트 [key, layer, "medium"] 목록을 돌려준다."""
    return [c["notes"] for c in BL.generate(cat, n, used, seed) if c["bucket"] == GOURMAND]


def _title(notes: list, note_ko: dict[str, str]) -> str:
    return " · ".join(f"{BL.LAYER_KO[layer]} {note_ko[k]}{STRENGTH_KO.get(s, '')}" for k, layer, s in notes)


def sheet_text(scored: list[dict], reference: list[dict], cat: dict) -> str:
    """scored·reference: {no, notes, keys: [perfume key]}. 표에 나가는 칸은 blind_v8._row와 점수 칸뿐이다."""
    by_key = {p["key"]: p for p in cat["perfumes"]}
    note_ko = {n["key"]: n["nameKo"] for n in cat["notes"]}
    accord_ko = {a["key"]: a["nameKo"] for a in cat["accords"]}
    head = ["| 순위 | 향수 | 브랜드 | 주요 노트 (탑 / 미들 / 베이스) | 주요 어코드 | 점수 |", "|---:|---|---|---|---|---|"]

    def section(prefix: str, e: dict) -> list[str]:
        rows = [f"{BL._row(by_key[k], note_ko, accord_ko, i + 1)} 점수: _ |" for i, k in enumerate(e["keys"])]
        return [f"## {prefix}{e['no']}. {_title(e['notes'], note_ko)}", "", *head, *rows, ""]

    lines = ["# 눈으로 평가 시트", "",
             "향수마다 조합과 얼마나 비슷한지 1~5점을 `점수: _` 칸에 적어 주세요. 필요하면 Fragrantica 전체 노트를 참고해도 됩니다.", "",
             "| 점수 | 기준 |", "|---:|---|",
             "| 5 | 조합 노트 3개 포함, 분위기 일치 |", "| 4 | 조합 노트 2개 포함, 분위기 일치 |",
             "| 3 | 조합 노트 1~2개 포함, 또는 분위기만 비슷 |", "| 2 | 조합 노트 1개 겹치지만 분위기 다름 |", "| 1 | 거의 무관 |", ""]
    for e in scored:
        lines += section("", e)
    if reference:
        lines += ["# 참고 (집계하지 않습니다)", ""]
        for e in reference:
            lines += section("참고 ", e)
    return "\n".join(lines)
