"""등급 기준값 비교 + 클론 표본 (2026-10-31 사장 요청). 엔진·카탈로그는 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/grades_clones_check.py   (실제 카탈로그·원본 CSV 필요)
만드는 파일: docs/engine_grades_clones.md
"""
from __future__ import annotations

import csv
import gzip
import json
import random
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import catalog_rules as R  # noqa: E402
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
SEED = 20261031


def esc(text: str) -> str:
    return text.replace("|", "\\|")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = json.load(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rt", encoding="utf-8"))
    eng = E.Engine(cat)
    out = ["# 등급 기준값 비교 · 클론 표본", "",
           f"> `py pipeline/experiments/grades_clones_check.py` (2026-10-31). 엔진 v{cat['engineVersion']} (어코드 {E.W_ACCORD} : 노트 {E.W_NOTE}, 강 {E.INTENSITIES[-1]}).", ""]

    # ---------- 등급 분포 ----------
    tops = [eng.recommend(V.blend(s), k=10) for _, s in V.BLENDS]
    out += ["## 등급 분포 (블라인드 비교 조합 10개)", "",
            "| 기준 (매우 비슷 / 비슷) | Top 5 (50개): 매우 비슷 / 비슷 / 참고 | Top 10 (100개): 매우 비슷 / 비슷 / 참고 | 매우 비슷이 없는 조합 |",
            "|---|---|---|---|"]
    for vs, s in ((0.87, 0.79), (0.90, 0.84)):
        g = lambda x: "VS" if x >= vs else "S" if x >= s else "R"  # noqa: E731
        g5 = [g(r["score"]) for recs in tops for r in recs[:5]]
        g10 = [g(r["score"]) for recs in tops for r in recs]
        none = sum(1 for recs in tops if all(g(r["score"]) != "VS" for r in recs))
        cnt = lambda gs: f"{gs.count('VS')} / {gs.count('S')} / {gs.count('R')}"  # noqa: E731
        out.append(f"| {vs} / {s} | {cnt(g5)} | {cnt(g10)} | {none} |")
    out.append("")

    # ---------- 클론 표본 ----------
    raw = {}
    with (APP / "pipeline" / "data" / "raw" / "parfumo_data_clean.csv").open(encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            raw[R.perfume_key(r["URL"])] = r
    sig = defaultdict(list)
    for p in cat["perfumes"]:
        sig[tuple(tuple(sorted(p[layer])) for layer in ("top", "middle", "base"))].append(p)
    def same(ps):
        return len({(p["ratingCount"], tuple(p["accords"])) for p in ps}) == 1

    def partly(ps):
        fp = [(p["ratingCount"], tuple(p["accords"])) for p in ps]
        return not same(ps) and len(set(fp)) < len(fp)

    clone_sets = [ps for ps in sig.values() if len({p["group"] for p in ps}) > 1 and len({p["brand"] for p in ps}) > 1]
    sample = random.Random(SEED).sample(clone_sets, min(20, len(clone_sets)))
    out += ["## 클론 표본 (브랜드가 다른 묶음 " + f"{len(clone_sets)}개 중 무작위 20개, 고정 난수 {SEED})", "",
            "- 노트 수 = 층별 노트 개수 합. 원본 순서 = 원본 CSV의 노트 문자열이 글자까지 같은지(사람이 따로 적었다면 순서·표기가 조금씩 다르기 쉽다).",
            "- 판단: 서로 다른 향수인데 **평가 수와 어코드까지 똑같으면 수집 오류 의심**(한 향수의 칸이 다른 향수에 복사된 것으로 보임 — 진짜 클론이면 평가 수가 같을 리 없다). "
            "평가 수가 다르고 한쪽이 평가 많은 '원작'이면 **클론·오마주 향수로 보임**, 그 밖은 **확인 필요**.",
            f"- 전체(브랜드가 다른 {len(clone_sets)}묶음): 평가 수·어코드까지 모두 같음 {sum(1 for ps in clone_sets if same(ps))}묶음 · "
            f"일부만 같음 {sum(1 for ps in clone_sets if partly(ps))}묶음 · 다름 {sum(1 for ps in clone_sets if not same(ps) and not partly(ps))}묶음", ""]
    for i, ps in enumerate(sample, 1):
        ps = sorted(ps, key=lambda p: (-p["ratingCount"], p["key"]))
        n_notes = sum(len(ps[0][layer]) for layer in ("top", "middle", "base"))
        strings = {(raw[p["key"]]["Top_Notes"], raw[p["key"]]["Middle_Notes"], raw[p["key"]]["Base_Notes"]) for p in ps}
        years = [p["year"] for p in ps]
        if same(ps):
            guess = "수집 오류 의심 (평가 수·어코드까지 같음)"
        elif partly(ps):
            guess = "일부 수집 오류 의심 (평가 수·어코드가 같은 향수가 섞임)"
        elif ps[0]["ratingCount"] >= 3 * max(p["ratingCount"] for p in ps[1:]) and len({y for y in years if y}) > 1:
            guess = "클론·오마주 향수로 보임 (평가 많은 원작 + 다른 연도의 다른 브랜드)"
        else:
            guess = "확인 필요"
        out += [f"### {i}. 노트 {n_notes}개 · 원본 순서 {'같음' if len(strings) == 1 else '다름'} · {guess}", "",
                "| 브랜드 — 이름 | 연도 | 평가 수 | 상위 어코드 | 조향사 | 원본 주소 |", "|---|---|---|---|---|---|",
                *[f"| {esc(p['brand'] + ' — ' + p['name'])} | {p['year'] or '?'} | {p['ratingCount']} | {' · '.join(p['accords'][:3])} "
                  f"| {esc(raw[p['key']].get('Perfumers') or '?')} | {raw[p['key']]['URL']} |"
                  for p in ps], ""]
    (APP / "docs" / "engine_grades_clones.md").write_text("\n".join(out), encoding="utf-8")
    print("\n".join(out[:12]))


if __name__ == "__main__":
    main()
