"""엔진 v7: 어코드·노트 강도와 선호를 뺀 등급 (docs/engine_v2_trial.md, 사장 결정 2026-11-02)"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import pytest

PIPELINE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PIPELINE))
import build_catalog as B  # noqa: E402
import catalog_rules as R  # noqa: E402
import engine_ref as E  # noqa: E402


def perfume(key, top, **kw):
    return {"key": key, "brand": key, "name": key, "group": key, "ratingCount": 100, "accords": ["a", "b"],
            "top": top, "middle": [], "base": [], **kw}


def catalog(*perfumes):
    return {
        "accords": [{"key": "a", "family": "f1"}, {"key": "b", "family": "f2"}],
        "families": [{"code": "f1", "wheelOrder": 1}, {"code": "f2", "wheelOrder": 2}],
        "notes": [{"key": k, "family": "f1", "df": 2, "tier": "browse", "profile": [0.5, 0.5]} for k in ("x", "y", "z")],
        "perfumes": list(perfumes),
    }


X_TOP = [E.BlendNote("x", "top", 1.0)]


def test_어코드_강도가_있으면_5_4_3_2_1_대신_쓴다():
    eng = E.Engine(catalog(perfume("p", ["x"], accordShares={"a": 10.0, "b": 90.0}), perfume("q", ["x"])))
    assert eng.vec["p"] == pytest.approx([10.0, 90.0])
    assert eng.vec["q"] == pytest.approx([100 * 5 / 9, 100 * 4 / 9])


def test_고른_노트가_강한_향수가_더_높다():
    eng = E.Engine(catalog(perfume("weak", ["x", "y"], noteWeights={"x": 0.1, "y": 0.9}),
                           perfume("strong", ["x", "y"], noteWeights={"x": 0.9, "y": 0.1})))
    recs = {r["perfumeKey"]: r for r in eng.recommend(X_TOP)}
    assert recs["strong"]["noteMatch"] == pytest.approx(0.3 + 0.7 * math.sqrt(0.9))
    assert recs["weak"]["noteMatch"] == pytest.approx(0.3 + 0.7 * math.sqrt(0.1))


def test_강도가_없는_향수는_같은_몫으로_계산한다():
    eng = E.Engine(catalog(perfume("p", ["x", "y", "z"])))
    assert eng.recommend(X_TOP)[0]["noteMatch"] == pytest.approx(0.3 + 0.7 * math.sqrt(1 / 3))


def test_노트가_많을수록_일치도가_낮아진다():
    eng = E.Engine(catalog(perfume("few", ["x"]), perfume("many", ["x", "y", "z"])))
    recs = {r["perfumeKey"]: r for r in eng.recommend(X_TOP)}
    assert recs["few"]["noteMatch"] == pytest.approx(1.0)
    assert recs["many"]["noteMatch"] < recs["few"]["noteMatch"]


def test_없는_노트는_강도와_상관없이_0():
    eng = E.Engine(catalog(perfume("p", ["y"], noteWeights={"y": 1.0})))
    eng.notes["x"]["family"] = "f2"  # 같은 계열 점수도 받지 않게
    assert eng.recommend(X_TOP)[0]["noteMatch"] == 0.0


def test_선호를_켜도_등급과_유사도는_그대로이고_순위_점수만_바뀐다():
    eng = E.Engine(catalog(perfume("p", ["x"])))
    off = eng.recommend(X_TOP)[0]
    on = eng.recommend(X_TOP, preferred={"f1"})[0]
    assert on["similarity"] == pytest.approx(off["similarity"])
    assert on["grade"] == off["grade"]
    assert on["score"] != pytest.approx(off["score"])
    assert off["score"] == pytest.approx(off["similarity"])


def test_노트만_보는_기준선은_강도를_쓰지_않는다():
    eng = E.Engine(catalog(perfume("p", ["x", "y"], noteWeights={"x": 0.1, "y": 0.9})))
    assert eng.recommend(X_TOP, note_only=True)[0]["score"] == pytest.approx(1.0)


def _perfume(key, notes):
    return B.Perfume(key=key, brand=key, name=key, year=None, concentration=None, rating=None, rating_count=10,
                     accords=[], layers={"top": notes, "middle": [], "base": []})


def test_프로필을_풀_때_강한_노트가_어코드를_더_가져간다():
    ps = [_perfume("a", ["rose", "musk"]), _perfume("b", ["musk"])]
    vec = {"a": {"floral": 100.0}, "b": {"aquatic": 100.0}}
    start = {k: {a: 1.0 / len(R.ACCORD_KEYS) for a in R.ACCORD_KEYS} for k in ("rose", "musk")}
    rose_strong = B.deconvolve(ps, vec, start, 400, {"a": {"rose": 0.95, "musk": 0.05}})
    rose_weak = B.deconvolve(ps, vec, start, 400, {"a": {"rose": 0.05, "musk": 0.95}})
    assert rose_strong["musk"]["aquatic"] > 0.5
    assert rose_strong["rose"]["floral"] > 0.5
    assert rose_weak["musk"]["floral"] > rose_strong["musk"]["floral"]


@pytest.fixture(scope="module")
def fixture_catalog():
    return B.build_fixture().catalog


def _by(cat, key):
    return next(p for p in cat["perfumes"] if p["key"] == key)


def test_연결된_향수만_강도가_들어간다(fixture_catalog):
    t1 = _by(fixture_catalog, "T001")
    assert sum(t1["accordShares"].values()) == pytest.approx(100, abs=0.01)
    assert sum(t1["noteWeights"].values()) == pytest.approx(1, abs=0.001)
    assert t1["noteWeights"]["lemon"] > t1["noteWeights"]["jasmine"]
    assert set(t1["noteWeights"]) == set(t1["top"]) | set(t1["middle"]) | set(t1["base"])
    assert "estimatedNotes" not in t1 and "estimatedAccords" not in t1
    # v8부터 연결되지 않은 향수는 값이 없는 대신 추정치로 채우고 표시한다
    assert _by(fixture_catalog, "T005")["estimatedNotes"] is True  # 이름은 같아도 노트가 다른 향수
    assert _by(fixture_catalog, "T006")["estimatedAccords"] is True  # 데이터에 없는 향수


def test_투표가_없는_향수는_노트_강도가_실제값이_아니다(fixture_catalog):
    assert _by(fixture_catalog, "T004")["estimatedNotes"] is True
    assert _by(fixture_catalog, "T004")["estimatedAccords"] is True  # 연결되는 어코드도 없음


def test_엔진_버전(fixture_catalog):
    assert fixture_catalog["engineVersion"] == 8
