"""v8 제외 목록을 복구된 이름 기준으로 다시 판정해 해시로 고정한다 (2026-11-04 사장 결정). 한 번 돌리면 된다.

앱 폴더에서:  py pipeline/experiments/freeze_exclusions.py
읽는 것: pipeline/data/out/의 정리 전 catalog.json.gz, excluded_keys.csv, review_candidates.csv (decant_audit.py의 결과)
만드는 것: pipeline/manual/{filtered,review}_keys.csv (비밀 키를 섞은 HMAC, 커밋), pipeline/data/out/의 이름 든 표 (커밋 안 함)

- 오염 1,015개는 확정이라 다시 판정하지 않는다. 복구로 새로 후보가 된 행에만 같은 규칙을 쓴다.
- 검토 대상는 decant_audit.py의 정의 그대로 다시 판정한다. 연도 차이 5년 이상이면서 어코드 겹침 50% 미만이면 제외, 나머지는 표시.
- 먼저 정리 전 카탈로그로 지난 판정을 그대로 재현하는지 확인한다.
"""
from __future__ import annotations

import csv
import gzip
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import build_catalog as B  # noqa: E402
import catalog_rules as R  # noqa: E402
import decant_join as D  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
OUT, MANUAL = APP / "pipeline" / "data" / "out", APP / "pipeline" / "manual"
LAY = ("top", "middle", "base")
src = (APP / "pipeline" / "experiments" / "engine_v8_trial.py").read_text(encoding="utf-8")
FOUL = set(re.search(r'FOUL = set\("([^"]+)"', src).group(1).split("|"))
AMB = set("gasoline|asphalt|rubber|dust|mud|fish|grease|chlorine|melting plastic|burnt toast|rust|diesel|sulfur|swamp|chemical|acid|damp|smog|funk|skunk|mildew|mold|moldy leather|burnt electronics|fume".split("|"))

RAW = B.read_csv(APP / "pipeline" / "data" / "raw" / "parfumo_data_clean.csv")
DECANT = D.load_rows(APP / "pipeline" / "data" / "raw" / "decant" / "perfumes.db")
NOTE_ALIASES = D.read_note_aliases(MANUAL / "fragrantica_note_aliases.csv")
AMAP = D.read_accord_map(MANUAL / "fragrantica_accords.csv")
IDX = D.source_index(DECANT, NOTE_ALIASES)
JUNK = B.read_junk(MANUAL / "junk_notes.csv")
ALIASES = B.read_aliases(MANUAL / "aliases.csv")
BRAND_FIXES = B.read_brand_fixes(MANUAL / "brand_fixes.csv")
JUNK_KEYS = {R.perfume_key(r["URL"]) for r in RAW
             if any(R.note_key(n) in JUNK for c in ("Top_Notes", "Middle_Notes", "Base_Notes") for n in R.split_list(r[c]))}


def jac(a: set, b: set) -> float:
    return len(a & b) / len(a | b) if a | b else 0.0


def loose(a: set, b: set) -> float:
    wa, wb = [set(x.split()) for x in a], [set(x.split()) for x in b]
    tot = len(a) + len(b)
    return (sum(any(x <= y or y <= x for y in wb) for x in wa) + sum(any(x <= y or y <= x for y in wa) for x in wb)) / tot if tot else 0.0


def top_accords(strengths: dict) -> set:
    s = D.accord_shares(strengths, AMAP)
    return set(sorted(s, key=lambda a: -s[a])[:5]) if s else set()


def notes_of(p: dict) -> set:
    return {k for layer in LAY for k in p[layer]}


def polluted(p: dict) -> tuple[str, bool]:
    """(묶음, 오염 규칙으로 제외되는가). decant_audit.py의 excluded()와 같은 규칙: 옛 후보 고르기, 정규화 안 한 노트"""
    ns = notes_of(p)
    group = "certain" if ns & (FOUL - AMB) else "ambiguous" if ns & AMB else "junk" if p["key"] in JUNK_KEYS else "clean"
    if group in ("certain", "junk"):
        return group, True
    if group == "clean":
        return group, False
    cs = sorted(IDX.get(D.name_key(p["brand"], p["name"]), ()), key=lambda r: -r["votes"])
    if p["year"]:
        cs = [r for r in cs if r["year"] and abs(r["year"] - int(p["year"])) <= 1] or cs
    hit = cs[0] if cs and cs[0]["notes"] else None
    return group, not (hit and jac({D.norm(k) for k in ns - FOUL}, set(hit["notes"])) >= 0.3)


def judge(p: dict) -> dict | None:
    """검토 대상 정의(decant_audit.py sus2). 해당하지 않으면 None"""
    cs = D.candidates(p["brand"], p["name"], int(p["year"]) if p["year"] else None, IDX)
    h = cs[0] if cs and cs[0]["notes"] else None
    if not h or not p["year"] or not h["year"]:
        return None
    rest = {D.norm_note(k) for k in notes_of(p) - FOUL}
    other = {D.norm_note(k) for k in h["notes"]}
    best = max((jac(rest, {D.norm_note(k) for k in x["notes"]}) for x in cs if x["notes"]), default=0)
    gap = abs(int(p["year"]) - h["year"])
    if jac(rest, other) >= 0.3 or gap < 2 or loose(rest, other) >= 0.5 or best >= 0.3:
        return None
    return {"gap": gap, "acc": jac(set(p["accords"]), top_accords(h["accords"])), "hit": h}


def suspects_of(perfumes: list[dict]) -> dict[str, dict]:
    out = {}
    for p in perfumes:
        if polluted(p)[0] == "clean" and (j := judge(p)):
            out[p["key"]] = j
    return out


def excluded_by(sus: dict[str, dict]) -> set[str]:
    return {k for k, j in sus.items() if j["gap"] >= 5 and j["acc"] < 0.5}


def write_hashes(path: Path, keys: set[str]) -> None:
    # 사유는 적지 않는다. 목록은 어떤 판단도 드러내지 않게 둔다
    path.write_text("key_hmac\n" + "".join(h + "\n" for h in sorted(map(B.key_hash, keys))), encoding="utf-8", newline="\n")


def write_table(name: str, rows: list[dict]) -> None:
    with (OUT / name).open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]) if rows else ["empty"])
        w.writeheader()
        w.writerows(rows)


def row(p: dict, **extra) -> dict:
    return {"brand": p["brand"], "name": p["name"], "year": p["year"] or "", "ratings": p["ratingCount"], **extra}


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    with (OUT / "excluded_keys.csv").open(encoding="utf-8", newline="") as f:
        frozen = {r["key"] for r in csv.DictReader(f)}
    with (OUT / "review_candidates.csv").open(encoding="utf-8", newline="") as f:
        old_gap = {r["key"]: int(r["year_gap"]) for r in csv.DictReader(f)}
    old_cat = json.loads(gzip.open(OUT / "catalog.json.gz", "rb").read())["perfumes"]
    old = {p["key"]: p for p in old_cat}

    # 자체 확인: 복구 전 카탈로그로 지난 판정을 재현한다
    re_pol = {p["key"] for p in old_cat if polluted(p)[1]}
    re_sus = suspects_of(old_cat)
    print("재현: 오염", len(re_pol), "기록과 차이", len(re_pol ^ frozen), "| 의심", len(re_sus), "기록과 차이", len(set(re_sus) ^ set(old_gap)),
          "(맨 앞 번호 보호로 바뀌는 이름 잘림 행)", "| 제외", len(excluded_by(re_sus)))
    old_ex = excluded_by(re_sus)
    assert old_ex <= set(old_gap)
    old_flag = set(old_gap) - old_ex  # '이전' 판정은 기록을 기준으로 한다

    cat = B.build(RAW, R.REAL, ALIASES, B.read_overrides(MANUAL / "note_family_override.csv"), B.read_korean(MANUAL / "notes_ko.csv"),
                  version="freeze", source_name="x", source_note="x", junk=JUNK, brand_fixes=BRAND_FIXES,
                  keep_separate=B.keys_from_hashes(RAW, MANUAL / "separate_keys.csv"),
                  decant={"rows": DECANT, "accord_map": AMAP, "aliases": NOTE_ALIASES}).catalog["perfumes"]
    ps = {p["key"]: p for p in cat}
    print("복구 후 정리 전 후보", len(ps), "새로 들어옴", len(set(ps) - set(old)), "빠짐", len(set(old) - set(ps)))
    assert not set(old) - set(ps), "복구로 빠진 행이 있으면 안 된다"

    new_rows = [ps[k] for k in set(ps) - set(old)]
    new_pol = {p["key"] for p in new_rows if polluted(p)[1]}
    pollution = (frozen & set(ps)) | new_pol
    print("오염: 확정", len(frozen), "그중 후보에 있음", len(frozen & set(ps)), "| 새 행 중 같은 규칙으로 제외", len(new_pol), Counter(polluted(ps[k])[0] for k in new_pol))
    passes = [ps[k] for k in frozen if k in ps and not polluted(ps[k])[1]]
    print("오염 확정 중 복구 후 규칙을 통과", len(passes))

    sus = {k: j for k, j in suspects_of(list(ps.values())).items() if k not in pollution}
    cleared = B.keys_from_hashes(RAW, MANUAL / "reviewed_keys.csv")  # 사장이 웹으로 진짜임을 확인한 행은 제외하지 않고 표시만
    hidden = excluded_by(sus) - cleared
    flags = set(sus) - hidden
    assert cleared <= flags
    print("검토 대상: 의심", len(sus), "제외", len(hidden), "표시", len(flags))
    print("제외 키", len(pollution | hidden), "최종 후보", len(set(ps) - pollution - hidden))
    strength = sum(bool(p.get("noteWeights")) for k, p in ps.items() if k not in pollution | hidden)
    print("강도 보유(옛 연결)", strength, f"{100 * strength / len(set(ps) - pollution - hidden):.2f}%")

    write_hashes(MANUAL / "filtered_keys.csv", pollution | hidden)
    write_hashes(MANUAL / "review_keys.csv", flags)

    # 표시 40개가 어떻게 바뀌었나
    changes = []
    for k in sorted(old_ex | old_flag | hidden | flags):
        before = "제외" if k in old_ex else "표시" if k in old_flag else "없음"
        after = "제외" if k in hidden else "표시" if k in flags else "의심 아님"
        if before != after:
            p = ps[k]
            changes.append(row(p, key=k, before=before, after=after, gap=(sus.get(k) or {}).get("gap", ""), accord_overlap=round((sus.get(k) or {}).get("acc", 0), 2) if k in sus else ""))
    write_table("exclusion_changes.csv", changes)
    print("판정 변경", Counter((c["before"], c["after"]) for c in changes))
    for c in changes:
        print("  ", c["before"], "→", c["after"], "|", c["brand"], "|", c["name"], "|", c["year"], "| 평가", c["ratings"], "| gap", c["gap"], "| 겹침", c["accord_overlap"])

    # 보류 목록
    write_table("hold_list_web_check_10.csv", [row(ps[k], label="웹 재출시작 확인 후보", gap=sus[k]["gap"], accord_overlap=round(sus[k]["acc"], 2))
                                               for k in sorted(hidden, key=lambda k: (-ps[k]["ratingCount"], k))[:10]])
    write_table("hold_list_ambiguous_pass.csv", [row(p, label="이름 복구 후 오염 규칙 통과") for p in sorted(passes, key=lambda p: -p["ratingCount"])])
    print("-- 복구 후 규칙을 통과하는 오염 행")
    for p in passes:
        print("  ", p["brand"], "|", p["name"], "|", p["year"], "| 평가", p["ratingCount"])

    # 212 의심 행
    k212 = next(k for k in ps if k.lower().endswith(":212") and "Carolina_Herrera" in k)
    p = ps[k212]
    print("-- 212 행:", p["name"], "| 강도", bool(p.get("noteWeights")), "| 의심", k212 in sus, "| 제외", k212 in hidden, "| 표시", k212 in flags)

    # 주소에 번호가 없는 최종 후보
    final = set(ps) - pollution - hidden
    no_slug = []
    for r in RAW:
        k = R.perfume_key(r["URL"])
        num = (r["Number"] or "").strip()
        if k in final and num[:1].isdigit() and num.rstrip(".") not in re.findall(r"\d+", k.split(":", 1)[-1]):
            no_slug.append({"brand": r["Brand"].strip(), "number": num, "source_name": r["Name"].strip(), "slug": k.split(":", 1)[-1], "restored": ps[k]["name"], "year": r["Release_Year"], "ratings": ps[k]["ratingCount"]})
    no_slug.sort(key=lambda x: -x["ratings"])
    write_table("number_not_in_slug.csv", no_slug)
    print("-- 주소에 번호 없음", len(no_slug))
    for x in no_slug:
        print("  ", x["brand"], "|", x["number"], "|", x["source_name"], "|", x["slug"], "|", x["year"], "| 평가", x["ratings"])

    # 합치기 대표가 제외되어 진짜 향수가 사라지는가
    def is_cand(r: dict) -> bool:
        acc = [a for a in (R.accord_key(x) for x in R.split_list(r["Main_Accords"])) if a in R.ACCORDS and a not in R.JUNK_ACCORDS]
        lay = [{ALIASES.get(R.note_key(n), R.note_key(n)) for n in R.split_list(r[c]) if R.note_key(n) not in JUNK} for c in ("Top_Notes", "Middle_Notes", "Base_Notes")]
        return bool(acc) and all(lay) and sum(map(len, lay)) >= 3 and (B._int(r["Rating_Count"]) or 0) >= R.REAL.min_votes

    groups: dict[tuple, list[dict]] = defaultdict(list)
    for r in RAW:
        if is_cand(r):
            groups[R.dedupe_name(BRAND_FIXES.get(r["Brand"].strip(), r["Brand"].strip()), R.restore_name(r.get("Number"), r["Name"]))].append(r)
    gone = pollution | hidden
    reps, lost = 0, []
    for rows in groups.values():
        if len(rows) < 2:
            continue
        rep = next((r for r in rows if R.perfume_key(r["URL"]) in ps), None)
        if rep is None or R.perfume_key(rep["URL"]) not in gone:
            continue
        reps += 1
        rp = ps[R.perfume_key(rep["URL"])]
        for r in rows:
            if r is rep:
                continue
            notes = {ALIASES.get(R.note_key(n), R.note_key(n)) for c in ("Top_Notes", "Middle_Notes", "Base_Notes") for n in R.split_list(r[c]) if R.note_key(n) not in JUNK}
            if notes & FOUL:
                continue
            year = B._int(r["Release_Year"])
            cs = D.candidates(rp["brand"], R.restore_name(r.get("Number"), r["Name"]), year, IDX)
            h = cs[0] if cs and cs[0]["notes"] else None
            overlap = jac({D.norm_note(k) for k in notes}, {D.norm_note(k) for k in h["notes"]}) if h else None
            if overlap is not None and overlap >= 0.3:
                lost.append({"brand": rp["brand"], "excluded_name": rp["name"], "excluded_year": rp["year"] or "", "excluded_ratings": rp["ratingCount"],
                             "reason": "오염" if rp["key"] in pollution else "검토 대상", "clean_slug": R.perfume_key(r["URL"]).split(":", 1)[-1],
                             "clean_year": r["Release_Year"], "clean_ratings": r["Rating_Count"], "overlap": round(overlap, 2)})
    lost.sort(key=lambda x: -int(x["clean_ratings"]))
    write_table("merged_rep_excluded.csv", lost)
    print("-- 제외 대상 중 합치기 대표", reps, "| Decant와 일치하는 깨끗한 행이 섞인 경우", len(lost))
    for x in lost:
        print("  ", x["brand"], "|", x["excluded_name"], "|", x["excluded_year"], "| 평가", x["excluded_ratings"], "|", x["reason"], "| 깨끗한 행", x["clean_slug"], "|", x["clean_year"], "| 평가", x["clean_ratings"], "| 겹침", x["overlap"])


if __name__ == "__main__":
    main()
