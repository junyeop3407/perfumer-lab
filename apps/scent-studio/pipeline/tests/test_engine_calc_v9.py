"""계산 버전 9(㉮, 2026-11-13 사장 결정): 노트 일치는 고른 노트 중 가장 무거운 4개까지 다 맞으면 만점. 4개 이하면 그대로.
앱 app/tests/unit/engineCalcV9.test.ts와 같은 사례. 카탈로그 버전(ENGINE_VERSION 8, build_catalog)은 그대로.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_catalog as B  # noqa: E402
import engine_ref as E  # noqa: E402


def _mini():
    def note(key, df):
        return {"key": key, "nameEn": key, "nameKo": key, "family": "citrus", "df": df, "tier": "browse",
                "profile": [1, 0], "pairings": [], "description": ""}

    def perfume(key, top, middle, base):
        return {"key": key, "brand": f"B{key}", "name": key, "year": None, "concentration": None, "rating": None,
                "ratingCount": 1, "accords": ["citrus"], "top": top, "middle": middle, "base": base, "group": key}

    return {"version": "test", "engineVersion": 8, "source": {"name": "test", "note": ""},
            "families": [{"code": "citrus", "nameKo": "시트러스", "wheelOrder": 1}],
            "accords": [{"key": "citrus", "nameKo": "시트러스", "family": "citrus"}, {"key": "woody", "nameKo": "우디", "family": "citrus"}],
            "notes": [note("a", 1), note("b", 1), note("c", 1), note("d", 1), note("e", 4), note("f", 4)],
            "perfumes": [perfume("P1", ["a", "b", "c"], ["d"], []), perfume("P2", ["e"], ["f"], []),
                         perfume("P3", [], [], ["a"]), perfume("P4", [], [], ["e"])]}


SIX = [E.BlendNote("a", "top", 1.0), E.BlendNote("b", "top", 1.0), E.BlendNote("c", "top", 1.0),
       E.BlendNote("d", "middle", 1.0), E.BlendNote("e", "middle", 1.0), E.BlendNote("f", "base", 1.0)]


def test_계산_버전_9와_카탈로그_버전_8은_따로():
    assert E.CALC_VERSION == 9
    assert E.NOTE_FULL_COUNT == 4
    assert B.ENGINE_VERSION == 8


def test_노트_6개에서_가장_무거운_4개를_다_가지면_노트_일치_만점():
    recs = E.Engine(_mini()).recommend(SIX, k=4)
    p1 = next(r for r in recs if r["perfumeKey"] == "P1")
    assert abs(p1["noteMatch"] - 1.0) < 1e-12
