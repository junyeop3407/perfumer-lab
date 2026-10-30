"""골든 파일: Python 참조 엔진이 가짜 카탈로그로 계산한 기대 결과 → tests/golden/recommend_cases.json
Java engine 골든 테스트가 이 파일과 순위가 같고 점수 차 1e-9 이하인지 확인한다 (docs/design.md 14절).

앱 폴더에서:  py pipeline/build_catalog.py --fixture  다음에  py pipeline/make_golden.py
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from engine_ref import BlendNote, Engine  # noqa: E402

APP = Path(__file__).resolve().parent.parent
CATALOG = APP / "app" / "src" / "test" / "resources" / "catalog" / "test_catalog.json.gz"
OUT = APP / "tests" / "golden" / "recommend_cases.json"

A3 = [("lemon", "top", 1.5), ("rose", "middle", 1.0), ("sandalwood", "base", 0.5)]
BLIND_SPOT = [("lemon", "top", 1.5), ("osmanthus", "middle", 1.0), ("sandalwood", "base", 0.5)]

CASES = [
    {"name": "A3 조합", "blend": A3},
    {"name": "맹점 조합", "blend": BLIND_SPOT},
    {"name": "불호 패출리", "blend": A3, "disliked": ["patchouli"]},
    {"name": "선호 플로럴", "blend": A3, "preferred": ["floral"]},
    {"name": "선호 둘 + 불호 둘", "blend": BLIND_SPOT, "preferred": ["woody", "citrus"], "disliked": ["musk", "oud"]},
    {"name": "노트 하나", "blend": [("bergamot", "top", 1.0)]},
    {"name": "베이스 비고 우디·그린", "blend": [("cardamom", "top", 0.5), ("lavender", "middle", 1.5), ("cedarwood", "middle", 1.0)]},
    {"name": "층마다 3개", "blend": [
        ("lemon", "top", 1.5), ("bergamot", "top", 1.0), ("grapefruit", "top", 0.5),
        ("rose", "middle", 1.0), ("jasmine", "middle", 1.0), ("osmanthus", "middle", 0.5),
        ("sandalwood", "base", 1.0), ("musk", "base", 1.0), ("vanilla", "base", 1.5)]},
    {"name": "검색 전용 노트", "blend": [("hinoki", "middle", 1.0), ("vetiver", "base", 1.0)]},
    {"name": "멀리 떨어진 두 계열", "blend": [("lemon", "top", 1.0), ("amber", "base", 1.0)]},
    {"name": "전체 순위", "blend": [("lemon", "top", 1.0)], "k": 100},
]

ERRORS = [
    {"name": "빈 조합", "blend": [], "error": "EMPTY_BLEND"},
    {"name": "한 층에 4개", "blend": [("lemon", "top", 1.0), ("bergamot", "top", 1.0),
                                    ("grapefruit", "top", 1.0), ("mandarin orange", "top", 1.0)], "error": "LAYER_LIMIT"},
    {"name": "같은 노트 두 번", "blend": [("lemon", "top", 1.0), ("lemon", "middle", 1.0)], "error": "DUPLICATE_NOTE"},
    {"name": "고를 수 없는 노트", "blend": [("tuberose", "middle", 1.0)], "error": "NOTE_NOT_FOUND"},
    {"name": "없는 노트", "blend": [("unobtainium", "middle", 1.0)], "error": "NOTE_NOT_FOUND"},
    {"name": "잘못된 강도", "blend": [("lemon", "top", 2.0)], "error": "BAD_BLEND_NOTE"},
]


def to_blend(spec):
    return [BlendNote(k, layer, s) for k, layer, s in spec]


def blend_json(spec):
    return [{"noteKey": k, "layer": layer, "intensity": s} for k, layer, s in spec]


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    with gzip.open(CATALOG, "rt", encoding="utf-8") as f:
        eng = Engine(json.load(f))
    cases = []
    for c in CASES:
        b = to_blend(c["blend"])
        recs = eng.recommend(b, set(c.get("disliked", [])), set(c.get("preferred", [])), k=c.get("k", 10))
        cases.append({
            "name": c["name"],
            "blend": blend_json(c["blend"]),
            "disliked": c.get("disliked", []),
            "preferred": c.get("preferred", []),
            "k": c.get("k", 10),
            "displayRatios": [{"accord": a, "percent": p} for a, p in eng.display_ratios(b)],
            "balanceHints": eng.balance_hints(b),
            "recommendations": recs,
        })
    errors = [{"name": e["name"], "blend": blend_json(e["blend"]), "error": e["error"]} for e in ERRORS]
    for e in ERRORS:  # 기대한 오류가 정말 나는지 Python에서 먼저 확인
        try:
            eng.recommend(to_blend(e["blend"]))
        except ValueError as ex:
            assert str(ex) == e["error"], (e["name"], ex)
        else:
            raise AssertionError(f"{e['name']}: 오류가 나지 않음")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"catalogVersion": "test-1", "cases": cases, "errors": errors},
                              ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"골든: {OUT.relative_to(APP)} · 사례 {len(cases)}개 · 오류 사례 {len(errors)}개")


if __name__ == "__main__":
    main()
