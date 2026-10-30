"""파이프라인 테스트 (QA팀 추가 테스트, 승인 대상 아님). 가짜 데이터(tests/fixtures)로 확인한다."""
import gzip
import json
import sys
from pathlib import Path

import pytest

PIPELINE = Path(__file__).resolve().parent.parent
APP = PIPELINE.parent
sys.path.insert(0, str(PIPELINE))

import build_catalog as B  # noqa: E402
import catalog_rules as R  # noqa: E402


@pytest.fixture(scope="module")
def catalog():
    return B.build_fixture().catalog


def perfume(catalog, key):
    return next((p for p in catalog["perfumes"] if p["key"] == key), None)


def note(catalog, key):
    return next(n for n in catalog["notes"] if n["key"] == key)


def test_후보에서_평가_적은_것_층_빠진_것_어코드_없는_것을_뺀다(catalog):
    assert perfume(catalog, "T037") is None  # 평가 3건
    assert perfume(catalog, "T039") is None  # 노트 없음
    assert perfume(catalog, "T040") is None  # 어코드 없음
    assert len(catalog["perfumes"]) == 37


def test_같은_브랜드_같은_이름은_평가_많은_하나만(catalog):
    assert perfume(catalog, "T003") is not None  # 평가 300
    assert perfume(catalog, "T038") is None      # 같은 이름, 평가 150


def test_농도만_다른_버전은_같은_묶음(catalog):
    assert perfume(catalog, "T041")["group"] == perfume(catalog, "T001")["group"]
    assert perfume(catalog, "T003")["group"] != perfume(catalog, "T004")["group"]
    assert R.group_key("Carthusia", "Mediterraneo Carthusia 2018 Eau de Parfum", "Eau de Parfum") ==         R.group_key("Carthusia", "Mediterraneo Carthusia Profumo", "Profumo")
    assert R.group_key("Dior", "Sauvage Elixir", None) != R.group_key("Dior", "Sauvage Eau de Parfum", "Eau de Parfum")


def test_노트_프로필은_합이_1이고_21차원(catalog):
    for n in catalog["notes"]:
        assert len(n["profile"]) == 21
        assert sum(n["profile"]) == pytest.approx(1.0, abs=1e-4)


def test_노트_계열이_데이터에서_나온다(catalog):
    assert note(catalog, "lemon")["family"] == "citrus"
    assert note(catalog, "sandalwood")["family"] == "woody"
    assert note(catalog, "rose")["family"] == "floral"


def test_공개_범위(catalog):
    assert note(catalog, "lemon")["tier"] == "browse"
    assert note(catalog, "hinoki")["tier"] == "search"   # 2개 향수
    assert note(catalog, "tuberose")["tier"] == "hidden"  # 1개 향수


def test_목록에_보이는_노트는_궁합_노트가_있고_모두_목록_노트(catalog):
    tiers = {n["key"]: n["tier"] for n in catalog["notes"]}
    lemon = note(catalog, "lemon")
    assert 1 <= len(lemon["pairings"]) <= 3
    assert all(tiers[p] == "browse" for p in lemon["pairings"])


def test_한글명과_설명(catalog):
    lemon = note(catalog, "lemon")
    assert lemon["nameKo"] == "레몬"
    assert lemon["description"] == "밝고 산뜻하게 톡 쏘는 첫인상"


def test_어코드_순위_비중():
    w = R.rank_weights(["citrus", "floral", "fresh", "green", "woody"])
    assert w["citrus"] == pytest.approx(100 * 5 / 15)
    assert w["woody"] == pytest.approx(100 * 1 / 15)
    assert sum(R.rank_weights(["a", "b", "c", "d"]).values()) == pytest.approx(100)


def test_향수_key는_주소에서_만들고_슬래시가_없다():
    assert R.perfume_key("https://www.parfumo.com/Perfumes/Le_Re_Noir/455-tabac-ecarlate") == "Le_Re_Noir:455-tabac-ecarlate"
    assert R.perfume_key("https://example.invalid/T001") == "T001"


def test_노트_key_정리():
    assert R.note_key("  Ylang-Ylang ") == "ylang-ylang"
    assert R.note_key("Crème  Brûlée") == "creme brulee"


def test_커밋된_테스트_카탈로그가_가짜_데이터와_같다(catalog):
    """tests/fixtures를 바꾸고 다시 만들지 않으면 실패한다:  py pipeline/build_catalog.py --fixture"""
    committed = APP / "app" / "src" / "test" / "resources" / "catalog" / "test_catalog.json.gz"
    with gzip.open(committed, "rt", encoding="utf-8") as f:
        assert json.load(f) == catalog
