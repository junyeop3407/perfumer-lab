"""P1 추정치 보정 테스트 (QA팀 추가 테스트, 엔진 v8). 가짜 데이터로 확인한다."""
import copy
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import build_catalog as B  # noqa: E402
import estimate as EST  # noqa: E402

ACCORDS = ["citrus", "floral", "woody"]


def _p(key, notes, accords=("citrus", "floral"), **extra):
    return {"key": key, "accords": list(accords), "top": notes[:1], "middle": notes[1:2], "base": notes[2:], **extra}


def _dev_keys(n):
    """학습에 쓰는 개발 절반에 드는 key"""
    return [k for k in (f"k{i}" for i in range(n * 4)) if EST.is_dev(k)][:n]


def _catalog():
    real = [_p(k, ["lemon", "rose", "musk"], accordShares={"citrus": 70.0, "floral": 30.0},
               noteWeights={"lemon": 0.6, "rose": 0.3, "musk": 0.1}) for k in _dev_keys(6)]
    return {"accords": [{"key": a} for a in ACCORDS],
            "perfumes": real + [_p("none", ["lemon", "rose", "musk"]),
                                _p("accord-only", ["lemon", "rose", "musk"], accordShares={"citrus": 50.0, "woody": 50.0})]}


def _by(cat, key):
    return next(p for p in cat["perfumes"] if p["key"] == key)


def test_강도_없는_향수에만_추정치를_넣고_표시한다():
    cat = _catalog()
    before = copy.deepcopy(_by(cat, _dev_keys(1)[0]))
    EST.apply(cat)
    assert _by(cat, _dev_keys(1)[0]) == before, "실제 강도가 있는 향수는 그대로이고 표시도 없다"
    none = _by(cat, "none")
    assert none["estimatedAccords"] is True and none["estimatedNotes"] is True
    assert sum(none["accordShares"].values()) == pytest.approx(100, abs=0.01)
    assert sum(none["noteWeights"].values()) == pytest.approx(1, abs=0.001)
    assert none["noteWeights"]["lemon"] > none["noteWeights"]["musk"], "학습 데이터에서 강한 노트를 강하게 추정한다"


def test_어코드_강도만_있으면_노트만_추정한다():
    cat = _catalog()
    EST.apply(cat)
    p = _by(cat, "accord-only")
    assert p["accordShares"] == {"citrus": 50.0, "woody": 50.0}
    assert "estimatedAccords" not in p and p["estimatedNotes"] is True


def test_적용한_뒤_다시_배워도_같다():
    # 추정치가 학습에 섞이면 안 된다
    cat = _catalog()
    pred, prior = EST.fit(cat)
    EST.apply(cat)
    pred2, prior2 = EST.fit(cat)
    assert prior2 == prior
    assert (pred2 == pred).all()


def test_학습_자료가_없으면_아무것도_넣지_않는다():
    cat = {"accords": [{"key": a} for a in ACCORDS], "perfumes": [_p("none", ["lemon", "rose", "musk"])]}
    assert EST.apply(cat) == {"estimated_accords": 0, "estimated_notes": 0}
    assert set(cat["perfumes"][0]) == {"key", "accords", "top", "middle", "base"}


def test_카탈로그_생성에서_추정은_켤_때만_돈다():
    fixture = B.build_fixture()
    assert any(p.get("estimatedNotes") for p in fixture.catalog["perfumes"]), "앱 카탈로그와 같은 모양으로 만든다"
    assert fixture.report["estimated_notes"] > 0
    rows = [{"URL": f"https://example.invalid/X{i}", "Brand": "A", "Name": f"N{i}", "Release_Year": "2000", "Concentration": "NA", "Rating_Value": "8",
             "Rating_Count": "50", "Main_Accords": "citrus, floral", "Top_Notes": "lemon", "Middle_Notes": "rose", "Base_Notes": "musk"} for i in range(3)]
    plain = B.build(rows, B.R.FIXTURE, {}, {}, {}, version="t", source_note="t")
    assert not any("estimatedNotes" in p for p in plain.catalog["perfumes"]), "비교·측정용 빌드는 추정 없이 만든다"
