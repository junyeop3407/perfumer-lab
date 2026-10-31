"""등급 기준값 다시 재기 (2026-10-31 사장 요청). 기준값은 바꾸지 않고 비교만 한다.

앱 폴더에서:  py pipeline/experiments/grade_check.py   (실제 카탈로그 필요)
만드는 파일: docs/engine_grades.md
규칙(v3부터): 평가 많은 향수 100개(재현 정답과 같은 고르기)의 노트를 그대로 넣었을 때 그 향수 자신의 점수 →
매우 비슷 = 중앙값, 비슷 = 하위 25%.
"""
from __future__ import annotations

import gzip
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = json.load(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rt", encoding="utf-8"))
    eng = E.Engine(cat)
    by = {p["key"]: p for p in cat["perfumes"]}
    selfs = []
    for p, notes in V.reproduction_picks(cat, eng, 100):
        own = {by[r["perfumeKey"]]["group"]: r["score"]
               for r in eng.recommend([E.BlendNote(k, l, 1.0) for k, l in notes], k=200)}
        if p["group"] in own:
            selfs.append(own[p["group"]])
    new_vs, new_s = round(statistics.median(selfs), 2), round(statistics.quantiles(selfs, n=4)[0], 2)

    combos = V.BLENDS[:10]  # 사장 요청: 블라인드 조합 10개(기존)
    tops = [eng.recommend(V.blend(s), k=10) for _, s in combos]
    rows = []
    for name, (vs, s) in (("지금", (E.GRADE_VERY_SIMILAR, E.GRADE_SIMILAR)), ("새 규칙 값", (new_vs, new_s))):
        g = lambda x: "VS" if x >= vs else "S" if x >= s else "R"  # noqa: E731
        g5 = [g(r["score"]) for recs in tops for r in recs[:5]]
        g10 = [g(r["score"]) for recs in tops for r in recs]
        none = sum(1 for recs in tops if all(g(r["score"]) != "VS" for r in recs))
        cnt = lambda gs: f"{gs.count('VS')} / {gs.count('S')} / {gs.count('R')}"  # noqa: E731
        rows.append(f"| {name}: {vs} / {s} | {cnt(g5)} | {cnt(g10)} | {none} |")
    out = ["# 등급 기준값 다시 재기", "",
           f"> `py pipeline/experiments/grade_check.py` (2026-10-31). 엔진 v{cat['engineVersion']}. 기준값은 바꾸지 않았다.", "",
           f"- 규칙: 평가 많은 향수 100개의 노트를 그대로 넣었을 때 그 향수 자신의 점수({len(selfs)}개) → "
           f"중앙값 {statistics.median(selfs):.3f} · 하위 25% {statistics.quantiles(selfs, n=4)[0]:.3f} → **{new_vs} / {new_s}**", "",
           "## 블라인드 조합 10개의 등급 분포 (매우 비슷 / 비슷 / 참고)", "",
           "| 기준 | Top 5 (50개) | Top 10 (100개) | 매우 비슷이 하나도 없는 조합 |", "|---|---|---|---|", *rows, ""]
    (APP / "docs" / "engine_grades.md").write_text("\n".join(out), encoding="utf-8")
    print("\n".join(out[3:]))


if __name__ == "__main__":
    main()
