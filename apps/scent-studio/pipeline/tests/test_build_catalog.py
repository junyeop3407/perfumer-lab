"""파이프라인 테스트 (QA팀 추가 테스트, 승인 대상 아님). 가짜 데이터(tests/fixtures)로 확인한다."""
import dataclasses
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


def test_브랜드_표기_오류를_고친다():
    rows = [{"URL": "https://example.invalid/X1", "Brand": "Estēe Lauder", "Name": "Test", "Release_Year": "2000",
             "Concentration": "NA", "Rating_Value": "8", "Rating_Count": "50", "Main_Accords": "citrus, floral",
             "Top_Notes": "lemon", "Middle_Notes": "rose", "Base_Notes": "musk"}]
    res = B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t", brand_fixes={"Estēe Lauder": "Estée Lauder"})
    assert [p["brand"] for p in res.catalog["perfumes"]] == ["Estée Lauder"]


def test_노트_key_정리():
    assert R.note_key("  Ylang-Ylang ") == "ylang-ylang"
    assert R.note_key("Crème  Brûlée") == "creme brulee"


def test_커밋된_테스트_카탈로그가_가짜_데이터와_같다(catalog):
    """tests/fixtures를 바꾸고 다시 만들지 않으면 실패한다:  py pipeline/build_catalog.py --fixture"""
    committed = APP / "tests" / "fixtures" / "test_catalog.json.gz"
    with gzip.open(committed, "rt", encoding="utf-8") as f:
        assert json.load(f) == catalog


def _row(key, name, year, conc, votes, top, middle, base):
    return {"URL": f"https://example.invalid/{key}", "Brand": "Joop!", "Name": name, "Release_Year": year,
            "Concentration": conc, "Rating_Value": "8", "Rating_Count": str(votes), "Main_Accords": "sweet, oriental",
            "Top_Notes": top, "Middle_Notes": middle, "Base_Notes": base}


def test_이름이_같아도_노트가_다르면_다른_묶음():
    # 2026-10-31 사장 요청: 이름에서 연도를 지워 Joop! Homme 1989와 노트가 전혀 다른 2021이 한 향수로 묶였다
    rows = [
        _row("J89", "Joop! Homme Joop! 1989 Eau de Toilette", "1989", "Eau de Toilette", 2732,
             "orange blossom, bergamot", "heliotrope, cinnamon", "vanilla, tonka bean, sandalwood"),
        _row("J89AS", "Joop! Homme Joop! 1989 After Shave", "1989", "After Shave", 15,
             "orange blossom, bergamot", "heliotrope, cinnamon", "vanilla, tonka bean, sandalwood"),
        _row("J21", "Joop! Homme Joop! 2021 Eau de Parfum", "2021", "Eau de Parfum", 638,
             "hazelnut, cardamom", "fir needle, moss", "cashmeran, vetiver"),
    ]
    g = {p["key"]: p["group"] for p in B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t").catalog["perfumes"]}
    assert g["J89"] == g["J89AS"], "노트가 같은 농도 버전은 같은 묶음"
    assert g["J89"] != g["J21"], "노트가 다른 새 향수는 다른 묶음"
    assert g["J89"] == "joop!|homme", "평가 많은 쪽 묶음은 이름 그대로 (예전 묶음 값 유지)"


def _copy_row(key, brand, name, year, votes, notes, accords="floral, sweet", perfumer="NA"):
    top, middle, base = notes
    return {"URL": f"https://example.invalid/{key}", "Brand": brand, "Name": name, "Release_Year": year,
            "Concentration": "NA", "Rating_Value": "7.5", "Rating_Count": str(votes), "Main_Accords": accords,
            "Top_Notes": top, "Middle_Notes": middle, "Base_Notes": base, "Perfumers": perfumer}


def test_복사_오류는_원본만_남기거나_묶음째_뺀다():
    # 2026-10-31 사장 요청: 브랜드가 다른데 노트·평가 수·어코드·조향사가 모두 같으면 원본 데이터셋의 복사 오류로 본다
    a = ("lemon, bergamot", "rose", "musk")
    b = ("cardamom, pink pepper", "iris", "vetiver")
    c = ("lemon", "jasmine", "sandalwood")
    rows = [
        # 원본 X1: 조향사 Pierre가 브랜드 Xhouse에서 다른 향수(X2)도 만들었다 → 원본으로 남음, 복사본 Y1은 빠짐
        _copy_row("X1", "Xhouse", "Original", "2001", 500, a, perfumer="Pierre"),
        _copy_row("X2", "Xhouse", "Other", "2005", 40, c, accords="citrus, woody", perfumer="Pierre"),
        _copy_row("Y1", "Yhouse", "Copied", "2015", 500, a, perfumer="Pierre"),
        # 조향사 정보가 없어 원본을 못 가림 → 둘 다 빠짐
        _copy_row("P1", "Phouse", "One", "2010", 80, b),
        _copy_row("Q1", "Qhouse", "Two", "2012", 80, b),
        # 노트는 같지만 평가 수가 다름 → 진짜 클론일 수 있어 둘 다 남음
        _copy_row("R1", "Rhouse", "Famous", "1990", 900, c, accords="citrus, woody"),
        _copy_row("S1", "Shouse", "Clone", "2020", 30, c, accords="citrus, woody"),
    ]
    res = B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t")
    keys = {p["key"] for p in res.catalog["perfumes"]}
    assert {"X1", "X2", "R1", "S1"} <= keys
    assert not {"Y1", "P1", "Q1"} & keys
    excluded = {e["key"]: e for e in res.report["copy_suspects"]}
    assert excluded["Y1"]["decision"] == "excluded" and excluded["X1"]["decision"] == "kept_original"
    assert excluded["P1"]["decision"] == excluded["Q1"]["decision"] == "excluded"


def test_함께_든_노트의_몫을_빼고_노트_프로필을_만든다():
    # 2026-10-31 사장 결정: 머스크는 플로럴 향수에 장미와 함께 자주 들어가서 플로럴로 잡혔다.
    # 플로럴은 장미가, 우디는 시더우드가 가져가고 머스크에는 자기 몫(파우더리)이 남아야 한다.
    # 끌어당기기(K)는 계열 평균 쪽 규칙이라 여기서는 끄고(0) 노트 프로필 계산만 본다
    rows = []
    for i in range(20):
        rows.append(_copy_row(f"A{i}", "T", f"Rose Musk {i}", "2010", 100 + i, ("bergamot", "rose", "musk"),
                              accords="floral, citrus, fresh, powdery"))
        rows.append(_copy_row(f"B{i}", "T", f"Rose Cedar {i}", "2010", 200 + i, ("bergamot", "rose", "cedarwood"),
                              accords="floral, citrus, fresh, woody"))
    for i in range(40):
        rows.append(_copy_row(f"D{i}", "T", f"Spice Wood {i}", "2010", 300 + i, ("cardamom", "vetiver", "cedarwood"),
                              accords="woody, spicy"))
    params = dataclasses.replace(R.FIXTURE, shrink_k=0.0)
    cat = B.build(rows, params, {}, {}, {}, version="t", source_note="t").catalog
    keys = [a["key"] for a in cat["accords"]]
    prof = {n["key"]: n["profile"] for n in cat["notes"]}
    top = lambda k: keys[max(range(len(keys)), key=lambda i: prof[k][i])]  # noqa: E731
    assert top("rose") == "floral"
    assert top("musk") == "powdery", f"머스크가 함께 든 장미의 몫을 가져감: {top('musk')}"
