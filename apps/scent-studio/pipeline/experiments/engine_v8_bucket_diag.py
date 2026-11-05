"""분류별 v7·v8 Top 5 진단과 추정 어코드 평평함 확인 (2026-11-05 사장 요청, 측정만. 고치지 않는다).

앱 폴더에서:  py pipeline/experiments/engine_v8_bucket_diag.py
읽는 것: pipeline/data/out/{catalog.json.gz, catalog_v7_compare.json.gz, queries_v8.json}
만드는 파일: docs/engine_v8_bucket_diag.md (향수 이름 없음)

- 쿼리: 남겨 둔 절반 474개. 분류는 쿼리 향수의 첫 어코드 계열(블라인드 규칙).
- 쿼리 향수와 같은 묶음은 목록에서 뺀다. 쿼리마다 한 번씩, 층마다 3개(측정 기준)와 층마다 1개(블라인드처럼 짧은 조합)로 잰다.
- 어코드 비중은 두 목록 모두 v8 카탈로그 값으로 읽는다. 강도 없는 향수는 v7 목록에서도 추정치로 읽는다는 뜻이다.
- 핵심 어코드: v8 노트 프로필로 계산한 조합 벡터의 1위 어코드.
- 95% 구간: 쿼리 단위 부트스트랩 5,000회. 분류·항목이 여러 개라 구간을 여러 번 본 것을 보정하지 않았다.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blind_v8 as BL  # noqa: E402
import blind_v8_make as MK  # noqa: E402
import engine_ref as E  # noqa: E402
import estimate as EST  # noqa: E402
import evaluate as V  # noqa: E402

DOC = MK.APP / "docs" / "engine_v8_bucket_diag.md"
HOLDOUT = 474
SEED = 20261107
BOOT = 5000
METRICS = (("notes", "평균 노트 일치 수"), ("core", "핵심 어코드 비중 %"), ("bucket", "분류 어코드 비중 %"), ("est", "추정치 향수 비율 %"))
ROWS = (*BL.BUCKETS, "전체")


def ci(values: np.ndarray, rng: np.random.Generator) -> tuple[float, float]:
    means = values[rng.integers(0, len(values), (BOOT, len(values)))].mean(axis=1)
    return float(np.percentile(means, 2.5)), float(np.percentile(means, 97.5))


def bucket_share(shares: dict[str, float], bucket: str, family: dict[str, str]) -> float:
    return sum(v for a, v in shares.items() if BL.BUCKET_OF[family[a]] == bucket)


def shape(values: np.ndarray) -> tuple[float, float, float]:
    """행마다 (1위 값, 상위 3개 합, 유효 어코드 수). 유효 어코드 수가 클수록 평평하다."""
    v = np.sort(values)[::-1]
    p = v[v > 0] / v.sum()
    return float(v[0]), float(v[:3].sum()), float(np.exp(-(p * np.log(p)).sum()))


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    v7, v8 = MK.load("catalog_v7_compare.json.gz"), MK.load("catalog.json.gz")
    assert v7["engineVersion"] == 7 and v8["engineVersion"] == 8
    qs = [q for q in json.loads((MK.OUT / "queries_v8.json").read_text(encoding="utf-8")) if q["holdout"]]
    assert len(qs) == HOLDOUT, len(qs)
    eng7, eng8 = E.Engine(v7), E.Engine(v8)
    P = {p["key"]: p for p in v8["perfumes"]}
    family = {a["key"]: a["family"] for a in v8["accords"]}
    accords = eng8.accords
    bucket_of = lambda p: BL.BUCKET_OF[family[p["accords"][0]]]  # noqa: E731
    rng = np.random.default_rng(SEED)
    lines = ["# 분류별 v7·v8 진단 (측정만)", "", "> 2026-11-05 사장 요청. 고치지 않았다. 방법은 `pipeline/experiments/engine_v8_bucket_diag.py` 맨 위 설명.", ""]

    counts = {b: sum(bucket_of(P[q["key"]]) == b for q in qs) for b in BL.BUCKETS}
    print("쿼리", counts, flush=True)

    for label, one in (("층마다 3개 (측정 기준)", False), ("층마다 1개 (블라인드처럼 짧은 조합)", True)):
        data = {b: {m: ([], []) for m, _ in METRICS} for b in ROWS}
        same = {b: [] for b in ROWS}
        for n, q in enumerate(qs):
            notes = q["notes"]
            if one:
                seen: set[str] = set()
                notes = [(k, layer) for k, layer in notes if not (layer in seen or seen.add(layer))]
            spec = V.blend([(k, layer, "medium") for k, layer in notes])
            vector = eng8.blend_vector(spec)
            core = accords[int(np.argmax(vector))]
            me = P[q["key"]]
            wanted = {k for k, _ in notes}
            bucket = bucket_of(me)
            lists = []
            for side, eng in enumerate((eng7, eng8)):
                top = [P[r["perfumeKey"]] for r in eng.recommend(spec, k=7)]
                top = [p for p in top if p["group"] != me["group"]][:5]
                lists.append({p["key"] for p in top})
                row = {"notes": np.mean([len(wanted & {*p["top"], *p["middle"], *p["base"]}) for p in top]),
                       "core": np.mean([p["accordShares"].get(core, 0.0) for p in top]),
                       "bucket": np.mean([bucket_share(p["accordShares"], bucket, family) for p in top]),
                       "est": 100.0 * np.mean([bool(p.get("estimatedAccords")) for p in top])}
                for m, _ in METRICS:
                    for b in (bucket, "전체"):
                        data[b][m][side].append(row[m])
            for b in (bucket, "전체"):
                same[b].append(len(lists[0] & lists[1]))
            if (n + 1) % 100 == 0:
                print(label, n + 1, flush=True)
        lines += [f"## Top 5 비교: {label}", "", "| 분류 | 쿼리 | 항목 | v7 | v8 | v8 − v7 (95% 구간) |", "|---|---:|---|---:|---:|---|"]
        for b in ROWS:
            for m, name in METRICS:
                a, c = np.array(data[b][m][0]), np.array(data[b][m][1])
                low, high = ci(c - a, rng)
                digits = 2 if m == "notes" else 1
                lines.append(f"| {b} | {len(a)} | {name} | {a.mean():.{digits}f} | {c.mean():.{digits}f} | {(c - a).mean():+.{digits}f} ({low:+.{digits}f}~{high:+.{digits}f}) |")
            lines.append(f"| {b} | {len(same[b])} | 두 목록에 함께 있는 향수 수 | | | {np.mean(same[b]):.2f} / 5 |")
        lines.append("")

    # 평평함 1: 저장된 값 그대로. 추정 행과 실제 행은 향수 집단이 다르다
    ps = v8["perfumes"]
    lines += ["## 평평함 1: 저장된 값 (추정 행 vs 실제 행, 향수 집단이 다름)", "",
              "| 분류 | 종류 | 행 수 | 1위 값 25%·중앙·75% | 1위 평균 | 상위 3개 합 평균 | 유효 어코드 수 평균 | 분류 비중 평균 |", "|---|---|---:|---|---:|---:|---:|---:|"]
    for b in ROWS:
        for kind, flag in (("추정", True), ("실제", False)):
            rows = [p for p in ps if bool(p.get("estimatedAccords")) == flag and p.get("accordShares") and (b == "전체" or bucket_of(p) == b)]
            s = np.array([shape(np.array([p["accordShares"].get(a, 0.0) for a in accords])) for p in rows])
            share = np.mean([bucket_share(p["accordShares"], bucket_of(p), family) for p in rows])
            q1, q2, q3 = np.percentile(s[:, 0], [25, 50, 75])
            lines.append(f"| {b} | {kind} | {len(rows)} | {q1:.1f} · {q2:.1f} · {q3:.1f} | {s[:, 0].mean():.1f} | {s[:, 1].mean():.1f} | {s[:, 2].mean():.1f} | {share:.1f} |")
    lines.append("")

    # 평평함 2: 같은 향수로 비교. 학습에 쓰지 않은 실제 행의 실제 값 vs 추정 값
    pred = EST.fit(v8)[0]
    lines += ["## 평평함 2: 같은 향수의 실제 값 vs 추정 값 (학습에 안 쓴 절반)", "",
              "| 분류 | 향수 | 항목 | 실제 | 추정 | 추정 − 실제 (95% 구간) |", "|---|---:|---|---:|---:|---|"]
    for b in ROWS:
        idx = [i for i, p in enumerate(ps) if p.get("accordShares") and not p.get("estimatedAccords") and not EST.is_dev(p["key"]) and (b == "전체" or bucket_of(p) == b)]
        real = np.array([[ps[i]["accordShares"].get(a, 0.0) for a in accords] for i in idx])
        guess = pred[idx]
        rs, gs = np.array([shape(r) for r in real]), np.array([shape(g) for g in guess])
        own = lambda m: np.array([sum(row[j] for j, a in enumerate(accords) if BL.BUCKET_OF[family[a]] == bucket_of(ps[i])) for i, row in zip(idx, m)])  # noqa: E731
        items = (("1위 값", rs[:, 0], gs[:, 0]), ("상위 3개 합", rs[:, 1], gs[:, 1]), ("유효 어코드 수", rs[:, 2], gs[:, 2]), ("분류 비중", own(real), own(guess)))
        for name, r, g in items:
            low, high = ci(g - r, rng)
            lines.append(f"| {b} | {len(idx)} | {name} | {r.mean():.1f} | {g.mean():.1f} | {(g - r).mean():+.1f} ({low:+.1f}~{high:+.1f}) |")
    lines.append("")
    DOC.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
