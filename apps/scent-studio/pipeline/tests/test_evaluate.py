"""기준표·블라인드 집계 테스트 (QA팀 추가 테스트, 2026-10-31)"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import evaluate as V  # noqa: E402


def test_조합_일부만_답해도_집계한다(tmp_path):
    f = tmp_path / "answers.txt"
    f.write_text("출처: 사장\n블라인드: 11A 12B\n점수: 11=4 12=2\n", encoding="utf-8")
    ans = V.read_answers(f, n_combos=12)
    assert ans["combos"] == {11: "A", 12: "B"}
    assert ans["scores"] == {11: 4, 12: 2}
    side = {i: "A" for i in range(1, 13)}
    out = V.combo_result(ans, side, [(f"조합{i}", []) for i in range(1, 13)])
    assert "부분 답 2 / 12개: 지금 엔진 쪽 1개" in out[0]
    assert len([l for l in out if l.startswith("| 1")]) == 2  # 답한 두 조합만 표에


def test_모든_조합에_답하면_판정한다(tmp_path):
    f = tmp_path / "answers.txt"
    f.write_text("블라인드: " + " ".join(f"{i}A" for i in range(1, 11)) + "\n", encoding="utf-8")
    ans = V.read_answers(f, n_combos=10)
    side = {i: ("A" if i <= 6 else "B") for i in range(1, 11)}
    assert V.combo_result(ans, side, [("x", [])] * 10)[0].startswith("**결과: 6 / 10 ✅")


def test_순환과_한_칸_밀림을_표시한다():
    # 6번 모양: 2→3→4가 같은 향수들끼리 자리만 돈다, 1·5번 줄은 양쪽이 같다
    notes = V.undecidable_notes(6, ["m", "z", "a", "e", "v"], ["m", "a", "e", "z", "v"])
    assert any("6-1" in n and "같은 향수" in n for n in notes)
    assert any("순환" in n and "6-2/6-3/6-4" in n for n in notes)
    # 9번 모양: B가 A를 한 칸 아래로 민 목록
    notes = V.undecidable_notes(9, ["a", "b", "c", "d", "e"], ["n", "a", "b", "c", "d"])
    assert any("한 칸씩 밀림" in n and "B 1위만 새 향수" in n for n in notes)
    assert V.undecidable_notes(1, ["a", "b", "c"], ["x", "y", "z"]) == []


def test_등급_기준값_후보를_사람_점수와_비교한다():
    rec = lambda *scores: [{"score": s} for s in scores]  # noqa: E731
    tops = {1: rec(0.95, 0.90, 0.85), 2: rec(0.88, 0.86, 0.80)}
    rows = V.grade_candidate_rows(tops, {1: 5, 2: 2})
    first = rows[0]  # 0.87 / 0.79: 1번(5점) 1위 매우 비슷, 2번(2점)에도 매우 비슷 있음
    assert first.startswith("| 0.87 / 0.79 | 1/1 (100%) | 1/1 (100%) | 0 |")
    last = rows[-1]  # 0.92 / 0.87: 2번에는 매우 비슷 없음
    assert last.startswith("| 0.92 / 0.87 | 1/1 (100%) | 0/1 (0%) | 1 |")
