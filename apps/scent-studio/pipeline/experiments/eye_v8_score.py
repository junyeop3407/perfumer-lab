"""9단계 눈으로 평가 점수 채우기와 집계 (규칙: docs/engine_eye_v8_rules.md).

앱 폴더에서:
  py pipeline/experiments/eye_v8_score.py fill --scores "1=5 5 4 3 5;2=5 3 5 5 4;..."
  py pipeline/experiments/eye_v8_score.py tally

출력은 번호와 점수뿐이다. 향수 이름은 출력하지 않는다. 이미 점수가 있는 칸은 덮어쓰지 않는다.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import eye_v8 as EY  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
COMBOS = APP / "docs" / "engine_eye_v8_combos.json"
SHEET = APP / "pipeline" / "data" / "out" / "eye_v8_sheet.md"


def fill(scores: str) -> None:
    parsed = {int(no): [int(x) for x in rest.split()] for no, rest in (part.split("=") for part in scores.split(";"))}
    SHEET.write_text(EY.fill_scores(SHEET.read_text(encoding="utf-8"), parsed), encoding="utf-8", newline="\n")
    print("채움", len(parsed), "조합", sum(len(v) for v in parsed.values()), "칸")


def tally() -> None:
    combos = json.loads(COMBOS.read_text(encoding="utf-8"))["combos"]
    result = EY.tally(EY.read_scores(SHEET.read_text(encoding="utf-8")), combos)
    for no, mean in result["combos"].items():
        print(f"{no}: {mean:.2f}")
    for name in ("base", "new_gourmand", "all_gourmand", "all"):
        print(f"{name}: {result[name]:.4f}")
    print("순위별:", " ".join(f"{i + 1}위 {v:.2f}" for i, v in enumerate(result["ranks"])))
    print("판정:", result["verdict"])


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("what", choices=["fill", "tally"])
    ap.add_argument("--scores")
    a = ap.parse_args()
    fill(a.scores) if a.what == "fill" else tally()


if __name__ == "__main__":
    main()
