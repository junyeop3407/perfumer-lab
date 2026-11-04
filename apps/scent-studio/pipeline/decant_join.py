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

from catalog_rules import split_lead_number

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
    lead, n = split_lead_number(n, b) if b else ("", n)
    if b:
        n = n.replace(b, " ")
    n = re.sub(r"\b(19|20)\d{2}\b", " ", n)
    return b, " ".join(f"{lead} {_WORDS.sub(' ', n)}".split())


_MODIFIERS = set("candied wild golden white absolute nectar zest juice skin peel sambac co2 essence extract oil".split())
_ORIGINS = set("indian italian brazilian moroccan morocco french madagascan madagascar sicilian egyptian chinese peru sichuan tunisian spanish "
               "indonesian bulgarian bourbon calabrian somalian paraguayan persian californian california japanese damask grasse turkish haitian "
               "mexican virginia virginian texas american caribbean guatemalan amalfi mysore tahitian australian english russian african "
               "florida ceylon tonkin siberian himalayan".split())
_GENDER = re.compile(r"\b(for women|pour femme|femme|for men|pour homme|homme)\b")


def norm_note(k: str) -> str:
    """수식어·산지를 뗀 노트 이름 (대조 도구 개선, docs/data_cleaning_words.md 10절)"""
    n = norm(k)
    return " ".join(w for w in n.split() if w not in _MODIFIERS and w not in _ORIGINS) or n


def name_gender(name: str) -> str | None:
    found = {"f" if x in ("for women", "pour femme", "femme") else "m" for x in _GENDER.findall(norm(name))}
    return found.pop() if len(found) == 1 else None


def _source_gender(r: dict) -> str:
    return name_gender(r["name"]) or {"female": "f", "male": "m"}.get(r.get("gender"), "u")


def candidates(brand: str, name: str, year: int | None, index: dict) -> list[dict]:
    """같은 이름 후보: 성별이 맞는 것만, 표 많은 순, 연도가 맞는 것 우선"""
    cs = list(index.get(name_key(brand, name), ()))
    mine = name_gender(name)
    if mine:
        cs = [x for x in cs if _source_gender(x) in (mine, "u")]
    else:
        cs = [x for x in cs if not name_gender(x["name"])] or cs
    cs.sort(key=lambda r: -r["votes"])
    if year:
        cs = [r for r in cs if r["year"] and abs(r["year"] - year) <= YEAR_TOLERANCE] or cs
    return cs


def read_accord_map(path: Path) -> dict[str, str]:
    """Fragrantica 어코드(소문자) → Parfumo 어코드. 빈칸은 버리는 어코드."""
    with path.open(encoding="utf-8", newline="") as f:
        return {r["fragrantica"].strip().lower(): r["parfumo"].strip() for r in csv.DictReader(f)}


def read_note_aliases(path: Path) -> dict[str, str]:
    with path.open(encoding="utf-8", newline="") as f:
        return {norm(r["fragrantica"]): norm(r["catalog"]) for r in csv.DictReader(f)}


def read_brand_aliases(path: Path) -> dict[str, str]:
    """Decant 브랜드(정규화) → 카탈로그 브랜드(정규화). 표기만 다른 같은 브랜드 (manual/decant_brand_aliases.csv)"""
    with path.open(encoding="utf-8", newline="") as f:
        return {r["decant"]: r["catalog"] for r in csv.DictReader(f)}


def source_index(rows: list[dict], aliases: dict[str, str], brand_aliases: dict[str, str] | None = None) -> dict[tuple[str, str], list[dict]]:
    """원본 행 {id, brand, name, year, votes, notes{이름: 강도}, accords{이름: 강도}} → 이름으로 찾는 표"""
    out: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in rows:
        notes: dict[str, int] = {}
        for raw, weight in r["notes"].items():
            k = aliases.get(norm(raw), norm(raw))
            notes[k] = max(weight, notes.get(k, 0))
        brand, name = name_key(r["brand"], r["name"])
        out[(brand_aliases or {}).get(brand, brand), name].append({**r, "notes": notes})
    return out


def match(brand: str, name: str, year: int | None, notes: set[str], index: dict) -> dict | None:
    """같은 이름 후보 중 첫째(candidates). 노트가 MIN_NOTE_OVERLAP 미만 겹치면 다른 향수로 본다."""
    cands = candidates(brand, name, year, index)
    if not cands:
        return None
    hit = cands[0]
    mine, other = {norm_note(k) for k in notes}, {norm_note(k) for k in hit["notes"]}
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
    by_note: dict[str, int] = {}
    for k, v in raw.items():
        by_note[norm_note(k)] = max(v, by_note.get(norm_note(k), 0))
    found = [by_note[norm_note(k)] / 100 for k in notes if norm_note(k) in by_note]
    if not found or max(found) == 0:
        return None
    alpha = votes / (votes + VOTE_HALF)
    fill = statistics.mean(found)
    vals = {k: max(by_note[norm_note(k)] / 100 if norm_note(k) in by_note else fill, WEIGHT_FLOOR) ** alpha for k in notes}
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
        return [{"id": pid, "brand": brands.get(bid, ""), "name": name or "", "year": year, "votes": votes, "gender": gender,
                 "notes": notes[pid], "accords": accords[pid]}
                for pid, name, bid, year, votes, gender in c.execute("select id,name,brand_id,year,vote_count,gender from perfumes")]
    finally:
        c.close()
