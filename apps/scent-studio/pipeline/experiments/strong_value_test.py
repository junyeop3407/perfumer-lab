"""'강' 값 시험 (2026-10-31 사장 요청): 어코드 0.35 : 노트 0.55에서 강 2.0 / 2.5 / 3.0 비교

앱 폴더에서:  py pipeline/experiments/strong_value_test.py   (실제 카탈로그 pipeline/data/out 필요)
만드는 파일: docs/engine_strong.md
성격이 다른 '강' 조합 6개마다: '강' 노트의 대표 어코드가 Top 5에서 1위인 향수 수,
'중' 노트들의 대표 어코드가 Top 5에 얼마나 남아 있는지(한 노트가 조합을 덮어버리는지).
노트의 대표 어코드 = 노트 프로필에서 가장 큰 어코드. 강 1.0(모두 중)은 비교 기준선.
"""
from __future__ import annotations

import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import engine_ref as E  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
WEIGHTS = (0.35, 0.55)
STRONG_VALUES = (1.0, 2.0, 2.5, 3.0)

# (이름, 성격, '강' 노트, '중' 노트들, '약' 노트들)
COMBOS = [
    ("바닐라 강 · 베르가못 · 자스민", "센 노트", ("vanilla", "base"), [("bergamot", "top"), ("jasmine", "middle")], []),
    ("패출리 강 · 만다린 · 장미", "센 노트", ("patchouli", "base"), [("mandarin orange", "top"), ("rose", "middle")], []),
    ("우드(오드) 강 · 베르가못 · 장미", "센 노트", ("oud", "base"), [("bergamot", "top"), ("rose", "middle")], []),
    ("레몬 강 · 오스만투스 · 샌달우드 약 (맹점, 기존)", "약한 노트", ("lemon", "top"), [("osmanthus", "middle")], [("sandalwood", "base")]),
    ("녹차 강 · 베르가못 · 샌달우드", "약한 노트", ("green tea", "middle"), [("bergamot", "top"), ("sandalwood", "base")], []),
    ("머스크 강 · 만다린 · 아이리스", "약한 노트", ("musk", "base"), [("mandarin orange", "top"), ("iris", "middle")], []),
]


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = json.load(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rt", encoding="utf-8"))
    out_file, profile_note = "engine_strong.md", "엔진 v3 프로필"
    if "--candidate" in sys.argv:  # 후보 프로필("함께 든 노트 몫 빼기", note_profile_check.py)로 같은 시험
        from note_profile_check import deconvolved
        cat = deconvolved(cat)
        out_file, profile_note = "engine_strong_candidate.md", "후보 프로필(함께 든 노트 몫 빼기, `note_profile_check.py`)"
    eng = E.Engine(cat)
    E.W_ACCORD, E.W_NOTE = WEIGHTS
    by = {p["key"]: p for p in cat["perfumes"]}
    ko = {a["key"]: a["nameKo"] for a in cat["accords"]}
    note_ko = {n["key"]: n["nameKo"] for n in cat["notes"]}

    def label(key: str) -> str:
        return f"{by[key]['brand']} — {by[key]['name']}".replace("|", "\\|")

    def main_accord(note: str) -> str:
        prof = eng.notes[note]["profile"]
        return eng.accords[max(range(len(prof)), key=lambda i: prof[i])]

    out = ["# '강' 값 시험", "",
           f"> `py pipeline/experiments/strong_value_test.py` (2026-10-31). 어코드 {WEIGHTS[0]} : 노트 {WEIGHTS[1]}, {profile_note}. "
           "강 1.0 = 모두 '중'(기준선).",
           "> '강 어코드 1위' = Top 5 중 1위 어코드가 '강' 노트의 대표 어코드인 향수 수. "
           "'중 어코드 남음' = Top 5 중 '중' 노트의 대표 어코드를 (목록 어디에든) 가진 향수 수. 0/5에 가까우면 '강' 노트가 조합을 덮은 것.",
           "> 머스크는 이 데이터에서 대표 어코드가 플로럴로 나온다(머스크가 든 향수에 플로럴이 많아서).", ""]
    summary = ["## 요약", "", "| 조합 | 성격 | 강 노트 → 대표 어코드 | " + " | ".join(f"강 {s}" for s in STRONG_VALUES) + " |",
               "|---|---|---|" + "---|" * len(STRONG_VALUES)]
    details = ["## 조합별 Top 5", ""]
    for name, kind, (sk, sl), mids, weaks in COMBOS:
        sa = main_accord(sk)
        mid_acc = [(mk, main_accord(mk)) for mk, _ in mids]
        cells = []
        details += [f"### {name}", "",
                    f"- 강: {note_ko[sk]} → {ko[sa]} · 중: " + ", ".join(f"{note_ko[m]} → {ko[a]}" for m, a in mid_acc), ""]
        for strong in STRONG_VALUES:
            E.INTENSITIES = tuple(sorted({0.5, 1.0, strong}))
            blend = ([E.BlendNote(sk, sl, strong)] + [E.BlendNote(m, l, 1.0) for m, l in mids]
                     + [E.BlendNote(w, l, 0.5) for w, l in weaks])
            recs = eng.recommend(blend, k=5)
            accs = [by[r["perfumeKey"]]["accords"] for r in recs]
            first = sum(1 for a in accs if a[0] == sa)
            kept = [(m, a, sum(1 for x in accs if a in x)) for m, a in mid_acc]
            ratios = dict(eng.display_ratios(blend))
            kept_txt = " · ".join(f"{ko[a]} {n}/5" for _, a, n in kept)
            cells.append(f"강 1위 {first}/5 · 중 남음 {kept_txt}")
            details += [f"**강 {strong}** — 조합 비율: {ko[sa]} {ratios.get(sa, 0)}%, "
                        + ", ".join(f"{ko[a]} {ratios.get(a, 0)}%" for _, a in mid_acc)
                        + f" — 강 어코드 1위 {first}/5 · 중 어코드 남음 {kept_txt}", "",
                        "| 순위 | 향수 | 상위 어코드 |", "|---|---|---|",
                        *[f"| {i + 1} | {label(r['perfumeKey'])} | {' · '.join(ko[a] for a in by[r['perfumeKey']]['accords'][:3])} |"
                          for i, r in enumerate(recs)], ""]
        summary.append(f"| {name} | {kind} | {note_ko[sk]} → {ko[sa]} | " + " | ".join(cells) + " |")
        print(name, "|", " / ".join(cells))
    (APP / "docs" / out_file).write_text("\n".join(out + summary + [""] + details), encoding="utf-8")


if __name__ == "__main__":
    main()
