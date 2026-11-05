"""v8 3단계 재측정 (2026-11-04, 승인된 계획). 엔진·카탈로그 파일은 바꾸지 않는다(메모리에서만 다시 만든다).

앱 폴더에서:  py pipeline/experiments/engine_v8_final.py
만드는 파일: docs/engine_v8_final.md (향수 이름 없음), pipeline/data/out/final_traces.txt (이름 있음, 커밋 안 함)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_catalog as B  # noqa: E402
import catalog_rules as R  # noqa: E402
import decant_join as D  # noqa: E402
import engine_ref as E  # noqa: E402
import engine_v8_trial as T  # noqa: E402
import evaluate as V  # noqa: E402
import similar_votes as SV  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
OUT_DIR, MANUAL = APP / "pipeline" / "data" / "out", APP / "pipeline" / "manual"
DOC = APP / "docs" / "engine_v8_final.md"
RAW = B.read_csv(APP / "pipeline" / "data" / "raw" / "parfumo_data_clean.csv")
DECANT = D.load_rows(APP / "pipeline" / "data" / "raw" / "decant" / "perfumes.db")
HASHES = B.read_key_hashes(MANUAL / "filtered_keys.csv")
FLAGS = B.read_key_hashes(MANUAL / "review_keys.csv")
EXCLUDE = {k for k in (R.perfume_key(r["URL"]) for r in RAW) if B.key_hash(k) in HASHES}
NEW_MATCH, NEW_WEIGHTS = D.match, D.note_weights


def old_match(brand, name, year, notes, index):
    """지금 v7 카탈로그의 연결 (성별·노트 정규화·브랜드 별칭 없음)"""
    cands = sorted(index.get(D.name_key(brand, name), ()), key=lambda r: -r["votes"])
    if not cands:
        return None
    if year:
        cands = [r for r in cands if r["year"] and abs(r["year"] - year) <= D.YEAR_TOLERANCE] or cands
    hit = cands[0]
    mine, other = {D.norm(k) for k in notes}, set(hit["notes"])
    union = mine | other
    return hit if union and len(mine & other) / len(union) >= D.MIN_NOTE_OVERLAP else None


def old_weights(notes, raw, votes):
    found = [raw[D.norm(k)] / 100 for k in notes if D.norm(k) in raw]
    if not found or max(found) == 0:
        return None
    alpha = votes / (votes + D.VOTE_HALF)
    fill = sum(found) / len(found)
    vals = {k: max(raw[D.norm(k)] / 100 if D.norm(k) in raw else fill, D.WEIGHT_FLOOR) ** alpha for k in notes}
    total = sum(vals.values())
    return {k: v / total for k, v in vals.items()}


def use_join(old: bool) -> None:
    D.match, D.note_weights = (old_match, old_weights) if old else (NEW_MATCH, NEW_WEIGHTS)


def build(old: bool, m1: bool) -> dict:
    use_join(old)
    amap = D.read_accord_map(MANUAL / "fragrantica_accords.csv")
    if m1:
        assert amap["aromatic"] == "green"
        amap["aromatic"] = "fresh"
    decant = {"rows": DECANT, "accord_map": amap, "aliases": D.read_note_aliases(MANUAL / "fragrantica_note_aliases.csv")}
    if not old:
        decant["brand_aliases"] = D.read_brand_aliases(MANUAL / "decant_brand_aliases.csv")
    return B.build(RAW, R.REAL, B.read_aliases(MANUAL / "aliases.csv"), B.read_overrides(MANUAL / "note_family_override.csv"),
                   B.read_korean(MANUAL / "notes_ko.csv"), version="final", source_name="x", source_note="x", decant=decant,
                   junk=B.read_junk(MANUAL / "junk_notes.csv"), brand_fixes=B.read_brand_fixes(MANUAL / "brand_fixes.csv"), exclude=EXCLUDE,
                   keep_separate=B.keys_from_hashes(RAW, MANUAL / "separate_keys.csv"),
                   keep_merged=B.keys_from_hashes(RAW, MANUAL / "merge_keys.csv")).catalog


def half(cat: dict, eng: E.Engine, est: tuple) -> tuple:
    rank = np.array([eng.perfume_vector(p["accords"]) for p in cat["perfumes"]])
    return 0.5 * rank + 0.5 * est[0], {k: 0.5 + 0.5 * v for k, v in est[1].items()}


def w_ci(real: dict, masked: dict, r: float, real0: dict, masked0: dict, r0: float, keys: list[str]) -> str:
    """W 차이의 95% 구간 (쿼리 단위 재표집). W = r·사람 유사 + (1 − r)·가림"""
    def per(rk, mk, share):
        return np.array([V.weighted_hit(float(rk[q] <= 50), float(mk[q] <= 50), share) for q in keys])
    d = per(real, masked, r) - per(real0, masked0, r0)
    rng = np.random.default_rng(T.SEED)
    lo, hi = np.percentile([d[rng.integers(0, len(d), len(d))].mean() for _ in range(1000)], [2.5, 97.5])
    return f"{100 * d.mean():+.1f} ({100 * lo:+.1f}~{100 * hi:+.1f})"


def blind_trace(name: str, s: T.Scorer, f) -> None:
    fam = s.eng.accord_family
    f.write(f"\n=== {name}\n맹점 Top 5 (공식: Parfumo 목록 / 참고: Decant 상위 5 어코드)\n")
    for rank, i in enumerate(s.recommend(T.spec_of(V.BLIND_SPOT), 5), 1):
        p = s.ps[i]
        official = {"citrus", "floral"} <= {fam[a] for a in p["accords"]}
        shares = p.get("accordShares") or {}
        top = sorted(shares, key=lambda a: -shares[a])[:5]
        ref = {"citrus", "floral"} <= {fam[a] for a in top} if top else None
        f.write(f"  {rank}. {'O' if official else 'X'}/{'-' if ref is None else 'O' if ref else 'X'} {p['brand']} - {p['name']} | {', '.join(p['accords'])} | Decant {', '.join(f'{a} {shares[a]:.0f}' for a in top)} | 몫 {p.get('noteWeights') and {k: round(v, 3) for k, v in p['noteWeights'].items() if k in ('lemon', 'osmanthus', 'sandalwood')}}\n")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    old, new, new_m = build(True, False), build(False, False), build(False, True)
    assert [p["key"] for p in old["perfumes"]] == [p["key"] for p in new["perfumes"]] == [p["key"] for p in new_m["perfumes"]]
    print("후보", len(new["perfumes"]), flush=True)
    eng_old, eng, eng_m = E.Engine(old), E.Engine(new), E.Engine(new_m)
    # 옛 연결로 쿼리를 만들어 고정한다. 새 연결로 늘어나는 쿼리는 비교에 넣지 않는다
    use_join(True)
    qs = SV.build_queries(old, eng_old)
    est, est_m = T.fit_estimate(new, eng), T.fit_estimate(new_m, eng_m)
    variants = {"v7 (지금, 옛 연결)": T.Scorer(old, True), "v7 새 연결": T.Scorer(new, True),
                "P1 추정치 보정": T.Scorer(new, True, estimate=est), "P1-half 보정 절반": T.Scorer(new, True, estimate=half(new, eng, est)),
                "M1 연결표 수정": T.Scorer(new_m, True), "M1 + P1": T.Scorer(new_m, True, estimate=est_m)}
    for s in variants.values():
        T.install_group_mask(s)
    base = variants["v7 (지금, 옛 연결)"]
    T.check_same(base, lambda sp: eng_old.scores(V.blend(sp)), V.V6_COMPARE_COMBOS[:3])
    T.check_same(variants["v7 새 연결"], lambda sp: eng.scores(V.blend(sp)), V.V6_COMPARE_COMBOS[:3])

    hold = [q["key"] for q in qs if q["holdout"]]
    specs24 = [sp for _, sp in list(V.BLENDS) + list(V.V6_COMPARE_COMBOS)]
    rand = T.random_blends(base.cat, base.eng, 300, T.SEED + 2)
    picks20 = V.reproduction_picks(base.cat, base.eng, 20)
    flag_mask = {n: np.array([B.key_hash(p["key"]) in FLAGS for p in s.ps]) for n, s in variants.items()}
    ranks = {n: (T.human_ranks(s, qs), T.human_ranks(s, qs, masked=True)) for n, s in variants.items()}
    r0, m0 = ranks["v7 (지금, 옛 연결)"]
    share0 = float(base.has.mean())
    new_share = float(variants["v7 새 연결"].has.mean())
    doc = ["# 엔진 v8 재측정", "",
           "> 2026-11-05 · `py pipeline/experiments/engine_v8_final.py`. **엔진·카탈로그 파일은 바꾸지 않았습니다.** 판정 규칙은 측정 전에 정했습니다(`docs/plan_v8.md`).", "",
           f"후보 {len(base.ps):,}개. 강도 보유: 옛 연결 {100 * share0:.1f}%, 새 연결 {100 * new_share:.1f}%. 사람 유사 쿼리 {len(qs):,}개(옛 연결로 만들어 고정), 남겨 둔 절반 {len(hold):,}개.", "",
           "W = r × 사람 유사 Top 50 + (1 − r) × 가림 Top 50. r은 그 데이터의 강도 보유 비율입니다. W는 결과를 본 뒤 만든 지표라 블라인드 진출 판단에만 씁니다.", "",
           "| 방안 | 사람 유사 Top 50 | v7 대비 %p (95% 구간) | 가림 Top 50 | 가림 v7 대비 %p | W | W v7 대비 %p (95% 구간) | W (r 고정) | Top 10 강도 보유 (24조합) | Top 10 강도 보유 (무작위 300) | Top 10 표시 행 | 재현 20 | 맹점 | 다양성 |",
           "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for name, s in variants.items():
        real, masked = ranks[name]
        share = float(s.has.mean())
        fam = s.eng.accord_family
        blind = sum({"citrus", "floral"} <= {fam[a] for a in s.ps[i]["accords"]} for i in s.recommend(T.spec_of(V.BLIND_SPOT), 5))
        brands = {s.ps[i]["brand"] for _, sp in V.BLENDS for i in s.recommend(T.spec_of(sp))}
        h, g = T.hit_rate(real, hold, 50), T.hit_rate(masked, hold, 50)
        top = [i for sp in rand for i in s.recommend(T.spec_of(sp))]
        doc.append(f"| {name} | {h:.1f}% | {T.diff_ci(real, r0, hold, 50)} | {g:.1f}% | {g - T.hit_rate(m0, hold, 50):+.1f} | {V.weighted_hit(h, g, share):.1f} | {w_ci(real, masked, share, r0, m0, share0, hold)} | "
                   f"{V.weighted_hit(h, g, new_share):.1f} | {T.share_of(s, specs24):.1f}% | {T.share_of(s, rand):.1f}% | {100 * float(flag_mask[name][top].mean()):.2f}% | {T.repro(s, picks20)}/20 | {blind}/5 | {len(brands)} |")
        print(doc[-1], flush=True)

    fresh = [c for i, c in enumerate(V.V6_COMPARE_COMBOS, 1) if i in T.FRESH_IN_V6_COMBOS] + \
            [c for c in T.EXTRA_FRESH if all(k in base.eng.notes and base.eng.notes[k]["tier"] != "hidden" for k, _, _ in c[1])]
    fam = base.eng.accord_family
    fresh_keys = [q["key"] for q in qs if q["holdout"] and T.BUCKET[fam[base.ps[base.idx[q["key"]]]["accords"][0]]] == "프레시"]
    doc += ["", f"## M1 프레시 판단 (프레시 쿼리 {len(fresh_keys)}개, 프레시 조합 {len(fresh)}개)", "",
            "| 방안 | 프레시 사람 유사 Top 50 | M1 없는 쪽 대비 %p (95% 구간) | 프레시 가림 Top 50 | Top 10 프레시 계열 비중 |", "|---|---:|---:|---:|---:|"]
    for name, other in (("v7 새 연결", None), ("M1 연결표 수정", "v7 새 연결"), ("P1 추정치 보정", None), ("M1 + P1", "P1 추정치 보정")):
        s = variants[name]
        real, masked = ranks[name]
        diff = T.diff_ci(real, ranks[other][0], fresh_keys, 50) if other else "기준"
        share = np.mean([T.family_share(s, s.recommend(T.spec_of(sp)), fams=T.FRESH_FAMS) for _, sp in fresh])
        doc.append(f"| {name} | {T.hit_rate(real, fresh_keys, 50):.1f}% | {diff} | {T.hit_rate(masked, fresh_keys, 50):.1f}% | {share:.1f}% |")
        print(doc[-1], flush=True)

    with (OUT_DIR / "final_traces.txt").open("w", encoding="utf-8") as f:
        for name, s in variants.items():
            blind_trace(name, s, f)
    DOC.write_text("\n".join(doc) + "\n", encoding="utf-8", newline="\n")
    print("저장", DOC, flush=True)


if __name__ == "__main__":
    main()
