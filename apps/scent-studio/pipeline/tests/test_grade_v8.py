"""10단계 등급 기준값 부품 테스트 (QA팀 추가 테스트)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import grade_v8 as G  # noqa: E402

VS, S, R = G.GRADES


def test_기준값은_상위_10퍼센트와_40퍼센트이고_셋째_자리다():
    pool = [i / 100 for i in range(101)]
    assert G.thresholds(pool) == (0.9, 0.6)
    assert G.thresholds([0.12345] * 3) == (0.123, 0.123)


def test_경계값은_위_등급이다():
    cut = (0.76, 0.63)
    assert [G.grade_of(x, cut) for x in (0.76, 0.7599, 0.63, 0.6299)] == [VS, S, S, R]


def test_순서와_평균을_모두_맞으면_통과():
    assert G.verdict({VS: [5] * 5, S: [4] * 5, R: [3] * 5})[0]


def test_순서가_어긋나면_실패():
    assert not G.verdict({VS: [4] * 5, S: [5] * 5, R: [3] * 5})[0]
    assert not G.verdict({VS: [5] * 5, S: [3] * 5, R: [3] * 5})[0]


def test_매우_비슷_평균이_4_미만이면_실패():
    assert not G.verdict({VS: [3.9] * 5, S: [3] * 5, R: [2] * 5})[0]


def test_5개_미만_등급은_순서에서_뺀다():
    assert G.verdict({VS: [5] * 5, S: [4] * 5, R: [5] * 4})[0]


def test_판정할_등급이_부족하면_통과가_아니다():
    assert not G.verdict({VS: [5] * 5, S: [4] * 4, R: []})[0]
    assert not G.verdict({VS: [5] * 4, S: [4] * 5, R: [3] * 5})[0]
