"""10단계 등급 기준값 계산과 검증 (규칙: docs/engine_grade_v8_rules.md).

앱 폴더에서:  py pipeline/experiments/grade_v8_run.py
읽는 것: pipeline/data/out/{catalog.json.gz, queries_v8.json, eye_v8_sheet.md}, docs/engine_eye_v8_combos.json
만드는 파일: docs/engine_grade_v8.md (향수 이름 없음)
값을 바꾸지 않는다. 임시값 칸은 코드에 적어 둔 0.87/0.81을 쓴다. 통과 여부를 끝에 출력한다.
"""
from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blind_v8_make as MK  # noqa: E402
import engine_ref as E  # noqa: E402
import engine_v8_trial as T  # noqa: E402
import evaluate as V  # noqa: E402
import eye_v8 as EY  # noqa: E402
import grade_v8 as G  # noqa: E402

DOC = MK.APP / "docs" / "engine_grade_v8.md"
OLD = (0.87, 0.81)
SEED_MAIN, SEED_CHECK = 20261104, 20261105
BOOT = 5000
VS, S, R = G.GRADES


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = MK.load("catalog.json.gz")
    assert cat["engineVersion"] == 8
    eng = E.Engine(cat)
    P = {p["key"]: p for p in cat["perfumes"]}

    def top10(spec, k: int = 10) -> list[dict]:
        return eng.recommend(V.blend(spec), k=k)

    def has_all(r: dict, spec) -> bool:
        p = P[r["perfumeKey"]]
        return {n[0] for n in spec} <= {*p["top"], *p["middle"], *p["base"]}

    main_specs = T.random_blends(cat, eng, 500, SEED_MAIN)
    check_specs = T.random_blends(cat, eng, 500, SEED_CHECK)
    main_lists = [top10(s) for s in main_specs]
    print("시드 1 끝", flush=True)
    check_lists = [top10(s) for s in check_specs]
    print("시드 2 끝", flush=True)
    cut = G.thresholds([r["similarity"] for rs in main_lists for r in rs])
    cut2 = G.thresholds([r["similarity"] for rs in check_lists for r in rs])
    print("기준값", cut, "확인", cut2, flush=True)
    cuts = (("임시값", OLD), ("새 값", cut))

    combos24 = list(V.BLENDS) + list(V.V6_COMPARE_COMBOS)
    lists24 = [(sp, top10(sp)) for _, sp in combos24]

    lines = ["# 엔진 v8 등급 기준값 (10단계)", "", "> 규칙 `docs/engine_grade_v8_rules.md`. `py pipeline/experiments/grade_v8_run.py`로 만든 문서. 향수 이름 없음.", "",
             "## 기준값", "", "| 시드 | 매우 비슷 | 비슷 | 비고 |", "|---|---:|---:|---|",
             f"| {SEED_MAIN} | {cut[0]:.3f} | {cut[1]:.3f} | 채택 |", f"| {SEED_CHECK} | {cut2[0]:.3f} | {cut2[1]:.3f} | 안정성 확인 |",
             f"| 차이 | {abs(cut[0] - cut2[0]):.3f} | {abs(cut[1] - cut2[1]):.3f} | |", ""]

    # 실제 추천 Top 10 등급 분포
    lines += ["## 임시값 → 새 값: 실제 추천 Top 10 등급 분포", "", "| 대상 | 향수 | 기준 | 매우 비슷 | 비슷 | 참고 |", "|---|---:|---|---:|---:|---:|"]
    pools = (("비교 24조합 Top 10", [r for _, rs in lists24 for r in rs]), ("비교 24조합 1위", [rs[0] for _, rs in lists24]),
             ("무작위 500조합 Top 10", [r for rs in check_lists for r in rs]), ("무작위 500조합 1위", [rs[0] for rs in check_lists]))
    kinds = (("실제 값 향수", False), ("추정 값 향수", True))
    pct = lambda c, n, g: f"{c[g]} ({100 * c[g] / n:.1f}%)"  # noqa: E731
    for label, rows in pools:
        for name, c in cuts:
            n = Counter(G.grade_of(r["similarity"], c) for r in rows)
            lines.append(f"| {label} | {len(rows)} | {name} {c[0]:.3f}/{c[1]:.3f} | {pct(n, len(rows), VS)} | {pct(n, len(rows), S)} | {pct(n, len(rows), R)} |")
    for kind, flag in kinds:
        rows = [r for _, rs in lists24 for r in rs if bool(P[r["perfumeKey"]].get("estimatedAccords")) == flag] + [r for rs in check_lists for r in rs if bool(P[r["perfumeKey"]].get("estimatedAccords")) == flag]
        for name, c in cuts:
            n = Counter(G.grade_of(r["similarity"], c) for r in rows)
            lines.append(f"| {kind} (24 + 500조합 Top 10) | {len(rows)} | {name} {c[0]:.3f}/{c[1]:.3f} | {pct(n, len(rows), VS)} | {pct(n, len(rows), S)} | {pct(n, len(rows), R)} |")
    lines.append("")

    # engine_v8_trial ① 표와 같은 칸
    qs = [q for q in json.loads((MK.OUT / "queries_v8.json").read_text(encoding="utf-8")) if q["holdout"]]
    q_lists = []
    for q in qs:
        seen: set[str] = set()
        notes = [(k, layer) for k, layer in q["notes"] if not (layer in seen or seen.add(layer))]
        me = P[q["key"]]
        rs = [r for r in top10([(k, layer, "medium") for k, layer in notes], 11) if P[r["perfumeKey"]]["group"] != me["group"]][:10]
        q_lists.append((set(q["pos"]), rs))
    print("쿼리 끝", flush=True)
    lines += ["## 확인 표 (`engine_v8_trial.md` ①과 같은 칸)", "",
              f"비교 24조합 Top 10, 시드 {SEED_CHECK} 무작위 500조합, 남겨 둔 절반 쿼리 {len(qs)}개(층마다 1개, 강도 실제 값 향수만).", "",
              "| 기준 | 매우 비슷 | 비슷 | 참고 | 노트 전부 든 향수 중 '참고' | 무작위 500조합 1위가 '참고' | 노트가 빠진 1위인데 '매우 비슷' | 사람 유사 정답 비율: 매우 비슷 / 비슷 / 참고 |",
              "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for name, c in cuts:
        count = Counter(G.grade_of(r["similarity"], c) for _, rs in lists24 for r in rs)
        whole = [r for sp, rs in lists24 for r in rs if has_all(r, sp)]
        whole_ref = sum(G.grade_of(r["similarity"], c) == R for r in whole)
        first_ref = sum(G.grade_of(rs[0]["similarity"], c) == R for rs in check_lists)
        miss = [rs[0] for sp, rs in zip(check_specs, check_lists) if not has_all(rs[0], sp)]
        miss_very = sum(G.grade_of(r["similarity"], c) == VS for r in miss)
        hit, tot = Counter(), Counter()
        for pos, rs in q_lists:
            for r in rs:
                if not P[r["perfumeKey"]].get("estimatedAccords"):
                    g = G.grade_of(r["similarity"], c)
                    tot[g] += 1
                    hit[g] += r["perfumeKey"] in pos
        prec = " / ".join(f"{100 * hit[g] / tot[g]:.1f}%" if tot[g] else "—" for g in G.GRADES)
        lines.append(f"| {name} {c[0]:.3f}/{c[1]:.3f} | {count[VS]} | {count[S]} | {count[R]} | {whole_ref}/{len(whole)} | {first_ref}/500 | {miss_very}/{len(miss)} | {prec} |")
    lines.append("")

    # 눈 평가 검증
    combos = json.loads((MK.APP / "docs" / "engine_eye_v8_combos.json").read_text(encoding="utf-8"))["combos"]
    scores = EY.read_scores((MK.OUT / "eye_v8_sheet.md").read_text(encoding="utf-8"))
    sheet = (MK.OUT / "eye_v8_sheet.md").read_text(encoding="utf-8")
    items = []  # (조합 번호, 등급용 유사도, 점수, 추정 여부, 분류, 구분)
    for c in combos:
        rs = eng.recommend(V.blend([tuple(n) for n in c["notes"]]), k=5)
        for r, sc in zip(rs, scores[c["no"]]):
            assert P[r["perfumeKey"]]["name"].replace("|", "\\|") in sheet, "시트의 향수와 다릅니다"
            items.append((c["no"], r["similarity"], sc, bool(P[r["perfumeKey"]].get("estimatedAccords")), c["bucket"], c["kind"]))
    assert len(items) == 75

    rng = np.random.default_rng(SEED_MAIN)

    def rows_for(label: str, subset: list[tuple], c: tuple[float, float], name: str) -> list[str]:
        nos = sorted({i[0] for i in subset})
        out = []
        for g in G.GRADES:
            mine = [i for i in subset if G.grade_of(i[1], c) == g]
            if not mine:
                out.append(f"| {label} | {name} | {g} | 0 | — | — |")
                continue
            by_combo = {n: [i[2] for i in mine if i[0] == n] for n in nos}
            boots = []
            for _ in range(BOOT):
                picked = [x for n in rng.choice(nos, len(nos)) for x in by_combo[n]]
                if picked:
                    boots.append(np.mean(picked))
            low, high = np.percentile(boots, [2.5, 97.5])
            out.append(f"| {label} | {name} | {g} | {len(mine)} | {np.mean([i[2] for i in mine]):.2f} | {low:.2f}~{high:.2f} |")
        return out

    lines += ["## 검증: 등급별 눈 평가 점수", "", "| 묶음 | 기준 | 등급 | 향수 | 평균 점수 | 95% 구간 (조합 단위) |", "|---|---|---|---:|---:|---|"]
    gourmand = [i for i in items if i[4] == "구르망"]
    groups = (("평가 75개 전체", items), ("기본 10개", [i for i in items if i[5] == "기본"]), ("구르망 6개", gourmand),
              ("실제 값 향수", [i for i in items if not i[3]]), ("추정 값 향수", [i for i in items if i[3]]))
    for label, subset in groups:
        lines += rows_for(label, subset, cut, "새 값")
    lines += rows_for("평가 75개 전체", items, OLD, "임시값")
    ok, why = G.verdict({g: [i[2] for i in items if G.grade_of(i[1], cut) == g] for g in G.GRADES})
    lines += ["", f"**판정: {'통과' if ok else '통과 못 함'}.** {why}", ""]
    DOC.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print("\n".join(lines))
    print("RESULT", "PASS" if ok else "FAIL", cut)


if __name__ == "__main__":
    main()
