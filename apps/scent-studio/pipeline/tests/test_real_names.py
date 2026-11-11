"""5-1 "공개 빌드에 실제 이름이 있으면 실패" 검사 테스트 (QA팀 추가 테스트, 승인 대상 아님).
plan_stage5.md 1-4: 실제 이름 목록은 공개 저장소에 두지 않는다 → 비밀 키를 섞은 HMAC 해시 목록(커밋 안 함)만 쓴다.
키나 해시 목록이 없으면 '건너뜀'. 브랜드·향수 이름 둘 다 검사.
여기서 쓰는 '실제' 이름은 모두 테스트용으로 지어낸 이름이다.
"""
import gzip
import json
import subprocess
import sys
from pathlib import Path

import pytest

PIPELINE = Path(__file__).resolve().parent.parent
APP = PIPELINE.parent
sys.path.insert(0, str(PIPELINE))

import real_names as N  # noqa: E402
import sample_catalog as S  # noqa: E402

SECRET = "test-only-secret-not-real"
PRIVATE = {  # 지어낸 '비공개' 카탈로그
    "version": "2099.01.01", "engineVersion": 8, "source": {"name": "Parfumo", "note": "x"},
    "perfumes": [
        {"key": "Zorbel_Kint:Velmora_Nine", "brand": "Zorbel Kint", "name": "Velmora Nine"},
        {"key": "Quillane:Ashfern", "brand": "Quillane", "name": "Ashfern"},
        {"key": "Brisko:Noctua Élan", "brand": "Brisko", "name": "Noctua Élan Absolu"},
    ],
}


def write_gz(path: Path, obj) -> Path:
    path.write_bytes(gzip.compress(json.dumps(obj, ensure_ascii=False).encode("utf-8"), mtime=0))
    return path


@pytest.fixture
def hashes(tmp_path, monkeypatch):
    monkeypatch.setenv(N.SECRET_ENV, SECRET)
    src = write_gz(tmp_path / "private.json.gz", PRIVATE)
    out = tmp_path / "real_name_hashes.txt"
    N.make_hashes(src, out)
    return out


def demo(brand="Demo Atelier", name="Demo Cedar No. 1", extra=None):
    c = {"source": {"name": "sample", "note": "가상 데이터"},
         "perfumes": [{"key": "sample-001", "brand": brand, "name": name}]}
    if extra:
        c["extra"] = extra
    return c


def test_hash_file_has_no_plain_names(hashes):
    text = hashes.read_text(encoding="utf-8").lower()
    for word in ("zorbel", "kint", "velmora", "quillane", "ashfern", "brisko", "noctua"):
        assert word not in text
    lines = [l for l in text.splitlines() if l and not l.startswith("#")]
    assert lines and all(len(l.split()) == 2 and l.split()[0] in ("b", "n") and len(l.split()[1]) == 64 for l in lines)


def test_hash_depends_on_secret(tmp_path, monkeypatch, hashes):
    monkeypatch.setenv(N.SECRET_ENV, "another-secret")
    other = tmp_path / "other.txt"
    N.make_hashes(tmp_path / "private.json.gz", other)
    assert set(other.read_text().splitlines()[1:]).isdisjoint(set(hashes.read_text().splitlines()[1:]))


def test_clean_sample_passes(tmp_path, hashes):
    f = write_gz(tmp_path / "s.json.gz", demo())
    r = N.check(f, hashes)
    assert r.status == "pass" and r.hits == 0


@pytest.mark.parametrize("brand,name", [
    ("Zorbel Kint", "Demo Cedar No. 1"),          # 브랜드 칸
    ("Demo Atelier", "Velmora Nine"),             # 이름 칸
    ("Demo Atelier", "ASHFERN"),                  # 한 단어 이름, 대소문자
    ("QUILLANE", "Demo Cedar No. 1"),             # 한 단어 브랜드
    ("Demo Atelier", "noctua elan absolu"),       # 악센트 없이
    ("Demo Atelier", "Sample Velmora   Nine Test"),  # 이름 안의 단어 묶음
    ("Sample Zorbel-Kint House", "Demo No. 2"),   # 브랜드 안의 단어 묶음, 기호
])
def test_real_name_in_fields_fails(tmp_path, hashes, brand, name):
    f = write_gz(tmp_path / "s.json.gz", demo(brand, name))
    r = N.check(f, hashes)
    assert r.status == "fail" and r.hits >= 1


def test_real_name_in_other_text_fails(tmp_path, hashes):
    f = write_gz(tmp_path / "s.json.gz", demo(extra={"about": "inspired by Zorbel Kint"}))
    assert N.check(f, hashes).status == "fail"


def test_plain_text_bundle_checked(tmp_path, hashes):
    """웹 번들 같은 일반 글자 파일도 검사한다"""
    js = tmp_path / "bundle.js"
    js.write_text('var a="Hello";var b="Velmora Nine";', encoding="utf-8")
    assert N.check(js, hashes).status == "fail"
    js.write_text('var a="Hello Demo";', encoding="utf-8")
    assert N.check(js, hashes).status == "pass"


def test_report_does_not_print_names(tmp_path, hashes, capsys):
    f = write_gz(tmp_path / "s.json.gz", demo("Zorbel Kint", "Velmora Nine"))
    assert N.main(["check", str(f), "--hashes", str(hashes)]) == 1
    out = capsys.readouterr()
    text = (out.out + out.err).lower()
    assert "zorbel" not in text and "velmora" not in text


def test_skipped_without_secret(tmp_path, hashes, monkeypatch, capsys):
    monkeypatch.delenv(N.SECRET_ENV, raising=False)
    f = write_gz(tmp_path / "s.json.gz", demo("Zorbel Kint"))
    assert N.check(f, hashes).status == "skipped"
    assert N.main(["check", str(f), "--hashes", str(hashes)]) == 0
    assert "건너뜀" in capsys.readouterr().out
    assert N.main(["check", str(f), "--hashes", str(hashes), "--require"]) == 3


def test_skipped_without_hash_file(tmp_path, monkeypatch):
    monkeypatch.setenv(N.SECRET_ENV, SECRET)
    f = write_gz(tmp_path / "s.json.gz", demo("Zorbel Kint"))
    r = N.check(f, tmp_path / "missing.txt", private=tmp_path / "missing.json.gz")
    assert r.status == "skipped"


def test_builds_hash_file_when_private_catalog_exists(tmp_path, monkeypatch):
    """해시 목록이 없어도 비공개 카탈로그와 키가 있으면 그 자리에서 만들어 검사한다"""
    monkeypatch.setenv(N.SECRET_ENV, SECRET)
    private = write_gz(tmp_path / "private.json.gz", PRIVATE)
    hashes = tmp_path / "h.txt"
    f = write_gz(tmp_path / "s.json.gz", demo("Quillane"))
    assert N.check(f, hashes, private=private).status == "fail"
    assert hashes.exists()


def test_hash_file_from_other_secret_is_rejected(tmp_path, hashes, monkeypatch):
    """다른 키로 만든 목록은 아무것도 못 잡으므로 '건너뜀'이 아니라 오류로 멈춘다"""
    monkeypatch.setenv(N.SECRET_ENV, "another-secret")
    f = write_gz(tmp_path / "s.json.gz", demo("Zorbel Kint"))
    with pytest.raises(SystemExit):
        N.check(f, hashes)


def test_generated_sample_passes(tmp_path, hashes):
    f = tmp_path / "sample.json.gz"
    S.main(["--out", str(f)])
    assert N.check(f, hashes).status == "pass"


def test_hash_file_is_git_ignored():
    """해시 목록 자체도 커밋하지 않는다"""
    r = subprocess.run(["git", "check-ignore", "-q", str(N.DEFAULT_HASHES)], cwd=APP)
    assert r.returncode == 0
    assert N.DEFAULT_HASHES.name == "real_name_hashes.txt"


def test_real_name_as_field_name_fails_and_is_not_printed(tmp_path, hashes, capsys):
    """칸 이름(JSON key)도 글자다. 걸린 칸 이름은 위치에도 쓰지 않는다"""
    f = write_gz(tmp_path / "s.json.gz", demo(extra={"Velmora Nine": 0.5, "rose": 0.5}))
    r = N.check(f, hashes)
    assert r.status == "fail"
    assert N.main(["check", str(f), "--hashes", str(hashes)]) == 1
    assert "velmora" not in capsys.readouterr().out.lower()
