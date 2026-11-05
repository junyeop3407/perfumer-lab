"""9단계 눈으로 평가 부품 테스트 (QA팀 추가 테스트). 가짜 데이터로 확인한다."""
import sys
from pathlib import Path

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


def test_조합의_강도를_보여_주고_참고는_끝에_따로_둔다():
    text = _sheet()
    assert "## 1. 탑 C1 (강) · 미들 F1 · 베이스 W1 (약)" in text
    assert text.index("## 1.") < text.index("# 참고 (집계하지 않습니다)") < text.index("## 참고 8.")
