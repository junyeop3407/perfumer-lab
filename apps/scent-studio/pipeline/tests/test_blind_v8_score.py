"""8단계 블라인드 채점 부품 테스트 (QA팀 추가 테스트). 가짜 시트와 가짜 키로 확인한다."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import blind_v8 as BL  # noqa: E402

BLANK = "- 판정: ☐ A  ☐ B  ☐ 비슷함\n- 근거: \n- 채팅 AI 의견(집계 제외): \n"
SHEET = f"# 시트\n\n## 1. x\n\n| 표 |\n\n{BLANK}\n## 2. y\n\n두 목록이 완전히 같아 자동으로 **비슷함**입니다.\n\n## 3. z\n\n{BLANK}\n## 4. w\n\n{BLANK}\n"
KEY = {"1": {"A": "v7", "B": "v8"}, "3": {"A": "v8", "B": "v7"}, "4": {"A": "v8", "B": "v7"}}


def test_채운_판정을_다시_읽고_표와_AI_의견_칸은_그대로다():
    text = BL.fill_sheet(SHEET, {1: "B", 3: "A", 4: "비슷함"}, "채팅 AI 판정")
    assert BL.read_judgements(text) == {1: "B", 3: "A", 4: "비슷함"}
    assert text.count("- 근거: 채팅 AI 판정\n") == 3 and text.count("- 채팅 AI 의견(집계 제외): \n") == 3
    assert "| 표 |" in text and "자동으로 **비슷함**" in text


@pytest.mark.parametrize("picks", [{1: "A", 3: "A"}, {1: "A", 2: "A", 3: "A", 4: "A"}])
def test_판정_번호가_시트와_다르면_멈춘다(picks):
    with pytest.raises(SystemExit):
        BL.fill_sheet(SHEET, picks, "r")


@pytest.mark.parametrize("line", ["☐ A  ☐ B  ☐ 비슷함", "☑ A  ☑ B  ☐ 비슷함"])
def test_표시가_하나가_아니면_멈춘다(line):
    with pytest.raises(SystemExit):
        BL.read_judgements(f"## 1. x\n\n- 판정: {line}\n")


def test_A_B를_키로_버전으로_바꾸고_비슷함은_뺀다():
    assert BL.winners({1: "B", 3: "B", 4: "비슷함"}, KEY) == {1: "v8", 3: "v7"}


def test_부호_검정의_정확한_p값():
    assert BL.sign_p(0, 0) == 1.0
    assert BL.sign_p(13, 13) == 1.0
    assert BL.sign_p(15, 5) == pytest.approx(0.041389, abs=1e-6)
    assert BL.sign_p(5, 15) == BL.sign_p(15, 5)
    assert BL.sign_p(14, 6) == pytest.approx(0.115318, abs=1e-6)


def test_규칙_표의_필요_승수에서_결론이_갈린다():
    for n, need in {15: 12, 20: 15, 25: 18, 30: 21, 35: 24, 40: 27}.items():
        assert BL.sign_p(need, n - need) < 0.05 <= BL.sign_p(need - 1, n - need + 1)


def test_결론():
    assert BL.verdict(15, 5) == "v8 유의 승"
    assert BL.verdict(5, 15) == "v7 유의 승"
    assert BL.verdict(14, 6) == "유의차 없음"
    assert BL.verdict(10, 9) == "유효 부족(8개 추가)"
