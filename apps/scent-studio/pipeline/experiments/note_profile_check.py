"""노트 프로필 점검 (2026-10-31 사장 요청): 머스크 → 플로럴, 패출리 → 스파이시처럼 엉뚱하게 잡히는지

앱 폴더에서:  py pipeline/experiments/note_profile_check.py   (실제 카탈로그 필요, 이 실험만 numpy를 쓴다)
만드는 파일: docs/engine_note_profiles.md
엔진·카탈로그는 바꾸지 않는다. 고칠 방법 후보("함께 든 노트의 몫 빼기")는 메모리에서만 만들어 비교한다.
"""
from __future__ import annotations

import copy
import gzip
import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent

# 사용 빈도 상위 50개 노트에 기대하는 어코드 (Claude가 조향 상식으로 적은 것. 판정 참고용)
EXPECTED = {
    "musk": "powdery synthetic animal creamy", "bergamot": "citrus fresh", "sandalwood": "woody creamy",
    "vanilla": "sweet gourmand", "patchouli": "earthy woody", "rose": "floral", "jasmine": "floral",
    "cedarwood": "woody", "amber": "oriental resinous sweet", "vetiver": "woody earthy green",
    "mandarin orange": "citrus fruity", "lemon": "citrus fresh", "iris": "powdery floral", "tonka bean": "sweet powdery gourmand",
    "orange blossom": "floral", "lavender": "fougère fresh green", "cardamom": "spicy", "ylang-ylang": "floral",
    "pink pepper": "spicy", "lily of the valley": "floral", "frankincense": "resinous smoky",
    "white musk": "powdery synthetic fresh creamy", "benzoin": "resinous sweet oriental", "geranium": "floral green fougère",
    "leather": "leathery", "oakmoss": "chypre green earthy", "violet": "floral powdery", "grapefruit": "citrus fresh",
    "oud": "woody oriental animal", "cinnamon": "spicy", "ambergris": "animal aquatic", "neroli": "citrus floral",
    "orange": "citrus", "nutmeg": "spicy", "blackcurrant": "fruity", "labdanum": "resinous leathery oriental",
    "saffron": "spicy leathery", "ginger": "spicy fresh", "peach": "fruity", "freesia": "floral",
    "tuberose": "floral", "heliotrope": "powdery sweet", "coriander": "spicy green", "black pepper": "spicy",
    "clove": "spicy", "moss": "green chypre", "peony": "floral", "magnolia": "floral", "raspberry": "fruity",
    "gaiac wood": "woody smoky",
}


def deconvolved(cat: dict, min_df: int = 20, iters: int = 400) -> dict:
    """후보 방법: 향수 어코드 ≈ 그 향수 노트 프로필의 평균 으로 보고, 음수 없는 최소제곱으로 노트 프로필을 푼다.
    머스크가 든 향수의 플로럴은 함께 든 장미·자스민이 가져가고, 머스크에는 자기 몫만 남는다.
    적게 나오는 노트(df < min_df)는 지금처럼 계열 평균을 쓴다."""
    acc = [a["key"] for a in cat["accords"]]
    notes = [n["key"] for n in cat["notes"]]
    idx = {k: i for i, k in enumerate(notes)}
    perf = cat["perfumes"]
    Y = np.zeros((len(perf), len(acc)))
    X = np.zeros((len(perf), len(notes)))
    w = [5, 4, 3, 2, 1]
    for r, p in enumerate(perf):
        ws = w[:len(p["accords"])]
        for a, x in zip(p["accords"], ws):
            Y[r, acc.index(a)] = x / sum(ws)
        ks = {k for layer in ("top", "middle", "base") for k in p[layer]}
        for k in ks:
            X[r, idx[k]] = 1.0 / len(ks)
    F = np.array([n["profile"] for n in cat["notes"]]) + 1e-3  # 지금 프로필에서 출발
    XtY, XtX = X.T @ Y, X.T @ X
    for _ in range(iters):  # 곱셈 갱신 (음수가 생기지 않음)
        F *= XtY / (XtX @ F + 1e-12)
    F /= F.sum(axis=1, keepdims=True)
    out = copy.deepcopy(cat)
    fam_sum, fam_w = defaultdict(lambda: np.zeros(len(acc))), defaultdict(float)
    for n in out["notes"]:
        if n["df"] >= min_df:
            fam_sum[n["family"]] += n["df"] * F[idx[n["key"]]]
            fam_w[n["family"]] += n["df"]
    for n in out["notes"]:
        prof = F[idx[n["key"]]] if n["df"] >= min_df or n["family"] not in fam_w else fam_sum[n["family"]] / fam_w[n["family"]]
        n["profile"] = [round(float(x), 6) for x in prof / prof.sum()]
    return out


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = json.load(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rt", encoding="utf-8"))
    ko = {a["key"]: a["nameKo"] for a in cat["accords"]}
    acc = [a["key"] for a in cat["accords"]]
    alt = deconvolved(cat)
    alt_by = {n["key"]: n for n in alt["notes"]}

    def top3(prof):
        order = sorted(range(len(prof)), key=lambda i: -prof[i])[:3]
        return [acc[i] for i in order], " · ".join(f"{ko[acc[i]]} {prof[i]:.2f}" for i in order)

    def verdict(t, expected):
        return "✓" if t[0] in expected else "△" if t[1] in expected else "✗"

    rows, counts = [], {"now": defaultdict(int), "alt": defaultdict(int)}
    for n in sorted(cat["notes"], key=lambda n: -n["df"])[:50]:
        exp = EXPECTED[n["key"]].split()
        t_now, s_now = top3(n["profile"])
        t_alt, s_alt = top3(alt_by[n["key"]]["profile"])
        v_now, v_alt = verdict(t_now, exp), verdict(t_alt, exp)
        counts["now"][v_now] += 1
        counts["alt"][v_alt] += 1
        rows.append(f"| {n['nameKo']} ({n['key']}) | {n['df']:,} | {' · '.join(ko[a] for a in exp)} | {s_now} | {v_now} | {s_alt} | {v_alt} |")

    # 머스크 강: 지금 엔진(0.35:0.55)에서 정말 플로럴이 세지는지 + 후보 방법이면
    E.W_ACCORD, E.W_NOTE = 0.35, 0.55
    by = {p["key"]: p for p in cat["perfumes"]}
    musk = []
    for label, c in (("지금 프로필", cat), ("후보: 함께 든 노트 몫 빼기", alt)):
        eng = E.Engine(c)
        for strong in (1.0, 2.0, 3.0):
            E.INTENSITIES = tuple(sorted({0.5, 1.0, strong}))
            bl = [E.BlendNote("musk", "base", strong), E.BlendNote("mandarin orange", "top", 1.0), E.BlendNote("iris", "middle", 1.0)]
            ratios = eng.display_ratios(bl)[:4]
            recs = eng.recommend(bl, k=5)
            musk.append(f"| {label} | {strong} | {', '.join(f'{ko[a]} {p}%' for a, p in ratios)} | "
                        + " / ".join(" · ".join(ko[a] for a in by[r['perfumeKey']]['accords'][:2]) for r in recs) + " |")

    # 후보 방법의 품질 지표 (재현·맹점·다양성) — 강 2.0, 0.35:0.55
    def quality(c):
        E.INTENSITIES = (0.5, 1.0, 2.0)
        eng = E.Engine(c)
        fam = {a["key"]: a["family"] for a in c["accords"]}
        res = {}
        for n_p in (20, 100):
            picks, brands = [], set()
            for p in sorted(c["perfumes"], key=lambda p: (-p["ratingCount"], p["key"])):
                seen: set[str] = set()
                ns = [(k, l) for l in ("top", "middle", "base") for k in p[l][:3]
                      if eng.notes[k]["tier"] != "hidden" and not (k in seen or seen.add(k))]
                if p["brand"] in brands or not ns:
                    continue
                picks.append((p, ns)); brands.add(p["brand"])
                if len(picks) == n_p:
                    break
            hit = 0
            for p, ns in picks:
                gs = [by[r["perfumeKey"]]["group"] for r in eng.recommend([E.BlendNote(k, l, 1.0) for k, l in ns], k=10)]
                hit += p["group"] in gs and gs.index(p["group"]) < 3
            res[n_p] = hit
        spot = eng.recommend(V.blend(V.BLIND_SPOT), k=5)
        cf = sum(1 for r in spot if {"citrus", "floral"} <= {fam[a] for a in by[r["perfumeKey"]]["accords"]})
        cfirst = sum(1 for r in spot if by[r["perfumeKey"]]["accords"][0] == "citrus")
        brands = set()
        for _, s in V.BLENDS:
            brands |= {by[r["perfumeKey"]]["brand"] for r in eng.recommend(V.blend(s), k=10)}
        return res[20], res[100], cf, cfirst, len(brands)

    q = [(label, *quality(c)) for label, c in (("지금 프로필", cat), ("후보: 함께 든 노트 몫 빼기", alt))]

    out = ["# 노트 프로필 점검", "",
           "> `py pipeline/experiments/note_profile_check.py` (2026-10-31). 엔진·카탈로그는 바꾸지 않았다.", "",
           "## 엔진이 '강'을 어떻게 쓰나", "",
           "- 노트마다 어코드 21개에 대한 비중 묶음(프로필, 합 1)이 카탈로그에 있다. 조합 벡터 = Σ 강도 × 프로필(design.md 8-3절).",
           "- 그래서 '강'은 **대표 어코드 하나가 아니라 그 노트의 프로필 전체**를 키운다. '대표 어코드'(프로필에서 가장 큰 칸)는 실험표를 읽으려고 붙인 이름표일 뿐이다.",
           "- 문제는 프로필의 **내용**: 지금 프로필(특징 강조)은 \"그 노트가 든 향수들에 어떤 어코드가 평균보다 많이 붙었나\"다. "
           "머스크는 대부분의 플로럴 향수 베이스에 들어가서 플로럴이, 패출리·베티버·라벤더는 '스파이시'가 붙은 남성향에 많이 들어가서 스파이시가 1위가 된다.",
           "- 데이터 사정: '스파이시' 어코드는 향수의 57%(7,914개)에 붙을 만큼 넓게 쓰이고, '흙내'는 4%, '푸제르'는 1%뿐이다(패출리가 든 향수도 71%가 스파이시, 8%만 흙내). "
           "머스크·앰버그리스는 21개 어코드 중에 제 어코드가 없다.", "",
           "## 사용 빈도 상위 50개 노트", "",
           f"판정: ✓ 1위가 기대 어코드 · △ 2위가 기대 어코드 · ✗ 둘 다 아님. 기대 어코드는 Claude가 조향 상식으로 적은 참고값이다. "
           f"**지금 ✓ {counts['now']['✓']} · △ {counts['now']['△']} · ✗ {counts['now']['✗']}** / "
           f"**후보 ✓ {counts['alt']['✓']} · △ {counts['alt']['△']} · ✗ {counts['alt']['✗']}**", "",
           "| 노트 | 향수 수 | 기대 어코드 | 지금 프로필 상위 3 | 판정 | 후보(함께 든 노트 몫 빼기) 상위 3 | 판정 |",
           "|---|---|---|---|---|---|---|", *rows, "",
           "## 머스크 강을 고르면 (머스크 강 · 만다린 · 아이리스, 어코드 0.35 : 노트 0.55)", "",
           "| 프로필 | 강 | 조합 비율 상위 4 | Top 5의 상위 어코드 2개 |", "|---|---|---|---|", *musk, "",
           "## 후보 방법의 품질 (강 2.0, 어코드 0.35 : 노트 0.55)", "",
           "| 프로필 | 재현 20 (기준 15) | 재현 100 | 맹점 (기준 4) | 맹점 시트러스 1위 | 다양성 브랜드 (기준 40) |", "|---|---|---|---|---|---|",
           *[f"| {r[0]} | {r[1]}/20 | {r[2]}/100 | {r[3]}/5 | {r[4]} | {r[5]} |" for r in q], ""]
    (APP / "docs" / "engine_note_profiles.md").write_text("\n".join(out), encoding="utf-8")
    print(f"지금 ✓{counts['now']['✓']} △{counts['now']['△']} ✗{counts['now']['✗']} / 후보 ✓{counts['alt']['✓']} △{counts['alt']['△']} ✗{counts['alt']['✗']}")
    for r in q:
        print(r)


if __name__ == "__main__":
    main()
