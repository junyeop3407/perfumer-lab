"""8단계 블라인드 판정 채우기와 채점 (규칙: docs/engine_blind_v8_rules.md).

앱 폴더에서:
  py pipeline/experiments/blind_v8_score.py fill --picks "2:B,3:비슷함,..." --reason "..."
  (정답 키를 표준 입력으로) | py pipeline/experiments/blind_v8_score.py score --low 13,16,19,26

정답 키는 표준 입력으로만 받아 파일로 남기지 않는다. 정답 키와 조합별 승자는 출력하지 않고 합계만 출력한다.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import blind_v8 as BL  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
COMBOS = APP / "docs" / "engine_blind_v8_combos.json"
SHEET = APP / "pipeline" / "data" / "out" / "blind_v8_sheet.md"


def fill(picks: str, reason: str) -> None:
    text = SHEET.read_text(encoding="utf-8")
    if "☑" in text:
        raise SystemExit("이미 판정이 채워져 있습니다. 덮어쓰지 않습니다.")
    parsed = {int(n): c for n, c in (p.split(":") for p in picks.split(","))}
    if set(parsed.values()) - set(BL.CHOICES):
        raise SystemExit("판정은 A, B, 비슷함만 됩니다")
    SHEET.write_text(BL.fill_sheet(text, parsed, reason), encoding="utf-8", newline="\n")
    print("채움", dict(Counter(BL.read_judgements(SHEET.read_text(encoding="utf-8")).values())))


def _count(won: dict[int, str]) -> tuple[int, int]:
    return sum(w == "v8" for w in won.values()), sum(w == "v7" for w in won.values())


def _line(label: str, won: dict[int, str]) -> None:
    v8, v7 = _count(won)
    print(f"{label}: 유효 {v8 + v7} | v8 {v8} : v7 {v7} | p={BL.sign_p(v8, v7):.6f} | {BL.verdict(v8, v7)}")


def score(low: list[int]) -> None:
    judged = BL.read_judgements(SHEET.read_text(encoding="utf-8"))
    print("판정", dict(Counter(judged.values())), "칸", len(judged))
    won = BL.winners(judged, json.load(sys.stdin))
    _line("전체", won)
    bucket = {c["no"]: c["bucket"] for c in json.loads(COMBOS.read_text(encoding="utf-8"))["combos"]}
    for b in BL.BUCKETS:
        v8, v7 = _count({n: w for n, w in won.items() if bucket[n] == b})
        same = sum(bucket[n] == b and j == "비슷함" for n, j in judged.items())
        print(f"  {b}: v8 {v8} : v7 {v7} | 비슷함 {same}")
    rest = {n: w for n, w in won.items() if n not in low}
    _line(f"확신 낮은 {len(low)}개 뺌", rest)
    # 번호를 받지 못한 판정이 어느 것이든 결론이 바뀌는지 본다. 어떤 조합인지는 출력하지 않는다.
    outcomes = Counter()
    for extra in set(judged) - set(low):
        v8, v7 = _count({n: w for n, w in rest.items() if n != extra})
        outcomes[(v8, v7, round(BL.sign_p(v8, v7), 6), BL.verdict(v8, v7))] += 1
    print("한 개 더 뺄 때 가능한 결과 (v8, v7, p, 결론): 경우 수")
    for k, n in sorted(outcomes.items()):
        print(" ", k, n)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stdin.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("what", choices=["fill", "score"])
    ap.add_argument("--picks")
    ap.add_argument("--reason")
    ap.add_argument("--low", default="")
    a = ap.parse_args()
    if a.what == "fill":
        fill(a.picks, a.reason)
    else:
        score([int(n) for n in a.low.split(",") if n])


if __name__ == "__main__":
    main()
