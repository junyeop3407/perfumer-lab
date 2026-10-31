"""엔진 합격 기준표 (docs/design.md 14절)

앱 폴더에서:  py pipeline/build_catalog.py  다음에  py pipeline/evaluate.py

만드는 파일:
  - docs/engine_blind.md   블라인드 비교만 (A/B 중 어느 쪽이 지금 엔진인지 숨김)
  - docs/engine_report.md  나머지 기준표. 지금 엔진의 추천 목록이 보이는 표(맹점 Top 5, 눈으로 평가)는
                           사장이 블라인드 비교 답을 낸 뒤에만 넣는다 (먼저 보면 A/B가 드러나므로)
사장의 답은 docs/engine_blind_answers.txt 에 적는다. 예:
  출처: 사장                      (Claude가 대신 고른 임시 답안이면 claude-provisional)
  고정본: engine_blind_v3_pairs.json   (답을 단 문서의 줄별 A/B 향수. 문서를 다시 만들어도 이걸로 채점)
  블라인드: 1-1A 1-2B … 10-5A    (조합-줄마다 A/B, 양쪽이 같은 향수면 =)  또는  1A 2B … 10B (조합마다)
  점수: 4 3 4 5 3 4 4 3 4 4       (눈으로 평가, 없으면 비워 둔다)
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
import engine_ref  # noqa: E402
from engine_ref import BlendNote, Engine  # noqa: E402

APP = Path(__file__).resolve().parent.parent
CATALOG = APP / "pipeline" / "data" / "out" / "catalog.json.gz"
DOCS = APP / "docs"
ANSWERS = DOCS / "engine_blind_answers.txt"
BLIND_SEED = 20261030

# 기획서의 맹점 조합 (기준표 2번). 2026-10-31 블라인드 비교에서는 뺐다: 이 조합의 지금 엔진 Top 5를
# 사장에게 따로 보여 주므로(docs/engine_variants.md) 블라인드에 넣으면 A/B가 드러난다
BLIND_SPOT = [("lemon", "top", "strong"), ("osmanthus", "middle", "medium"), ("sandalwood", "base", "weak")]

# 블라인드 비교·눈으로 평가·다양성용 조합 10개: 계열이 고루 섞이게
BLENDS = [
    ("핑크 페퍼 · 장미 · 패출리", [("pink pepper", "top", "medium"), ("rose", "middle", "medium"), ("patchouli", "base", "medium")]),
    ("베르가못 · 장미 · 머스크", [("bergamot", "top", "medium"), ("rose", "middle", "medium"), ("musk", "base", "medium")]),
    ("자몽 · 라벤더 · 베티버", [("grapefruit", "top", "medium"), ("lavender", "middle", "medium"), ("vetiver", "base", "medium")]),
    ("만다린 · 자스민 · 바닐라", [("mandarin orange", "top", "medium"), ("jasmine", "middle", "medium"), ("vanilla", "base", "medium")]),
    ("핑크 페퍼 · 아이리스 · 시더우드", [("pink pepper", "top", "medium"), ("iris", "middle", "medium"), ("cedarwood", "base", "medium")]),
    ("레몬 · 녹차 · 화이트 머스크", [("lemon", "top", "medium"), ("green tea", "middle", "medium"), ("white musk", "base", "medium")]),
    ("카다멈 · 타바코 · 우드 · 앰버", [("cardamom", "top", "medium"), ("tobacco", "middle", "medium"), ("oud", "base", "strong"), ("amber", "base", "medium")]),
    ("복숭아 · 작약 · 머스크", [("peach", "top", "medium"), ("peony", "middle", "medium"), ("musk", "base", "medium")]),
    ("마린 노트 · 세이지 · 시더우드 약", [("marine notes", "top", "strong"), ("sage", "middle", "medium"), ("cedarwood", "base", "weak")]),
    ("시나몬 · 통카 빈 · 바닐라 · 샌달우드", [("cinnamon", "top", "medium"), ("tonka bean", "middle", "medium"), ("vanilla", "base", "strong"), ("sandalwood", "base", "medium")]),
]


def blend(spec):
    return [BlendNote(k, layer, engine_ref.intensity(s)) for k, layer, s in spec]


def cell(text: str) -> str:
    """마크다운 표 칸: 이름 안의 | 가 칸을 밀지 않게"""
    return text.replace("|", "\\|")


def reproduction_picks(catalog: dict, eng: Engine, n: int) -> list[tuple[dict, list[tuple[str, str]]]]:
    """재현 시험의 정답: 평가 많은 향수 n개(브랜드 겹치지 않게)와 넣을 노트(화면 규칙대로 층마다 앞 3개, 같은 노트는 한 번).
    다른 브랜드에 노트·어코드가 똑같은 향수가 있으면 정답에서 뺀다(2026-10-31 사장 요청: 평가 수만 조금 다른
    '거의 복사'도 원본 데이터셋의 복사 오류일 수 있어서. 예: Outremer Lavande 694 / XerJoff 695)."""
    sig = lambda p: (tuple(tuple(sorted(p[layer])) for layer in ("top", "middle", "base")), tuple(p["accords"]))  # noqa: E731
    brands_by_sig: dict = {}
    for p in catalog["perfumes"]:
        brands_by_sig.setdefault(sig(p), set()).add(p["brand"])
    picks, brands = [], set()
    for p in sorted(catalog["perfumes"], key=lambda p: (-p["ratingCount"], p["key"])):
        seen: set[str] = set()
        notes = [(k, layer) for layer in ("top", "middle", "base") for k in p[layer][:3]
                 if eng.notes[k]["tier"] != "hidden" and not (k in seen or seen.add(k))]
        if p["brand"] in brands or not notes or len(brands_by_sig[sig(p)]) > 1:
            continue
        picks.append((p, notes))
        brands.add(p["brand"])
        if len(picks) == n:
            break
    return picks


def parse_scores(text: str) -> list[int]:
    """눈으로 평가 점수: "4 3 4 …"(조합 순서대로) 또는 "1=4 2=3 …"(조합 번호=점수)"""
    pairs = re.findall(r"(\d+)\s*=\s*(\d)", text)
    if pairs:
        by_no = {int(n): int(v) for n, v in pairs}
        return [by_no[i] for i in sorted(by_no)] if sorted(by_no) == list(range(1, len(BLENDS) + 1)) else [-1]
    return [int(x) for x in re.findall(r"\d", text.split("(")[0])]


def read_answers() -> dict | None:
    """답안 파일: {"source", "pairs"(고정본 경로|None), "lines"{(조합, 줄): A|B|=} 또는 "combos"{조합: A|B}, "scores"[]}.
    파일이 없으면 None. # 으로 시작하는 줄은 설명"""
    if not ANSWERS.exists():
        return None
    text = "\n".join(l for l in ANSWERS.read_text(encoding="utf-8").splitlines() if not l.lstrip().startswith("#"))

    def field(name: str) -> str:
        m = re.search(rf"{name}\s*:(.*)", text)
        return m.group(1).strip() if m else ""

    blind, score_text = field("블라인드"), field("점수")
    ans = {"source": field("출처") or "사장", "pairs": DOCS / field("고정본") if field("고정본") else None,
           "scores": parse_scores(score_text), "lines": {}, "combos": {}}
    if re.search(r"\d+-\d+", blind):
        ans["lines"] = {(int(i), int(j)): ab.upper() for i, j, ab in re.findall(r"(\d+)-(\d+)\s*([ABab=])", blind)}
        expected = {(i, j) for i in range(1, len(BLENDS) + 1) for j in range(1, 6)}
        if set(ans["lines"]) != expected:
            raise SystemExit(f"{ANSWERS.name}: 블라인드 답이 1-1 ~ {len(BLENDS)}-5 를 모두 담아야 합니다")
    else:
        ans["combos"] = {int(n): ab.upper() for n, ab in re.findall(r"(\d+)\s*([ABab])", blind)}
        if sorted(ans["combos"]) != list(range(1, len(BLENDS) + 1)):
            raise SystemExit(f"{ANSWERS.name}: 블라인드 1~{len(BLENDS)}번 A/B")
    if ans["scores"] and (len(ans["scores"]) != len(BLENDS) or not all(1 <= x <= 5 for x in ans["scores"])):
        raise SystemExit(f"{ANSWERS.name}: 점수는 1~5점 {len(BLENDS)}개 (아직이면 비워 둔다)")
    return ans


def score_lines(ans: dict, pairs: list, engine_side: dict[int, str]) -> tuple[float, list[str], dict]:
    """줄별 답 채점. 같은 향수 줄은 빼고, 같은 두 향수가 자리만 바뀐 줄은 처음 것만 센다(일관성만 확인).
    조합마다 남은 줄에서 엔진 쪽을 더 많이 고르면 1, 같으면 0.5. → (점수, 표 줄, 집계)"""
    total, rows = 0.0, []
    tally = {"engine": 0, "other": 0, "same": [], "dup": [], "inconsistent": []}
    for i, lines in enumerate(pairs, 1):
        seen: dict = {}
        e = o = 0
        for j, (a, b) in enumerate(lines, 1):
            pick = ans["lines"][(i, j)]
            if a == b:
                tally["same"].append(f"{i}-{j}")
                continue
            chosen = a if pick == "A" else b if pick == "B" else None
            key = frozenset((a, b))
            if key in seen:
                first, first_chosen = seen[key]
                tally["dup"].append(f"{i}-{first}/{i}-{j}")
                if chosen != first_chosen:
                    tally["inconsistent"].append(f"{i}-{first}/{i}-{j}")
                continue
            seen[key] = (j, chosen)
            if pick == "=":
                continue
            if pick == engine_side[i]:
                e += 1
            else:
                o += 1
        win = 1.0 if e > o else 0.5 if e == o else 0.0
        total += win
        tally["engine"] += e
        tally["other"] += o
        verdict = "승" if win == 1 else "동점" if win else "패"
        rows.append(f"| {i}. {BLENDS[i - 1][0]} | {engine_side[i]} | {e} : {o} | {verdict} |")
    return total, rows, tally


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
    current_pairs: list = []
    blind = [
        "# 블라인드 비교",
        "",
        header,
        "",
        "조합마다(또는 줄마다) A와 B 중 **조합의 느낌에 더 가까운 쪽**을 골라 주세요.",
        "한쪽은 지금 엔진(어코드 + 노트), 다른 쪽은 노트만 보는 방식입니다. 어느 쪽인지는 숨겨 두었습니다.",
        "**이 파일의 답을 내기 전에는 `engine_report.md`의 추천 목록을 보지 않도록, 그 표들은 답을 받은 뒤에 넣습니다.**",
        "",
        "답은 이렇게 주세요: `블라인드: 1A 2B 3A …` (조합마다) 또는 `블라인드: 1-1A 1-2B … 10-5A` (줄마다, 양쪽이 같은 향수면 `=`)",
        "",
    ]
    for i, (name, spec) in enumerate(BLENDS, 1):
        current = eng.recommend(blend(spec), k=5)
        note_only = eng.recommend(blend(spec), k=5, note_only=True)
        a_is_engine = rng.random() < 0.5
        engine_side[i] = "A" if a_is_engine else "B"
        A, B = (current, note_only) if a_is_engine else (note_only, current)
        current_pairs.append([[A[j]["perfumeKey"], B[j]["perfumeKey"]] for j in range(5)])
        blind += [f"## {i}. {name}", "", "| | A | B |", "|---|---|---|"]
        for j in range(5):
            blind.append(f"| {j + 1} | {cell(label(A[j]['perfumeKey']))} ({accords_ko(A[j]['perfumeKey'])}) "
                         f"| {cell(label(B[j]['perfumeKey']))} ({accords_ko(B[j]['perfumeKey'])}) |")
        blind.append("")
        # 판별력 없는 줄 표시 (2026-10-31): 양쪽이 같은 향수 / 같은 두 향수가 자리만 바뀐 줄
        ka, kb = [r["perfumeKey"] for r in A], [r["perfumeKey"] for r in B]
        same = [f"{i}-{j + 1}" for j in range(5) if ka[j] == kb[j]]
        swapped = [f"{i}-{j + 1}/{i}-{k + 1}" for j in range(5) for k in range(j + 1, 5)
                   if ka[j] != kb[j] and {ka[j], kb[j]} == {ka[k], kb[k]}]
        if same:
            blind.append(f"- 양쪽이 같은 향수인 줄: {', '.join(same)} → `=`로 답하면 됩니다(집계에서 뺌)")
        if swapped:
            blind.append(f"- 같은 두 향수가 자리만 바뀐 줄: {', '.join(swapped)} → 한 번만 셉니다")
        if same or swapped:
            blind.append("")
    (DOCS / "engine_blind.md").write_text("\n".join(blind), encoding="utf-8")

    # ---------- 기준표 ----------
    lines = ["# 추천 엔진 합격 기준표", "", header,
             "> 기준은 docs/design.md 14절. 블라인드 비교는 `engine_blind.md`.", ""]
    if answers is None:
        lines += ["> ⏳ **블라인드 비교 답을 기다리는 중.** 지금 엔진의 추천 목록이 보이는 표(2절 표, 4절)는 답을 받은 뒤에 나옵니다.", ""]
    results = {}

    # 1. 재현: 평가 많은 향수 20개(브랜드 겹치지 않게)의 노트를 그대로 넣으면 그 향수가 Top 3
    picks = reproduction_picks(catalog, eng, 20)
    hit, rows, hits, limits = 0, [], {}, []
    sig = lambda p: tuple(tuple(sorted(p[layer])) for layer in ("top", "middle", "base"))  # noqa: E731
    for p, notes in picks:
        recs = eng.recommend([BlendNote(k, layer, 1.0) for k, layer in notes], k=10)
        groups = [by_key[r["perfumeKey"]]["group"] for r in recs]
        rank = groups.index(p["group"]) + 1 if p["group"] in groups else None  # 같은 향수의 다른 농도도 맞힌 것으로
        ok = bool(rank and rank <= 3)
        hit += ok
        hits[sig(p)] = hits.get(sig(p), False) or ok
        dropped = [k for layer in ("top", "middle", "base") for k in p[layer][3:]]
        if not ok and dropped:
            limits.append(f"- {cell(label(p['key']))}: 층마다 3개 제한으로 {', '.join(eng.notes[k]['nameKo'] for k in dropped)} 빠짐")
        rows.append(f"| {cell(label(p['key']))} | {rank or '10위 밖'} |")
    results["reproduce"] = hit
    unique_hit = sum(hits.values())
    clones = [p for p, _ in picks if sum(1 for q, _ in picks if sig(q) == sig(p)) > 1]
    lines += ["## 1. 재현 (기준: 20개 중 15개 이상이 Top 3, 2026-10-30 사장 결정으로 16→15)", "",
              f"**결과: {hit} / 20 {'✅' if hit >= 15 else '❌'}** · 고유 노트 기준(클론은 1개로): {unique_hit} / {len(hits)}", "",
              "- 넣는 노트는 화면 규칙대로 층마다 앞 3개까지. 고유 노트 기준은 노트 데이터가 완전히 같은 향수(클론)를 하나로 세고, 그중 하나라도 맞히면 맞힌 것으로 본다",
              f"- 이 20개 안의 클론: {', '.join(cell(label(p['key'])) for p in clones) or '없음'}", ""]
    if limits:
        lines += ["**알려진 한계: 화면에서 표현 불가** — 층마다 3개까지만 고를 수 있어서 이 향수들의 노트를 다 넣을 수 없다. "
                  "빠진 노트(주로 시트러스)가 향의 성격을 정해서, 남은 노트로 만든 조합이 다른 계열로 기운다.", "", *limits, ""]
    lines += ["<details><summary>향수별 순위</summary>", "", "| 향수 | 순위 |", "|---|---|", *rows, "", "</details>", ""]

    # 2. 맹점
    b0 = blend(BLIND_SPOT)
    recs = eng.recommend(b0, k=5)
    ok = sum(1 for r in recs if {"citrus", "floral"} <= {acc_family[a] for a in by_key[r["perfumeKey"]]["accords"]})
    citrus_first = sum(1 for r in recs if by_key[r["perfumeKey"]]["accords"][0] == "citrus")
    ratios = eng.display_ratios(b0)
    results["blindspot"] = ok
    top3_ok = sum(1 for r in recs if {"citrus", "floral"} <= {acc_family[a] for a in by_key[r["perfumeKey"]]["accords"][:3]})
    lines += ["## 2. 맹점 조합 (기준: Top 5 중 4개 이상이 시트러스와 플로럴을 둘 다 가짐)", "",
              f"**결과: {ok} / 5 {'✅' if ok >= 4 else '❌'}**", "",
              "- 판정 방법: 향수의 어코드 목록 **전체(최대 5개)**를 계열로 바꿔, 시트러스 계열과 플로럴 계열이 둘 다 있으면 통과. "
              "design.md 14절은 \"둘 다 가짐\"이라고만 적었고, 0절 보정 설명은 \"추천 상위 5개 모두에 시트러스가 들어간다\"(순위 무관)라서 전체 목록 기준이 의도에 맞다",
              f"- 참고: 상위 3개 어코드만 보면 {top3_ok} / 5",
              f"- 조합 어코드: {', '.join(f'{acc_ko[a]} {p}%' for a, p in ratios[:4])}",
              f"- Top 5 중 1위 어코드가 시트러스인 향수: {citrus_first}개 (참고: 레몬을 '강'으로 골랐을 때 시트러스가 앞서는지)", ""]
    if answers is not None:
        def family_share(key: str, family: str) -> str:
            vec = eng.perfume_vector(by_key[key]["accords"])
            share = sum(v for a, v in zip(eng.accords, vec) if acc_family[a] == family)
            ranks = [str(i + 1) for i, a in enumerate(by_key[key]["accords"]) if acc_family[a] == family]
            return f"{share:.0f}% ({','.join(ranks)}번째)" if ranks else "없음"

        lines += ["| 순위 | 향수 | 상위 어코드 | 시트러스 계열 비중 | 플로럴 계열 비중 | 점수 |", "|---|---|---|---|---|---|",
                  *[f"| {i + 1} | {cell(label(r['perfumeKey']))} | {accords_ko(r['perfumeKey'])} "
                    f"| {family_share(r['perfumeKey'], 'citrus')} | {family_share(r['perfumeKey'], 'floral')} | {r['score']:.3f} |"
                    for i, r in enumerate(recs)], "",
                  "비중 = 향수 어코드 벡터(목록 순서대로 5·4·3·2·1, 합 100)에서 그 계열 어코드가 차지하는 몫.", ""]

    # 3. 블라인드 비교 결과
    lines += ["## 3. 블라인드 비교 (기준: 지금 엔진 쪽을 10개 중 6개 이상 고름)", ""]
    if answers is None:
        lines += ["`engine_blind.md`에서 답을 받은 뒤 채웁니다.", ""]
    else:
        source = answers["source"]
        if source != "사장":
            lines += [f"> ⚠️ **임시 답안 (source: {source})**: 사람이 아니라 Claude가 향을 맡지 않고 노트 피라미드 지식과 "
                      "어코드 라벨로 고른 답이다. 어코드 엔진 쪽에 유리하게 치우쳤을 수 있다. **사장 답안이 오면 그걸 기준으로 한다.**", ""]
        if answers["lines"]:
            pairs_file = answers["pairs"]
            if pairs_file and pairs_file.exists():
                pairs = [c["lines"] for c in json.loads(pairs_file.read_text(encoding="utf-8"))["combos"]]
            else:
                pairs = current_pairs
            wins, rows, t = score_lines(answers, pairs, engine_side)
            results["blind"] = wins
            decided = t["engine"] + t["other"]
            share = f"- 줄 단위: 엔진 쪽 {t['engine']} / {decided}줄 ({t['engine'] / decided:.0%})" if decided else "- 줄 단위: 판별 가능한 줄 없음"
            consistent = "모두 같은 향수를 골랐음" if not t["inconsistent"] else "다르게 고름 " + ", ".join(t["inconsistent"])
            doc_note = f"- 채점 기준: `{pairs_file.name if pairs_file else 'engine_blind.md (지금)'}`"
            if pairs != current_pairs:
                doc_note += " — ⚠️ 지금 문서와 줄 구성이 다름(답은 고정본 기준)"
            lines += [f"**결과: {wins:g} / 10 {'✅' if wins >= 6 else '❌'}** (조합마다 남은 줄에서 엔진 쪽을 더 많이 고르면 1, 같으면 0.5)", "",
                      share,
                      f"- 뺀 줄 — 양쪽이 같은 향수: {', '.join(t['same']) or '없음'} · 자리만 바뀐 같은 쌍(뒤 줄은 세지 않음): {', '.join(t['dup']) or '없음'}",
                      f"- 자리만 바뀐 쌍의 판단 일관성: {consistent}",
                      doc_note, "",
                      "| 조합 | 지금 엔진 쪽 | 엔진 : 다른 쪽 | 판정 |", "|---|---|---|---|", *rows, ""]
        else:
            chosen = answers["combos"]
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
        score_text = f"{answers['scores'][i - 1]}점" if answers and answers["scores"] else "☐"
        eye += [f"**{i}. {name}** — 조합 어코드: {', '.join(f'{acc_ko[a]} {p}%' for a, p in eng.display_ratios(b)[:4])} — 점수: {score_text}", "",
                "| 순위 | 향수 | 상위 어코드 | 점수 | 등급 |", "|---|---|---|---|---|",
                *[f"| {j + 1} | {cell(label(r['perfumeKey']))} | {accords_ko(r['perfumeKey'])} | {r['score']:.3f} | {r['grade']} |"
                  for j, r in enumerate(r10[:5])], ""]
    if answers is None:
        lines += ["블라인드 비교 답을 받은 뒤, 조합마다 지금 엔진의 Top 5를 보여 드리고 1~5점을 받습니다.", ""]
    elif not answers["scores"]:
        lines += ["**점수 대기**: 조합마다 아래 Top 5를 보고 1~5점을 `engine_blind_answers.txt`의 `점수:` 줄에 적는다.", "", *eye]
    else:
        avg = statistics.mean(answers["scores"])
        results["eye"] = avg
        if answers["source"] != "사장":
            lines += [f"> ⚠️ **임시 점수 (source: {answers['source']})**: 사람이 아니라 Claude가 향을 맡지 않고 노트 지식과 "
                      "어코드 라벨로 매긴 점수다. **사장 점수가 오면 그걸로 덮어쓴다.**", ""]
        lines += [f"**결과: 평균 {avg:.1f}점 {'✅' if avg >= 3.5 else '❌'}**", "", *eye]

    # 5. 다양성
    results["brands"] = len(all_brands)
    lines += ["## 5. 다양성 (기준: 조합 10개의 Top 10 전체에서 서로 다른 브랜드 40개 이상)", "",
              f"**결과: {len(all_brands)}개 {'✅' if len(all_brands) >= 40 else '❌'}**", ""]

    # 6. 등급 기준값 참고
    lines += ["## 6. 점수 분포 (등급 기준값 정하기용)", "",
              f"- 1위 점수: 평균 {statistics.mean(top1):.3f} · 최소 {min(top1):.3f} · 최대 {max(top1):.3f}",
              f"- 10위 점수: 평균 {statistics.mean(top10):.3f} · 최소 {min(top10):.3f} · 최대 {max(top10):.3f}",
              f"- 지금 기준(engine_ref.py): {engine_ref.GRADE_VERY_SIMILAR} 이상 매우 비슷 · {engine_ref.GRADE_SIMILAR} 이상 비슷 · 그 아래 참고", ""]

    # 7. 계산 시간 (Python 참고값. 앱의 TypeScript 엔진은 Vitest 엔진 테스트에서 따로 잰다)
    t = time.perf_counter()
    for _, spec in BLENDS:
        eng.recommend(blend(spec), k=10)
    ms = (time.perf_counter() - t) * 1000 / len(BLENDS)
    lines += ["## 7. 계산 시간", "", f"- Python 참조 엔진 1회 평균 {ms:.0f}ms (참고값. 기준 300ms는 앱의 TypeScript 엔진으로 잰다)", ""]

    (DOCS / "engine_report.md").write_text("\n".join(lines), encoding="utf-8")
    old_key = DOCS / "engine_blind_key.md"
    if old_key.exists():
        old_key.unlink()  # 예전 방식의 정답 파일 (이제 남기지 않음)
    summary = (f"재현 {results['reproduce']}/20 · 맹점 {results['blindspot']}/5 (시트러스 1위 {citrus_first}) · "
               f"브랜드 {results['brands']}개 · Python {ms:.0f}ms/회")
    if answers:
        summary += f" · 블라인드 {results['blind']:g}/10 ({answers['source']})"
        if "eye" in results:
            summary += f" · 눈으로 {results['eye']:.1f}점"
    print(summary)


if __name__ == "__main__":
    main()
