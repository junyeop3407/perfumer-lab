"""어코드 : 노트 가중치 비교 (2026-10-31 사장 요청: 재현 실패 원인 = 노트는 다 맞는데 어코드가 달라 밀림)

앱 폴더에서:  py pipeline/experiments/weight_grid.py   (실제 카탈로그 pipeline/data/out 필요)
만드는 파일: docs/engine_weights.md
참조 엔진(engine_ref)의 가중치만 메모리에서 바꿔 비교한다. 선호 계열 0.1은 그대로, 어코드+노트 = 0.9.
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
# (어코드, 노트, '강' 값). 첫 줄이 지금 엔진 v3 (비교 기준)
CONFIGS = [(0.45, 0.45, 2.0), (0.40, 0.50, 2.0), (0.35, 0.55, 2.0), (0.35, 0.55, 2.5), (0.35, 0.55, 3.0),
           (0.30, 0.60, 2.0), (0.25, 0.65, 2.0), (0.20, 0.70, 2.0)]


def picks(cat, eng, n):
    """평가 많은 향수 n개(브랜드 겹치지 않게)와 그 노트 (evaluate.py 재현과 같은 고르기, 같은 노트는 한 번만)"""
    out, brands = [], set()
    for p in sorted(cat["perfumes"], key=lambda p: (-p["ratingCount"], p["key"])):
        seen: set[str] = set()
        notes = [(k, l) for l in ("top", "middle", "base") for k in p[l][:3]
                 if eng.notes[k]["tier"] != "hidden" and not (k in seen or seen.add(k))]
        if p["brand"] in brands or not notes:
            continue
        out.append((p, notes))
        brands.add(p["brand"])
        if len(out) == n:
            break
    return out


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = json.load(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rt", encoding="utf-8"))
    eng = E.Engine(cat)
    by = {p["key"]: p for p in cat["perfumes"]}
    fam = {a["key"]: a["family"] for a in cat["accords"]}
    p20, p100 = picks(cat, eng, 20), picks(cat, eng, 100)
    note_only = {name: {r["perfumeKey"] for r in eng.recommend(V.blend(s), k=5, note_only=True)} for name, s in V.BLENDS}
    base_top5 = None

    rows = []
    for wa, wn, strong in CONFIGS:
        E.W_ACCORD, E.W_NOTE = wa, wn
        E.INTENSITIES = (0.5, 1.0, strong)

        def rank_of(p, notes):
            gs = [by[r["perfumeKey"]]["group"] for r in eng.recommend([E.BlendNote(k, l, 1.0) for k, l in notes], k=10)]
            return gs.index(p["group"]) + 1 if p["group"] in gs else 99

        r20 = [rank_of(p, n) for p, n in p20]
        r100 = [rank_of(p, n) for p, n in p100]
        spot = eng.recommend(V.blend(V.BLIND_SPOT), k=5)
        cf = sum(1 for r in spot if {"citrus", "floral"} <= {fam[a] for a in by[r["perfumeKey"]]["accords"]})
        cf3 = sum(1 for r in spot if {"citrus", "floral"} <= {fam[a] for a in by[r["perfumeKey"]]["accords"][:3]})
        cfirst = sum(1 for r in spot if by[r["perfumeKey"]]["accords"][0] == "citrus")
        brands, overlap_note, top5 = set(), [], {}
        for name, s in V.BLENDS:
            rs = eng.recommend(V.blend(s), k=10)
            brands |= {by[r["perfumeKey"]]["brand"] for r in rs}
            top5[name] = {r["perfumeKey"] for r in rs[:5]}
            overlap_note.append(len(top5[name] & note_only[name]) / 5)
        if base_top5 is None:
            base_top5 = top5
        changed = sum(len(top5[n] - base_top5[n]) for n in top5)
        # 등급 기준값(v3 규칙): 향수 100개의 노트를 그대로 넣었을 때 그 향수 자신의 점수 중앙값·하위 25%
        selfs = []
        for p, n in p100:
            own = {by[r["perfumeKey"]]["group"]: r["score"] for r in eng.recommend([E.BlendNote(k, l, 1.0) for k, l in n], k=200)}
            if p["group"] in own:
                selfs.append(own[p["group"]])
        fails = [p["name"] for (p, _), rk in zip(p20, r20) if rk > 3]
        rows.append((wa, wn, strong, changed, sum(rk <= 3 for rk in r20), sum(rk <= 3 for rk in r100), sum(rk <= 10 for rk in r100),
                     cf, cf3, cfirst, len(brands), statistics.mean(overlap_note),
                     round(statistics.median(selfs), 2), round(statistics.quantiles(selfs, n=4)[0], 2), fails))
        print(f"{wa}/{wn} 강{strong} 재현20 {rows[-1][4]} 재현100 {rows[-1][5]} 시트러스1위 {cfirst} 블라인드 Top5 변화 {changed}/50")

    out = ["# 어코드 : 노트 가중치 비교", "",
           "> `py pipeline/experiments/weight_grid.py` (2026-10-31). 선호 계열 0.1은 그대로, 어코드 + 노트 = 0.9. 엔진 v3 프로필·강 2.0 그대로.", "",
           "첫 줄이 지금 엔진 v3. '블라인드 조합 Top 5 변화' = 블라인드 비교 조합 10개의 Top 5(50개) 중 v3에 없던 향수 수.", "",
           "| 어코드 : 노트 | 강 | 재현 20 (기준 15, Top 3) | 재현 100 (Top 3) | 재현 100 (Top 10) | 맹점 (전체 / 상위 3) | 맹점 시트러스 1위 | 블라인드 조합 Top 5 변화 | 다양성 브랜드 (기준 40) | 노트만 방식과 Top 5 겹침 (블라인드 조합 10개 × 5) | 등급 기준(자기 점수 중앙값 / 하위 25%) |",
           "|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in rows:
        out.append(f"| {r[0]:.2f} : {r[1]:.2f} | {r[2]} | {r[4]}/20 | {r[5]}/100 | {r[6]}/100 | {r[7]}/5 · {r[8]}/5 | {r[9]} | {r[3]}/50 | {r[10]} | {round(r[11] * 50)}/50 ({r[11]:.0%}) | {r[12]} / {r[13]} |")
    out += ["", "## 재현 20에서 실패한 향수", ""]
    for r in rows:
        out.append(f"- {r[0]:.2f} : {r[1]:.2f} · 강 {r[2]} — {', '.join(r[14]) or '없음'}")
    out.append("")
    (APP / "docs" / "engine_weights.md").write_text("\n".join(out), encoding="utf-8")


if __name__ == "__main__":
    main()
