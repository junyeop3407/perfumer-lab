"""레몬 '강' 문제: 프로필 방식 × '강' 값 × 최소 등장 수 비교 (2026-10-31 사장 요청)

앱 폴더에서:  py pipeline/experiments/strong_lemon_variants.py   (원본 pipeline/data/raw 필요)
만드는 파일: docs/engine_variants.md (맹점 조합 Top 5 비교, 재현, 다양성, 등급 기준값 계산)
실제 파이프라인 코드(build_catalog.build)와 참조 엔진(engine_ref)을 그대로 쓰고, 설정값만 바꾼다.
"""
from __future__ import annotations

import dataclasses
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import build_catalog as B  # noqa: E402
import catalog_rules as R  # noqa: E402
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent

VARIANTS = [
    ("지금 (평균 빼기 · 강 1.5)", dict(profile_method="diff", min_profile_df=0), 1.5),
    ("특징 강조(PPMI)만 · 강 1.5", dict(profile_method="ppmi", min_profile_df=20), 1.5),
    ("강 2.0만 (평균 빼기)", dict(profile_method="diff", min_profile_df=0), 2.0),
    ("둘 다: 특징 강조 + 강 2.0 ★추천", dict(profile_method="ppmi", min_profile_df=20), 2.0),
    ("(참고) 둘 다인데 최소 등장 수 기준 없음", dict(profile_method="ppmi", min_profile_df=0), 2.0),
]


def run(rows, manual, label, overrides, strong):
    params = dataclasses.replace(R.REAL, **overrides)
    res = B.build(rows, params, B.read_aliases(manual / "aliases.csv"),
                  B.read_overrides(manual / "note_family_override.csv"), B.read_korean(manual / "notes_ko.csv"),
                  version="exp", source_note="exp", junk=B.read_junk(manual / "junk_notes.csv"))
    cat = res.catalog
    E.INTENSITIES = (0.5, 1.0, strong)
    eng = E.Engine(cat)
    by_key = {p["key"]: p for p in cat["perfumes"]}
    acc_ko = {a["key"]: a["nameKo"] for a in cat["accords"]}
    acc_fam = {a["key"]: a["family"] for a in cat["accords"]}
    S = V.blend  # 강도 이름 → 지금 E.INTENSITIES 값

    spot = S(V.BLIND_SPOT)
    recs = eng.recommend(spot, k=5)
    top5 = [(by_key[r["perfumeKey"]], r["score"]) for r in recs]
    citrus_first = sum(1 for p, _ in top5 if p["accords"][0] == "citrus")
    cf = sum(1 for p, _ in top5 if {"citrus", "floral"} <= {acc_fam[a] for a in p["accords"]})
    ratios = eng.display_ratios(spot)

    # 재현: evaluate.py와 같은 방식
    picks, brands = [], set()
    for p in sorted(cat["perfumes"], key=lambda p: (-p["ratingCount"], p["key"])):
        notes = [(k, l) for l in ("top", "middle", "base") for k in p[l][:3] if eng.notes[k]["tier"] != "hidden"]
        if p["brand"] in brands or not notes:
            continue
        picks.append((p, notes))
        brands.add(p["brand"])
        if len(picks) == 20:
            break
    rep = 0
    for p, notes in picks:
        gs = [by_key[r["perfumeKey"]]["group"] for r in eng.recommend([E.BlendNote(k, l, 1.0) for k, l in notes], k=10)]
        rep += 1 if p["group"] in gs and gs.index(p["group"]) < 3 else 0

    # 다양성 + 점수 분포 (평가 조합 10개 + 맹점의 Top 10)
    all_brands, scores = set(), []
    for _, spec in [("맹점", V.BLIND_SPOT), *V.BLENDS]:
        rs = eng.recommend(S(spec), k=10)
        all_brands |= {by_key[r["perfumeKey"]]["brand"] for r in rs}
        scores += [r["score"] for r in rs]

    # 등급 기준값 재료: 평가 많은 향수 100개(브랜드 겹치지 않게)의 노트를 그대로 넣었을 때 그 향수 자신의 점수
    selfs, brands = [], set()
    for p in sorted(cat["perfumes"], key=lambda p: (-p["ratingCount"], p["key"])):
        seen: set[str] = set()
        notes = [(k, l) for l in ("top", "middle", "base") for k in p[l][:3]
                 if eng.notes[k]["tier"] != "hidden" and not (k in seen or seen.add(k))]
        if p["brand"] in brands or not notes:
            continue
        brands.add(p["brand"])
        own = {by_key[r["perfumeKey"]]["group"]: r["score"]
               for r in eng.recommend([E.BlendNote(k, l, 1.0) for k, l in notes], k=200)}
        if p["group"] in own:
            selfs.append(own[p["group"]])
        if len(brands) == 100:
            break

    # 최소 등장 수 기준이 적용된 노트 (목록·검색에 보이는 것 중 계열 평균 프로필을 쓰게 된 노트)
    rare = [n for n in cat["notes"] if n["df"] < overrides["min_profile_df"] and n["tier"] != "hidden"]

    lines = [f"## {label}", "",
             f"- 맹점 조합 어코드: {', '.join(f'{acc_ko[a]} {p}%' for a, p in ratios[:4])}",
             f"- Top 5 중 1위 어코드가 시트러스: **{citrus_first}/5** · 시트러스와 플로럴 둘 다: {cf}/5",
             f"- 재현(기준 15): **{rep}/20** · 다양성(기준 40): 브랜드 {len(all_brands)}개",
             (f"- 계열 평균 프로필을 쓰는 노트(향수 {overrides['min_profile_df']}개 미만에 나옴, 목록·검색에 보이는 것): {len(rare)}개"
              if overrides["min_profile_df"] else "- 최소 등장 수 기준 없음 (적게 나오는 노트도 K=20 끌어당기기만)"),
             "", "| 순위 | 향수 | 상위 어코드 | 점수 |", "|---|---|---|---|",
             *[f"| {i + 1} | {p['brand']} — {p['name']} | {' · '.join(acc_ko[a] for a in p['accords'][:3])} | {s:.3f} |"
               for i, (p, s) in enumerate(top5)], ""]
    return lines, selfs


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    manual = APP / "pipeline" / "manual"
    rows = B.read_csv(APP / "pipeline" / "data" / "raw" / "parfumo_data_clean.csv")
    out = ["# 레몬 '강' 문제: 방식 비교", "",
           "> `py pipeline/experiments/strong_lemon_variants.py`로 만든 문서 (2026-10-31). "
           "맹점 조합 = 레몬(강) · 오스만투스(중) · 샌달우드(약). 블라인드 비교에서는 이 조합을 뺐다.", ""]
    selfs = {}
    for label, overrides, strong in VARIANTS:
        lines, own = run(rows, manual, label, overrides, strong)
        out += lines
        selfs[label] = own
        print(lines[0], "|", lines[3].strip("- "), "|", lines[4].strip("- "))

    # 등급 기준값 (2026-10-31): "그 향수의 노트를 그대로 넣었을 때 그 향수가 받는 점수"를 기준으로.
    # 매우 비슷 = 그 점수의 중앙값 이상(진짜 같은 향수만큼), 비슷 = 하위 25% 이상(같은 향수 넷 중 셋이 넘는 선)
    out += ["## 등급 기준값", "",
            "평가 많은 향수 100개의 노트를 그대로 넣었을 때 **그 향수 자신이 받는 점수**를 기준으로 삼는다.",
            "매우 비슷 = 그 점수의 중앙값 이상(진짜 같은 향수만큼 비슷), 비슷 = 하위 25% 이상.", "",
            "| 방식 | 자기 점수 중앙값 | 하위 25% |", "|---|---|---|"]
    for label, own in selfs.items():
        q1 = statistics.quantiles(own, n=4)[0]
        out.append(f"| {label} | {statistics.median(own):.3f} | {q1:.3f} |")
    rec = selfs[VARIANTS[3][0]]
    vs, s_ = round(statistics.median(rec), 2), round(statistics.quantiles(rec, n=4)[0], 2)
    out += ["", f"→ 추천 방식의 새 기준값(소수 둘째 자리로 반올림): **매우 비슷 ≥ {vs} · 비슷 ≥ {s_}** (지금 0.85 · 0.75)", ""]
    print(out[-2])
    (APP / "docs" / "engine_variants.md").write_text("\n".join(out), encoding="utf-8")


if __name__ == "__main__":
    main()
