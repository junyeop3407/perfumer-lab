"""9단계 눈으로 평가 부품 (규칙: docs/engine_eye_v8_rules.md). 조합 만들기와 시트 문서. 추천 계산은 하지 않는다."""
from __future__ import annotations

import re
from collections import Counter

import blind_v8 as BL

SEED = 20261106
GOURMAND = "구르망"
TOP = 5
PASS = 3.5
SCORE_RANGE = range(1, 6)
BLANK = " 점수: _ |"
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


def fill_scores(text: str, scores: dict[int, list[int]]) -> str:
    """평가 조합의 빈 점수 칸을 위에서부터 채운다. 참고 조합은 건드리지 않는다. 번호·개수·범위가 어긋나면 멈춘다."""
    parts = re.split(r"(?m)^(## \d+\. .*)$", text)
    seen = set()
    for i in range(1, len(parts), 2):
        no = int(re.match(r"## (\d+)\.", parts[i]).group(1))
        seen.add(no)
        body, rest = (parts[i + 1].split("# 참고", 1) + [None])[:2]
        values = scores.get(no, [])
        if body.count(BLANK) != TOP or len(values) != TOP or any(v not in SCORE_RANGE for v in values):
            raise SystemExit(f"{no}번 조합: 빈 칸 {body.count(BLANK)}개, 점수 {values}")
        for v in values:
            body = body.replace(BLANK, f" 점수: {v} |", 1)
        parts[i + 1] = body if rest is None else f"{body}# 참고{rest}"
    if seen != set(scores):
        raise SystemExit(f"조합 번호가 시트와 다릅니다: {sorted(seen ^ set(scores))}")
    return "".join(parts)


def read_scores(text: str) -> dict[int, list[int]]:
    """평가 조합의 점수를 읽는다. 참고 조합은 읽지 않는다. 빈 칸이 있으면 멈춘다."""
    parts = re.split(r"(?m)^## (\d+)\. .*$", text.split("# 참고 (집계하지 않습니다)")[0])
    out = {}
    for i in range(1, len(parts), 2):
        values = [int(v) for v in re.findall(r"(?m) 점수: (\d) \|$", parts[i + 1])]
        if len(values) != TOP or BLANK in parts[i + 1]:
            raise SystemExit(f"{parts[i]}번 조합의 점수가 {len(values)}개입니다")
        out[int(parts[i])] = values
    return out


def tally(scores: dict[int, list[int]], combos: list[dict]) -> dict:
    """규칙 문서의 집계. 조합 점수는 Top 5 평균이고, 묶음 평균은 조합 점수의 평균이다."""
    if set(scores) != {c["no"] for c in combos}:
        raise SystemExit("점수와 조합의 번호가 다릅니다")
    means = {no: sum(v) / TOP for no, v in sorted(scores.items())}
    avg = lambda nos: sum(means[n] for n in nos) / len(nos)  # noqa: E731
    base = avg([c["no"] for c in combos if c["kind"] == "기본"])
    new = avg([c["no"] for c in combos if c["kind"] == GOURMAND])
    every = avg([c["no"] for c in combos if c["bucket"] == GOURMAND])
    verdict = "합격" if base >= PASS else "불합격"
    if min(new, every) < PASS:
        verdict += ", 구르망 평균 미달: 고치지 않고 보고 후 멈춤"
    return {"combos": means, "base": base, "new_gourmand": new, "all_gourmand": every, "all": avg(list(means)),
            "ranks": [sum(v[i] for v in scores.values()) / len(scores) for i in range(TOP)], "verdict": verdict}
