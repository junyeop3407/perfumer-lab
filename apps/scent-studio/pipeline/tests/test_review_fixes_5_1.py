"""5-1 리뷰(QA) 지적 회귀 테스트 (docs/review.md "5-1"). 여기 '실제' 이름은 모두 지어낸 이름이다."""
import gzip
import json
import sys
from pathlib import Path

import pytest

PIPELINE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PIPELINE))

import real_names as N  # noqa: E402

SECRET = "test-only-secret-not-real"


def write_gz(path: Path, obj) -> Path:
    path.write_bytes(gzip.compress(json.dumps(obj, ensure_ascii=False).encode("utf-8"), mtime=0))
    return path


def private(*pairs):
    return {"version": "2099.01.01", "perfumes": [{"key": f"k{i}", "brand": b, "name": n} for i, (b, n) in enumerate(pairs)]}


def demo(brand="Demo Atelier", name="Demo Cedar 01", extra=None):
    c = {"perfumes": [{"key": "sample-001", "brand": brand, "name": name}]}
    if extra:
        c["extra"] = extra
    return c


@pytest.fixture
def env(tmp_path, monkeypatch):
    monkeypatch.setenv(N.SECRET_ENV, SECRET)
    return tmp_path


def test_qa1_tool_error_exit_code_differs_from_found(env, monkeypatch, capsys):
    """QA 중간-1: 목록이 다른 키로 만들어졌으면 '이름 있음'(1)이 아니라 도구 오류(2), 원인은 표준 출력에"""
    src = write_gz(env / "p.json.gz", private(("Zorbel Kint", "Velmora Nine")))
    hashes = env / "h.txt"
    N.make_hashes(src, hashes)
    monkeypatch.setenv(N.SECRET_ENV, "another-secret")
    f = write_gz(env / "s.json.gz", demo())
    assert N.main(["check", str(f), "--hashes", str(hashes), "--private", str(env / "none.json.gz")]) == 2
    assert "다시 만들어" in capsys.readouterr().out


def test_qa2_stale_hash_list_is_rebuilt_when_private_catalog_changes(env):
    """QA 중간-2: 비공개 카탈로그가 바뀌면(새 브랜드) 옛 목록을 쓰지 않고 다시 만든다"""
    src = write_gz(env / "p.json.gz", private(("Zorbel Kint", "Velmora Nine")))
    hashes = env / "h.txt"
    N.make_hashes(src, hashes)
    write_gz(src, private(("Zorbel Kint", "Velmora Nine"), ("Quorvane", "Ashfern Light")))
    f = write_gz(env / "s.json.gz", demo("Quorvane"))
    assert N.check(f, hashes, private=src).status == "fail"


def test_qa3_short_names_still_caught_in_their_own_field(env):
    """QA 중간-3: 정리 후 2자 이하 브랜드·이름도 빠지지 않는다(칸 전체가 같을 때만 — 문장 속 짧은 글자는 오탐이라 안 봄)"""
    src = write_gz(env / "p.json.gz", private(("MB", "Velmora Nine"), ("Quorvane", "X7")))
    hashes = env / "h.txt"
    N.make_hashes(src, hashes)
    assert N.check(write_gz(env / "a.json.gz", demo(" mb ")), hashes, private=src).status == "fail"
    assert N.check(write_gz(env / "b.json.gz", demo(name="x7")), hashes, private=src).status == "fail"
    ok = demo(extra={"about": "mb x7 inside a sentence"})
    assert N.check(write_gz(env / "c.json.gz", ok), hashes, private=src).status == "pass"


def test_qa7_one_word_name_outside_its_field_passes(env):
    """QA 낮음-7: 한 단어 향수 이름은 brand/name 칸 전체가 같을 때만 본다(긴 이름·다른 칸·문장 안은 통과)"""
    src = write_gz(env / "p.json.gz", private(("Zorbel Kint", "Ashfern")))
    hashes = env / "h.txt"
    N.make_hashes(src, hashes)
    for c in (demo(name="Demo Ashfern Cedar 01"), demo(extra={"about": "an ashfern breeze"})):
        assert N.check(write_gz(env / "s.json.gz", c), hashes, private=src).status == "pass"
