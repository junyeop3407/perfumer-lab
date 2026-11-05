"""v8 카탈로그 파일로 잰 값이 메모리 측정(engine_v8_final.py의 P1)과 같은지 확인한다 (v8 7단계).

앱 폴더에서:  py pipeline/experiments/engine_v8_check.py
읽는 것: pipeline/data/out/catalog.json.gz (v8), catalog_v7_compare.json.gz (v7 비교본)
만드는 파일: docs/engine_v8_check.md (향수 이름 없음), pipeline/data/out/queries_v8.json (쿼리 893개, 이름 있음, 커밋 안 함)
다르면 끝에서 멈춘다(종료 코드 1). 저장값의 반올림 때문에 순위가 몇 개 달라지는 것은 개수로 보고한다.
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import engine_ref as E  # noqa: E402
import engine_v8_final as F  # noqa: E402
import engine_v8_trial as T  # noqa: E402
import estimate as EST  # noqa: E402
import evaluate as V  # noqa: E402
import similar_votes as SV  # noqa: E402

DOC = F.APP / "docs" / "engine_v8_check.md"
QUERIES, HOLDOUT = 893, 474  # 2026-11-05 사장 결정: 새 측정 기준
TOLERANCE = 0.05  # 표에 보이는 자릿수(소수 첫째)에서 같아야 한다


def load(name: str) -> dict:
    return json.loads(gzip.open(F.OUT_DIR / name, "rb").read())


def scorer(cat: dict, estimate: tuple | None = None) -> T.Scorer:
    s = T.Scorer(cat, True, estimate=estimate)
    T.install_group_mask(s)
    return s


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    v8, v7 = load("catalog.json.gz"), load("catalog_v7_compare.json.gz")
    assert v8["engineVersion"] == 8 and v7["engineVersion"] == 7
    assert [p["key"] for p in v8["perfumes"]] == [p["key"] for p in v7["perfumes"]], "두 카탈로그의 후보가 다르다"
    ps = v8["perfumes"]

    F.use_join(True)  # 쿼리는 옛 연결로 만들어 고정한다 (engine_v8_final.py와 같음)
    qs = SV.build_queries(v7, E.Engine(v7))
    hold = [q["key"] for q in qs if q["holdout"]]
    (F.OUT_DIR / "queries_v8.json").write_text(json.dumps(qs, ensure_ascii=False), encoding="utf-8")
    assert (len(qs), len(hold)) == (QUERIES, HOLDOUT), f"쿼리가 기준과 다르다: {len(qs)} / {len(hold)}"

    memory = F.build(False, False)  # 메모리 측정의 P1: 추정치를 파일에 넣기 전
    assert [p["key"] for p in memory["perfumes"]] == [p["key"] for p in ps]
    mem_est = EST.fit(memory)
    file_est = EST.fit(v8)  # 파일에서 실제 강도만 배운다. 가림 시험에 쓴다

    # 파일에 저장한 값이 메모리 계산과 같은가 (반올림 자릿수까지)
    accords = [a["key"] for a in v8["accords"]]
    worst_a = worst_n = 0.0
    for i, (p, m) in enumerate(zip(ps, memory["perfumes"])):
        if p.get("estimatedAccords"):
            worst_a = max(worst_a, max(abs(p["accordShares"].get(a, 0.0) - mem_est[0][i][j]) for j, a in enumerate(accords)))
        else:
            assert p.get("accordShares") == m.get("accordShares"), p["key"]
        if p.get("estimatedNotes"):
            notes = list(p["noteWeights"])
            total = sum(mem_est[1].get(k, 1.0) for k in notes) or 1.0
            worst_n = max(worst_n, max(abs(p["noteWeights"][k] - mem_est[1].get(k, 1.0) / total) for k in notes))
        else:
            assert p.get("noteWeights") == m.get("noteWeights"), p["key"]
    print(f"저장값과 메모리의 최대 차이: 어코드 {worst_a:.6f} · 노트 {worst_n:.6f}", flush=True)
    assert np.abs(file_est[0] - mem_est[0]).max() < 1e-9 and file_est[1] == mem_est[1], "파일에서 다시 배운 추정이 메모리와 다르다"

    base, s_file, s_mem = scorer(v7), scorer(v8, file_est), scorer(memory, mem_est)
    # 측정 계산이 앱 계산(참조 엔진)과 같은가: v8 파일은 추정치도 저장값 그대로 읽는다
    eng8 = E.Engine(v8)
    T.check_same(s_file, lambda sp: eng8.scores(V.blend(sp)), V.V6_COMPARE_COMBOS[:3])

    real0, masked0 = T.human_ranks(base, qs), T.human_ranks(base, qs, masked=True)
    r0 = float(base.has.mean())
    r8 = sum(not p.get("estimatedNotes") for p in ps) / len(ps)
    rows, values = [], {}
    fam = s_file.eng.accord_family
    picks20 = V.reproduction_picks(base.cat, base.eng, 20)
    for name, s, r in (("메모리 측정 P1", s_mem, float(s_mem.has.mean())), ("v8 카탈로그 파일", s_file, r8)):
        real, masked = T.human_ranks(s, qs), T.human_ranks(s, qs, masked=True)
        h, g = T.hit_rate(real, hold, 50), T.hit_rate(masked, hold, 50)
        w = V.weighted_hit(h, g, r)
        d = h - T.hit_rate(real0, hold, 50)
        blind = sum({"citrus", "floral"} <= {fam[a] for a in s.ps[i]["accords"]} for i in s.recommend(T.spec_of(V.BLIND_SPOT), 5))
        brands = {s.ps[i]["brand"] for _, sp in V.BLENDS for i in s.recommend(T.spec_of(sp))}
        values[name] = {"h": h, "g": g, "w": w, "d": d, "real": real, "masked": masked}
        rows.append(f"| {name} | {h:.1f}% | {T.diff_ci(real, real0, hold, 50)} | {g:.1f}% | {w:.1f} | {F.w_ci(real, masked, r, real0, masked0, r0, hold)} | {100 * r:.1f}% | {T.repro(s, picks20)}/20 | {blind}/5 | {len(brands)} |")
        print(rows[-1], flush=True)
    a, b = values["메모리 측정 P1"], values["v8 카탈로그 파일"]
    moved = sum(a["real"][q] != b["real"][q] for q in hold)
    moved50 = sum((a["real"][q] <= 50) != (b["real"][q] <= 50) for q in hold)
    same = all(abs(a[k] - b[k]) < TOLERANCE for k in ("h", "g", "w", "d"))
    n_acc, n_note = sum(bool(p.get("estimatedAccords")) for p in ps), sum(bool(p.get("estimatedNotes")) for p in ps)
    DOC.write_text("\n".join([
        "# 엔진 v8 카탈로그 확인", "",
        f"> `py pipeline/experiments/engine_v8_check.py`. 카탈로그 `{v8['version']}` · 엔진 v{v8['engineVersion']} · 후보 {len(ps):,}개. 기준 v7은 `catalog_v7_compare.json.gz`(정리 데이터 + 옛 연결).", "",
        f"쿼리 {len(qs):,}개, 남겨 둔 절반 {len(hold):,}개(새 기준, 옛 연결로 만들어 고정). 추정치 표시: 어코드 {n_acc:,}개 · 노트 {n_note:,}개.", "",
        "| 측정 | 사람 유사 Top 50 | v7 대비 %p (95% 구간) | 가림 Top 50 | W | W v7 대비 %p (95% 구간) | 강도 보유 비율 r | 재현 | 맹점 | 다양성 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|", *rows, "",
        f"- 저장값과 메모리 계산의 최대 차이(반올림 때문): 어코드 {worst_a:.6f} · 노트 {worst_n:.6f}",
        f"- 남겨 둔 절반에서 순위가 달라진 쿼리 {moved}개, Top 50 안팎이 바뀐 쿼리 {moved50}개", ""]), encoding="utf-8", newline="\n")
    print(f"순위 달라짐 {moved} · Top 50 안팎 {moved50} · 일치 {same}", flush=True)
    if not same:
        raise SystemExit("v8 카탈로그의 측정값이 메모리 측정과 다릅니다.")


if __name__ == "__main__":
    main()
