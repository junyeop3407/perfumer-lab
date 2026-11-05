"""9단계 눈으로 평가 만들기 (규칙: docs/engine_eye_v8_rules.md).

앱 폴더에서:
  py pipeline/experiments/eye_v8_make.py combos   조합 고정: docs/engine_eye_v8_combos.json (추천 계산 안 함)
  py pipeline/experiments/eye_v8_make.py sheet    시트: pipeline/data/out/eye_v8_sheet.md (커밋 안 함)

출력은 조합과 개수만이다. 추천 목록은 출력하지 않는다. 이미 있는 조합·시트는 덮어쓰지 않는다.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blind_v8_make as MK  # noqa: E402
import engine_ref as E  # noqa: E402
import engine_v8_trial as T  # noqa: E402
import evaluate as V  # noqa: E402
import eye_v8 as EY  # noqa: E402

COMBOS = MK.APP / "docs" / "engine_eye_v8_combos.json"
SHEET = MK.OUT / "eye_v8_sheet.md"
BASE_COUNT, GOURMAND_COUNT = 10, 5
REFERENCE = (8, 24, 36)


def combos() -> None:
    if COMBOS.exists():
        raise SystemExit(f"{COMBOS.name}가 이미 있습니다. 조합은 고정이라 덮어쓰지 않습니다.")
    cat = MK.load("catalog.json.gz")
    family = {n["key"]: n["family"] for n in cat["notes"]}
    blind = json.loads(MK.COMBOS.read_text(encoding="utf-8"))["combos"]
    used = {frozenset(k for k, _, _ in c[1]) for c in [*V.BLENDS, *V.V6_COMPARE_COMBOS, *V.v3_combos(), ("맹점", V.BLIND_SPOT), *T.EXTRA_FRESH]}
    used |= {frozenset(k for k, _, _ in c["notes"]) for c in blind}
    base = [[list(n) for n in spec] for _, spec in V.BLENDS[:BASE_COUNT]]
    new = EY.gourmand_combos(cat, GOURMAND_COUNT, used)
    out = [{"no": i + 1, "kind": kind, "bucket": EY.combo_bucket([k for k, _, _ in notes], family), "notes": notes}
           for i, (kind, notes) in enumerate([*(("기본", n) for n in base), *(("구르망", n) for n in new)])]
    COMBOS.write_text(json.dumps({"seed": EY.SEED, "combos": out}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    note_ko = {n["key"]: n["nameKo"] for n in cat["notes"]}
    for c in out:
        print(c["no"], c["kind"], c["bucket"], " · ".join(note_ko[k] for k, _, _ in c["notes"]))


def sheet() -> None:
    if SHEET.exists():
        raise SystemExit("시트가 이미 있습니다. 점수를 적었을 수 있으므로 덮어쓰지 않습니다.")
    cat = MK.load("catalog.json.gz")
    assert cat["engineVersion"] == 8
    eng = E.Engine(cat)
    top5 = lambda notes: [r["perfumeKey"] for r in eng.recommend(V.blend([tuple(n) for n in notes]), k=5)]  # noqa: E731
    scored = [{"no": c["no"], "notes": c["notes"], "keys": top5(c["notes"])} for c in json.loads(COMBOS.read_text(encoding="utf-8"))["combos"]]
    blind = {c["no"]: c for c in json.loads(MK.COMBOS.read_text(encoding="utf-8"))["combos"]}
    reference = [{"no": n, "notes": blind[n]["notes"], "keys": top5(blind[n]["notes"])} for n in REFERENCE]
    SHEET.write_text(EY.sheet_text(scored, reference, cat), encoding="utf-8", newline="\n")
    print("시트", SHEET.relative_to(MK.APP), "| 평가", len(scored), "| 참고", len(reference))


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("what", choices=["combos", "sheet"])
    a = ap.parse_args()
    combos() if a.what == "combos" else sheet()


if __name__ == "__main__":
    main()
