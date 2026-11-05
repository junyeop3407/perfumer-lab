"""파이프라인 테스트 (QA팀 추가 테스트, 승인 대상 아님). 가짜 데이터(tests/fixtures)로 확인한다."""
import dataclasses
import gzip
import hashlib
import json
import os
import subprocess
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


def test_공개_복사_목록에는_향수_이름과_평가_수가_없다():
    record = {"set": 1, "kind": "copy", "key": "a:secret", "brand": "A", "name": "Secret", "year": 2000, "rating_count": 63,
              "decision": "excluded", "reason": "복사본 — 원본은 B — Origin (2023) Noble (조향사 단서)", "perfumer_clue_brands": 1}
    public = B.public_copy_record(record)
    assert not {"key", "name", "rating_count"} & public.keys()
    assert public["reason"] == "복사본 (조향사 단서)"


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


def test_거의_복사도_원본만_남기거나_묶음째_뺀다():
    # 2026-10-31 사장 결정: 브랜드가 다르고 노트·어코드가 같은데 평가 수만 조금 다르면(거의 복사) 완전 복사처럼 처리
    a = ("lavender, bergamot", "geranium", "cedarwood")
    rows = [_copy_row("L1", "Lhouse", "Lavande", "2005", 694, a, accords="fougère, fresh"),
            _copy_row("Z1", "Zhouse", "Zefiro", "2012", 695, a, accords="fougère, fresh"),
            _copy_row("F1", "Fhouse", "Far", "2012", 300, a, accords="fougère, fresh")]
    res = B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t")
    keys = {p["key"] for p in res.catalog["perfumes"]}
    assert "F1" in keys, "평가 수 차이가 크면 진짜 클론일 수 있어 남긴다"
    assert not {"L1", "Z1"} & keys, "원본을 가릴 단서가 없으면 묶음째 뺀다"
    marked = {e["key"]: e for e in res.report["copy_suspects"]}
    assert marked["L1"]["kind"] == marked["Z1"]["kind"] == "near_copy"
    assert marked["L1"]["decision"] == "excluded" and "F1" not in marked


# ---------- v8 0단계: 제외는 빼기만 한다, 이름 앞 번호 복구 (2026-11-04 사장 요청) ----------
def _build(rows, exclude=frozenset()):
    return B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t", exclude=exclude)


def _keys(res):
    return {p["key"] for p in res.catalog["perfumes"]}


def _cleanup_rows():
    a = ("lemon, bergamot", "rose", "musk")
    b = ("cardamom, pink pepper", "iris", "vetiver")
    c = ("lemon", "jasmine", "sandalwood")
    return [
        _copy_row("W1", "Dup", "Same Name", "2001", 500, a),     # 합치기 대표
        _copy_row("W2", "Dup", "Same Name", "2001", 90, a),      # 합치기에서 진 행 (노트가 같아 겹침 검사도 통과)
        _copy_row("X1", "Xhouse", "Original", "2001", 300, c, accords="citrus, woody", perfumer="Pierre"),  # 복사 원본
        _copy_row("X2", "Xhouse", "Other", "2005", 40, a, accords="citrus, fresh", perfumer="Pierre"),
        _copy_row("Y1", "Yhouse", "Copied", "2015", 300, c, accords="citrus, woody", perfumer="Pierre"),    # 복사본
        _copy_row("N1", "Nhouse", "Normal", "2010", 70, b, accords="spicy, woody"),
    ]


def test_정리_후_행은_정리_전_행의_부분집합이고_제외한_만큼만_빠진다():
    before = _keys(_build(_cleanup_rows()))
    for exclude in ({"W1"}, {"X2"}, {"N1"}, {"W1", "X1"}, {"없는 행"}):
        after = _keys(_build(_cleanup_rows(), exclude=exclude))
        assert after <= before
        assert after == before - exclude


def test_합치기_대표를_빼도_진_행이_되살아나지_않는다():
    assert "W2" not in _keys(_build(_cleanup_rows(), exclude={"W1"}))


def test_복사_묶음의_행을_빼도_나머지_판정이_그대로다():
    # X2를 미리 빼면 조향사 단서가 사라져 X1도 '원본을 못 가림'으로 빠지던 문제
    res = _build(_cleanup_rows(), exclude={"X2"})
    assert "X1" in _keys(res) and "Y1" not in _keys(res)
    assert {e["key"]: e["decision"] for e in res.report["copy_suspects"]}["X1"] == "kept_original"


def test_제외한_행의_노트는_프로필_계산에_쓰지_않는다():
    rows = _cleanup_rows() + [_copy_row("Z1", "Zhouse", "Foul", "2010", 60, ("sewer", "rose", "musk"), accords="floral, sweet")]
    assert "sewer" in {n["key"] for n in _build(rows).catalog["notes"]}
    res = _build(rows, exclude={"Z1"})
    assert "sewer" not in {n["key"] for n in res.catalog["notes"]}
    assert res.report["excluded_rows"] == 1


@pytest.mark.parametrize("number,name,expected", [
    ("400", "XerJoff", "400 XerJoff"),
    ("24", ", Faubourg", "24, Faubourg"),
    ("360", "° Red for Men", "360° Red for Men"),
    ("20", "|20", "20|20"),
    ("12.", "Un Crime", "12.Un Crime"),
    ("NA", "Plain", "Plain"),
    ("", "Plain", "Plain"),
    (None, "Plain", "Plain"),
    ("T001", "Plain", "Plain"),  # 가짜 데이터의 일련번호
    (".", "Plain", "Plain"),
])
def test_Number_칸이_있으면_이름에_붙는다(number, name, expected):
    assert R.restore_name(number, name) == expected


def _numbered(number, key, name, votes, notes):
    return {**_copy_row(key, "Houseo", name, "2010", votes, notes), "Number": number}


def test_복구_뒤_합치기에서_서로_다른_향수가_합쳐지지_않는다():
    a = ("lemon, bergamot", "rose", "musk")
    b = ("cardamom, pink pepper", "iris", "vetiver")
    rows = [_numbered("1962", "F62", "Houseo", 300, a), _numbered("1976", "F76", "Houseo", 200, b),
            _numbered("NA", "F00", "Houseo", 100, a)]
    res = _build(rows)
    assert _keys(res) == {"F62", "F76", "F00"}
    names = {p["key"]: p["name"] for p in res.catalog["perfumes"]}
    assert names == {"F62": "1962 Houseo", "F76": "1976 Houseo", "F00": "Houseo"}
    assert len({p["group"] for p in res.catalog["perfumes"]}) == 3


def test_숫자로_시작하는_이름이_연도로_지워지지_않는다():
    assert R.group_key("Floris", "1962 Floris", None) != R.group_key("Floris", "1976 Floris", None)
    assert R.group_key("Acme", "2022 Generation Man", None) == "acme|2022 generation man"
    # 브랜드 뒤에 오는 연도는 예전처럼 지운다
    assert R.group_key("Joop!", "Joop! Homme Joop! 1989 Eau de Toilette", "Eau de Toilette") == "joop!|homme"
    assert R.group_key("Carolina Herrera", "Carolina Herrera Carolina Herrera 1988 Eau de Parfum", "Eau de Parfum") != \
        R.group_key("Carolina Herrera", "1988 Carolina Herrera", None)


def test_커밋한_제외_목록에는_향수_이름이_없고_해시만_있다():
    for name, count in (("filtered_keys.csv", 1081), ("review_keys.csv", 40)):
        rows = B.read_csv(PIPELINE / "manual" / name)
        assert len(rows) == count == len({r["key_hmac"] for r in rows})
        assert all(len(r["key_hmac"]) == 64 and set(r["key_hmac"]) <= set("0123456789abcdef") for r in rows)
    assert B.read_key_hashes(PIPELINE / "manual" / "filtered_keys.csv").isdisjoint(B.read_key_hashes(PIPELINE / "manual" / "review_keys.csv"))


def test_따로_두라고_한_행은_노트가_겹쳐도_합치지_않는다():
    a = ("lemon, bergamot", "rose", "musk")
    rows = [_copy_row("A1", "Dup", "Same Name", "2013", 300, a), _copy_row("A2", "Dup", "Same Name", "2004", 70, a),
            _copy_row("B1", "Dup", "Other Name", "2013", 200, a), _copy_row("B2", "Dup", "Other Name", "2004", 60, a)]
    res = B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t", keep_separate={"A2"})
    assert _keys(res) == {"A1", "A2", "B1"}, "지정한 행만 분리하고, 노트가 같은 B2는 그대로 합친다"
    # 노트가 다르면 추천에서도 다른 향수로 본다
    rows = [_copy_row("C1", "Dup", "Same Name", "2013", 300, _A), _copy_row("C2", "Dup", "Same Name", "2004", 70, _B)]
    g = {p["key"]: p["group"] for p in B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t", keep_separate={"C2"}).catalog["perfumes"]}
    assert g["C1"] != g["C2"]


# ---------- 합치기 노트 겹침 검사 (2026-11-05 사장 결정) ----------
_A = ("lemon, bergamot", "rose", "musk")
_B = ("cardamom, pink pepper", "iris", "vetiver")


def test_이름이_같아도_대표와_노트_겹침이_05_미만이면_합치지_않는다():
    # 대표와의 겹침: A2는 3/4 = 0.75라 합치고, A3는 0이라 따로 둔다
    rows = [_copy_row("A1", "Dup", "Same Name", "2013", 300, _A),
            _copy_row("A2", "Dup", "Same Name", "2010", 90, ("lemon", "rose", "musk")),
            _copy_row("A3", "Dup", "Same Name", "2004", 70, _B)]
    res = _build(rows)
    assert _keys(res) == {"A1", "A3"}
    assert res.report["merge_split_rows"] == 1
    g = {p["key"]: p["group"] for p in res.catalog["perfumes"]}
    assert g["A1"] != g["A3"], "살아난 행은 추천에서도 다른 향수로 본다"


def test_겹침이_정확히_05면_합친다():
    # 공통 2개 / 합집합 4개 = 0.5
    rows = [_copy_row("A1", "Dup", "Same Name", "2013", 300, ("lemon", "rose", "musk")),
            _copy_row("A2", "Dup", "Same Name", "2004", 70, ("lemon", "rose", "iris"))]
    assert _keys(_build(rows)) == {"A1"}


def test_분리된_행끼리_노트가_겹치면_그_둘은_합친다():
    rows = [_copy_row("A1", "Dup", "Same Name", "2013", 300, _A), _copy_row("A2", "Dup", "Same Name", "2004", 70, _B),
            _copy_row("A3", "Dup", "Same Name", "2005", 50, _B)]
    res = _build(rows)
    assert _keys(res) == {"A1", "A2"}, "A3는 A1과 다르지만 A2와 같다"
    assert res.report["merge_split_groups"] == 1 and res.report["merge_split_rows"] == 1


def test_합친_채_두라고_한_행은_겹침이_낮아도_합친다():
    rows = [_copy_row("A1", "Dup", "Same Name", "2011", 151, _A), _copy_row("A2", "Dup", "Same Name", "1998", 90, _B),
            _copy_row("B1", "Dup", "Other Name", "2011", 200, _A), _copy_row("B2", "Dup", "Other Name", "1998", 60, _B)]
    res = B.build(rows, R.FIXTURE, {}, {}, {}, version="t", source_note="t", keep_merged={"A2"})
    assert _keys(res) == {"A1", "B1", "B2"}, "지정한 행만 합치고 나머지는 규칙대로"


def test_해시_목록의_행을_원본에서_찾고_없으면_멈춘다(tmp_path, monkeypatch):
    monkeypatch.setenv(B.SECRET_ENV, "test-secret")
    f = tmp_path / "h.csv"
    f.write_text("key_hmac\n" + B.key_hash("X1") + "\n", encoding="utf-8")
    rows = [{"URL": "https://example.invalid/X1"}, {"URL": "https://example.invalid/X2"}]
    assert B.keys_from_hashes(rows, f) == {"X1"}
    with pytest.raises(SystemExit):
        B.keys_from_hashes(rows[1:], f)


def test_키_해시는_비밀_키를_섞고_어느_컴퓨터에서나_같다(monkeypatch):
    monkeypatch.setenv(B.SECRET_ENV, "test-secret")
    # 파이썬 표준 hmac으로 따로 계산해 고정한 값: 코드나 환경이 달라져도 이 값이 나와야 한다
    expected = "bbac92f46ab534fe0052d853d38413cfb17dfc42585c8ec28b5a0d97fd301668"
    assert B.key_hash("Brand:perfume-1") == expected
    assert expected != hashlib.sha256(b"Brand:perfume-1").hexdigest(), "공개 주소만으로 계산할 수 없어야 한다"
    monkeypatch.setenv(B.SECRET_ENV, "other-secret")
    assert B.key_hash("Brand:perfume-1") != expected
    code = f"import sys; sys.path.insert(0, {str(PIPELINE)!r}); import build_catalog as B; print(B.key_hash('Brand:perfume-1'))"
    for seed in ("1", "2"):
        env = {**os.environ, B.SECRET_ENV: "test-secret", "PYTHONHASHSEED": seed, "PYTHONUTF8": "1"}
        assert subprocess.run([sys.executable, "-c", code], env=env, capture_output=True, text=True, check=True).stdout.strip() == expected


def test_비밀_키가_없으면_알려_주고_멈춘다(monkeypatch):
    monkeypatch.delenv(B.SECRET_ENV, raising=False)
    with pytest.raises(SystemExit, match=B.SECRET_ENV):
        B.key_hash("X1")


RAW_CSV = PIPELINE / "data" / "raw" / "parfumo_data_clean.csv"


@pytest.mark.skipif(not (os.environ.get(B.SECRET_ENV) and RAW_CSV.exists()), reason="비밀 키와 원본 데이터가 있는 컴퓨터에서만 돈다")
def test_이_컴퓨터의_비밀_키로_목록이_원본과_모두_맞는다():
    rows = B.read_csv(RAW_CSV)
    for name, count in (("filtered_keys.csv", 1081), ("review_keys.csv", 40), ("reviewed_keys.csv", 2), ("separate_keys.csv", 1), ("merge_keys.csv", 0)):
        assert len(B.keys_from_hashes(rows, PIPELINE / "manual" / name)) == count


def test_보류_목록은_상위_행과_고정_행을_함께_남긴다():
    # 2026-11-05: freeze_exclusions.py를 다시 돌리면 손으로 넣은 행이 사라지던 문제. 고정 행은 manual/hold_keys.csv에서 읽는다
    ratings = {"a": 300, "b": 200, "c": 100, "d": 50, "e": 40}
    assert R.hold_list_keys({"a", "b", "c"}, ratings, {"e", "d"}, top=2) == ["a", "b", "d", "e"]
    assert R.hold_list_keys({"a", "b", "c"}, ratings, {"a", "e"}, top=2) == ["a", "b", "e"], "상위 행과 겹쳐도 한 번만"
    assert R.hold_list_keys({"a"}, ratings, set(), top=10) == ["a"]


@pytest.mark.skipif(not (os.environ.get(B.SECRET_ENV) and RAW_CSV.exists()), reason="비밀 키와 원본 데이터가 있는 컴퓨터에서만 돈다")
def test_보류_고정_행은_제외_목록에_있고_원본에서_찾을_수_있다():
    keys = B.keys_from_hashes(B.read_csv(RAW_CSV), PIPELINE / "manual" / "hold_keys.csv")
    assert len(keys) == 4
    assert B.read_key_hashes(PIPELINE / "manual" / "hold_keys.csv") <= B.read_key_hashes(PIPELINE / "manual" / "filtered_keys.csv")


def test_보고서를_쓸_수_있다(tmp_path, catalog):
    # 2026-11-05: 문구의 {filtered,review}를 변수로 읽어 실제 빌드가 카탈로그를 쓴 뒤 멈추던 문제
    out = tmp_path / "report.md"
    B.write_report(B.build_fixture(), out, "t")
    text = out.read_text(encoding="utf-8")
    assert "manual/{filtered,review}_keys.csv" in text and "강도 추정치" in text


def _write_cat(path, version):
    with gzip.open(path, "wt", encoding="utf-8") as f:
        json.dump({"engineVersion": version, "perfumes": []}, f)
    return path


def test_옛_스크립트용_읽기는_v8_카탈로그면_멈춘다(tmp_path):
    assert R.load_old_catalog(_write_cat(tmp_path / "v7.json.gz", 7))["engineVersion"] == 7
    with pytest.raises(SystemExit, match="catalog_v7_compare"):
        R.load_old_catalog(_write_cat(tmp_path / "v8.json.gz", 8))


def test_옛_스크립트는_모두_버전을_확인하고_읽는다():
    current = {"engine_v8_check.py", "freeze_exclusions.py", "save_v7_compare.py"}  # v8을 일부러 읽거나 v7 파일만 읽는 것
    for path in (PIPELINE / "experiments").glob("*.py"):
        if path.name not in current:
            assert "gzip.open(" not in path.read_text(encoding="utf-8"), f"{path.name}: load_old_catalog를 쓰지 않음"
