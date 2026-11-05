"""사람 유사 시험: 지금 엔진 vs 예전 엔진 v6 (docs/engine_v2_trial.md와 같은 시험을 실제 엔진으로). 엔진 설정은 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/similar_votes.py   (실제 카탈로그, 원본 CSV, Decant 데이터 필요)
만드는 파일: docs/engine_similar_votes.md (집계만. 추천 목록은 보이지 않는다)

향수 p의 노트를 넣었을 때, Fragrantica 사용자가 "p와 비슷하다"고 투표한 향수가 몇 위에 오는지 잰다. 엔진은 이 투표를 쓰지 않는다.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import random
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from catalog_rules import load_old_catalog  # noqa: E402
import decant_join as D  # noqa: E402
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
RAW = APP / "pipeline" / "data" / "raw" / "decant"
MANUAL = APP / "pipeline" / "manual"
POS_MIN_UP, POS_MIN_RATIO = 20, 0.7  # 시험과 같은 정답 기준
MAX_QUERIES = 3000
SEED = 20261102


def build_queries(cat: dict, eng: E.Engine) -> list[dict]:
    """쿼리 {key, notes, pos(정답 key), holdout}. 시험(engine_v2_trial)과 같은 규칙"""
    index = D.source_index(D.load_rows(RAW / "perfumes.db"), D.read_note_aliases(MANUAL / "fragrantica_note_aliases.csv"))
    by_key = {p["key"]: p for p in cat["perfumes"]}
    key_of: dict[int, str] = {}
    for p in cat["perfumes"]:
        hit = D.match(p["brand"], p["name"], p["year"], {*p["top"], *p["middle"], *p["base"]}, index)
        if hit and (hit["id"] not in key_of or p["ratingCount"] > by_key[key_of[hit["id"]]]["ratingCount"]):
            key_of[hit["id"]] = p["key"]
    cold = D.open_db(RAW / "perfumes_cold.db")
    out = []
    for pid, key in sorted(key_of.items()):
        doc = cold.execute("select doc from perfumes_cold where id=?", (pid,)).fetchone()
        p = by_key[key]
        pos = []
        for q, up, down in (json.loads(doc[0]).get("reminds_me_of") or []) if doc else []:
            if up >= POS_MIN_UP and up / max(up + down, 1) >= POS_MIN_RATIO and q in key_of:
                if by_key[key_of[q]]["group"] != p["group"]:
                    pos.append(key_of[q])
        seen: set[str] = set()
        notes = [(k, layer) for layer in E.LAYERS for k in p[layer][:3]
                 if eng.notes[k]["tier"] != "hidden" and not (k in seen or seen.add(k))]
        if pos and notes:
            out.append({"key": key, "notes": notes, "pos": pos,
                        "holdout": int(hashlib.sha1(key.encode()).hexdigest(), 16) % 2 == 1})
    random.Random(SEED).shuffle(out)
    return out[:MAX_QUERIES]


def ranks(eng: E.Engine, cat: dict, qs: list[dict], one_per_layer: bool, cross_brand: bool) -> dict[str, int]:
    """쿼리 key → 정답 중 가장 높은 순위. 같은 향수 묶음은 빼고, 브랜드 상한은 적용하지 않는다."""
    by_key = {p["key"]: p for p in cat["perfumes"]}
    out = {}
    for q in qs:
        me = by_key[q["key"]]
        pos = [k for k in q["pos"] if not cross_brand or by_key[k]["brand"] != me["brand"]]
        if not pos:
            continue
        notes = q["notes"]
        if one_per_layer:
            seen: set[str] = set()
            notes = [(k, layer) for k, layer in notes if not (layer in seen or seen.add(layer))]
        scores = eng.scores([E.BlendNote(k, layer, 1.0) for k, layer in notes])
        rest = np.array([s for k, s in scores.items() if by_key[k]["group"] != me["group"]])
        out[q["key"]] = min(int((rest > scores[k]).sum()) + 1 for k in pos)
    return out


def compare(a: dict[str, int], b: dict[str, int], keys: list[str], k: int) -> tuple[float, float, str]:
    x = np.array([a[q] <= k for q in keys], dtype=float)
    y = np.array([b[q] <= k for q in keys], dtype=float)
    rng = np.random.default_rng(SEED)
    diff = x - y
    boots = [diff[rng.integers(0, len(diff), len(diff))].mean() for _ in range(1000)]
    lo, hi = np.percentile(boots, [2.5, 97.5])
    return 100 * x.mean(), 100 * y.mean(), f"{100 * diff.mean():+.1f} ({100 * lo:+.1f}~{100 * hi:+.1f})"


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat7 = load_old_catalog(APP / "pipeline" / "data" / "out" / "catalog.json.gz")
    eng7 = E.Engine(cat7)
    eng6, cat6, with_v6 = V.v6_engine()
    qs = build_queries(cat7, eng7)
    assert {p["key"] for p in cat6["perfumes"]} == {p["key"] for p in cat7["perfumes"]}
    rows = ["# 사람 유사 시험: 지금 엔진 vs v6", "",
            f"> 카탈로그 `{cat7['version']}` · 엔진 v{cat7['engineVersion']} · `py pipeline/experiments/similar_votes.py`로 만든 문서 · 집계만 싣습니다",
            "", f"쿼리 {len(qs):,}개. 정답은 찬성 {POS_MIN_UP}표 이상, 찬성 비율 {POS_MIN_RATIO:.0%} 이상의 '비슷하다' 투표입니다. "
            "**개발 절반은 시험판을 고를 때 이미 본 값이고, 남겨 둔 절반도 시험판 확인에 한 번 썼습니다.**", "",
            "| 입력 | 정답 | 쿼리 묶음 | 쿼리 수 | 지금 Top 10 | v6 Top 10 | 지금 Top 50 | v6 Top 50 | Top 50 차이 %p (95% 구간) |",
            "|---|---|---|---:|---:|---:|---:|---:|---:|"]
    for one, one_name in ((False, "층마다 3개"), (True, "층마다 1개")):
        for cross, cross_name in ((False, "전체"), (True, "다른 브랜드만")):
            r7 = ranks(eng7, cat7, qs, one, cross)
            r6 = with_v6(lambda: ranks(eng6, cat6, qs, one, cross))
            for part, keep in (("남겨 둔 절반", True), ("개발 절반", False)):
                keys = [q["key"] for q in qs if q["holdout"] == keep and q["key"] in r7]
                t10_7, t10_6, _ = compare(r7, r6, keys, 10)
                t50_7, t50_6, d50 = compare(r7, r6, keys, 50)
                rows.append(f"| {one_name} | {cross_name} | {part} | {len(keys):,} | {t10_7:.1f}% | {t10_6:.1f}% | {t50_7:.1f}% | {t50_6:.1f}% | {d50} |")
                print(rows[-1], flush=True)
    (APP / "docs" / "engine_similar_votes.md").write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
