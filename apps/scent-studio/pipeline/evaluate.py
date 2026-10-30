"""엔진 합격 기준표 (docs/design.md 14절)

앱 폴더에서:  py pipeline/build_catalog.py  다음에  py pipeline/evaluate.py

만드는 파일:
  - docs/engine_blind.md   블라인드 비교만 (A/B 중 어느 쪽이 지금 엔진인지 숨김)
  - docs/engine_report.md  나머지 기준표. 지금 엔진의 추천 목록이 보이는 표(맹점 Top 5, 눈으로 평가)는
                           사장이 블라인드 비교 답을 낸 뒤에만 넣는다 (먼저 보면 A/B가 드러나므로)
사장의 답은 docs/engine_blind_answers.txt 에 적는다. 예:
  블라인드: 1A 2B 3A 4A 5B 6A 7B 8A 9A 10B
  점수: 4 3 4 5 3 4 4 3 4 4
A/B 배치는 고정된 난수로 정하므로 파일에 따로 남기지 않는다 (같은 카탈로그·엔진이면 언제 돌려도 같음).
"""
from __future__ import annotations

import gzip
import json
import random
import re
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from engine_ref import BlendNote, Engine  # noqa: E402

APP = Path(__file__).resolve().parent.parent
CATALOG = APP / "pipeline" / "data" / "out" / "catalog.json.gz"
DOCS = APP / "docs"
ANSWERS = DOCS / "engine_blind_answers.txt"
BLIND_SEED = 20261030

# 평가용 조합 10개: 계열이 고루 섞이게. 1번은 기획서의 맹점 조합
BLENDS = [
    ("맹점: 레몬 강 · 오스만투스 · 샌달우드 약", [("lemon", "top", 1.5), ("osmanthus", "middle", 1.0), ("sandalwood", "base", 0.5)]),
    ("베르가못 · 장미 · 머스크", [("bergamot", "top", 1.0), ("rose", "middle", 1.0), ("musk", "base", 1.0)]),
    ("자몽 · 라벤더 · 베티버", [("grapefruit", "top", 1.0), ("lavender", "middle", 1.0), ("vetiver", "base", 1.0)]),
    ("만다린 · 자스민 · 바닐라", [("mandarin orange", "top", 1.0), ("jasmine", "middle", 1.0), ("vanilla", "base", 1.0)]),
    ("핑크 페퍼 · 아이리스 · 시더우드", [("pink pepper", "top", 1.0), ("iris", "middle", 1.0), ("cedarwood", "base", 1.0)]),
    ("레몬 · 녹차 · 화이트 머스크", [("lemon", "top", 1.0), ("green tea", "middle", 1.0), ("white musk", "base", 1.0)]),
    ("카다멈 · 타바코 · 우드 · 앰버", [("cardamom", "top", 1.0), ("tobacco", "middle", 1.0), ("oud", "base", 1.5), ("amber", "base", 1.0)]),
    ("복숭아 · 작약 · 머스크", [("peach", "top", 1.0), ("peony", "middle", 1.0), ("musk", "base", 1.0)]),
    ("마린 노트 · 세이지 · 시더우드 약", [("marine notes", "top", 1.5), ("sage", "middle", 1.0), ("cedarwood", "base", 0.5)]),
    ("시나몬 · 통카 빈 · 바닐라 · 샌달우드", [("cinnamon", "top", 1.0), ("tonka bean", "middle", 1.0), ("vanilla", "base", 1.5), ("sandalwood", "base", 1.0)]),
]


def blend(spec):
    return [BlendNote(k, layer, s) for k, layer, s in spec]


def read_answers() -> tuple[dict[int, str], list[int]] | None:
    """사장의 답: ({조합 번호: "A"|"B"}, [점수 10개]). 파일이 없으면 None"""
    if not ANSWERS.exists():
        return None
    text = ANSWERS.read_text(encoding="utf-8")
    blind_line = re.search(r"블라인드\s*:(.*)", text)
    score_line = re.search(r"점수\s*:(.*)", text)
    picks = {int(n): ab.upper() for n, ab in re.findall(r"(\d+)\s*([ABab])", blind_line.group(1))} if blind_line else {}
    scores = [int(x) for x in re.findall(r"\d", score_line.group(1))] if score_line else []
    if sorted(picks) != list(range(1, len(BLENDS) + 1)) or len(scores) != len(BLENDS):
        raise SystemExit(f"{ANSWERS.name} 형식이 맞지 않습니다: 블라인드 1~{len(BLENDS)}번 A/B, 점수 {len(BLENDS)}개")
    return picks, scores


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    with gzip.open(CATALOG, "rt", encoding="utf-8") as f:
        catalog = json.load(f)
    eng = Engine(catalog)
    by_key = {p["key"]: p for p in catalog["perfumes"]}
    acc_ko = {a["key"]: a["nameKo"] for a in catalog["accords"]}
    acc_family = {a["key"]: a["family"] for a in catalog["accords"]}
    answers = read_answers()

    def label(key):
        p = by_key[key]
        return f"{p['brand']} — {p['name']}"

    def accords_ko(key):
        return " · ".join(acc_ko[a] for a in by_key[key]["accords"][:3])

    header = f"> 카탈로그 `{catalog['version']}` · 엔진 v{catalog['engineVersion']} · 후보 향수 {len(catalog['perfumes']):,}개 · `py pipeline/evaluate.py`로 만든 문서"

    # ---------- 블라인드 비교 (따로 파일) ----------
    rng = random.Random(BLIND_SEED)
    engine_side: dict[int, str] = {}
    blind = [
        "# 블라인드 비교",
        "",
        header,
        "",
        "조합마다 A와 B 중 **조합의 느낌에 더 가까운 쪽**을 골라 주세요.",
        "한쪽은 지금 엔진(어코드 + 노트), 다른 쪽은 노트만 보는 방식입니다. 어느 쪽인지는 숨겨 두었습니다.",
        "**이 파일의 답을 내기 전에는 `engine_report.md`의 추천 목록을 보지 않도록, 그 표들은 답을 받은 뒤에 넣습니다.**",
        "",
        "답은 이렇게 주세요: `블라인드: 1A 2B 3A …`",
        "",
    ]
    for i, (name, spec) in enumerate(BLENDS, 1):
        current = eng.recommend(blend(spec), k=5)
        note_only = eng.recommend(blend(spec), k=5, note_only=True)
        a_is_engine = rng.random() < 0.5
        engine_side[i] = "A" if a_is_engine else "B"
        A, B = (current, note_only) if a_is_engine else (note_only, current)
        blind += [f"## {i}. {name}", "", "| | A | B |", "|---|---|---|"]
        for j in range(5):
            blind.append(f"| {j + 1} | {label(A[j]['perfumeKey'])} ({accords_ko(A[j]['perfumeKey'])}) "
                         f"| {label(B[j]['perfumeKey'])} ({accords_ko(B[j]['perfumeKey'])}) |")
        blind.append("")
    (DOCS / "engine_blind.md").write_text("\n".join(blind), encoding="utf-8")

    # ---------- 기준표 ----------
    lines = ["# 추천 엔진 합격 기준표", "", header,
             "> 기준은 docs/design.md 14절. 블라인드 비교는 `engine_blind.md`.", ""]
    if answers is None:
        lines += ["> ⏳ **블라인드 비교 답을 기다리는 중.** 지금 엔진의 추천 목록이 보이는 표(2절 표, 4절)는 답을 받은 뒤에 나옵니다.", ""]
    results = {}

    # 1. 재현: 평가 많은 향수 20개(브랜드 겹치지 않게)의 노트를 그대로 넣으면 그 향수가 Top 3
    picks, brands = [], set()
    for p in sorted(catalog["perfumes"], key=lambda p: (-p["ratingCount"], p["key"])):
        notes = [(k, layer) for layer in ("top", "middle", "base") for k in p[layer][:3]
                 if eng.notes[k]["tier"] != "hidden"]
        if p["brand"] in brands or not notes:
            continue
        picks.append((p, notes))
        brands.add(p["brand"])
        if len(picks) == 20:
            break
    hit, rows = 0, []
    for p, notes in picks:
        recs = eng.recommend([BlendNote(k, layer, 1.0) for k, layer in notes], k=10)
        groups = [by_key[r["perfumeKey"]]["group"] for r in recs]
        rank = groups.index(p["group"]) + 1 if p["group"] in groups else None  # 같은 향수의 다른 농도도 맞힌 것으로
        hit += 1 if rank and rank <= 3 else 0
        rows.append(f"| {label(p['key'])} | {rank or '10위 밖'} |")
    results["reproduce"] = hit
    lines += ["## 1. 재현 (기준: 20개 중 15개 이상이 Top 3, 2026-10-30 사장 결정으로 16→15)", "",
              f"**결과: {hit} / 20 {'✅' if hit >= 15 else '❌'}**", "",
              "<details><summary>향수별 순위</summary>", "", "| 향수 | 순위 |", "|---|---|", *rows, "", "</details>", ""]

    # 2. 맹점
    b0 = blend(BLENDS[0][1])
    recs = eng.recommend(b0, k=5)
    ok = sum(1 for r in recs if {"citrus", "floral"} <= {acc_family[a] for a in by_key[r["perfumeKey"]]["accords"]})
    citrus_first = sum(1 for r in recs if by_key[r["perfumeKey"]]["accords"][0] == "citrus")
    ratios = eng.display_ratios(b0)
    results["blindspot"] = ok
    lines += ["## 2. 맹점 조합 (기준: Top 5 중 4개 이상이 시트러스와 플로럴을 둘 다 가짐)", "",
              f"**결과: {ok} / 5 {'✅' if ok >= 4 else '❌'}**", "",
              f"- 조합 어코드: {', '.join(f'{acc_ko[a]} {p}%' for a, p in ratios[:4])}",
              f"- Top 5 중 1위 어코드가 시트러스인 향수: {citrus_first}개 (참고: 레몬을 '강'으로 골랐을 때 시트러스가 앞서는지)", ""]
    if answers is not None:
        lines += ["| 순위 | 향수 | 상위 어코드 | 점수 |", "|---|---|---|---|",
                  *[f"| {i + 1} | {label(r['perfumeKey'])} | {accords_ko(r['perfumeKey'])} | {r['score']:.3f} |"
                    for i, r in enumerate(recs)], ""]

    # 3. 블라인드 비교 결과
    lines += ["## 3. 블라인드 비교 (기준: 지금 엔진 쪽을 10개 중 6개 이상 고름)", ""]
    if answers is None:
        lines += ["`engine_blind.md`에서 답을 받은 뒤 채웁니다.", ""]
    else:
        chosen, scores = answers
        wins = sum(1 for i, ab in chosen.items() if engine_side[i] == ab)
        results["blind"] = wins
        lines += [f"**결과: {wins} / 10 {'✅' if wins >= 6 else '❌'}**", "",
                  "| 조합 | 고른 쪽 | 지금 엔진 쪽 |", "|---|---|---|",
                  *[f"| {i}. {BLENDS[i - 1][0]} | {chosen[i]} | {engine_side[i]} |" for i in range(1, len(BLENDS) + 1)], ""]

    # 4. 눈으로 평가 (지금 엔진의 목록이 보이므로 블라인드 답 뒤에만)
    lines += ["## 4. 눈으로 평가 (기준: 평균 3.5점 이상)", ""]
    all_brands, top1, top10 = set(), [], []
    eye = []
    for i, (name, spec) in enumerate(BLENDS, 1):
        b = blend(spec)
        r10 = eng.recommend(b, k=10)
        all_brands |= {by_key[r["perfumeKey"]]["brand"] for r in r10}
        top1.append(r10[0]["score"])
        top10.append(r10[-1]["score"])
        score_text = f"{answers[1][i - 1]}점" if answers else "☐"
        eye += [f"**{i}. {name}** — 조합 어코드: {', '.join(f'{acc_ko[a]} {p}%' for a, p in eng.display_ratios(b)[:4])} — 점수: {score_text}", "",
                "| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |", "|---|---|---|---|---|",
                *[f"| {j + 1} | {label(r['perfumeKey'])} | {accords_ko(r['perfumeKey'])} | {r['score']:.3f} | {r['grade']} |"
                  for j, r in enumerate(r10[:5])], ""]
    if answers is None:
        lines += ["블라인드 비교 답을 받은 뒤, 조합마다 지금 엔진의 Top 5를 보여 드리고 1~5점을 받습니다.", ""]
    else:
        avg = statistics.mean(answers[1])
        results["eye"] = avg
        lines += [f"**결과: 평균 {avg:.1f}점 {'✅' if avg >= 3.5 else '❌'}**", "", *eye]

    # 5. 다양성
    results["brands"] = len(all_brands)
    lines += ["## 5. 다양성 (기준: 조합 10개의 Top 10 전체에서 서로 다른 브랜드 40개 이상)", "",
              f"**결과: {len(all_brands)}개 {'✅' if len(all_brands) >= 40 else '❌'}**", ""]

    # 6. 등급 기준값 참고
    lines += ["## 6. 점수 분포 (등급 기준값 정하기용)", "",
              f"- 1위 점수: 평균 {statistics.mean(top1):.3f} · 최소 {min(top1):.3f} · 최대 {max(top1):.3f}",
              f"- 10위 점수: 평균 {statistics.mean(top10):.3f} · 최소 {min(top10):.3f} · 최대 {max(top10):.3f}",
              "- 지금 기준: 0.85 이상 매우 비슷 · 0.75 이상 비슷 · 그 아래 참고", ""]

    # 7. 계산 시간 (Python 참고값. 앱의 Java 엔진은 엔진 테스트에서 따로 잰다)
    t = time.perf_counter()
    for _, spec in BLENDS:
        eng.recommend(blend(spec), k=10)
    ms = (time.perf_counter() - t) * 1000 / len(BLENDS)
    lines += ["## 7. 계산 시간", "", f"- Python 참조 엔진 1회 평균 {ms:.0f}ms (참고값. 기준 300ms는 폰의 Java 엔진으로 잰다)", ""]

    (DOCS / "engine_report.md").write_text("\n".join(lines), encoding="utf-8")
    old_key = DOCS / "engine_blind_key.md"
    if old_key.exists():
        old_key.unlink()  # 예전 방식의 정답 파일 (이제 남기지 않음)
    summary = (f"재현 {results['reproduce']}/20 · 맹점 {results['blindspot']}/5 (시트러스 1위 {citrus_first}) · "
               f"브랜드 {results['brands']}개 · Python {ms:.0f}ms/회")
    if answers:
        summary += f" · 블라인드 {results['blind']}/10 · 눈으로 {results['eye']:.1f}점"
    print(summary)


if __name__ == "__main__":
    main()
