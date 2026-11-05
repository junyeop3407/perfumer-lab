"""v7 vs v6 블라인드 비교에 넣을 조합 고르기 (2026-11-02). 엔진은 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/pick_v6_combos.py   (실제 카탈로그·원본 CSV 필요)
만드는 파일: docs/engine_v6_combo_pick.md (후보마다 두 엔진 Top 5가 몇 개 다른지만. 추천 목록은 보이지 않는다)

규칙: 예전 v6 vs v3 문서(engine_blind_vs_v3.md)의 조합은 쓰지 않는다. 그 문서를 본 적이 있으면 v6 목록을 알아볼 수 있어
A/B가 드러나기 때문이다. 기준선 조합 중 그 문서에 없던 6개를 먼저 보고, 결과를 보기 전에 적어 둔 후보(pick_v3_combos.CANDIDATES)를
적은 순서대로 이어 본다. 두 엔진 Top 5가 2개 이상 다른 것만 남기고, 패출리가 든 조합 1개를 채운 뒤 적은 순서대로 12개를 고른다.
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_rules import load_old_catalog  # noqa: E402
import evaluate as V  # noqa: E402
from engine_ref import Engine  # noqa: E402
from pick_v3_combos import CANDIDATES  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = load_old_catalog(APP / "pipeline" / "data" / "out" / "catalog.json.gz")
    eng = Engine(cat)
    eng6, _, with_v6 = V.v6_engine()
    shown = {name for name, _ in V.v3_combos()}
    first = [c for c in V.BLENDS if c[0] not in shown]
    rows = []
    for name, spec in first + [c for c in CANDIDATES if c[0] not in shown]:
        try:
            a = {r["perfumeKey"] for r in eng.recommend(V.blend(spec), k=5)}
            b = {r["perfumeKey"] for r in with_v6(lambda: eng6.recommend(V.blend(spec), k=5))}
        except ValueError as e:
            rows.append({"name": name, "spec": spec, "diff": None, "why": str(e)})
            continue
        rows.append({"name": name, "spec": spec, "diff": len(a - b), "why": ""})
    ok = [r for r in rows if r["diff"] is not None and r["diff"] >= 2]
    chosen = ok[:6] if all(r["name"] in {c[0] for c in first} for r in ok[:6]) else [r for r in ok if r["name"] in {c[0] for c in first}]
    if not any(k == "patchouli" for r in chosen for k, _, _ in r["spec"]):
        chosen.append(next(r for r in ok if r not in chosen and any(k == "patchouli" for k, _, _ in r["spec"])))
    for r in ok:
        if len(chosen) == 12:
            break
        if r not in chosen:
            chosen.append(r)
    names = {r["name"] for r in chosen}
    lines = ["# v7 vs v6 블라인드 조합 고르기", "", "> `py pipeline/experiments/pick_v6_combos.py`로 만든 문서. 추천 목록은 보지 않고 다른 향수 수만 셌습니다.", "",
             "| 후보 | Top 5 중 다른 향수 | 고름 |", "|---|---:|---|",
             *[f"| {r['name']} | {r['diff'] if r['diff'] is not None else '쓸 수 없음 ' + r['why']} | {'✓' if r['name'] in names else ''} |" for r in rows], ""]
    (APP / "docs" / "engine_v6_combo_pick.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"고름 {len(chosen)}개")
    print(json.dumps([[r["name"], r["spec"]] for r in chosen], ensure_ascii=False))


if __name__ == "__main__":
    main()
