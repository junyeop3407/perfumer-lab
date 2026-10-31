"""Parfumo CSV → catalog.json.gz + report.md (docs/design.md 7절).

실제 데이터:  py pipeline/build_catalog.py
가짜 데이터:  py pipeline/build_catalog.py --fixture
(앱 폴더에서 실행. Python 기본 기능만 씀)
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import gzip
import json
import math
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))  # 같은 폴더의 규칙 파일
import catalog_rules as R  # noqa: E402

APP_DIR = Path(__file__).resolve().parent.parent
ENGINE_VERSION = 3  # 2026-10-31 v3: 프로필 특징 강조(PPMI)·최소 등장 수 20, 강 2.0, 등급 0.87/0.79


@dataclass
class Perfume:
    key: str
    brand: str
    name: str
    year: int | None
    concentration: str | None
    rating: float | None
    rating_count: int
    accords: list[str]
    layers: dict[str, list[str]]  # top/middle/base → note keys (순서 유지, 중복 제거)

    @property
    def all_notes(self) -> set[str]:
        return {n for ns in self.layers.values() for n in ns}


@dataclass
class BuildResult:
    catalog: dict
    report: dict = field(default_factory=dict)


def _int(v):
    return None if R.is_na(v) else int(float(v))


def _float(v):
    return None if R.is_na(v) else float(v)


def read_csv(path: Path) -> list[dict]:
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def read_aliases(path: Path | None) -> dict[str, str]:
    if path is None or not path.exists():
        return {}
    return {R.note_key(r["alias"]): R.note_key(r["canonical"]) for r in read_csv(path)}


def read_junk(path: Path | None) -> set[str]:
    if path is None or not path.exists():
        return set()
    return {R.note_key(r["note_key"]) for r in read_csv(path)}


def read_overrides(path: Path | None) -> dict[str, str]:
    if path is None or not path.exists():
        return {}
    return {R.note_key(r["note_key"]): r["family_code"].strip() for r in read_csv(path)}


def read_brand_fixes(path: Path | None) -> dict[str, str]:
    """원본의 브랜드 표기 오류 → 바른 이름 (사람이 고친 표, 2026-10-31)"""
    if path is None or not path.exists():
        return {}
    return {r["raw"].strip(): r["fixed"].strip() for r in read_csv(path)}


def read_korean(path: Path | None) -> dict[str, tuple[str, str]]:
    if path is None or not path.exists():
        return {}
    return {
        R.note_key(r["note_key"]): (r["name_ko"].strip(), (r.get("description_ko") or "").strip())
        for r in read_csv(path)
    }


def build(rows: list[dict], params: R.Params, aliases: dict[str, str],
          overrides: dict[str, str], korean: dict[str, tuple[str, str]],
          version: str, source_note: str, junk: set[str] = frozenset(),
          brand_fixes: dict[str, str] | None = None) -> BuildResult:
    report: dict = {"rows": len(rows)}
    display_name: dict[str, Counter] = defaultdict(Counter)  # note key → 원래 표기들

    def canon(raw: str) -> str:
        k = R.note_key(raw)
        k = aliases.get(k, k)
        display_name[k][raw.strip()] += 1
        return k

    # 2·3: 정리 + 후보 표시
    candidates: list[Perfume] = []
    junk_accords = 0
    junk_notes = 0
    for r in rows:
        accords = []
        for a in R.split_list(r["Main_Accords"]):
            ak = R.accord_key(a)
            if ak in R.JUNK_ACCORDS or ak not in R.ACCORDS:
                junk_accords += 1
                continue
            if ak not in accords:
                accords.append(ak)
        layers = {}
        for layer, col in (("top", "Top_Notes"), ("middle", "Middle_Notes"), ("base", "Base_Notes")):
            seen: list[str] = []
            for n in R.split_list(r[col]):
                if R.note_key(n) in junk:  # 원본에 섞인 실재하지 않는 노트 이름 (manual/junk_notes.csv)
                    junk_notes += 1
                    continue
                k = canon(n)
                if k not in seen:
                    seen.append(k)
            layers[layer] = seen
        votes = _int(r["Rating_Count"]) or 0
        if not all(layers.values()) or sum(len(v) for v in layers.values()) < 3:
            continue
        if not accords or votes < params.min_votes:
            continue
        candidates.append(Perfume(
            key=R.perfume_key(r["URL"]), brand=(brand_fixes or {}).get(r["Brand"].strip(), r["Brand"].strip()),
            name=r["Name"].strip(),
            year=_int(r["Release_Year"]),
            concentration=None if R.is_na(r["Concentration"]) else r["Concentration"].strip(),
            rating=_float(r["Rating_Value"]), rating_count=votes,
            accords=accords[:5], layers=layers,
        ))
    report["junk_accord_values"] = junk_accords
    report["junk_note_values"] = junk_notes
    report["candidates_before_dedupe"] = len(candidates)

    # 4: 중복 합치기 (평가 수 많은 것 하나만)
    best: dict[tuple[str, str], Perfume] = {}
    for p in candidates:
        k = R.dedupe_name(p.brand, p.name)
        if k not in best or p.rating_count > best[k].rating_count:
            best[k] = p
    perfumes = sorted(best.values(), key=lambda p: p.key)
    report["duplicates_merged"] = len(candidates) - len(perfumes)
    keys = Counter(p.key for p in perfumes)
    dup_keys = [k for k, c in keys.items() if c > 1]
    if dup_keys:
        raise SystemExit(f"향수 key가 겹칩니다: {dup_keys[:10]}")
    report["candidates"] = len(perfumes)
    groups = Counter(R.group_key(p.brand, p.name, p.concentration) for p in perfumes)
    report["groups_with_versions"] = sum(1 for c in groups.values() if c > 1)
    n = len(perfumes)

    # 5: 어코드 벡터와 전체 평균
    vec = {p.key: R.rank_weights(p.accords) for p in perfumes}
    g = {a: sum(vec[p.key].get(a, 0.0) for p in perfumes) / n for a in R.ACCORD_KEYS}

    # 6: 노트 프로필 (8-2절)
    by_note: dict[str, list[Perfume]] = defaultdict(list)
    for p in perfumes:
        for k in p.all_notes:
            by_note[k].append(p)
    df = {k: len(ps) for k, ps in by_note.items()}
    excess: dict[str, dict[str, float]] = {}
    for k, ps in by_note.items():
        m = {a: sum(vec[p.key].get(a, 0.0) for p in ps) / len(ps) for a in R.ACCORD_KEYS}
        excess[k] = {a: max(0.0, m[a] - g[a]) for a in R.ACCORD_KEYS}

    # 7: 노트 계열 = 평균 초과분 1위 어코드의 계열 (사람이 고친 표 우선)
    family_of: dict[str, str] = {}
    for k, e in excess.items():
        if k in overrides:
            family_of[k] = overrides[k]
            continue
        top = max(R.ACCORD_KEYS, key=lambda a: (e[a], -R.ACCORD_KEYS.index(a)))
        if e[top] == 0.0:  # 모든 어코드가 평균 이하: 평균 그 자체의 1위로
            ps = by_note[k]
            m = {a: sum(vec[p.key].get(a, 0.0) for p in ps) / len(ps) for a in R.ACCORD_KEYS}
            top = max(R.ACCORD_KEYS, key=lambda a: (m[a], -R.ACCORD_KEYS.index(a)))
        family_of[k] = R.ACCORDS[top][1]

    # 프로필의 재료(특징): 계열은 위처럼 평균 초과분으로 정하고, 프로필은 params.profile_method로 만든다.
    # ppmi(특징 강조) = m·max(0, ln(m/g)): 평균보다 "몇 배" 많은지를 보므로 레몬처럼 여러 어코드에 퍼진 노트도
    # 제 특징(시트러스)이 살아난다 (2026-10-31 사장 결정, docs/NOTES.md)
    feature: dict[str, dict[str, float]] = {}
    for k, ps in by_note.items():
        if params.profile_method == "diff":
            feature[k] = excess[k]
            continue
        m = {a: sum(vec[p.key].get(a, 0.0) for p in ps) / len(ps) for a in R.ACCORD_KEYS}
        feature[k] = {a: m[a] * max(0.0, math.log(m[a] / g[a])) if m[a] > 0 and g[a] > 0 else 0.0
                      for a in R.ACCORD_KEYS}

    # 계열 평균(끌어당기기 목표) f_c: 같은 계열 노트들의 특징을 df로 가중 평균.
    # 적게 나오는 노트(df < min_profile_df)는 특징이 부풀려지기 쉬워 평균에 넣지 않는다 (그런 노트뿐이면 모두 넣음)
    def family_prior(reliable_only: bool) -> dict[str, dict[str, float]]:
        fam_sum: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
        fam_w: Counter = Counter()
        for k, e in feature.items():
            if reliable_only and df[k] < params.min_profile_df:
                continue
            c = family_of[k]
            fam_w[c] += df[k]
            for a in R.ACCORD_KEYS:
                fam_sum[c][a] += df[k] * e[a]
        return {c: {a: fam_sum[c][a] / fam_w[c] for a in R.ACCORD_KEYS} for c in fam_w}

    fam_prior = {**family_prior(False), **family_prior(True)}

    def normalized(v: dict[str, float]) -> dict[str, float] | None:
        t = sum(v.values())
        return None if t == 0 else {a: v[a] / t for a in R.ACCORD_KEYS}

    profile: dict[str, list[float]] = {}
    for k, e in feature.items():
        f = fam_prior[family_of[k]]
        if df[k] < params.min_profile_df:
            p = normalized(f)  # 적게 나오는 노트: 계열 평균 프로필 그대로
        else:
            shrunk = {a: (df[k] * e[a] + params.shrink_k * f[a]) / (df[k] + params.shrink_k) for a in R.ACCORD_KEYS}
            p = normalized(shrunk) or normalized(f)
        profile[k] = [round(p[a], 6) for a in R.ACCORD_KEYS] if p else [0.0] * len(R.ACCORD_KEYS)

    # 8: 공개 범위
    def tier(k: str) -> str:
        if df[k] >= params.browse_min_df:
            return "browse"
        if df[k] >= params.search_min_df:
            return "search"
        return "hidden"

    tiers = {k: tier(k) for k in df}
    occurrences = sum(len(p.all_notes) for p in perfumes)
    hidden_occ = sum(1 for p in perfumes for k in p.all_notes if tiers[k] == "hidden")
    report["notes_total"] = len(df)
    report["notes_browse"] = sum(1 for t in tiers.values() if t == "browse")
    report["notes_search"] = sum(1 for t in tiers.values() if t == "search")
    report["notes_hidden"] = sum(1 for t in tiers.values() if t == "hidden")
    report["unmapped_ratio"] = hidden_occ / occurrences if occurrences else 0.0

    # 9: 궁합 노트 (정규화 PMI, 목록에 보이는 노트를 짝으로)
    co: Counter = Counter()
    for p in perfumes:
        ns = sorted(k for k in p.all_notes if tiers[k] != "hidden")
        for i in range(len(ns)):
            for j in range(i + 1, len(ns)):
                co[(ns[i], ns[j])] += 1
    pmi: dict[str, list[tuple[float, str]]] = defaultdict(list)
    for (a, b), c in co.items():
        if c < params.pairing_min_co:
            continue
        # NPMI: 흔한 노트(머스크 등)나 아주 드문 노트로 쏠리지 않게 -1~1로 정규화
        score = 1.0 if c == n else math.log(c * n / (df[a] * df[b])) / -math.log(c / n)
        if tiers[b] == "browse":
            pmi[a].append((score, b))
        if tiers[a] == "browse":
            pmi[b].append((score, a))
    pairings = {k: [b for _, b in sorted(v, key=lambda t: (-t[0], t[1]))[:3]] for k, v in pmi.items()}

    # 10: 한글명·설명
    fam_name = dict(R.FAMILIES)
    no_korean = [k for k, t in tiers.items() if t != "hidden" and k not in korean]
    report["selectable_without_korean"] = len(no_korean)

    notes_out = []
    for k in sorted(df, key=lambda k: (-df[k], k)):
        name_en = display_name[k].most_common(1)[0][0]
        ko, desc = korean.get(k, (None, ""))
        notes_out.append({
            "key": k,
            "nameEn": name_en,
            "nameKo": ko or name_en,
            "family": family_of[k],
            "df": df[k],
            "tier": tiers[k],
            "profile": profile[k],
            "pairings": pairings.get(k, []),
            "description": desc or f"{fam_name[family_of[k]]} 계열의 노트",
        })

    perfumes_out = [{
        "key": p.key, "brand": p.brand, "name": p.name, "year": p.year,
        "concentration": p.concentration, "rating": p.rating, "ratingCount": p.rating_count,
        "accords": p.accords, "top": p.layers["top"], "middle": p.layers["middle"], "base": p.layers["base"],
        "group": R.group_key(p.brand, p.name, p.concentration),
    } for p in perfumes]

    catalog = {
        "version": version,
        "engineVersion": ENGINE_VERSION,
        "source": {"name": "Parfumo", "note": source_note},
        "families": [{"code": c, "nameKo": ko, "wheelOrder": i + 1} for i, (c, ko) in enumerate(R.FAMILIES)],
        "accords": [{"key": a, "nameKo": ko, "family": fam} for a, (ko, fam) in R.ACCORDS.items()],
        "globalMean": [round(g[a], 6) for a in R.ACCORD_KEYS],
        "notes": notes_out,
        "perfumes": perfumes_out,
    }
    report["families_of_selectable_notes"] = dict(Counter(
        family_of[k] for k, t in tiers.items() if t != "hidden").most_common())
    report["no_korean_examples"] = sorted(no_korean, key=lambda k: -df[k])[:30]
    return BuildResult(catalog, report)


def write_catalog(catalog: dict, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps(catalog, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    # mtime=0: 같은 입력이면 같은 파일 (커밋된 테스트 카탈로그가 괜히 바뀌지 않게)
    with gzip.GzipFile(out, "wb", mtime=0) as f:
        f.write(data)


def write_report(result: BuildResult, out: Path, title: str) -> None:
    r = result.report
    lines = [
        f"# {title}",
        "",
        f"- 만든 날: {dt.date.today().isoformat()} · 카탈로그 버전 `{result.catalog['version']}`",
        f"- 원본 행: {r['rows']:,} · 잘못된 어코드 값 버림: {r['junk_accord_values']:,} · 실재하지 않는 노트 이름 버림: {r['junk_note_values']:,}",
        f"- 후보(중복 합치기 전): {r['candidates_before_dedupe']:,} · 중복 합침: {r['duplicates_merged']:,} · **후보: {r['candidates']:,}**",
        f"- 농도만 다른 버전이 있는 향수 묶음: {r['groups_with_versions']:,} (추천에서는 묶음마다 하나만)",
        f"- 노트: 전체 {r['notes_total']:,} · 목록 {r['notes_browse']:,} · 검색 전용 {r['notes_search']:,} · 고를 수 없음 {r['notes_hidden']:,}",
        f"- **미매핑 비율(후보 향수의 노트 중 고를 수 없는 노트의 비율): {r['unmapped_ratio']:.1%}** (기준 5% 이하)",
        f"- 한글명이 없는 고를 수 있는 노트: {r['selectable_without_korean']:,}",
        "",
        "## 고를 수 있는 노트의 계열",
        "",
        "| 계열 | 노트 수 |",
        "|---|---|",
        *[f"| {c} | {k} |" for c, k in r["families_of_selectable_notes"].items()],
        "",
        "## 한글명이 없는 노트 (많이 쓰이는 순, 30개까지)",
        "",
        ", ".join(r["no_korean_examples"]) or "없음",
        "",
    ]
    out.write_text("\n".join(lines), encoding="utf-8")


def build_fixture() -> BuildResult:
    """가짜 테스트 데이터(tests/fixtures)로 만든다. 테스트도 이 함수를 쓴다."""
    fx = APP_DIR / "tests" / "fixtures"
    return build(
        read_csv(fx / "synthetic_parfumo.csv"), R.FIXTURE, {}, read_overrides(fx / "note_family_override.csv"),
        read_korean(fx / "notes_ko.csv"), version="test-1",
        source_note="테스트용 가짜 데이터 (tests/fixtures)",
    )


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--fixture", action="store_true", help="가짜 테스트 데이터로 테스트 카탈로그를 만든다")
    args = ap.parse_args()

    if args.fixture:
        result = build_fixture()
        out = APP_DIR / "tests" / "fixtures" / "test_catalog.json.gz"
        write_catalog(result.catalog, out)
        print(f"테스트 카탈로그: {out.relative_to(APP_DIR)} ({result.report['candidates']}개 향수)")
        return

    raw = APP_DIR / "pipeline" / "data" / "raw" / "parfumo_data_clean.csv"
    if not raw.exists():
        raise SystemExit("원본이 없습니다. 먼저  py pipeline/fetch.py")
    manual = APP_DIR / "pipeline" / "manual"
    version = dt.date.today().strftime("%Y.%m.%d")
    result = build(
        read_csv(raw), R.REAL, read_aliases(manual / "aliases.csv"),
        read_overrides(manual / "note_family_override.csv"), read_korean(manual / "notes_ko.csv"),
        version=version, source_note="Parfumo (TidyTuesday 2024-12-10 정리본). 커뮤니티 데이터 기반 추정치",
        junk=read_junk(manual / "junk_notes.csv"), brand_fixes=read_brand_fixes(manual / "brand_fixes.csv"),
    )
    out = APP_DIR / "pipeline" / "data" / "out" / "catalog.json.gz"
    write_catalog(result.catalog, out)
    # 앱에 넣는 카탈로그 (커밋하지 않음, .gitignore)
    assets = APP_DIR / "app" / "assets" / "catalog" / "catalog.json.gz"
    write_catalog(result.catalog, assets)
    write_report(result, APP_DIR / "pipeline" / "report.md", "카탈로그 보고서")
    print(f"카탈로그: {out.relative_to(APP_DIR)} · 후보 {result.report['candidates']:,} · "
          f"미매핑 {result.report['unmapped_ratio']:.1%} · 크기 {out.stat().st_size / 1024:.0f}KB")


if __name__ == "__main__":
    main()
