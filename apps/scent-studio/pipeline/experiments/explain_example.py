"""docs/engine_how_it_works.md의 계산 예시 숫자를 만든다. 엔진 설정은 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/explain_example.py   (실제 카탈로그 필요)
- 예시 조합은 블라인드 문서에 없는 조합을 쓴다. 블라인드 조합이면 멈춘다(A/B가 드러나지 않게).
- 보여 줄 향수가 블라인드 문서에 나오면 [블라인드 문서에 있음]으로 표시한다. 문서에는 그런 향수를 쓰지 않는다.
- 엔진 점수를 식대로 손으로 다시 계산해 서로 맞는지 확인한다.
"""
from __future__ import annotations

import gzip
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
APP = Path(__file__).resolve().parent.parent.parent

BASE = [("lime", "top", "medium"), ("lily of the valley", "middle", "medium"), ("cedarwood", "base", "medium")]
STRONG = [("lime", "top", "medium"), ("lily of the valley", "middle", "medium"), ("cedarwood", "base", "strong")]
ALL_STRONG = [(k, layer, "strong") for k, layer, _ in BASE]
SWAPPED = [("lime", "base", "medium"), ("lily of the valley", "middle", "medium"), ("cedarwood", "top", "medium")]

used = [{k for k, _, _ in spec} for _, spec in V.BLENDS + V.v3_combos()]
if {k for k, _, _ in BASE} in used:
    raise SystemExit("예시 조합이 블라인드 조합과 겹칩니다")

blind_text = (APP / "docs" / "engine_blind.md").read_text(encoding="utf-8") + (APP / "docs" / "engine_blind_vs_v3.md").read_text(encoding="utf-8")
catalog = json.loads(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rb").read())
eng = E.Engine(catalog)
by_key = {p["key"]: p for p in catalog["perfumes"]}
names = eng.accords


def top(vec, n=6):
    total = sum(vec) or 1.0
    ranked = sorted(zip(names, vec), key=lambda t: -t[1])[:n]
    return " · ".join(f"{a} {100 * v / total:.1f}%" for a, v in ranked if v > 0)


def blend(spec):
    return [E.BlendNote(k, layer, E.intensity(s)) for k, layer, s in spec]


def hand_score(p, spec):
    """engine_ref를 부르지 않고 설명서의 식대로 계산"""
    b = [0.0] * len(names)
    for k, _, s in spec:
        for i, v in enumerate(eng.notes[k]["profile"]):
            b[i] += E.intensity(s) * v
    w = len(p["accords"])
    weights = [5, 4, 3, 2, 1][:w]
    a = [0.0] * len(names)
    for acc, x in zip(p["accords"], weights):
        a[names.index(acc)] = 100.0 * x / sum(weights)
    cos = sum(x * y for x, y in zip(a, b)) / (math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))
    all_notes = set(p["top"]) | set(p["middle"]) | set(p["base"])
    fams = {eng.notes[k]["family"] for k in all_notes}
    nm = ws = 0.0
    rows = []
    for k, layer, _ in spec:
        wn = math.log(1 + eng.n / eng.notes[k]["df"])
        c = 1.0 if k in p[layer] else 0.5 if k in all_notes else 0.3 if eng.notes[k]["family"] in fams else 0.0
        nm += wn * c
        ws += wn
        rows.append((k, round(wn, 3), c))
    return cos, nm / ws, (0.35 * cos + 0.55 * nm / ws) / 0.9, rows


print(f"후보 N = {eng.n:,} · 엔진 v{catalog['engineVersion']} · 어코드 {len(names)}개")
print("전체 평균 어코드:", top(catalog["globalMean"], 8))

print("\n## 노트 프로필")
for k, _, _ in BASE:
    n = eng.notes[k]
    having = [p for p in catalog["perfumes"] if k in set(p["top"]) | set(p["middle"]) | set(p["base"])]
    m = [sum(eng.vec[p["key"]][i] for p in having) / len(having) for i in range(len(names))]
    print(f"- {k} ({n['nameKo']}) df={n['df']} tier={n['tier']} 계열={n['family']} w={math.log(1 + eng.n / n['df']):.3f}")
    print("    단순 평균:", top(m))
    print("    최종 프로필:", top(n["profile"]))

for title, spec in (("중·중·중", BASE), ("시더우드 강", STRONG), ("모두 강", ALL_STRONG), ("라임·시더우드 층 맞바꿈", SWAPPED)):
    print(f"\n## {title}")
    print("조합 비율:", top(eng.blend_vector(blend(spec)), 8))
    print("화면 비율:", eng.display_ratios(blend(spec)))
    print("안내:", eng.balance_hints(blend(spec)))
    for rank, r in enumerate(eng.recommend(blend(spec), k=5), 1):
        p = by_key[r["perfumeKey"]]
        flag = " [블라인드 문서에 있음]" if p["name"] in blind_text else ""
        cos, nm, score, rows = hand_score(p, spec)
        assert abs(score - r["score"]) < 1e-9 and abs(cos - r["accordSim"]) < 1e-9 and abs(nm - r["noteMatch"]) < 1e-9
        print(f"{rank}. {p['brand']} — {p['name']}{flag}")
        print(f"   점수 {r['score']:.4f} ({r['grade']}) = 어코드 {r['accordSim']:.4f} · 노트 {r['noteMatch']:.4f} | 노트별 {rows}")
        print(f"   어코드 {p['accords']} · 탑 {p['top']} · 미들 {p['middle']} · 베이스 {p['base']} · 평가 {p['ratingCount']}")

base = {r["perfumeKey"]: r for r in eng.recommend(blend(BASE), k=eng.n)}
strong = {r["perfumeKey"]: r for r in eng.recommend(blend(ALL_STRONG), k=eng.n)}
same = list(base)[:10] == list(strong)[:10]
print("\n모두 강 vs 모두 중: Top 10 같음 =", same)

print("\n## 선호 계열(우디) 켠 경우")
first = next(iter(base))
p = by_key[first]
r = next(x for x in eng.recommend(blend(BASE), preferred={"woody"}, k=eng.n) if x["perfumeKey"] == first)
print(f"{p['name']}: 선호 없음 {base[first]['score']:.4f} → 선호 우디 {r['score']:.4f}, 우디 계열 비중 어코드 {p['accords']}")
scores = [x["score"] for x in eng.recommend(blend(BASE), preferred={"citrus"}, k=1)]
print("시트러스 선호 1위 점수:", [round(s, 4) for s in scores])

print("\n## 향수 어코드 비중 예시 (순위 5·4·3·2·1)")
print([(a, round(eng.vec[first][names.index(a)], 1)) for a in p["accords"]])
