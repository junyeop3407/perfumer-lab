"""샘플 카탈로그(Plan B 공개 데모용 가상 데이터) 만들기 — plan_stage5.md 1-3.

    py pipeline/sample_catalog.py [--out app/assets/catalog/catalog.json.gz] [--seed 20261111]

- 실제 데이터 파일을 하나도 읽지 않는다. 읽는 파일은 우리가 쓴 노트 한글명 표(pipeline/manual/notes_ko.csv) 하나뿐이다.
- 노트 150개(계열 10 × 15)와 계열별 기본 어코드 비율(FAMILY_ACCORDS, 아래에 직접 정함)에 고정 난수로 작은 흔들림을 준다.
  실제 데이터에서 계산한 값은 쓰지 않는다.
- 가상 브랜드 20개 × 향수 15개 = 300개. 브랜드·향수 이름은 누가 봐도 가짜(Demo·Sample·Test·Mock …)다.
  어코드는 노트 프로필을 노트 몫으로 평균해 계산하고, 평점·평가 수·연도도 지어낸 값이다.
- 형식은 엔진 8 카탈로그와 같은 칸이다. source = {"name": "sample", "note": "가상 데이터 …"}.
- 같은 seed면 바이트까지 같은 파일(gzip 시각 0, 파일 이름 안 넣음). 생성 결과는 커밋하지 않는다.
"""
from __future__ import annotations

import argparse
import csv
import gzip
import json
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

PIPELINE = Path(__file__).resolve().parent
sys.path.insert(0, str(PIPELINE))

import catalog_rules as R  # noqa: E402  (계열·어코드 이름표만 쓴다. 데이터 파일은 읽지 않음)

NOTES_KO = PIPELINE / "manual" / "notes_ko.csv"
DEFAULT_OUT = PIPELINE.parent / "app" / "assets" / "catalog" / "catalog.json.gz"
DEFAULT_SEED = 20261111
VERSION = "sample-1"
SOURCE = {"name": "sample", "note": "가상 데이터 — 공개 데모용으로 지어낸 브랜드·향수·평점입니다. 실제 향수 정보가 아닙니다."}

# 계열마다 노트 15개(일반 단어). 앞 10개는 휠에서 고르는 노트(browse), 뒤 5개는 검색으로만(search)
FAMILY_NOTES: dict[str, list[str]] = {
    "citrus": ["bergamot", "lemon", "mandarin orange", "grapefruit", "orange", "lime", "bitter orange", "petitgrain",
               "yuzu", "citron", "tangerine", "blood orange", "pink grapefruit", "kumquat", "clementine"],
    "fruity": ["blackcurrant", "peach", "raspberry", "pear", "apple", "plum", "pineapple", "lychee", "apricot",
               "passion fruit", "melon", "fig", "blackberry", "mango", "strawberry"],
    "floral": ["rose", "jasmine", "iris", "orange blossom", "ylang-ylang", "lily of the valley", "violet", "tuberose",
               "peony", "magnolia", "freesia", "gardenia", "osmanthus", "orchid", "lilac"],
    "soft": ["musk", "white musk", "ambrette seed", "heliotrope", "cashmeran", "aldehydes", "powdery notes", "suede",
             "ambroxan", "iso-e-super", "cream", "milk", "civet", "castoreum", "hedione"],
    "sweet": ["vanilla", "tonka bean", "caramel", "honey", "praline", "coconut", "almond", "cocoa", "chocolate",
              "coffee", "brown sugar", "marshmallow", "cotton candy", "hazelnut", "sugar"],
    "spicy": ["cardamom", "pink pepper", "cinnamon", "nutmeg", "saffron", "ginger", "clove", "black pepper",
              "coriander", "cumin", "star anise", "pimento", "white pepper", "sichuan pepper", "bay leaf"],
    "amber": ["amber", "benzoin", "labdanum", "frankincense", "myrrh", "opoponax", "styrax", "elemi resin",
              "tolu balm", "peru balsam", "cistus", "ambergris", "white amber", "balsam fir", "resins"],
    "woody": ["sandalwood", "cedarwood", "vetiver", "patchouli", "oud", "gaiac wood", "cypress", "birch", "leather",
              "oakmoss", "moss", "tobacco", "teakwood", "pine", "driftwood"],
    "green": ["lavender", "rosemary", "basil", "mint", "sage", "clary sage", "galbanum", "green notes", "violet leaf",
              "fig leaf", "green tea", "tea", "artemisia", "thyme", "juniper berry"],
    "fresh": ["marine notes", "aquatic notes", "sea salt", "ozonic notes", "ozone", "seawater", "salt", "salty notes",
              "cucumber", "watermelon", "water lily", "lotus", "bamboo", "eucalyptus", "fresh notes"],
}
BROWSE_PER_FAMILY = 10

# 계열별 기본 어코드 비율 (직접 정한 값, 실제 데이터에서 계산하지 않음). 합은 아무 값이어도 되고 나중에 1로 맞춘다
FAMILY_ACCORDS: dict[str, dict[str, float]] = {
    "citrus": {"citrus": 6, "fresh": 2, "green": 1, "floral": 0.5, "fougère": 0.3, "woody": 0.3},
    "fruity": {"fruity": 6, "sweet": 2, "floral": 1, "citrus": 0.5, "fresh": 0.3},
    "floral": {"floral": 7, "powdery": 1, "sweet": 0.7, "fresh": 0.4, "green": 0.3, "creamy": 0.3},
    "soft": {"powdery": 3, "synthetic": 2, "creamy": 1.5, "animal": 1, "floral": 1, "sweet": 0.5, "woody": 0.5},
    "sweet": {"sweet": 4, "gourmand": 3, "creamy": 1, "oriental": 0.8, "powdery": 0.5},
    "spicy": {"spicy": 6, "oriental": 1.5, "woody": 1, "citrus": 0.4, "fresh": 0.3, "sweet": 0.3},
    "amber": {"resinous": 3.5, "oriental": 3, "sweet": 1, "smoky": 0.8, "spicy": 0.7, "woody": 0.7, "animal": 0.3},
    "woody": {"woody": 5.5, "earthy": 1.2, "smoky": 0.8, "leathery": 0.6, "chypre": 0.6, "green": 0.4, "spicy": 0.3},
    "green": {"green": 4, "fougère": 2.5, "fresh": 1.5, "citrus": 0.6, "woody": 0.6, "spicy": 0.3},
    "fresh": {"fresh": 4, "aquatic": 4, "citrus": 0.8, "green": 0.6, "synthetic": 0.4, "floral": 0.2},
}
# 계열 기본값과 성격이 꽤 다른 노트 몇 개만 더한다 (직접 정함)
NOTE_EXTRA: dict[str, dict[str, float]] = {
    "leather": {"leathery": 5, "smoky": 1},
    "suede": {"leathery": 2},
    "oakmoss": {"chypre": 4, "earthy": 1},
    "moss": {"earthy": 3, "green": 1},
    "patchouli": {"earthy": 2, "chypre": 1},
    "vetiver": {"earthy": 2, "green": 1},
    "tobacco": {"smoky": 2, "sweet": 1},
    "birch": {"smoky": 3, "leathery": 1},
    "civet": {"animal": 5},
    "castoreum": {"animal": 4, "leathery": 1},
    "lavender": {"fougère": 3, "floral": 1},
    "coffee": {"smoky": 1},
    "cocoa": {"gourmand": 1},
    "iris": {"powdery": 3},
    "heliotrope": {"sweet": 1, "floral": 1},
    "aldehydes": {"synthetic": 2},
    "ambroxan": {"woody": 1, "synthetic": 1},
    "iso-e-super": {"woody": 2},
    "white amber": {"synthetic": 1},
}

# 층마다 잘 쓰는 계열 (지어낸 규칙)
LAYER_FAMILIES = {
    "top": ["citrus", "fruity", "green", "spicy", "fresh"],
    "middle": ["floral", "spicy", "fruity", "green", "soft"],
    "base": ["woody", "amber", "sweet", "soft"],
}
LAYER_COUNTS = {"top": (2, 4), "middle": (2, 4), "base": (2, 3)}
LAYER_BOOST = {"top": 1.4, "middle": 1.0, "base": 0.8}  # 노트 몫의 대략적인 비중

# 가상 브랜드 20개: 모두 누가 봐도 가짜인 말(Demo·Sample·Test·Mock·Placeholder·Example·Dummy·Fictional·Prototype·Lorem)을 넣는다.
# 각 브랜드의 주 계열 2개
BRANDS: list[tuple[str, tuple[str, str]]] = [
    ("Demo Atelier", ("floral", "woody")),
    ("Sample Maison", ("citrus", "green")),
    ("Placeholder Parfums", ("amber", "spicy")),
    ("Test Scent Lab", ("fresh", "citrus")),
    ("Mock Fragrance Co.", ("sweet", "fruity")),
    ("Example House", ("woody", "spicy")),
    ("Dummy Perfumery", ("soft", "floral")),
    ("Fictional Essences", ("green", "woody")),
    ("Prototype Parfums", ("fruity", "floral")),
    ("Lorem Ipsum Scents", ("amber", "sweet")),
    ("Demo Botanica", ("green", "floral")),
    ("Sample Studio", ("soft", "woody")),
    ("Test Accord Works", ("spicy", "citrus")),
    ("Mock Orchard", ("fruity", "sweet")),
    ("Placeholder Nocturne", ("amber", "woody")),
    ("Example Bloom", ("floral", "fruity")),
    ("Dummy Timber", ("woody", "amber")),
    ("Fictional Atelier", ("citrus", "floral")),
    ("Prototype Aqua", ("fresh", "green")),
    ("Lorem Velvet", ("sweet", "soft")),
]
PERFUMES_PER_BRAND = 15
NAME_WORDS = ["Demo", "Sample", "Test", "Mock", "Placeholder"]
CONCENTRATIONS = ["Eau de Toilette", "Eau de Parfum", "Parfum", "Eau de Cologne", None]
VARIANT_EVERY = 12  # 이 간격마다 바로 앞 향수의 농도만 다른 버전(같은 묶음 group)


def _title(key: str) -> str:
    return " ".join(w[:1].upper() + w[1:] for w in key.split(" "))


def _read_korean() -> dict[str, tuple[str, str]]:
    with open(NOTES_KO, encoding="utf-8", newline="") as f:
        return {r["note_key"]: (r["name_ko"], r.get("description_ko") or "") for r in csv.DictReader(f)}


def _profile(rng: random.Random, family: str, key: str) -> list[float]:
    base = dict(FAMILY_ACCORDS[family])
    for a, w in NOTE_EXTRA.get(key, {}).items():
        base[a] = base.get(a, 0.0) + w
    vec = {a: w * rng.uniform(0.7, 1.3) for a, w in base.items()}
    for a in rng.sample(R.ACCORD_KEYS, 2):  # 작은 흔들림
        vec[a] = vec.get(a, 0.0) + rng.uniform(0.0, 0.15)
    total = sum(vec.values())
    out = [round(vec.get(a, 0.0) / total, 6) for a in R.ACCORD_KEYS]
    out[out.index(max(out))] += round(1.0 - sum(out), 6)  # 반올림 차이를 가장 큰 칸에
    return [round(x, 6) for x in out]


def _pick_notes(rng: random.Random, families: tuple[str, str], used: set[str], layer: str) -> list[str]:
    lo, hi = LAYER_COUNTS[layer]
    pool: list[str] = []
    weights: list[float] = []
    for fam in LAYER_FAMILIES[layer] + [f for f in families if f not in LAYER_FAMILIES[layer]]:
        boost = 4.0 if fam in families else 1.0
        for i, k in enumerate(FAMILY_NOTES[fam]):
            if k in used:
                continue
            pool.append(k)
            weights.append(boost * (1.5 if i < BROWSE_PER_FAMILY else 0.6))
    picked: list[str] = []
    for _ in range(rng.randint(lo, hi)):
        k = rng.choices(pool, weights)[0]
        i = pool.index(k)
        pool.pop(i)
        weights.pop(i)
        picked.append(k)
        used.add(k)
    return picked


def _perfume(rng: random.Random, no: int, brand: str, families: tuple[str, str], profiles: dict[str, list[float]],
             name: str, concentration: str | None, layers: dict[str, list[str]] | None) -> dict:
    if layers is None:
        used: set[str] = set()
        layers = {layer: _pick_notes(rng, families, used, layer) for layer in ("top", "middle", "base")}
    strength = {k: LAYER_BOOST[layer] * rng.uniform(0.5, 1.5) for layer in ("top", "middle", "base") for k in layers[layer]}
    total = sum(strength.values())
    weights = {k: round(v / total, 5) for k, v in sorted(strength.items())}
    vec = [sum(weights[k] * profiles[k][i] for k in weights) for i in range(len(R.ACCORD_KEYS))]
    kept = {a: v for a, v in zip(R.ACCORD_KEYS, vec) if v >= 0.01}
    s = sum(kept.values())
    shares = {a: round(100.0 * v / s, 3) for a, v in kept.items()}
    top_key = max(shares, key=shares.get)
    shares[top_key] = round(shares[top_key] + round(100.0 - sum(shares.values()), 3), 3)
    shares = dict(sorted(shares.items(), key=lambda kv: -kv[1]))
    accords = sorted(shares, key=lambda a: -shares[a])[:5]
    year = None if rng.random() < 0.3 else rng.randint(2005, 2025)
    return {
        "key": f"sample-{no:03d}",
        "brand": brand,
        "name": name,
        "year": year,
        "concentration": concentration,
        "rating": round(rng.uniform(6.0, 9.2), 1),
        "ratingCount": rng.randint(12, 2400),
        "accords": accords,
        "top": layers["top"],
        "middle": layers["middle"],
        "base": layers["base"],
        "group": R.group_key(brand, name, concentration),
        "accordShares": shares,
        "noteWeights": weights,
    }


def build(seed: int = DEFAULT_SEED) -> dict:
    rng = random.Random(seed)
    korean = _read_korean()
    family_of = {k: fam for fam, keys in FAMILY_NOTES.items() for k in keys}
    profiles = {k: _profile(rng, family_of[k], k) for fam in FAMILY_NOTES for k in FAMILY_NOTES[fam]}

    perfumes: list[dict] = []
    no = 0
    for b, (brand, families) in enumerate(BRANDS):
        prev: dict | None = None
        for i in range(PERFUMES_PER_BRAND):
            no += 1
            if prev is not None and no % VARIANT_EVERY == 0:
                conc = next(c for c in CONCENTRATIONS if c and c != prev["concentration"])
                layers = {layer: list(prev[layer]) for layer in ("top", "middle", "base")}
                p = _perfume(rng, no, brand, families, profiles, prev["name"], conc, layers)
            else:
                lead = rng.choice(FAMILY_NOTES[rng.choice(families)][:BROWSE_PER_FAMILY])
                name = f"{NAME_WORDS[(b + i) % len(NAME_WORDS)]} {_title(lead)} {i + 1:02d}"
                p = _perfume(rng, no, brand, families, profiles, name, rng.choice(CONCENTRATIONS), None)
            perfumes.append(p)
            prev = p

    df: Counter = Counter()
    co: dict[str, Counter] = defaultdict(Counter)
    for p in perfumes:
        keys = sorted(set(p["top"] + p["middle"] + p["base"]))
        df.update(keys)
        for a in keys:
            for c in keys:
                if a != c:
                    co[a][c] += 1

    notes = []
    for fam in FAMILY_NOTES:
        for i, k in enumerate(FAMILY_NOTES[fam]):
            ko, desc = korean[k]
            pairs = [c for c, n in sorted(co[k].items(), key=lambda kv: (-kv[1], kv[0])) if n >= 2][:3]
            notes.append({
                "key": k, "nameEn": _title(k), "nameKo": ko, "family": fam, "df": df[k],
                "tier": "browse" if i < BROWSE_PER_FAMILY else "search",
                "profile": profiles[k], "pairings": pairs, "description": desc,
            })

    mean = [sum(p["accordShares"].get(a, 0.0) for p in perfumes) / len(perfumes) for a in R.ACCORD_KEYS]
    total = sum(mean)
    return {
        "version": VERSION,
        "engineVersion": 8,
        "source": dict(SOURCE),
        "families": [{"code": c, "nameKo": ko, "wheelOrder": i + 1} for i, (c, ko) in enumerate(R.FAMILIES)],
        "accords": [{"key": a, "nameKo": ko, "family": fam} for a, (ko, fam) in R.ACCORDS.items()],
        "globalMean": [round(100.0 * m / total, 6) for m in mean],
        "notes": notes,
        "perfumes": perfumes,
    }


def to_bytes(catalog: dict) -> bytes:
    data = json.dumps(catalog, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return gzip.compress(data, compresslevel=9, mtime=0)  # 파일 이름·시각을 넣지 않아 늘 같은 바이트


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="공개 데모용 가상 카탈로그를 만든다 (실제 데이터를 읽지 않음)")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--seed", type=int, default=DEFAULT_SEED)
    args = ap.parse_args(argv)
    body = to_bytes(build(args.seed))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    tmp = args.out.with_name(args.out.name + ".tmp")
    tmp.write_bytes(body)
    tmp.replace(args.out)
    print(f"샘플 카탈로그: 향수 {len(BRANDS) * PERFUMES_PER_BRAND}개, 브랜드 {len(BRANDS)}개, {len(body):,}바이트")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
