"""8단계 블라인드 부품 테스트 (QA팀 추가 테스트). 가짜 데이터로 확인한다."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import blind_v8 as BL  # noqa: E402

FAMILY_OF_ACCORD = {"citrus": "citrus", "floral": "floral", "woody": "woody", "sweet": "sweet"}
NOTES = {"citrus": ["c1", "c2", "c3", "c4"], "floral": ["f1", "f2", "f3", "f4"], "woody": ["w1", "w2", "w3", "w4"], "sweet": ["s1", "s2", "s3", "s4"]}


def _cat():
    notes = [{"key": k, "nameKo": k.upper(), "family": fam, "tier": "browse"} for fam, ks in NOTES.items() for k in ks]
    notes.append({"key": "hidden", "nameKo": "숨김", "family": "citrus", "tier": "hidden"})
    perfumes = []
    for fam, ks in NOTES.items():
        for i in range(12):
            perfumes.append({"key": f"{fam}{i}", "name": f"Name|{fam}{i}", "brand": f"B{i}", "accords": [fam, "woody"],
                             "top": [ks[i % 4], "hidden"], "middle": [ks[(i + 1) % 4]], "base": [NOTES["woody"][i % 4], ks[(i + 2) % 4]],
                             "accordShares": {fam: 77.123}, "noteWeights": {ks[0]: 0.777}, "estimatedAccords": True, "estimatedNotes": True,
                             "rating": 8.8, "ratingCount": 999})
    return {"accords": [{"key": a, "nameKo": a.upper(), "family": f} for a, f in FAMILY_OF_ACCORD.items()], "notes": notes, "perfumes": perfumes}


def test_조합은_분류별로_같은_수이고_규칙을_지킨다():
    cat = _cat()
    fam = {n["key"]: n["family"] for n in cat["notes"]}
    combos = BL.generate(cat, 3, set())
    assert [c["no"] for c in combos] == list(range(1, 13))
    for b in BL.BUCKETS:
        assert sum(c["bucket"] == b for c in combos) == 3
    for c in combos:
        keys = [k for k, _, _ in c["notes"]]
        assert [layer for _, layer, _ in c["notes"]] == ["top", "middle", "base"]
        assert {s for _, _, s in c["notes"]} == {"medium"}
        assert len(set(keys)) == 3 and "hidden" not in keys
        assert sum(BL.BUCKET_OF[fam[k]] == c["bucket"] for k in keys) >= 2
    assert len({frozenset(k for k, _, _ in c["notes"]) for c in combos}) == 12


def test_시드가_같으면_같고_늘려도_앞_조합은_그대로다():
    cat = _cat()
    assert BL.generate(cat, 2, set()) == BL.generate(cat, 2, set())
    assert BL.generate(cat, 3, set())[:8] == BL.generate(cat, 2, set())
    assert BL.generate(cat, 2, set(), seed=1) != BL.generate(cat, 2, set())


def test_이미_쓴_조합은_나오지_않는다():
    cat = _cat()
    first = BL.generate(cat, 2, set())
    used = {frozenset(k for k, _, _ in c["notes"]) for c in first[:4]}
    again = BL.generate(cat, 2, set(used))
    assert not used & {frozenset(k for k, _, _ in c["notes"]) for c in again}


def _sheet():
    cat = _cat()
    combo = [["c1", "top", "medium"], ["f1", "middle", "medium"], ["w1", "base", "medium"]]
    entries = [{"no": 1, "notes": combo, "A": ["citrus0", "citrus1"], "B": ["floral0", "floral1"]},
               {"no": 2, "notes": combo, "A": ["woody0"], "B": ["woody0"]}]
    return BL.sheet_text(entries, cat)


def test_시트에는_버전을_구분할_정보가_없다():
    text = _sheet()
    for leak in ("77.123", "0.777", "8.8", "999", "estimated", "추정", "강도", "v7", "v8", "엔진", "engine", "점수", "등급"):
        assert leak not in text, leak


def test_시트의_두_목록은_같은_칸이고_판정_칸은_비어_있다():
    text = _sheet()
    first = text.split("## 2.")[0]
    a, b = first.split("**A**")[1].split("**B**")
    header = "| 순위 | 향수 | 브랜드 | 주요 노트 (탑 / 미들 / 베이스) | 주요 어코드 |"
    assert header in a and header in b
    assert "Name\\|citrus0" in a and "B0" in a and "CITRUS · WOODY" in a
    assert "Name\\|floral1" in b
    assert first.count("- 판정: ☐ A  ☐ B  ☐ 비슷함") == 1 and "- 근거: \n" in first and "- 채팅 AI 의견(집계 제외): \n" in first


def test_Top_5가_완전히_같으면_판정_칸_없이_자동_비슷함():
    second = _sheet().split("## 2.")[1]
    assert "자동으로 **비슷함**" in second and "판정: ☐" not in second


def test_순서가_다르면_자동_비슷함이_아니다():
    cat = _cat()
    combo = [["c1", "top", "medium"], ["f1", "middle", "medium"], ["w1", "base", "medium"]]
    text = BL.sheet_text([{"no": 1, "notes": combo, "A": ["citrus0", "citrus1"], "B": ["citrus1", "citrus0"]}], cat)
    assert "판정: ☐" in text and "자동" not in text
