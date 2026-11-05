"""8단계 블라인드 만들기 (규칙: docs/engine_blind_v8_rules.md).

앱 폴더에서:
  py pipeline/experiments/blind_v8_make.py combos                 조합 고정: docs/engine_blind_v8_combos.json (추천 계산 안 함)
  py pipeline/experiments/blind_v8_make.py sheet --key-out <경로>  판정 시트: pipeline/data/out/blind_v8_sheet.md + 정답 키(비공개 백업 안의 경로)

출력은 개수만이다. 정답 키와 v7·v8 비교 결과는 출력하지 않는다. 이미 있는 조합·시트는 덮어쓰지 않는다.
A/B 배정은 고정 시드 없이 운영체제 난수로 정한다(공개된 코드로 다시 계산할 수 없게).
"""
from __future__ import annotations

import argparse
import gzip
import json
import secrets
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blind_v8 as BL  # noqa: E402
import engine_ref as E  # noqa: E402
import engine_v8_trial as T  # noqa: E402
import evaluate as V  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
COMBOS = APP / "docs" / "engine_blind_v8_combos.json"
OUT = APP / "pipeline" / "data" / "out"
SHEET = OUT / "blind_v8_sheet.md"
SHOWN = ("key", "brand", "name", "top", "middle", "base", "accords")


def load(name: str) -> dict:
    with gzip.open(OUT / name, "rt", encoding="utf-8") as f:
        return json.load(f)


def combos() -> None:
    if COMBOS.exists():
        raise SystemExit(f"{COMBOS.name}가 이미 있습니다. 조합은 고정이라 덮어쓰지 않습니다.")
    v7, v8 = load("catalog_v7_compare.json.gz"), load("catalog.json.gz")
    meta = lambda c: {n["key"]: (n["family"], n["tier"]) for n in c["notes"]}  # noqa: E731
    assert meta(v7) == meta(v8), "두 카탈로그의 노트 분류가 다릅니다"
    used = {frozenset(k for k, _, _ in c[1]) for c in [*V.BLENDS, *V.V6_COMPARE_COMBOS, *V.v3_combos(), ("맹점", V.BLIND_SPOT), *T.EXTRA_FRESH]}
    out = BL.generate(v7, 10, used)
    COMBOS.write_text(json.dumps({"seed": BL.SEED, "combos": out}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    print("조합", len(out), dict(Counter(c["bucket"] for c in out)))


def sheet(key_out: Path) -> None:
    if SHEET.exists() or key_out.exists():
        raise SystemExit("시트나 정답 키가 이미 있습니다. 판정 중에 A/B가 바뀌면 안 되므로 덮어쓰지 않습니다.")
    data = json.loads(COMBOS.read_text(encoding="utf-8"))["combos"]
    versions = {"v7": load("catalog_v7_compare.json.gz"), "v8": load("catalog.json.gz")}
    assert versions["v7"]["engineVersion"] == 7 and versions["v8"]["engineVersion"] == 8
    # 표에 나가는 칸이 두 카탈로그에서 같아야 버전이 드러나지 않는다
    shown = {v: {p["key"]: tuple(str(p[f]) for f in SHOWN) for p in c["perfumes"]} for v, c in versions.items()}
    assert shown["v7"] == shown["v8"], "표에 나가는 칸이 두 카탈로그에서 다릅니다"
    engines = {v: E.Engine(c) for v, c in versions.items()}
    entries, key, auto = [], {}, 0
    for c in data:
        blend = V.blend([tuple(n) for n in c["notes"]])
        lists = {v: [r["perfumeKey"] for r in eng.recommend(blend, k=5)] for v, eng in engines.items()}
        first = secrets.choice(("v7", "v8"))
        second = "v8" if first == "v7" else "v7"
        key[str(c["no"])] = {"A": first, "B": second}
        auto += lists["v7"] == lists["v8"]
        entries.append({"no": c["no"], "notes": c["notes"], "A": lists[first], "B": lists[second]})
    key_out.parent.mkdir(parents=True, exist_ok=True)
    key_out.write_text(json.dumps(key, indent=0) + "\n", encoding="utf-8", newline="\n")
    SHEET.write_text(BL.sheet_text(entries, versions["v8"]), encoding="utf-8", newline="\n")
    print("시트", SHEET.relative_to(APP), "| 조합", len(entries), dict(Counter(c["bucket"] for c in data)), "| 자동 비슷함", auto)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("what", choices=["combos", "sheet"])
    ap.add_argument("--key-out", type=Path)
    a = ap.parse_args()
    if a.what == "combos":
        combos()
    else:
        if not a.key_out:
            raise SystemExit("--key-out이 필요합니다")
        sheet(a.key_out)


if __name__ == "__main__":
    main()
