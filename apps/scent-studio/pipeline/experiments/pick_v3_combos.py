"""v6 vs v3 블라인드 비교에 넣을 새 조합 고르기 (2026-10-31 사장 요청). 엔진은 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/pick_v3_combos.py   (실제 카탈로그·원본 CSV 필요)
만드는 파일: docs/engine_v3_combo_pick.md (후보마다 두 엔진 Top 5가 몇 개 다른지만. 추천 목록은 보이지 않는다)

규칙(사장 지시): 실제 사용자가 고를 만한 3~4노트 조합 후보를 미리 적어 두고, 두 엔진(v6 / v3)의 Top 5가 2개 이상
다른 것만 남긴다. 어느 쪽 결과가 좋아 보이는지는 보지 않는다. 남은 것 중 아래 순서로 **적은 순서대로** 6개를 고른다:
머스크·패출리·라벤더가 든 조합 각각 1개 이상, '강'이 든 조합 2개 이상을 먼저 채우고, 나머지는 적은 순서대로.
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import evaluate as V  # noqa: E402
from engine_ref import Engine  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
M, S, W = "medium", "strong", "weak"

# 후보 (이름, [(노트, 층, 강도)]) — 결과를 보기 전에 적은 것. 순서가 곧 고르는 순서다
CANDIDATES = [
    ("자몽 · 장미 · 머스크", [("grapefruit", "top", M), ("rose", "middle", M), ("musk", "base", M)]),
    ("베르가못 · 아이리스 · 머스크 강", [("bergamot", "top", M), ("iris", "middle", M), ("musk", "base", S)]),
    ("만다린 · 은방울꽃 · 화이트 머스크", [("mandarin orange", "top", M), ("lily of the valley", "middle", M), ("white musk", "base", M)]),
    ("배 · 프리지아 · 머스크", [("pear", "top", M), ("freesia", "middle", M), ("musk", "base", M)]),
    ("핑크 페퍼 · 장미 · 패출리 강", [("pink pepper", "top", M), ("rose", "middle", M), ("patchouli", "base", S)]),
    ("베르가못 · 자스민 · 패출리 · 바닐라", [("bergamot", "top", M), ("jasmine", "middle", M), ("patchouli", "base", M), ("vanilla", "base", M)]),
    ("오렌지 · 카다멈 · 패출리", [("orange", "top", M), ("cardamom", "middle", M), ("patchouli", "base", M)]),
    ("레몬 · 라벤더 · 통카 빈", [("lemon", "top", M), ("lavender", "middle", M), ("tonka bean", "base", M)]),
    ("베르가못 · 라벤더 · 오크모스", [("bergamot", "top", M), ("lavender", "middle", M), ("oakmoss", "base", M)]),
    ("라벤더 강 · 제라늄 · 베티버", [("lavender", "top", S), ("geranium", "middle", M), ("vetiver", "base", M)]),
    ("민트 · 라벤더 · 시더우드", [("mint", "top", M), ("lavender", "middle", M), ("cedarwood", "base", M)]),
    ("자몽 · 생강 · 베티버", [("grapefruit", "top", M), ("ginger", "middle", M), ("vetiver", "base", M)]),
    ("레몬 · 복숭아 · 자스민 · 머스크", [("lemon", "top", M), ("peach", "top", M), ("jasmine", "middle", M), ("musk", "base", M)]),
    ("블랙커런트 · 장미 · 앰버", [("blackcurrant", "top", M), ("rose", "middle", M), ("amber", "base", M)]),
    ("사프란 · 장미 · 오드", [("saffron", "top", M), ("rose", "middle", M), ("oud", "base", M)]),
    ("카다멈 · 아이리스 · 앰버 강", [("cardamom", "top", M), ("iris", "middle", M), ("amber", "base", S)]),
    ("네롤리 · 오렌지 블로섬 · 머스크", [("neroli", "top", M), ("orange blossom", "middle", M), ("musk", "base", M)]),
    ("베르가못 · 녹차 · 시더우드", [("bergamot", "top", M), ("green tea", "middle", M), ("cedarwood", "base", M)]),
    ("레몬 강 · 바질 · 베티버", [("lemon", "top", S), ("basil", "middle", M), ("vetiver", "base", M)]),
    ("시나몬 · 자두 · 바닐라", [("cinnamon", "top", M), ("plum", "middle", M), ("vanilla", "base", M)]),
    ("라즈베리 · 장미 · 바닐라 · 머스크", [("raspberry", "top", M), ("rose", "middle", M), ("vanilla", "base", M), ("musk", "base", M)]),
    ("만다린 · 튜베로즈 · 샌달우드", [("mandarin orange", "top", M), ("tuberose", "middle", M), ("sandalwood", "base", M)]),
    ("핑크 페퍼 · 바이올렛 · 레더", [("pink pepper", "top", M), ("violet", "middle", M), ("leather", "base", M)]),
    ("베르가못 · 프랑킨센스 · 랍다넘", [("bergamot", "top", M), ("frankincense", "middle", M), ("labdanum", "base", M)]),
    ("레몬 · 해양 노트 · 앰버그리스", [("lemon", "top", M), ("sea notes", "top", M), ("ambergris", "base", M)]),
    ("자몽 · 라벤더 · 머스크", [("grapefruit", "top", M), ("lavender", "middle", M), ("musk", "base", M)]),
    ("배 · 아이리스 · 패출리", [("pear", "top", M), ("iris", "middle", M), ("patchouli", "base", M)]),
    ("만다린 · 피오니 · 머스크 강", [("mandarin orange", "top", M), ("peony", "middle", M), ("musk", "base", S)]),
    ("베르가못 · 클로브 · 패출리 · 앰버", [("bergamot", "top", M), ("clove", "middle", M), ("patchouli", "base", M), ("amber", "base", M)]),
    ("오렌지 · 자스민 · 바닐라 강", [("orange", "top", M), ("jasmine", "middle", M), ("vanilla", "base", S)]),
    ("레몬 · 로즈마리 · 라벤더 · 머스크", [("lemon", "top", M), ("rosemary", "top", M), ("lavender", "middle", M), ("musk", "base", M)]),
    ("블랙 페퍼 · 카다멈 · 시더우드", [("black pepper", "top", M), ("cardamom", "middle", M), ("cedarwood", "base", M)]),
    ("유자 · 녹차 · 머스크", [("yuzu", "top", M), ("green tea", "middle", M), ("musk", "base", M)]),
    ("복숭아 · 오스만투스 · 머스크", [("peach", "top", M), ("osmanthus", "middle", M), ("musk", "base", M)]),
    ("베르가못 · 장미 · 오크모스 · 패출리", [("bergamot", "top", M), ("rose", "middle", M), ("oakmoss", "base", M), ("patchouli", "base", M)]),
    ("레몬 · 일랑일랑 · 바닐라", [("lemon", "top", M), ("ylang-ylang", "middle", M), ("vanilla", "base", M)]),
    ("만다린 · 라벤더 · 바닐라", [("mandarin orange", "top", M), ("lavender", "middle", M), ("vanilla", "base", M)]),
    ("자몽 · 핑크 페퍼 · 오드 강", [("grapefruit", "top", M), ("pink pepper", "middle", M), ("oud", "base", S)]),
    ("베르가못 · 헬리오트로프 · 통카 빈", [("bergamot", "top", M), ("heliotrope", "middle", M), ("tonka bean", "base", M)]),
    ("레몬 · 은방울꽃 · 오크모스", [("lemon", "top", M), ("lily of the valley", "middle", M), ("oakmoss", "base", M)]),
    ("생강 · 장미 · 머스크 · 샌달우드", [("ginger", "top", M), ("rose", "middle", M), ("musk", "base", M), ("sandalwood", "base", M)]),
    ("카다멈 · 라벤더 · 패출리", [("cardamom", "top", M), ("lavender", "middle", M), ("patchouli", "base", M)]),
    ("만다린 · 자스민 · 패출리 강", [("mandarin orange", "top", M), ("jasmine", "middle", M), ("patchouli", "base", S)]),
    ("베르가못 · 바이올렛 · 머스크 · 앰버", [("bergamot", "top", M), ("violet", "middle", M), ("musk", "base", M), ("amber", "base", M)]),
]


def pick(rows: list[dict], n: int = 6) -> list[dict]:
    """두 엔진이 다른 후보(차이 2개 이상) 중에서, 정해진 조건을 적은 순서대로 채운다"""
    ok = [r for r in rows if r["usable"] and r["diff"] >= 2]
    chosen: list[dict] = []

    def take(cond):
        for r in ok:
            if r not in chosen and cond(r):
                chosen.append(r)
                return

    for note in ("musk", "patchouli", "lavender"):
        if not any(note in r["notes"] for r in chosen):
            take(lambda r, note=note: note in r["notes"])
    while sum(1 for r in chosen if r["strong"]) < 2:
        before = len(chosen)
        take(lambda r: r["strong"])
        if len(chosen) == before:
            break
    while len(chosen) < n:
        before = len(chosen)
        take(lambda r: True)
        if len(chosen) == before:
            break
    return chosen


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = json.load(gzip.open(V.CATALOG, "rt", encoding="utf-8"))
    eng = Engine(cat)
    v3 = V.v3_recommender()
    rows = []
    for name, spec in CANDIDATES:
        missing = [k for k, _, _ in spec if k not in eng.notes or eng.notes[k]["tier"] == "hidden"]
        row = {"name": name, "spec": spec, "notes": {k for k, _, _ in spec}, "strong": any(s == S for *_, s in spec),
               "usable": not missing, "missing": missing, "diff": None}
        if not missing:
            a = {r["perfumeKey"] for r in eng.recommend(V.blend(spec), k=5)}
            b = {r["perfumeKey"] for r in v3(spec)}
            row["diff"] = len(a - b)
        rows.append(row)
    chosen = pick(rows)
    out = ["# v6 vs v3 블라인드: 새 조합 고르기", "",
           "> `py pipeline/experiments/pick_v3_combos.py` (2026-10-31). 두 엔진의 Top 5가 몇 개 다른지만 보고 골랐다(추천 목록은 보지 않음).",
           f"> 후보 {len(rows)}개 · 쓸 수 있는 후보 {sum(r['usable'] for r in rows)}개 · 두 엔진이 2개 이상 다른 후보 "
           f"{sum(1 for r in rows if r['usable'] and r['diff'] >= 2)}개 · 고른 것 {len(chosen)}개", "",
           "| # | 후보 | Top 5 중 다른 향수 수 | 고름 |", "|---|---|---|---|"]
    for i, r in enumerate(rows, 1):
        diff = r["diff"] if r["usable"] else f"못 씀(노트 없음: {', '.join(r['missing'])})"
        out.append(f"| {i} | {r['name']} | {diff} | {'✔' if r in chosen else ''} |")
    out += ["", "## 고른 6개 (evaluate.py V3_EXTRA_COMBOS에 적음)", "", *[f"- {r['name']}: {r['spec']}" for r in chosen], ""]
    (APP / "docs" / "engine_v3_combo_pick.md").write_text("\n".join(out), encoding="utf-8")
    for r in chosen:
        print(repr((r["name"], r["spec"])) + ",")


if __name__ == "__main__":
    main()
