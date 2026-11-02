"""Decant(Fragrantica 데이터셋)를 Parfumo 카탈로그 향수에 연결한다 (engine v7, docs/engine_v2_trial.md).

연결된 향수만 어코드 강도(accord_shares)와 노트 강도 몫(note_weights)을 얻고, 나머지는 지금처럼 계산한다.
시험에서 정한 값: 노트가 30% 미만 겹치면 다른 향수로 본다, α = 표 / (표 + 70), 강도 하한 0.05.
"""
from __future__ import annotations

import csv
import re
import sqlite3
import statistics
import unicodedata
from collections import defaultdict
from pathlib import Path

MIN_NOTE_OVERLAP = 0.3
VOTE_HALF = 70  # 신뢰 하한 참고값 약 70표. α = 표 / (표 + 70)
WEIGHT_FLOOR = 0.05  # 강도 0 = 아무도 안 뽑은 노트. 0으로 두면 노트가 사라지므로 아주 작은 값을 준다
YEAR_TOLERANCE = 1

_WORDS = re.compile(r"\b(eau de parfum intense|extrait de parfum|eau de parfum|eau de toilette|eau de cologne|after shave lotion|"
                    r"after shave|extrait|parfum|perfume|cologne|edp|edt|edc|pour homme|pour femme|for men|for women)\b")


def norm(s: str | None) -> str:
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def name_key(brand: str, name: str) -> tuple[str, str]:
    """(브랜드, 이름에서 브랜드·연도·농도를 뺀 것)"""
    n, b = norm(name), norm(brand)
    if b:
        n = n.replace(b, " ")
    n = re.sub(r"\b(19|20)\d{2}\b", " ", n)
    return b, " ".join(_WORDS.sub(" ", n).split())


def read_accord_map(path: Path) -> dict[str, str]:
    """Fragrantica 어코드(소문자) → Parfumo 어코드. 빈칸은 버리는 어코드."""
    with path.open(encoding="utf-8", newline="") as f:
        return {r["fragrantica"].strip().lower(): r["parfumo"].strip() for r in csv.DictReader(f)}


def read_note_aliases(path: Path) -> dict[str, str]:
    with path.open(encoding="utf-8", newline="") as f:
        return {norm(r["fragrantica"]): norm(r["catalog"]) for r in csv.DictReader(f)}


def source_index(rows: list[dict], aliases: dict[str, str]) -> dict[tuple[str, str], list[dict]]:
    """원본 행 {id, brand, name, year, votes, notes{이름: 강도}, accords{이름: 강도}} → 이름으로 찾는 표"""
    out: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in rows:
        notes: dict[str, int] = {}
        for raw, weight in r["notes"].items():
            k = aliases.get(norm(raw), norm(raw))
            notes[k] = max(weight, notes.get(k, 0))
        out[name_key(r["brand"], r["name"])].append({**r, "notes": notes})
    return out


def match(brand: str, name: str, year: int | None, notes: set[str], index: dict) -> dict | None:
    """같은 이름 중 표가 많은 향수(연도가 맞는 것 우선). 노트가 MIN_NOTE_OVERLAP 미만 겹치면 다른 향수로 본다."""
    cands = sorted(index.get(name_key(brand, name), ()), key=lambda r: -r["votes"])
    if not cands:
        return None
    if year:
        cands = [r for r in cands if r["year"] and abs(r["year"] - year) <= YEAR_TOLERANCE] or cands
    hit = cands[0]
    mine, other = {norm(k) for k in notes}, set(hit["notes"])
    union = mine | other
    return hit if union and len(mine & other) / len(union) >= MIN_NOTE_OVERLAP else None


def accord_shares(strengths: dict[str, int], accord_map: dict[str, str]) -> dict[str, float] | None:
    """어코드 강도 → Parfumo 어코드별 비율(합 100). 연결되는 어코드가 없으면 None."""
    total: dict[str, float] = defaultdict(float)
    for name, s in strengths.items():
        target = accord_map.get(name.lower())
        if target:
            total[target] += s
    all_sum = sum(total.values())
    return {a: 100.0 * v / all_sum for a, v in total.items()} if all_sum > 0 else None


def note_weights(notes: set[str], raw: dict[str, int], votes: int) -> dict[str, float] | None:
    """향수 안의 노트 강도 몫 w (합 1). w ∝ max(강도, 하한)^α. 투표가 없는 향수는 None(같은 몫으로 계산)."""
    found = [raw[norm(k)] / 100 for k in notes if norm(k) in raw]
    if not found or max(found) == 0:
        return None
    alpha = votes / (votes + VOTE_HALF)
    fill = statistics.mean(found)
    vals = {k: max(raw[norm(k)] / 100 if norm(k) in raw else fill, WEIGHT_FLOOR) ** alpha for k in notes}
    total = sum(vals.values())
    return {k: v / total for k, v in vals.items()}


def open_db(db: Path) -> sqlite3.Connection:
    """받은 DB는 외부 파일이므로 읽기 전용으로 열고, 파일 안의 스키마에 든 함수를 믿지 않는다."""
    c = sqlite3.connect(f"file:{db.as_posix()}?mode=ro", uri=True)
    c.execute("PRAGMA trusted_schema=OFF")
    return c


def load_rows(db: Path) -> list[dict]:
    """perfumes.db → 원본 행 목록"""
    c = open_db(db)
    try:
        brands = dict(c.execute("select id,name from brands"))
        note_names = dict(c.execute("select id,name from notes"))
        accord_names = dict(c.execute("select id,name from accords"))
        notes: dict[int, dict[str, int]] = defaultdict(dict)
        for pid, nid, w in c.execute("select perfume_id,note_id,weight from perfume_notes"):
            name = note_names[nid]
            notes[pid][name] = max(w, notes[pid].get(name, 0))
        accords: dict[int, dict[str, int]] = defaultdict(dict)
        for pid, aid, s in c.execute("select perfume_id,accord_id,strength from perfume_accords"):
            accords[pid][accord_names[aid]] = s
        return [{"id": pid, "brand": brands.get(bid, ""), "name": name or "", "year": year, "votes": votes,
                 "notes": notes[pid], "accords": accords[pid]}
                for pid, name, bid, year, votes in c.execute("select id,name,brand_id,year,vote_count from perfumes")]
    finally:
        c.close()
