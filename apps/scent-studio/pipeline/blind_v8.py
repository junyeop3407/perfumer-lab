"""8단계 블라인드 부품 (규칙: docs/engine_blind_v8_rules.md). 조합 생성과 판정 시트 문서. 추천 계산은 하지 않는다."""
from __future__ import annotations

import re
from math import comb

import numpy as np

SEED = 20261105
BUCKETS = ("프레시", "플로럴", "우디 오리엔탈", "구르망")
BUCKET_OF = {"citrus": "프레시", "fresh": "프레시", "green": "프레시", "floral": "플로럴", "fruity": "플로럴", "soft": "플로럴",
             "woody": "우디 오리엔탈", "amber": "우디 오리엔탈", "spicy": "우디 오리엔탈", "sweet": "구르망"}
LAYERS = ("top", "middle", "base")
LAYER_KO = {"top": "탑", "middle": "미들", "base": "베이스"}
MAX_TRIES = 1_000_000
CHOICES = ("A", "B", "비슷함")
MIN_VALID = 20


def generate(cat: dict, per_bucket: int, used: set[frozenset], seed: int = SEED) -> list[dict]:
    """분류를 돌아가며 하나씩 만든다. per_bucket을 늘려도 앞 조합은 그대로라 8개 추가에 쓸 수 있다."""
    notes = {n["key"]: n for n in cat["notes"]}
    family = {a["key"]: a["family"] for a in cat["accords"]}
    pool = {b: sorted((p for p in cat["perfumes"] if BUCKET_OF[family[p["accords"][0]]] == b), key=lambda p: p["key"]) for b in BUCKETS}
    rngs = {b: np.random.default_rng([seed, i]) for i, b in enumerate(BUCKETS)}
    seen, out = set(used), []
    for _ in range(per_bucket):
        for b in BUCKETS:
            rng = rngs[b]
            for _ in range(MAX_TRIES):
                p = pool[b][int(rng.integers(len(pool[b])))]
                layers = [[k for k in p[layer] if notes[k]["tier"] == "browse"] for layer in LAYERS]
                if not all(layers):
                    continue
                pick = [ks[int(rng.integers(len(ks)))] for ks in layers]
                key = frozenset(pick)
                if len(key) == 3 and key not in seen and sum(BUCKET_OF[notes[k]["family"]] == b for k in pick) >= 2:
                    seen.add(key)
                    out.append({"no": len(out) + 1, "bucket": b, "notes": [[k, layer, "medium"] for k, layer in zip(pick, LAYERS)]})
                    break
            else:
                raise SystemExit(f"{b}: 조합을 만들지 못했습니다")
    return out


def _cell(text: str) -> str:
    return str(text).replace("|", "\\|").replace("\n", " ")


def _row(p: dict, note_ko: dict[str, str], accord_ko: dict[str, str], rank: int) -> str:
    """표에 나가는 칸은 여기서만 정한다. 강도·추정치·점수·버전은 읽지 않는다."""
    main_notes = " / ".join(", ".join(note_ko[k] for k in p[layer][:2]) for layer in LAYERS)
    accords = " · ".join(accord_ko[a] for a in p["accords"][:3])
    return f"| {rank} | {_cell(p['name'])} | {_cell(p['brand'])} | {_cell(main_notes)} | {_cell(accords)} |"


def sheet_text(entries: list[dict], cat: dict) -> str:
    """entries: {no, notes, A: [perfume key], B: [perfume key]}. cat은 표시용으로 한 카탈로그만 쓴다."""
    by_key = {p["key"]: p for p in cat["perfumes"]}
    note_ko = {n["key"]: n["nameKo"] for n in cat["notes"]}
    accord_ko = {a["key"]: a["nameKo"] for a in cat["accords"]}
    head = ["| 순위 | 향수 | 브랜드 | 주요 노트 (탑 / 미들 / 베이스) | 주요 어코드 |", "|---:|---|---|---|---|"]
    lines = ["# 블라인드 판정 시트", "",
             "질문: **선택한 노트 조합과 더 비슷한 향수 목록은?** 조합마다 A / B / 비슷함 중 하나를 골라 주세요.",
             "노트표·웹 리뷰를 참고했다면 근거 칸에 적어 주세요. 채팅 AI 의견은 따로 적어 주세요(집계하지 않습니다).", ""]
    for e in entries:
        combo = " · ".join(f"{LAYER_KO[layer]} {note_ko[k]}" for k, layer, _ in e["notes"])
        lines += [f"## {e['no']}. {combo}", ""]
        for side in ("A", "B"):
            lines += [f"**{side}**", "", *head, *[_row(by_key[k], note_ko, accord_ko, i + 1) for i, k in enumerate(e[side])], ""]
        if e["A"] == e["B"]:
            lines += ["두 목록이 완전히 같아 자동으로 **비슷함**입니다. 판정하지 않아도 됩니다.", ""]
        else:
            lines += ["- 판정: ☐ A  ☐ B  ☐ 비슷함", "- 근거: ", "- 채팅 AI 의견(집계 제외): ", ""]
    return "\n".join(lines)


def fill_sheet(text: str, picks: dict[int, str], reason: str) -> str:
    """빈 판정 칸을 채운다. 판정 칸이 있는 번호와 picks 번호가 정확히 같아야 한다. 표는 건드리지 않는다."""
    blank = "- 판정: ☐ A  ☐ B  ☐ 비슷함\n- 근거: \n"
    parts = re.split(r"(?m)^(## \d+\. .*)$", text)
    seen = set()
    for i in range(1, len(parts), 2):
        no = int(re.match(r"## (\d+)\.", parts[i]).group(1))
        if blank in parts[i + 1]:
            seen.add(no)
            if no in picks:
                marks = {c: "☑" if picks[no] == c else "☐" for c in CHOICES}
                parts[i + 1] = parts[i + 1].replace(blank, f"- 판정: {marks['A']} A  {marks['B']} B  {marks['비슷함']} 비슷함\n- 근거: {reason}\n")
    if seen != set(picks):
        raise SystemExit(f"판정 번호가 시트와 다릅니다: 빠짐 {sorted(seen - set(picks))}, 없는 번호 {sorted(set(picks) - seen)}")
    return "".join(parts)


def read_judgements(text: str) -> dict[int, str]:
    """시트에서 판정을 읽는다. 표시가 하나가 아닌 칸이 있으면 멈춘다. 자동 비슷함 조합은 칸이 없어 결과에 없다."""
    parts = re.split(r"(?m)^## (\d+)\. .*$", text)
    out = {}
    for i in range(1, len(parts), 2):
        line = re.search(r"(?m)^- 판정: (.*)$", parts[i + 1])
        if line:
            picked = re.findall(r"☑ (A|B|비슷함)", line.group(1))
            if len(picked) != 1:
                raise SystemExit(f"{parts[i]}번 판정 칸의 표시가 {len(picked)}개입니다")
            out[int(parts[i])] = picked[0]
    return out


def sign_p(wins_a: int, wins_b: int) -> float:
    """양측 부호 검정의 정확한 p값."""
    n = wins_a + wins_b
    if n == 0:
        return 1.0
    tail = sum(comb(n, i) for i in range(max(wins_a, wins_b), n + 1))
    return min(1.0, 2 * tail / 2**n)


def winners(judgements: dict[int, str], key: dict[str, dict[str, str]]) -> dict[int, str]:
    """{번호: 'v7'|'v8'}. 비슷함은 뺀다."""
    return {no: key[str(no)][pick] for no, pick in judgements.items() if pick != "비슷함"}


def verdict(v8: int, v7: int) -> str:
    """규칙 문서의 결과 표. 유효 20 미만이면 결론을 내지 않는다."""
    if v8 + v7 < MIN_VALID:
        return "유효 부족(8개 추가)"
    if sign_p(v8, v7) >= 0.05:
        return "유의차 없음"
    return "v8 유의 승" if v8 > v7 else "v7 유의 승"
