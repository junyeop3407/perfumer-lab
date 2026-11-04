"""정리된 데이터로 v8 후보 재측정 (2026-11-04 사장 요청). 엔진·카탈로그 파일은 바꾸지 않는다(메모리에서만 다시 만든다).

앱 폴더에서:  py pipeline/experiments/decant_audit.py  →  py pipeline/experiments/engine_v8_remeasure.py
만드는 파일: docs/engine_v8_remeasure.md (향수 이름 없음), pipeline/data/out/remeasure_traces.txt (이름 있음, 커밋 안 함)

데이터 A: 오염 1,015개 제외. 데이터 B: A에서 숨은 가짜 의심 행까지 제외.
"""
from __future__ import annotations

import csv
import gzip
import json
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
OUT_DIR = APP / "pipeline" / "data" / "out"
DOC = APP / "docs" / "engine_v8_remeasure.md"
MANUAL = APP / "pipeline" / "manual"
RAW = B.read_csv(APP / "pipeline" / "data" / "raw" / "parfumo_data_clean.csv")
DECANT = D.load_rows(APP / "pipeline" / "data" / "raw" / "decant" / "perfumes.db")


def keys_of(name: str) -> set[str]:
    with (OUT_DIR / name).open(encoding="utf-8", newline="") as f:
        return {r["key"] for r in csv.DictReader(f)}


def build(exclude: set[str], m1: bool) -> dict:
    amap = D.read_accord_map(MANUAL / "fragrantica_accords.csv")
    if m1:
        assert amap["aromatic"] == "green"
        amap["aromatic"] = "fresh"
    rows = [r for r in RAW if R.perfume_key(r["URL"]) not in exclude]
    return B.build(rows, R.REAL, B.read_aliases(MANUAL / "aliases.csv"), B.read_overrides(MANUAL / "note_family_override.csv"),
                   B.read_korean(MANUAL / "notes_ko.csv"), version="remeasure", source_name="x", source_note="x",
                   decant={"rows": DECANT, "accord_map": amap, "aliases": D.read_note_aliases(MANUAL / "fragrantica_note_aliases.csv")},
                   junk=B.read_junk(MANUAL / "junk_notes.csv"), brand_fixes=B.read_brand_fixes(MANUAL / "brand_fixes.csv")).catalog


def half(cat: dict, eng: E.Engine, est: tuple) -> tuple:
    """P1-half: 지금 방식(5·4·3·2·1, 균등 몫)과 P1의 중간"""
    rank = np.array([eng.perfume_vector(p["accords"]) for p in cat["perfumes"]])
    return 0.5 * rank + 0.5 * est[0], {k: 0.5 + 0.5 * v for k, v in est[1].items()}


def variants_for(exclude: set[str]) -> tuple[dict, dict]:
    cat, cat_m = build(exclude, False), build(exclude, True)
    assert [p["key"] for p in cat["perfumes"]] == [p["key"] for p in cat_m["perfumes"]]
    eng, eng_m = E.Engine(cat), E.Engine(cat_m)
    est, est_m = T.fit_estimate(cat, eng), T.fit_estimate(cat_m, eng_m)
    v = {"v7 (지금)": T.Scorer(cat, True), "M1 연결표 수정": T.Scorer(cat_m, True), "P1 추정치 보정": T.Scorer(cat, True, estimate=est),
         "P1-half 보정 절반": T.Scorer(cat, True, estimate=half(cat, eng, est)), "M1 + P1": T.Scorer(cat_m, True, estimate=est_m)}
    for s in v.values():
        T.install_group_mask(s)
    T.check_same(v["v7 (지금)"], lambda sp: eng.scores(V.blend(sp)), V.V6_COMPARE_COMBOS[:3])
    return cat, v


def table_of(sec: list[str]) -> list[str]:
    return [x for x in sec if x.startswith("| ") or x.startswith("|---")]


def traces(s_name: str, s: T.Scorer, f) -> None:
    """맹점 Top 5와 민트·라벤더·시더우드에서 Kaleidoscope 점수 구성"""
    f.write(f"\n=== {s_name}\n맹점 Top 5 (시트러스+플로럴 둘 다 있으면 O)\n")
    fam = s.eng.accord_family
    for rank, i in enumerate(s.recommend(T.spec_of(V.BLIND_SPOT), 5), 1):
        p = s.ps[i]
        ok = {"citrus", "floral"} <= {fam[a] for a in p["accords"]}
        f.write(f"  {rank}. {'O' if ok else 'X'} {p['brand']} - {p['name']} | {', '.join(p['accords'])} | 강도 {'있음' if s.has[i] else '없음'}\n")
    spec = T.spec_of(V.V6_COMPARE_COMBOS[11][1])
    parts = s.parts(spec)
    top = s.top(parts["sim"], 5)
    picks = list(top)
    k = next((i for i, p in enumerate(s.ps) if p["name"].startswith("Kaleidoscope") and p["brand"].startswith("Daniel Josier")), None)
    if k is not None and k not in picks:
        picks.append(k)
    f.write("민트·라벤더·시더우드 (어코드 기여 = 0.35·코사인/0.9, 노트 기여 = 0.55·노트 일치/0.9)\n")
    order = np.argsort(-parts["sim"])
    for i in picks:
        p = s.ps[i]
        rank = int(np.where(order == i)[0][0]) + 1
        vec = s.real_vec[i]
        topa = sorted(zip(s.eng.accords, vec), key=lambda x: -x[1])[:3]
        f.write(f"  {rank}위 {p['brand']} - {p['name']} | 점수 {parts['sim'][i]:.3f} | 어코드 {0.35 * parts['cos'][i] / 0.9:.3f} (코사인 {parts['cos'][i]:.3f}) | "
                f"노트 {0.55 * parts['nm'][i] / 0.9:.3f} (일치 {parts['nm'][i]:.3f}, 몫 합 {parts['held'][i]:.3f}) | 노트 전부 {'O' if parts['all_in'][i] else 'X'} | "
                f"{', '.join(f'{a} {x:.0f}' for a, x in topa)}\n")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    excluded, suspects = keys_of("excluded_keys.csv"), keys_of("suspect_keys.csv")
    orig = json.loads(gzip.open(OUT_DIR / "catalog.json.gz", "rb").read())
    orig_s = T.Scorer(orig, True)
    T.install_group_mask(orig_s)
    orig_keys = {p["key"] for p in orig["perfumes"]}
    print("제외 키 중 카탈로그에 있음", len(excluded & orig_keys), "의심", len(suspects), flush=True)

    doc = ["# 정리된 데이터로 v8 후보 재측정", "",
           "> 2026-11-04 · `py pipeline/experiments/engine_v8_remeasure.py`. **엔진·카탈로그 파일은 바꾸지 않았습니다.** 데이터별로 노트 프로필까지 다시 계산했습니다.", ""]
    base_qs = None
    with (OUT_DIR / "remeasure_traces.txt").open("w", encoding="utf-8") as tf:
        for label, exclude in (("A: 오염 1,015개 제외, 의심 행 포함", excluded), (f"B: A + 의심 {len(suspects)}개 제외", excluded | suspects)):
            cat, variants = variants_for(exclude)
            keys = {p["key"] for p in cat["perfumes"]}
            back = keys & exclude
            added = keys - orig_keys
            base = variants["v7 (지금)"]
            print(label, "후보", len(keys), "제외했는데 있음", len(back), "원래 없던 행", len(added), flush=True)
            qs = SV.build_queries(cat, base.eng)
            sec = T.bias_section(variants, list(V.BLENDS) + list(V.V6_COMPARE_COMBOS), qs)
            has = 100.0 * float(base.has.mean())
            doc += [f"## 데이터 {label}", "", f"후보 {len(keys):,}개, 강도 보유 {has:.1f}%(Top 10이 이 비율에 가까울수록 쏠림이 없음). 사람 유사 쿼리 {len(qs):,}개.", ""]
            doc += [x for x in table_of(sec) if "방안" in x or x.startswith("|---|---:|---:|---:|---:|---:|---:|---:|---:|") or any(x.startswith(f"| {n} ") for n in variants)]
            rand = T.random_blends(base.cat, base.eng, 300, T.SEED + 2)
            doc += ["", "| 방안 | Top 10 중 의심 행 (무작위 300조합) |", "|---|---:|"]
            for name, s in variants.items():
                mask = np.array([p["key"] in suspects for p in s.ps])
                doc.append(f"| {name} | {100 * float(mask[[i for sp in rand for i in s.recommend(T.spec_of(sp))]].mean()):.2f}% |")
            doc.append("")
            hold = [q["key"] for q in qs if q["holdout"]]
            if base_qs is None:
                base_qs = True
                r_new = T.human_ranks(base, qs)
                r_old = T.human_ranks(orig_s, qs)
                doc += ["### 정리 자체의 효과 (v7, 같은 쿼리)", "",
                        f"남겨 둔 절반 {len(hold):,}쿼리 사람 유사 Top 50: 정리 전 {T.hit_rate(r_old, hold, 50):.1f}% → 정리 후 {T.hit_rate(r_new, hold, 50):.1f}%, 차이 {T.diff_ci(r_new, r_old, hold, 50)} %p.", ""]
            for name, s in variants.items():
                traces(f"{label} / {name}", s, tf)
            print(label, "완료", flush=True)
    DOC.write_text("\n".join(doc) + "\n", encoding="utf-8")
    print("저장", DOC, flush=True)


if __name__ == "__main__":
    main()
