"""'강'을 노트 겹침 점수에도 반영하는 시험 (2026-10-31 사장 요청). 엔진 설정은 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/strong_both.py   (실제 카탈로그 필요)
만드는 파일: docs/engine_strong_both.md
- 강만 어코드 쪽: 지금 엔진. '강'은 조합 어코드 벡터에서만 노트 프로필을 키운다
- 강을 양쪽에: 노트 일치도에서도 고른 노트의 가중치(ln(1+N/df))에 강도를 곱한다 (engine_ref.NOTE_MATCH_BY_INTENSITY)
엔진 v5(몫 빼기 프로필, 복사 오류 제외, 어코드 0.35 : 노트 0.55) 기준.
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402
from strong_value_test import COMBOS  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
SETTINGS = [(s, both) for s in (2.0, 2.5, 3.0) for both in (False, True)]


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = json.load(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rt", encoding="utf-8"))
    eng = E.Engine(cat)
    by = {p["key"]: p for p in cat["perfumes"]}
    ko = {a["key"]: a["nameKo"] for a in cat["accords"]}
    fam = {a["key"]: a["family"] for a in cat["accords"]}
    v4 = json.loads((Path(__file__).resolve().parent / "v4_blind_top5.json").read_text(encoding="utf-8"))["top5"]
    p20, p100 = V.reproduction_picks(cat, eng, 20), V.reproduction_picks(cat, eng, 100)

    def main_accord(note):
        prof = eng.notes[note]["profile"]
        return eng.accords[max(range(len(prof)), key=lambda i: prof[i])]

    def has_note(key, note):
        p = by[key]
        return note in p["top"] + p["middle"] + p["base"]

    combo_rows, quality_rows = {}, []
    for strong, both in SETTINGS:
        E.INTENSITIES = (0.5, 1.0, strong)
        E.NOTE_MATCH_BY_INTENSITY = both
        label = f"강 {strong} · {'양쪽' if both else '어코드만'}"
        cells, contain = [], {}
        for name, _, (sk, sl), mids, weaks in COMBOS:
            bl = ([E.BlendNote(sk, sl, strong)] + [E.BlendNote(m, l, 1.0) for m, l in mids]
                  + [E.BlendNote(w, l, 0.5) for w, l in weaks])
            recs = eng.recommend(bl, k=5)
            accs = [by[r["perfumeKey"]]["accords"] for r in recs]
            sa = main_accord(sk)
            kept = " · ".join(f"{ko[main_accord(m)]} {sum(1 for x in accs if main_accord(m) in x)}/5" for m, _ in mids)
            cells.append(f"1위 {sum(1 for a in accs if a[0] == sa)}/5 · 중 {kept}")
            if sk in ("musk", "patchouli"):
                contain[sk] = sum(1 for r in recs if has_note(r["perfumeKey"], sk))
        combo_rows[label] = cells

        def rep(picks):
            hit = 0
            for p, notes in picks:
                gs = [by[r["perfumeKey"]]["group"] for r in eng.recommend([E.BlendNote(k, l, 1.0) for k, l in notes], k=10)]
                hit += p["group"] in gs and gs.index(p["group"]) < 3
            return hit

        spot = eng.recommend(V.blend(V.BLIND_SPOT), k=5)
        cf = sum(1 for r in spot if {"citrus", "floral"} <= {fam[a] for a in by[r["perfumeKey"]]["accords"]})
        cfirst = sum(1 for r in spot if by[r["perfumeKey"]]["accords"][0] == "citrus")
        brands, changed = set(), 0
        for name, s in V.BLENDS:
            rs = eng.recommend(V.blend(s), k=10)
            brands |= {by[r["perfumeKey"]]["brand"] for r in rs}
            changed += len({r["perfumeKey"] for r in rs[:5]} - set(v4[name]))
        quality_rows.append(f"| {label} | {contain['musk']}/5 | {contain['patchouli']}/5 | {rep(p20)}/20 | {rep(p100)}/100 "
                            f"| {cf}/5 | {cfirst} | {len(brands)} | {changed}/50 |")
        print(quality_rows[-1])
    E.NOTE_MATCH_BY_INTENSITY = False

    out = ["# '강'을 노트 겹침에도 반영하는 시험", "",
           "> `py pipeline/experiments/strong_both.py` (2026-10-31). 엔진 v5(몫 빼기 프로필, 복사 오류 제외, 어코드 0.35 : 노트 0.55). "
           "'어코드만' = 지금 엔진(강은 조합 어코드 쪽만). '양쪽' = 노트 일치도에서도 일치한 노트를 강도(약 0.5 / 중 1.0 / 강)로 가중.",
           "> 대표 어코드는 v5 프로필에서 가장 큰 칸(예: 머스크가 이제 플로럴이 아님).", "",
           "## 품질", "",
           "| 설정 | 머스크 강 Top 5에 머스크가 든 향수 | 패출리 강 Top 5에 패출리가 든 향수 | 재현 20 (기준 15) | 재현 100 | 맹점 (기준 4) "
           "| 맹점 시트러스 1위 | 다양성 브랜드 (기준 40) | 블라인드 조합 Top 5 중 v4에 없던 향수 |",
           "|---|---|---|---|---|---|---|---|---|", *quality_rows, "",
           "## '강' 조합 6개 (강 1위 = Top 5 중 강 노트의 대표 어코드가 1위인 향수 · 중 = 중 노트 대표 어코드를 가진 향수)", "",
           "| 조합 | " + " | ".join(combo_rows) + " |", "|---|" + "---|" * len(combo_rows)]
    for i, (name, _, (sk, _), mids, _) in enumerate(COMBOS):
        head = f"{name} (강 {cat_name(cat, sk)} → {ko[main_accord(sk)]})"
        out.append(f"| {head} | " + " | ".join(cells[i] for cells in combo_rows.values()) + " |")
    out.append("")
    (APP / "docs" / "engine_strong_both.md").write_text("\n".join(out), encoding="utf-8")


def cat_name(cat, key):
    return next(n["nameKo"] for n in cat["notes"] if n["key"] == key)


if __name__ == "__main__":
    main()
