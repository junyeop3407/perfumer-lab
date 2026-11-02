"""Decant(Fragrantica) 데이터 연결 규칙 (engine v7, docs/engine_v2_trial.md)"""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import decant_join as D  # noqa: E402

ACCORD_MAP = {"citrus": "citrus", "white floral": "floral", "rose": "floral", "woody": "woody", "bbq": ""}
ALIASES = {"cedar": "cedarwood"}


def source(**kw):
    row = {"id": 1, "brand": "Acme", "name": "Rose Water", "year": 2010, "votes": 930,
           "notes": {"Rose": 100, "Lemon": 50, "Cedar": 0}, "accords": {"citrus": 100, "rose": 60, "woody": 40}}
    return D.source_index([{**row, **kw}], ALIASES)


def test_이름은_브랜드_연도_농도를_빼고_맞춘다():
    assert D.name_key("Acme", "Rose Water Acme 2010 Eau de Parfum") == D.name_key("ACME", "Rose Water")
    assert D.name_key("Estée Lauder", "Beyond") == D.name_key("Estee Lauder", "Beyond")


def test_별칭을_적용해_노트가_겹치면_연결한다():
    hit = D.match("Acme", "Rose Water 2010 Eau de Toilette", 2010, {"rose", "lemon", "cedarwood"}, source())
    assert hit is not None and hit["id"] == 1


def test_이름만_같고_노트가_30퍼센트_미만_겹치면_연결하지_않는다():
    assert D.match("Acme", "Rose Water", 2010, {"oud", "leather", "tobacco", "rose"}, source()) is None


def test_연도가_맞는_향수를_고른다():
    idx = D.source_index([
        {"id": 1, "brand": "Acme", "name": "X", "year": 1990, "votes": 9000, "notes": {"Rose": 100}, "accords": {}},
        {"id": 2, "brand": "Acme", "name": "X", "year": 2020, "votes": 50, "notes": {"Rose": 100}, "accords": {}},
    ], {})
    assert D.match("Acme", "X", 2021, {"rose"}, idx)["id"] == 2


def test_어코드_강도는_연결표로_모아_합_100으로_맞춘다():
    shares = D.accord_shares({"citrus": 100, "white floral": 50, "rose": 30, "bbq": 20}, ACCORD_MAP)
    assert shares == pytest.approx({"citrus": 100 * 100 / 180, "floral": 100 * 80 / 180})
    assert sum(shares.values()) == pytest.approx(100)


def test_연결되는_어코드가_없으면_값이_없다():
    assert D.accord_shares({"bbq": 100}, ACCORD_MAP) is None
    assert D.accord_shares({}, ACCORD_MAP) is None


def test_노트_강도_몫은_합_1이고_강한_노트가_크다():
    w = D.note_weights({"rose", "lemon", "cedarwood"}, {"rose": 100, "lemon": 50, "cedarwood": 0}, votes=930)
    assert sum(w.values()) == pytest.approx(1)
    assert w["rose"] > w["lemon"] > w["cedarwood"] > 0  # 아무도 안 뽑은 노트도 하한이 있어 사라지지 않는다


def test_표가_적으면_강도_차이를_줄인다():
    many = D.note_weights({"rose", "lemon"}, {"rose": 100, "lemon": 10}, votes=7000)
    few = D.note_weights({"rose", "lemon"}, {"rose": 100, "lemon": 10}, votes=7)
    assert many["rose"] - many["lemon"] > few["rose"] - few["lemon"]


def test_데이터에_없는_노트는_있는_노트의_평균_강도를_쓴다():
    w = D.note_weights({"rose", "lemon", "oud"}, {"rose": 100, "lemon": 50}, votes=10_000)
    assert w["lemon"] < w["oud"] < w["rose"]


def test_아무도_투표하지_않은_향수는_값이_없다():
    assert D.note_weights({"rose", "lemon"}, {"rose": 0, "lemon": 0}, votes=500) is None
    assert D.note_weights({"rose"}, {}, votes=500) is None
