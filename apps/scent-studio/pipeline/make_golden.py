"""골든 파일: Python 참조 엔진이 가짜 카탈로그로 계산한 기대 결과 → tests/golden/recommend_cases.json
TypeScript 엔진 골든 테스트(app/tests/unit/engine.golden.test.ts)가 이 파일과 순위가 같고 점수 차 1e-9 이하인지 확인한다 (docs/design.md 14절).

앱 폴더에서:  py pipeline/build_catalog.py --fixture  다음에  py pipeline/make_golden.py
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from engine_ref import BlendNote, Engine, intensity  # noqa: E402

APP = Path(__file__).resolve().parent.parent
CATALOG = APP / "tests" / "fixtures" / "test_catalog.json.gz"
OUT = APP / "tests" / "golden" / "recommend_cases.json"

A3 = [("lemon", "top", "strong"), ("rose", "middle", "medium"), ("sandalwood", "base", "weak")]
BLIND_SPOT = [("lemon", "top", "strong"), ("osmanthus", "middle", "medium"), ("sandalwood", "base", "weak")]

CASES = [
    {"name": "A3 조합", "blend": A3},
    {"name": "맹점 조합", "blend": BLIND_SPOT},
    {"name": "불호 패출리", "blend": A3, "disliked": ["patchouli"]},
    {"name": "선호 플로럴", "blend": A3, "preferred": ["floral"]},
    {"name": "선호 둘 + 불호 둘", "blend": BLIND_SPOT, "preferred": ["woody", "citrus"], "disliked": ["musk", "oud"]},
    {"name": "노트 하나", "blend": [("bergamot", "top", "medium")]},
    {"name": "베이스 비고 우디·그린", "blend": [("cardamom", "top", "weak"), ("lavender", "middle", "strong"), ("cedarwood", "middle", "medium")]},
    {"name": "층마다 3개", "blend": [
        ("lemon", "top", "strong"), ("bergamot", "top", "medium"), ("grapefruit", "top", "weak"),
        ("rose", "middle", "medium"), ("jasmine", "middle", "medium"), ("osmanthus", "middle", "weak"),
        ("sandalwood", "base", "medium"), ("musk", "base", "medium"), ("vanilla", "base", "strong")]},
    {"name": "검색 전용 노트", "blend": [("hinoki", "middle", "medium"), ("vetiver", "base", "medium")]},
    {"name": "멀리 떨어진 두 계열", "blend": [("lemon", "top", "medium"), ("amber", "base", "medium")]},
    {"name": "전체 순위", "blend": [("lemon", "top", "medium")], "k": 100},
]

ERRORS = [
    {"name": "빈 조합", "blend": [], "error": "EMPTY_BLEND"},
    {"name": "한 층에 4개", "blend": [("lemon", "top", "medium"), ("bergamot", "top", "medium"),
                                    ("grapefruit", "top", "medium"), ("mandarin orange", "top", "medium")], "error": "LAYER_LIMIT"},
    {"name": "같은 노트 두 번", "blend": [("lemon", "top", "medium"), ("lemon", "middle", "medium")], "error": "DUPLICATE_NOTE"},
    {"name": "고를 수 없는 노트", "blend": [("tuberose", "middle", "medium")], "error": "NOTE_NOT_FOUND"},
    {"name": "없는 노트", "blend": [("unobtainium", "middle", "medium")], "error": "NOTE_NOT_FOUND"},
    {"name": "잘못된 강도 (예전 강 1.5)", "blend": [("lemon", "top", 1.5)], "error": "BAD_BLEND_NOTE"},
]


def to_blend(spec):
    return [BlendNote(k, layer, intensity(s)) for k, layer, s in spec]


def blend_json(spec):
    return [{"noteKey": k, "layer": layer, "intensity": intensity(s)} for k, layer, s in spec]


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
