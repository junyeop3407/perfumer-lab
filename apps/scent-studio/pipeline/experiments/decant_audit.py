"""Decant 대조 감사 (2026-11-04 사장 요청). 카탈로그·엔진은 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/decant_audit.py
표준 출력은 이름 없는 집계만. 향수 이름이 든 표는 커밋 안 하는 pipeline/data/out/ 에 쓴다.
기록용: 정리·이름 복구 전 카탈로그 기준이다. 제외 목록은 freeze_exclusions.py가 만든 pipeline/manual/{filtered,review}_keys.csv가 기준이고,
정리된 카탈로그로 다시 돌리면 결과가 달라지므로 다시 돌리지 않는다.
"""
from __future__ import annotations

import csv
import gzip
import json
import random
import re
import statistics as st
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, "pipeline")
from catalog_rules import load_old_catalog  # noqa: E402
import catalog_rules as R  # noqa: E402
import decant_join as D  # noqa: E402

SEED = 20261104
OUT = Path("pipeline/data/out")
LAY = ("top", "middle", "base")
src = Path("pipeline/experiments/engine_v8_trial.py").read_text(encoding="utf-8")
FOUL = set(re.search(r'FOUL = set\("([^"]+)"', src).group(1).split("|"))
AMB = set("gasoline|asphalt|rubber|dust|mud|fish|grease|chlorine|melting plastic|burnt toast|rust|diesel|sulfur|swamp|chemical|acid|damp|smog|funk|skunk|mildew|mold|moldy leather|burnt electronics|fume".split("|"))
NEW = "onion|charcoal|fermented almond|aged pepper|must|brine|tar|cabbage|black truffle|hay absolute".split("|")
JUNK = {r["note_key"] for r in csv.DictReader(open("pipeline/manual/junk_notes.csv", encoding="utf-8"))}

cat = load_old_catalog(OUT / "catalog.json.gz")
ps = cat["perfumes"]
known = set(cat["notes"]) if isinstance(cat["notes"], dict) else {n["key"] for n in cat["notes"]}
DB = Path("pipeline/data/raw/decant/perfumes.db")
rows = D.load_rows(DB)
al = D.read_note_aliases(Path("pipeline/manual/fragrantica_note_aliases.csv"))
amap = D.read_accord_map(Path("pipeline/manual/fragrantica_accords.csv"))
idx = D.source_index(rows, al)
c = D.open_db(DB)
names = dict(c.execute("select id,name from notes"))
tiers: dict[int, dict[int, list[str]]] = defaultdict(lambda: defaultdict(list))
for pid, nid, t in c.execute("select perfume_id,note_id,tier from perfume_notes order by perfume_id,tier,ord"):
    tiers[pid][t].append(names[nid])
c.close()
print("decant tier values", Counter(t for d in tiers.values() for t in d))
print("decant perfumes: 3 tiers", sum(len(d) >= 3 for d in tiers.values()), "of", len(rows), "/ with notes", len(tiers))

junk_keys = set()
for r in csv.DictReader(open("pipeline/data/raw/parfumo_data_clean.csv", encoding="utf-8")):
    if any(R.note_key(n) in JUNK for col in ("Top_Notes", "Middle_Notes", "Base_Notes") for n in R.split_list(r[col])):
        junk_keys.add(R.perfume_key(r["URL"]))


def notes_of(p):
    return {k for l in LAY for k in p[l]}


def cand_list(p):
    cs = sorted(idx.get(D.name_key(p["brand"], p["name"]), ()), key=lambda r: -r["votes"])
    if p["year"]:
        cs = [r for r in cs if r["year"] and abs(r["year"] - int(p["year"])) <= 1] or cs
    return cs


def jac(a, b):
    u = a | b
    return len(a & b) / len(u) if u else 0.0


def loose(a, b):
    """표기 차이를 봐 주는 겹침: 'white musk'와 'musk'처럼 한쪽 낱말이 다른 쪽에 모두 들어 있으면 같다고 본다."""
    wa, wb = [set(x.split()) for x in a], [set(x.split()) for x in b]
    ma = sum(any(x <= y or y <= x for y in wb) for x in wa)
    mb = sum(any(x <= y or y <= x for y in wa) for x in wb)
    tot = len(a) + len(b)
    return (ma + mb) / tot if tot else 0.0


def top_accords(strengths):
    s = D.accord_shares(strengths, amap)
    return set(sorted(s, key=lambda a: -s[a])[:5]) if s else set()


recs = []
for p in ps:
    ns = notes_of(p)
    cert, amb, jk = ns & (FOUL - AMB), ns & AMB, p["key"] in junk_keys
    group = "certain" if cert else "ambiguous" if amb else "junk" if jk else "clean"
    cs = cand_list(p)
    hit = cs[0] if cs and cs[0]["notes"] else None
    rec = {"p": p, "group": group, "hit": hit, "ncand": len(cs), "rest": {D.norm(k) for k in ns - FOUL}}
    if hit:
        o = set(hit["notes"])
        rec.update(j=jac(rec["rest"], o), loose=loose(rec["rest"], o), acc=jac(set(p["accords"]), top_accords(hit["accords"])),
                   best=max((jac(rec["rest"], set(x["notes"])) for x in cs if x["notes"]), default=0),
                   yr=bool(p["year"] and hit["year"] and abs(hit["year"] - int(p["year"])) <= 1),
                   nd=len(o), nm=len(rec["rest"]))
    recs.append(rec)


def excluded(r):
    return r["group"] in ("certain", "junk") or (r["group"] == "ambiguous" and not (r["hit"] and r["j"] >= 0.3))


G = defaultdict(list)
for r in recs:
    G[r["group"]].append(r)
ex = [r for r in recs if excluded(r)]
print("\n[groups]", {k: len(v) for k, v in G.items()}, "excluded", len(ex), "remaining", len(recs) - len(ex))

print("\n[hist: name-found rows, note overlap]")
polluted = [r for r in recs if r["group"] != "clean" and r["hit"]]
cl = [r for r in G["clean"] if r["hit"]]
for lo in range(0, 100, 10):
    f = lambda L: sum(lo / 100 <= r["j"] < (lo + 10) / 100 or (lo == 90 and r["j"] == 1) for r in L)
    print(f"{lo:3d}-{lo + 10}%  clean {f(cl):5d} {100 * f(cl) / len(cl):5.1f}%   polluted {f(polluted):4d} {100 * f(polluted) / len(polluted):5.1f}%")
print("found: clean", len(cl), "polluted", len(polluted))
print("unverified clean (no Decant name match or no notes):", len(G["clean"]) - len(cl))

print("\n[clean mismatch causes]")
mis = [r for r in cl if r["j"] < 0.3]
print("clean mismatch", len(mis))
for name, L in (("clean ok", [r for r in cl if r["j"] >= 0.3]), ("clean mismatch", mis), ("polluted", polluted)):
    print(f"{name}: n {len(L)} accord-overlap median {st.median(r['acc'] for r in L):.2f} loose median {st.median(r['loose'] for r in L):.2f} "
          f"loose>=.5 {100 * sum(r['loose'] >= .5 for r in L) / len(L):.1f}% year-ok {100 * sum(r['yr'] for r in L) / len(L):.0f}% "
          f"cands>1 {100 * sum(r['ncand'] > 1 for r in L) / len(L):.0f}% decant-notes median {st.median(r['nd'] for r in L)} our-notes median {st.median(r['nm'] for r in L)} rating median {st.median(r['p']['ratingCount'] for r in L)}")
left, why = list(mis), Counter()
for label, test in (("another same-name candidate >=30%", lambda r: r["best"] >= 0.3),
                    ("spelling only (loose>=50%)", lambda r: r["loose"] >= 0.5),
                    ("Decant has <=3 notes", lambda r: r["nd"] <= 3),
                    ("accords agree (>=50%) but notes differ", lambda r: r["acc"] >= 0.5)):
    keep = []
    for r in left:
        if test(r):
            why[label] += 1
        else:
            keep.append(r)
    left = keep
print(dict(why), "unexplained", len(left))
if left:
    print("unexplained: loose median", round(st.median(r["loose"] for r in left), 2), "acc median", round(st.median(r["acc"] for r in left), 2),
          "rating median", st.median(r["p"]["ratingCount"] for r in left))

print("\n[restore]")


def restorable(r):
    h = r["hit"]
    if not h:
        return "no-decant"
    t = tiers[h["id"]]
    if len([x for x in t.values() if x]) < 3:
        return "no-3-tiers"
    if not D.accord_shares(h["accords"], amap):
        return "no-accords"
    if h["votes"] <= 0 or max(h["notes"].values() or [0]) == 0:
        return "no-votes"
    return "ok"


found = [r for r in ex if r["hit"]]
print("excluded found in Decant", len(found), Counter(restorable(r) for r in found))
ok = [r for r in found if restorable(r) == "ok"]
if ok:
    print("restorable: year-ok", sum(r["yr"] for r in ok), "cands>1", sum(r["ncand"] > 1 for r in ok), "accord-overlap median", round(st.median(r["acc"] for r in ok), 2),
          "votes median", st.median(r["hit"]["votes"] for r in ok))
unk = Counter()
for r in ok:
    ks = {al.get(D.norm(n), D.norm(n)) for n in r["hit"]["notes"]}
    unk["all known" if ks <= known else "some unknown"] += 1
print("restored notes in catalog vocab", unk)
has = lambda p: bool(p.get("noteWeights"))
n0, h0 = len(ps), sum(has(p) for p in ps)
kept = [r["p"] for r in recs if not excluded(r)]
n1, h1 = len(kept), sum(has(p) for p in kept)
print(f"strength share: now {h0}/{n0} {100 * h0 / n0:.1f}%  after exclusion {h1}/{n1} {100 * h1 / n1:.1f}%  + restored {h1 + len(ok)}/{n1 + len(ok)} {100 * (h1 + len(ok)) / (n1 + len(ok)):.1f}%")
print("excluded rows with strength", sum(has(r["p"]) for r in ex))

hold = sorted(ex, key=lambda r: (-r["p"]["ratingCount"], r["p"]["key"]))[:50]
print("hold 50:", Counter(restorable(r) for r in hold), "rating range", hold[0]["p"]["ratingCount"], hold[-1]["p"]["ratingCount"])


def row(r):
    p, h = r["p"], r["hit"]
    return {"brand": p["brand"], "name": p["name"], "year": p["year"] or "", "ratings": p["ratingCount"], "group": r["group"],
            "overlap": f"{r['j']:.2f}" if h else "", "accord_overlap": f"{r['acc']:.2f}" if h else "", "restorable": restorable(r),
            "our_notes": " · ".join(k for l in LAY for k in p[l]), "decant_year": (h or {}).get("year") or "",
            "decant_votes": (h or {}).get("votes", ""), "decant_notes": " · ".join(h["notes"]) if h else ""}


def dump(name, L):
    with (OUT / name).open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, list(row(L[0])))
        w.writeheader()
        w.writerows(row(r) for r in L)


rng = random.Random(SEED)
ex_sorted, mis_sorted = sorted(ex, key=lambda r: r["p"]["key"]), sorted(mis, key=lambda r: r["p"]["key"])
dump("hold_list_50.csv", hold)
dump("verify_excluded_20.csv", rng.sample(ex_sorted, 20))
dump("verify_clean_mismatch_20.csv", rng.sample(mis_sorted, 20))
dump("excluded_all.csv", ex_sorted)

print("\n[new suspect words: rows without listed words]")
for w in NEW:
    L = [r for r in G["clean"] if w in notes_of(r["p"])]
    F = [r for r in L if r["hit"]]
    print(f"{w}: rows {len(L)} found {len(F)} >=30% {sum(r['j'] >= .3 for r in F)} <30% {sum(r['j'] < .3 for r in F)} unverified {len(L) - len(F)}")

print("\n[fake-row signals]")
brands = {D.norm(r["brand"]) for r in rows}
for name, L in (("clean", G["clean"]), ("polluted", [r for r in recs if r["group"] != "clean"])):
    print(name, "n", len(L), "brand in Decant %.1f%%" % (100 * sum(D.norm(r["p"]["brand"]) in brands for r in L) / len(L)),
          "has year %.1f%%" % (100 * sum(bool(r["p"]["year"]) for r in L) / len(L)))
    for lo, hi in ((0, 20), (20, 50), (50, 100), (100, 250), (250, 10 ** 9)):
        B = [r for r in L if lo <= r["p"]["ratingCount"] < hi]
        if B:
            print(f"  ratings {lo}-{hi}: n {len(B)} found {100 * sum(bool(r['hit']) for r in B) / len(B):.1f}%")


def yearstat(L):
    y = [r for r in L if r["p"]["year"] and r["hit"]["year"]]
    d = [abs(int(r["p"]["year"]) - r["hit"]["year"]) for r in y]
    return f"both years {len(y)}/{len(L)} within1 {100 * sum(x <= 1 for x in d) / max(len(d), 1):.1f}% median gap {st.median(d) if d else None}"


print("clean ok", yearstat([r for r in cl if r["j"] >= .3]))
print("clean mismatch", yearstat(mis))
print("unexplained", yearstat(left))
print("polluted", yearstat(polluted))
sus = [r for r in mis if r["p"]["year"] and r["hit"]["year"] and abs(int(r["p"]["year"]) - r["hit"]["year"]) > 1 and r["loose"] < .5 and r["best"] < .3]
print("clean rows that look like polluted (overlap<30%, loose<50%, year gap>1, no other candidate):", len(sus), "accord median", round(st.median(r["acc"] for r in sus), 2) if sus else None)
c = D.open_db(DB)
for nm in ("Bergamot", "Rose", "Vanilla"):
    print(nm, "by tier", dict(c.execute("select tier,count(*) from perfume_notes pn join notes n on n.id=pn.note_id where n.name=? group by tier", (nm,))))
c.close()
print("flat only (tier 3):", sum(set(d) == {3} for d in tiers.values()))

# 행 안에서 어코드와 (오염 단어를 뺀) 노트 프로필이 어긋나는지
import engine_ref as E  # noqa: E402
import numpy as np  # noqa: E402

eng = E.Engine(cat)
prof = {k: np.array(v["profile"]) for k, v in eng.notes.items()}
ag = defaultdict(list)
for r in recs:
    p = r["p"]
    rest = [k for k in notes_of(p) - FOUL if k in prof]
    if p["accords"] and rest:
        a, m = np.array(eng.perfume_vector(p["accords"])), np.mean([prof[k] for k in rest], axis=0)
        d = np.linalg.norm(a) * np.linalg.norm(m)
        if d:
            ag["clean" if r["group"] == "clean" else "polluted"].append(float(a @ m / d))
print("\n[accord-note cosine median]", {k: round(float(np.median(v)), 3) for k, v in ag.items()})

# ---------- 개선한 대조 도구 (2026-11-04 사장 요청). 제외 1,015개는 위 판정으로 확정이고, 아래 결과로 바꾸지 않는다 ----------
MODIFIERS = set("candied wild golden white absolute nectar zest juice skin peel sambac co2 essence extract oil".split())
ORIGINS = set("indian italian brazilian moroccan morocco french madagascan madagascar sicilian egyptian chinese peru sichuan tunisian spanish "
              "indonesian bulgarian bourbon calabrian somalian paraguayan persian californian california japanese damask grasse turkish haitian "
              "mexican virginia virginian texas american caribbean guatemalan amalfi mysore tahitian australian english russian african "
              "florida ceylon tonkin siberian himalayan".split())
GENDER = re.compile(r"\b(for women|pour femme|femme|for men|pour homme|homme)\b")


def norm_note(k):
    n = D.norm(k)
    return " ".join(w for w in n.split() if w not in MODIFIERS and w not in ORIGINS) or n


def name_gender(name):
    found = {"f" if x in ("for women", "pour femme", "femme") else "m" for x in GENDER.findall(D.norm(name))}
    return found.pop() if len(found) == 1 else None


c = D.open_db(DB)
col_gender = {pid: {"female": "f", "male": "m"}.get(g, "u") for pid, g in c.execute("select id,gender from perfumes")}
c.close()


def cand_gender(x):
    return name_gender(x["name"]) or col_gender.get(x["id"], "u")


def cand_new(p):
    cs = list(idx.get(D.name_key(p["brand"], p["name"]), ()))
    mine = name_gender(p["name"])
    if mine:
        cs = [x for x in cs if cand_gender(x) in (mine, "u")]
    else:
        cs = [x for x in cs if not name_gender(x["name"])] or cs
    cs.sort(key=lambda r: -r["votes"])
    if p["year"]:
        cs = [r for r in cs if r["year"] and abs(r["year"] - int(p["year"])) <= 1] or cs
    return cs


def gap(p, h):
    return abs(int(p["year"]) - h["year"]) if p["year"] and h["year"] else None


for r in recs:
    p = r["p"]
    cs = cand_new(p)
    h = cs[0] if cs and cs[0]["notes"] else None
    rest = {norm_note(k) for k in notes_of(p) - FOUL}
    r["h2"] = h
    if h:
        o = {norm_note(k) for k in h["notes"]}
        r.update(j2=jac(rest, o), loose2=loose(rest, o), best2=max((jac(rest, {norm_note(k) for k in x["notes"]}) for x in cs if x["notes"]), default=0),
                 gap2=gap(p, h), acc2=jac(set(p["accords"]), top_accords(h["accords"])), nd2=len(o))

print("\n[improved tool]")
cl2 = [r for r in G["clean"] if r["h2"]]
pol2 = [r for r in recs if r["group"] != "clean" and r["h2"]]
print(f"clean >=30%: old {sum(r['j'] >= .3 for r in cl)}/{len(cl)} {100 * sum(r['j'] >= .3 for r in cl) / len(cl):.1f}%  new {sum(r['j2'] >= .3 for r in cl2)}/{len(cl2)} {100 * sum(r['j2'] >= .3 for r in cl2) / len(cl2):.1f}%")
print(f"polluted >=30% (control): old {sum(r['j'] >= .3 for r in polluted)}/{len(polluted)} {100 * sum(r['j'] >= .3 for r in polluted) / len(polluted):.1f}%  new {sum(r['j2'] >= .3 for r in pol2)}/{len(pol2)} {100 * sum(r['j2'] >= .3 for r in pol2) / len(pol2):.1f}%")
print("unverified clean: old", len(G["clean"]) - len(cl), "new", len(G["clean"]) - len(cl2))
print("ambiguous rows kept by frozen rule 5; new tool would keep", sum(bool(r["group"] == "ambiguous" and r["h2"] and r["j2"] >= .3) for r in recs))
for lo in range(0, 100, 10):
    f = lambda L: sum(lo / 100 <= r["j2"] < (lo + 10) / 100 or (lo == 90 and r["j2"] == 1) for r in L)
    print(f"{lo:3d}-{lo + 10}%  clean {f(cl2):5d} {100 * f(cl2) / len(cl2):5.1f}%   polluted {f(pol2):4d} {100 * f(pol2) / len(pol2):5.1f}%")

mis2 = [r for r in cl2 if r["j2"] < .3]
print("clean mismatch new", len(mis2))
left2, why2 = list(mis2), Counter()
for label, test in (("year gap >=5 (other version)", lambda r: r["gap2"] is not None and r["gap2"] >= 5),
                    ("another same-name candidate >=30%", lambda r: r["best2"] >= .3),
                    ("spelling only (loose>=50%)", lambda r: r["loose2"] >= .5),
                    ("Decant has <=3 notes", lambda r: r["nd2"] <= 3),
                    ("accords agree (>=50%)", lambda r: r["acc2"] >= .5)):
    keep = []
    for r in left2:
        if test(r):
            why2[label] += 1
        else:
            keep.append(r)
    left2 = keep
print(dict(why2), "unexplained", len(left2))


def ys(L):
    g = [r["gap2"] for r in L if r["gap2"] is not None]
    return f"n {len(L)} both years {len(g)} within1 {100 * sum(x <= 1 for x in g) / max(len(g), 1):.1f}% gap>=5 {100 * sum(x >= 5 for x in g) / max(len(g), 1):.1f}%"


print("clean ok", ys([r for r in cl2 if r["j2"] >= .3]))
print("clean mismatch", ys(mis2))
print("unexplained", ys(left2))
print("polluted", ys(pol2))
sus2 = [r for r in mis2 if r["gap2"] is not None and r["gap2"] >= 2 and r["loose2"] < .5 and r["best2"] < .3]
print("suspects same definition as 112:", len(sus2), "gap 2-4:", sum(r["gap2"] < 5 for r in sus2), "gap>=5:", sum(r["gap2"] >= 5 for r in sus2))
print("accord overlap medians: clean ok", round(st.median(r["acc2"] for r in cl2 if r["j2"] >= .3), 2), "suspects 2-4y", round(st.median(r["acc2"] for r in sus2 if r["gap2"] < 5), 2), "suspects >=5y", round(st.median(r["acc2"] for r in sus2 if r["gap2"] >= 5), 2), "polluted", round(st.median(r["acc2"] for r in pol2), 2))
guess = [r for r in G["clean"] if "indigo for women" in D.norm(r["p"]["name"]) and D.norm(r["p"]["brand"]) == "guess"]
print("guess check overlap old/new", [(round(r["j"], 2), round(r["j2"], 2), r["h2"]["name"]) for r in guess if r["h2"] and r["hit"]])
with (OUT / "excluded_keys.csv").open("w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerow(["key"])
    w.writerows([r["p"]["key"]] for r in ex_sorted)
with (OUT / "review_candidates.csv").open("w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerow(["key", "year_gap"])
    w.writerows([r["p"]["key"], r["gap2"]] for r in sus2)

# 개선 도구를 카탈로그의 강도 연결(D.match)에 쓰면 무엇이 달라지는지. 엔진·카탈로그는 바꾸지 않는다
kept_r = [r for r in recs if not excluded(r)]
has_r = [r for r in kept_r if has(r["p"])]
diff_id = [r for r in has_r if r["hit"] and r["h2"] and r["hit"]["id"] != r["h2"]["id"]]
gender_wrong = [r for r in has_r if r["hit"] and name_gender(r["p"]["name"]) and cand_gender(r["hit"]) not in (name_gender(r["p"]["name"]), "u")]
gain = [r for r in kept_r if not has(r["p"]) and r["h2"] and r["j2"] >= .3 and r["h2"]["votes"] > 0]
print("\n[join impact] strength rows", len(has_r), "linked to a different Decant row under new tool", len(diff_id), "of which gender-conflict rows", len(gender_wrong),
      "| rows without strength the new tool could link", len(gain), f"-> share {100 * (len(has_r) + len(gain)) / len(kept_r):.1f}%")
