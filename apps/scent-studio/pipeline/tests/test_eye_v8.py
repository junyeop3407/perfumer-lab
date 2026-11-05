"""9단계 눈으로 평가 부품 테스트 (QA팀 추가 테스트). 가짜 데이터로 확인한다."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import eye_v8 as EY  # noqa: E402

FAMILY = {"s1": "sweet", "s2": "sweet", "f1": "floral", "f2": "fruity", "w1": "woody", "c1": "citrus"}
NOTES = {"citrus": ["c1", "c2", "c3", "c4"], "floral": ["f1", "f2", "f3", "f4"], "woody": ["w1", "w2", "w3", "w4"], "sweet": ["s1", "s2", "s3", "s4"]}


def _cat():
    notes = [{"key": k, "nameKo": k.upper(), "family": fam, "tier": "browse"} for fam, ks in NOTES.items() for k in ks]
    perfumes = [{"key": f"{fam}{i}", "name": f"Name|{fam}{i}", "brand": f"B{i}", "accords": [fam, "woody"],
                 "top": [ks[i % 4]], "middle": [ks[(i + 1) % 4]], "base": [ks[(i + 2) % 4]],
                 "accordShares": {fam: 77.123}, "noteWeights": {ks[0]: 0.777}, "estimatedAccords": True, "estimatedNotes": True,
                 "rating": 8.8, "ratingCount": 999} for fam, ks in NOTES.items() for i in range(12)]
    return {"accords": [{"key": a, "nameKo": a.upper(), "family": a} for a in NOTES], "notes": notes, "perfumes": perfumes}


def test_조합_분류():
    assert EY.combo_bucket(["s1", "s2", "f1"], FAMILY) == "구르망"
    assert EY.combo_bucket(["f1", "f2", "c1"], FAMILY) == "플로럴"
    assert EY.combo_bucket(["f1", "w1", "c1"], FAMILY) == "혼합"
    assert EY.combo_bucket(["w1", "c1", "s1", "s2"], FAMILY) == "구르망"


def test_구르망_조합은_정해진_수이고_이미_쓴_조합을_뺀다():
    cat = _cat()
    fam = {n["key"]: n["family"] for n in cat["notes"]}
    first = EY.gourmand_combos(cat, 3, set())
    assert first == EY.gourmand_combos(cat, 3, set()) and len(first) == 3
    for notes in first:
        assert sum(fam[k] == "sweet" for k, _, _ in notes) >= 2
    used = {frozenset(k for k, _, _ in first[0])}
    assert not used & {frozenset(k for k, _, _ in n) for n in EY.gourmand_combos(cat, 3, set(used))}


def _sheet():
    combo = [["c1", "top", "strong"], ["f1", "middle", "medium"], ["w1", "base", "weak"]]
    return EY.sheet_text([{"no": 1, "notes": combo, "keys": ["citrus0", "citrus1"]}], [{"no": 8, "notes": combo, "keys": ["woody0"]}], _cat())


def test_시트에_버전을_구분할_정보가_없다():
    text = _sheet()
    for leak in ("77.123", "0.777", "8.8", "999", "estimated", "추정", "v7", "v8", "엔진", "engine", "등급"):
        assert leak not in text, leak


def test_향수마다_점수_칸이_비어_있고_칸은_블라인드와_같다():
    text = _sheet()
    assert text.count(" 점수: _ |") == 3
    assert "| 순위 | 향수 | 브랜드 | 주요 노트 (탑 / 미들 / 베이스) | 주요 어코드 | 점수 |" in text
    assert "Name\\|citrus0" in text and "CITRUS · WOODY" in text


def _five():
    combo = [["c1", "top", "medium"], ["f1", "middle", "medium"], ["w1", "base", "medium"]]
    keys = [f"citrus{i}" for i in range(5)]
    return EY.sheet_text([{"no": 1, "notes": combo, "keys": keys}, {"no": 2, "notes": combo, "keys": keys}], [{"no": 8, "notes": combo, "keys": keys}], _cat())


def test_점수를_채우고_다시_읽으며_참고는_비워_둔다():
    text = EY.fill_scores(_five(), {1: [5, 4, 3, 2, 1], 2: [5, 5, 5, 5, 4]})
    assert EY.read_scores(text) == {1: [5, 4, 3, 2, 1], 2: [5, 5, 5, 5, 4]}
    assert text.split("# 참고 (집계")[1].count(EY.BLANK) == 5


@pytest.mark.parametrize("scores", [{1: [5, 5, 5, 5], 2: [5, 5, 5, 5, 5]}, {1: [5, 5, 5, 5, 6], 2: [5, 5, 5, 5, 5]}, {1: [5, 5, 5, 5, 5]},
                                    {1: [5, 5, 5, 5, 5], 2: [5, 5, 5, 5, 5], 3: [5, 5, 5, 5, 5]}])
def test_점수가_어긋나면_멈춘다(scores):
    with pytest.raises(SystemExit):
        EY.fill_scores(_five(), scores)


def test_이미_채운_시트는_다시_채우지_않고_빈_칸이_있으면_읽지_않는다():
    done = EY.fill_scores(_five(), {1: [5, 5, 5, 5, 5], 2: [5, 5, 5, 5, 5]})
    with pytest.raises(SystemExit):
        EY.fill_scores(done, {1: [1, 1, 1, 1, 1], 2: [1, 1, 1, 1, 1]})
    with pytest.raises(SystemExit):
        EY.read_scores(_five())


COMBOS = [{"no": 1, "kind": "기본", "bucket": "플로럴"}, {"no": 2, "kind": "기본", "bucket": "구르망"}, {"no": 3, "kind": "구르망", "bucket": "구르망"}]


def test_집계와_판정():
    r = EY.tally({1: [5] * 5, 2: [4] * 5, 3: [3, 3, 3, 3, 4]}, COMBOS)
    assert r["base"] == 4.5 and r["new_gourmand"] == pytest.approx(3.2)
    assert r["all_gourmand"] == pytest.approx(3.6) and r["ranks"][4] == pytest.approx(13 / 3)
    assert r["verdict"] == "합격, 구르망 평균 미달: 고치지 않고 보고 후 멈춤"
    assert EY.tally({1: [5] * 5, 2: [4] * 5, 3: [4] * 5}, COMBOS)["verdict"] == "합격"
    assert EY.tally({1: [3] * 5, 2: [3] * 5, 3: [4] * 5}, COMBOS)["verdict"].startswith("불합격")


def test_조합의_강도를_보여_주고_참고는_끝에_따로_둔다():
    text = _sheet()
    assert "## 1. 탑 C1 (강) · 미들 F1 · 베이스 W1 (약)" in text
    assert text.index("## 1.") < text.index("# 참고 (집계하지 않습니다)") < text.index("## 참고 8.")
