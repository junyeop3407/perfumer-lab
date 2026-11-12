"""5-2 사장 결정(2026-11-12): 노트 한글 이름 겹침 고치기 — 원본 표(manual/notes_ko.csv).
앱(app/src/catalog/noteNames.ts NOTE_NAME_FIXES)도 같은 값으로 지금 배포된 카탈로그를 고친다."""
import csv
import re
from pathlib import Path

PIPELINE = Path(__file__).resolve().parent.parent
NOTES_KO = PIPELINE / "manual" / "notes_ko.csv"
APP_FIXES = PIPELINE.parent / "app" / "src" / "catalog" / "noteNames.ts"

FIXED = {"oud": "우드(침향)", "woods": "나무향", "white oud": "화이트 우드(침향)"}


def table() -> dict[str, str]:
    with NOTES_KO.open(encoding="utf-8", newline="") as f:
        return {r["note_key"]: r["name_ko"] for r in csv.DictReader(f)}


def test_oud_woods_white_oud_names():
    t = table()
    for key, name in FIXED.items():
        assert t[key] == name


def test_no_other_note_named_like_fixed_ones():
    t = table()
    for key, name in FIXED.items():
        assert [k for k, v in t.items() if v == name] == [key]


def test_app_fixes_match_table():
    src = APP_FIXES.read_text(encoding="utf-8")
    block = src[src.index("NOTE_NAME_FIXES"): src.index("};")]
    app = dict(re.findall(r"^\s*'?([a-z ]+?)'?: '([^']+)'", block, re.MULTILINE))
    assert app == FIXED
