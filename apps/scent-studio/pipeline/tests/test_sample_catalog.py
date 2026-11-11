"""5-1 샘플 카탈로그(Plan B 공개 데모용 가상 데이터) 테스트 (QA팀 추가 테스트, 승인 대상 아님).
plan_stage5.md 1-3: 실제 데이터 파일을 읽지 않는다 · 엔진 8 카탈로그와 같은 칸 · 고정 난수면 바이트까지 같다 ·
누가 봐도 가짜인 이름(향수 300·브랜드 20) · 실제 key 모양·parfumo.com 없음.
"""
import builtins
import gzip
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path

import pytest

PIPELINE = Path(__file__).resolve().parent.parent
APP = PIPELINE.parent
sys.path.insert(0, str(PIPELINE))

import catalog_rules as R  # noqa: E402
import sample_catalog as S  # noqa: E402

FIXTURE = APP / "tests" / "fixtures" / "test_catalog.json.gz"
FAKE_WORDS = ("demo", "sample", "test", "mock", "placeholder", "example", "dummy", "fictional", "prototype", "lorem")


@pytest.fixture(scope="module")
def sample():
    return S.build()


def _shape(value):
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, (int, float)):
        return "number"
    if value is None:
        return "null"
    return type(value).__name__


def test_counts(sample):
    assert len(sample["perfumes"]) == 300
    assert len({p["brand"] for p in sample["perfumes"]}) == 20
    assert 120 <= len(sample["notes"]) <= 180


def test_source_says_sample(sample):
    assert sample["source"]["name"] == "sample"
    assert "가상" in sample["source"]["note"]
    assert sample["engineVersion"] == 8


def test_same_fields_as_engine8_catalog(sample):
    """칸 이름·형이 엔진 8 카탈로그(합성 테스트 카탈로그와 같은 모양)와 같다"""
    fx = json.loads(gzip.decompress(FIXTURE.read_bytes()))
    assert set(sample) == set(fx)
    for part in ("families", "accords", "notes"):
        want = {k: _shape(v) for k, v in fx[part][0].items()}
        for item in sample[part]:
            assert {k: _shape(v) for k, v in item.items()} == want, part
    p_fields = set(fx["perfumes"][0]) | {"estimatedAccords", "estimatedNotes"}
    for p in sample["perfumes"]:
        assert set(p) <= p_fields
        assert {"accordShares", "noteWeights"} <= set(p)
        assert isinstance(p["year"], int) or p["year"] is None
        assert isinstance(p["rating"], float) and isinstance(p["ratingCount"], int)
    assert sample["families"] == fx["families"]
    assert sample["accords"] == fx["accords"]
    assert len(sample["globalMean"]) == len(R.ACCORD_KEYS)


def test_numbers_are_consistent(sample):
    note_keys = {n["key"] for n in sample["notes"]}
    fams = {f["code"] for f in sample["families"]}
    for n in sample["notes"]:
        assert n["family"] in fams
        assert len(n["profile"]) == len(R.ACCORD_KEYS)
        assert math.isclose(sum(n["profile"]), 1.0, abs_tol=1e-4)
        assert set(n["pairings"]) <= note_keys
        assert n["tier"] in ("browse", "search", "hidden")
        assert n["nameKo"] and n["nameEn"]
    browse = [n for n in sample["notes"] if n["tier"] == "browse"]
    assert {n["family"] for n in browse} == fams, "계열마다 고를 수 있는 노트가 있다"
    for p in sample["perfumes"]:
        notes = p["top"] + p["middle"] + p["base"]
        assert notes and set(notes) <= note_keys
        assert len(set(notes)) == len(notes)
        assert set(p["noteWeights"]) == set(notes)
        assert math.isclose(sum(p["noteWeights"].values()), 1.0, abs_tol=1e-3)
        assert math.isclose(sum(p["accordShares"].values()), 100.0, abs_tol=0.05)
        assert set(p["accordShares"]) <= set(R.ACCORD_KEYS)
        assert 1 <= len(p["accords"]) <= 5
        assert p["accords"] == sorted(p["accordShares"], key=lambda a: -p["accordShares"][a])[: len(p["accords"])]
        assert 1.0 <= p["rating"] <= 10.0
    assert math.isclose(sum(sample["globalMean"]), 100.0, abs_tol=0.01)
    df = Counter(k for p in sample["perfumes"] for k in set(p["top"] + p["middle"] + p["base"]))
    for n in sample["notes"]:
        assert n["df"] == df[n["key"]]


def test_same_seed_same_bytes(tmp_path):
    a, b = tmp_path / "a.json.gz", tmp_path / "b.json.gz"
    S.main(["--out", str(a)])
    S.main(["--out", str(b)])
    assert a.read_bytes() == b.read_bytes()
    assert json.loads(gzip.decompress(a.read_bytes()))["source"]["name"] == "sample"


def test_other_seed_differs():
    assert S.build(seed=1)["perfumes"] != S.build(seed=2)["perfumes"]


def test_names_are_obviously_fake(sample):
    for p in sample["perfumes"]:
        assert any(w in p["brand"].lower() for w in FAKE_WORDS), p["brand"]
        assert any(w in p["name"].lower() for w in FAKE_WORDS), p["name"]


def test_no_real_key_shape_or_source_site(sample):
    """실제 key는 '브랜드:이름' 모양(콜론). 샘플 key는 sample- 로 시작하고 콜론이 없다"""
    text = json.dumps(sample, ensure_ascii=False).lower()
    assert "parfumo" not in text and "fragrantica" not in text and "http" not in text
    for p in sample["perfumes"]:
        assert re.fullmatch(r"sample-[0-9]{3}", p["key"]), p["key"]


def test_groups_follow_rule(sample):
    groups = Counter(p["group"] for p in sample["perfumes"])
    assert any(c > 1 for c in groups.values()), "농도만 다른 묶음이 몇 개 있다"
    for p in sample["perfumes"]:
        assert p["group"] == R.group_key(p["brand"], p["name"], p["concentration"])


def test_reads_no_real_data(monkeypatch):
    """실제 데이터 파일을 하나도 읽지 않는다: 여는 파일은 pipeline/manual/notes_ko.csv 하나뿐"""
    opened = []
    real_open = builtins.open

    def spy(file, *a, **k):
        opened.append(Path(file).resolve())
        return real_open(file, *a, **k)

    monkeypatch.setattr(builtins, "open", spy)
    monkeypatch.setattr(Path, "open", lambda self, *a, **k: spy(self, *a, **k))
    S.build()
    assert opened, "노트 한글명 파일은 읽는다"
    assert set(opened) == {(PIPELINE / "manual" / "notes_ko.csv").resolve()}
    src = (PIPELINE / "sample_catalog.py").read_text(encoding="utf-8")
    for bad in ("data/out", "data/raw", '"out"', '"raw"', "decant", "PRIVATE_DOCS", "private_doc", "load_old_catalog"):
        assert bad not in src, bad


def test_gzip_header_has_no_time_or_file_name(sample):
    """같은 seed면 언제 만들어도 같은 바이트: gzip 머리에 시각(mtime)·파일 이름을 넣지 않는다"""
    body = S.to_bytes(sample)
    assert body[:2] == b"\x1f\x8b"
    assert body[3] & 0x08 == 0, "파일 이름 표시 없음"
    assert body[4:8] == b"\x00\x00\x00\x00", "시각 0"
