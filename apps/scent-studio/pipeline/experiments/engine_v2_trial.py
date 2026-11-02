"""조향 추천 엔진 설계서 v2의 방안을 시험한다 (2026-11-02 사장 요청). 엔진 설정·카탈로그·블라인드 문서는 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/engine_v2_trial.py [--holdout]
필요한 것: pipeline/data/out/catalog.json.gz, pipeline/data/raw/decant/perfumes.db, perfumes_cold.db (커밋 안 함)
  받기: https://codeberg.org/decant/pages/raw/branch/pages/perfumes.db.gz , perfumes_cold.db.gz

- 기존 시험: 재현, 맹점, 다양성 (evaluate.py와 같은 입력과 판정)
- 사람 유사 시험(새로): 향수 p의 노트를 넣었을 때, Fragrantica 사용자가 "p와 비슷하다"고 투표한 향수 q가 몇 위에 오나.
  어느 시험판도 이 투표를 계산에 쓰지 않으므로 답을 미리 본 시험이 아니다.
- 개발 절반으로 시험판을 고르고, --holdout은 그 뒤 나머지 절반으로 마지막에 한 번 확인한다.
- 블라인드 문서가 무효가 되지 않게 조합별 추천 목록은 출력하지 않는다(모든 수치는 집계).

기록용: v6 카탈로그(engine_version 6)를 기준으로 쓴 스크립트다. 엔진 v7 뒤의 비교는 similar_votes.py를 쓴다.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import random
import re
import sqlite3
import statistics as st
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import engine_ref as E  # noqa: E402
import evaluate as V  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
APP = Path(__file__).resolve().parent.parent.parent
RAW = APP / "pipeline" / "data" / "raw" / "decant"

# 설계서 v2 초깃값
TV_A, TV_B = 0.7, 0.3
VOTE_FULL = 70  # 신뢰 하한 참고값 약 70표 (MDPI Cosmetics 7(1):11). α = 표 / (표 + 70)
V_FLOOR = 0.05  # 강도 0 = 아무도 안 뽑은 노트. 0으로 두면 그 노트가 사라져서 아주 작은 값을 준다 (시험에서 정한 값)
L_T = {"0분": {"top": .5, "middle": .3, "base": .2}, "1시간": {"top": .15, "middle": .5, "base": .35},
       "4시간": {"top": .05, "middle": .3, "base": .65}}
BAYES_WEIGHT, BAYES_C = 0.075, 70  # 5~10% 비중의 가운데
MIN_JOIN_JACCARD = 0.3  # 이름만 같은 다른 향수를 걸러 낸다
POS_MIN_UP, POS_MIN_RATIO = 20, 0.7  # 사람 유사 시험의 정답 기준
MAX_QUERIES = 3000

# 시험에서 정한 Fragrantica 어코드 92개 → Parfumo 어코드 21개 연결. 없는 것은 버린다(주로 희귀 어코드)
ACCORD_MAP = {
    "citrus": "citrus", "fresh spicy": "spicy", "green": "green", "sweet": "sweet", "aromatic": "green", "fruity": "fruity",
    "white floral": "floral", "honey": "sweet", "yellow floral": "floral", "woody": "woody", "powdery": "powdery",
    "floral": "floral", "tuberose": "floral", "vanilla": "sweet", "animalic": "animal", "fresh": "fresh", "musky": "powdery",
    "aldehydic": "synthetic", "amber": "oriental", "warm spicy": "spicy", "earthy": "earthy", "mossy": "chypre", "rose": "floral",
    "conifer": "woody", "balsamic": "resinous", "patchouli": "earthy", "iris": "powdery", "violet": "floral", "anis": "spicy",
    "soft spicy": "spicy", "leather": "leathery", "tropical": "fruity", "herbal": "green", "nutty": "gourmand", "coffee": "gourmand",
    "lavender": "fougère", "almond": "gourmand", "smoky": "smoky", "cacao": "gourmand", "soapy": "powdery", "tobacco": "smoky",
    "cinnamon": "spicy", "marine": "aquatic", "aquatic": "aquatic", "ozonic": "aquatic", "chocolate": "gourmand", "lactonic": "creamy",
    "coconut": "creamy", "cherry": "fruity", "caramel": "gourmand", "camphor": "green", "metallic": "synthetic", "mineral": "earthy",
    "salty": "aquatic", "terpenic": "green", "oud": "woody", "gourmand": "gourmand", "milky": "creamy", "foresty": "woody",
    "spice": "spicy", "pear": "fruity",
}
# Fragrantica 노트 이름 → 카탈로그 노트 key (많이 쓰이는 것만)
NOTE_ALIAS = {
    "cedar": "cedarwood", "lavender extract": "lavender", "agarwood oud": "oud", "incense": "frankincense", "olibanum": "frankincense",
    "black currant": "blackcurrant", "orris root": "iris", "orris": "iris", "pepper": "black pepper", "cloves": "clove",
    "citruses": "citrus fruits", "woody notes": "woods", "guaiac wood": "gaiac wood", "lily of the valley": "lily of the valley",
    "oakmoss": "oakmoss", "tree moss": "moss", "white musk": "white musk", "juniper berries": "juniper berry", "sea notes": "marine notes",
    "water notes": "aquatic notes", "passionfruit": "passion fruit", "litchi": "lychee", "jasmine sambac": "jasmine sambac",
}


def norm(s: str | None) -> str:
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


WORDS = re.compile(r"\b(eau de parfum intense|extrait de parfum|eau de parfum|eau de toilette|eau de cologne|after shave lotion|"
                   r"after shave|extrait|parfum|perfume|cologne|edp|edt|edc|pour homme|pour femme|for men|for women)\b")


def name_key(brand: str, name: str) -> tuple[str, str]:
    n, b = norm(name), norm(brand)
    if b:
        n = n.replace(b, " ")
    n = re.sub(r"\b(19|20)\d{2}\b", " ", n)
    return b, " ".join(WORDS.sub(" ", n).split())


class Data:
    def __init__(self) -> None:
        self.catalog = json.loads(gzip.open(APP / "pipeline" / "data" / "out" / "catalog.json.gz", "rb").read())
        if self.catalog["engineVersion"] != 6:
            raise SystemExit("이 스크립트는 v6 카탈로그 기준의 기록입니다. 지금 엔진 비교는 experiments/similar_votes.py")
        self.eng = E.Engine(self.catalog)
        self.perfumes = self.catalog["perfumes"]
        self.keys = [p["key"] for p in self.perfumes]
        self.idx = {k: i for i, k in enumerate(self.keys)}
        self.accords = self.eng.accords
        self.n = len(self.perfumes)
        self.A = np.array([self.eng.vec[k] for k in self.keys])
        self.layers = [{layer: set(p[layer]) for layer in E.LAYERS} for p in self.perfumes]
        self.all = [a["top"] | a["middle"] | a["base"] for a in self.layers]
        self.profile = {k: np.array(n["profile"]) for k, n in self.eng.notes.items()}
        self.fam_of = {k: n["family"] for k, n in self.eng.notes.items()}
        fams = sorted({*self.fam_of.values()})
        self.has_fam = {f: np.array([any(self.fam_of[k] == f for k in a) for a in self.all]) for f in fams}
        self.group = np.array([p["group"] for p in self.perfumes])
        self.brand = np.array([p["brand"] for p in self.perfumes])
        self.votes = np.array([p["ratingCount"] for p in self.perfumes])
        self.key_rank = np.argsort(np.argsort(self.keys))
        self._has: dict = {}
        self._same: dict = {}
        self._w: dict = {}
        self.join()

    def join(self) -> None:
        c = sqlite3.connect(f"file:{(RAW / 'perfumes.db').as_posix()}?mode=ro", uri=True)
        brands = dict(c.execute("select id,name from brands"))
        by_name = defaultdict(list)
        self.frag = {}
        for pid, name, bid, year, votes in c.execute("select id,name,brand_id,year,vote_count from perfumes"):
            by_name[name_key(brands.get(bid, ""), name)].append((pid, year, votes))
            self.frag[pid] = votes
        names = {i: norm(n) for i, n in c.execute("select id,name from notes")}
        pn = defaultdict(dict)
        for pid, nid, w in c.execute("select perfume_id,note_id,weight from perfume_notes"):
            k = NOTE_ALIAS.get(names[nid], names[nid])
            pn[pid][k] = max(w, pn[pid].get(k, 0))
        acc_names = {i: n.lower() for i, n in c.execute("select id,name from accords")}
        pa = defaultdict(dict)
        for pid, aid, s in c.execute("select perfume_id,accord_id,strength from perfume_accords"):
            pa[pid][acc_names[aid]] = s
        self.pid = {}
        self.jaccards = []
        tried = 0
        for p in self.perfumes:
            cand = by_name.get(name_key(p["brand"], p["name"]))
            if not cand:
                continue
            tried += 1
            cand = sorted(cand, key=lambda t: -t[2])
            if p["year"]:
                cand = [t for t in cand if t[1] and abs(t[1] - p["year"]) <= 1] or cand
            pid = cand[0][0]
            mine = {norm(n) for layer in E.LAYERS for n in p[layer]}
            other = set(pn[pid])
            j = len(mine & other) / len(mine | other) if mine | other else 0
            self.jaccards.append(j)
            if j >= MIN_JOIN_JACCARD:
                self.pid[p["key"]] = pid
        self.name_matched = tried
        self.pn, self.pa = pn, pa
        self.key_of = {}
        for k, pid in self.pid.items():
            if pid not in self.key_of or self.votes[self.idx[k]] > self.votes[self.idx[self.key_of[pid]]]:
                self.key_of[pid] = k
        # 노트 강도 투표로 만든 w_{p,n} (b = 1, l = 1: TGSC·시간축 없음)
        self.w_uniform: list[dict[str, float]] = []
        self.w_votes: list[dict[str, float]] = []
        self.has_votes = 0
        for p, notes in zip(self.perfumes, self.all):
            u = {k: 1 / len(notes) for k in notes}
            self.w_uniform.append(u)
            pid = self.pid.get(p["key"])
            raw = pn[pid] if pid else {}
            found = [raw[norm(k)] / 100 for k in notes if norm(k) in raw]
            if not found or max(found) == 0:
                self.w_votes.append(u)
                continue
            self.has_votes += 1
            alpha = self.frag[pid] / (self.frag[pid] + VOTE_FULL)
            fill = st.mean(found)
            vals = {k: max(raw[norm(k)] / 100 if norm(k) in raw else fill, V_FLOOR) ** alpha for k in notes}
            total = sum(vals.values())
            self.w_votes.append({k: v / total for k, v in vals.items()})
        # 어코드 강도 벡터 (연결된 향수만 바꾼다)
        self.A_strength = self.A.copy()
        self.strength_used = 0
        for i, p in enumerate(self.perfumes):
            pid = self.pid.get(p["key"])
            vec = np.zeros(len(self.accords))
            for a, s in (pa[pid] if pid else {}).items():
                if a in ACCORD_MAP:
                    vec[self.accords.index(ACCORD_MAP[a])] += s
            if vec.sum() > 0:
                self.A_strength[i] = 100 * vec / vec.sum()
                self.strength_used += 1

    def has(self, k: str) -> np.ndarray:
        if k not in self._has:
            self._has[k] = np.array([k in a for a in self.all])
        return self._has[k]

    def same(self, k: str, layer: str) -> np.ndarray:
        if (k, layer) not in self._same:
            self._same[(k, layer)] = np.array([k in a[layer] for a in self.layers])
        return self._same[(k, layer)]

    def weight(self, table: str, k: str) -> np.ndarray:
        if (table, k) not in self._w:
            src = self.w_votes if table == "votes" else self.w_uniform
            self._w[(table, k)] = np.array([d.get(k, 0.0) for d in src], dtype=np.float32)
        return self._w[(table, k)]

    def resolve_profiles(self, Y100: np.ndarray, weights: list[dict[str, float]]) -> dict[str, np.ndarray]:
        """build_catalog.deconvolve와 같은 방법으로 노트 프로필을 다시 푼다 (어코드 강도·노트 강도 반영). 끌어당기기·최소 등장 수도 같게."""
        note_keys = sorted(self.profile)
        ni = {k: i for i, k in enumerate(note_keys)}
        Y = Y100 / 100
        X = np.zeros((self.n, len(note_keys)))
        for r, d in enumerate(weights):
            for k, v in d.items():
                X[r, ni[k]] = v
        F = np.array([self.profile[k] for k in note_keys]) + 1e-3
        XtY, XtX = X.T @ Y, X.T @ X
        for _ in range(400):
            F *= XtY / (XtX @ F + 1e-12)
        df = {k: self.eng.notes[k]["df"] for k in note_keys}
        prior_sum, prior_w = defaultdict(lambda: np.zeros(len(self.accords))), defaultdict(float)
        for k, i in ni.items():
            if df[k] >= 20:
                prior_sum[self.fam_of[k]] += df[k] * F[i]
                prior_w[self.fam_of[k]] += df[k]
        prior = {f: prior_sum[f] / prior_w[f] for f in prior_w}
        out = {}
        for k, i in ni.items():
            f = prior.get(self.fam_of[k], F[i])
            v = f if df[k] < 20 else (df[k] * F[i] + 20 * f) / (df[k] + 20)
            out[k] = v / v.sum() if v.sum() else self.profile[k]
        return out


class Trial:
    """시험판 하나. note: v6 | tversky | hybrid | none, accord_on: 어코드 유사도를 쓰는지"""

    def __init__(self, d: Data, name: str, note="v6", table="uniform", A=None, profile=None, l=None, accord_on=True, bayes=False):
        self.d, self.name, self.note, self.table = d, name, note, table
        A = d.A if A is None else A
        self.Au = A / np.maximum(np.linalg.norm(A, axis=1, keepdims=True), 1e-12)
        self.profile, self.l, self.accord_on = profile or d.profile, l, accord_on
        self.bayes = None
        if bayes:
            r = np.array([p["rating"] if p["rating"] is not None else np.nan for p in d.perfumes])
            m = np.nanmean(r)
            self.bayes = np.nan_to_num((d.votes * np.nan_to_num(r, nan=m) + BAYES_C * m) / (d.votes + BAYES_C), nan=m) / 10

    def score(self, spec: list[tuple[str, str, float]]) -> np.ndarray:
        d = self.d
        b = np.zeros(len(d.accords))
        for k, layer, s in spec:
            b += s * (self.l[layer] if self.l else 1.0) * self.profile[k]
        cos = self.Au @ (b / np.linalg.norm(b))
        if self.note == "v6":
            nm, total = np.zeros(d.n), 0.0
            for k, layer, _ in spec:
                wn = np.log(1 + d.n / d.eng.notes[k]["df"])
                c = np.where(d.same(k, layer), 1.0, np.where(d.has(k), 0.5, np.where(d.has_fam[d.fam_of[k]], 0.3, 0.0)))
                nm += wn * c
                total += wn
            nm /= total
        elif self.note == "tversky":
            m, miss = np.zeros(d.n), np.zeros(d.n)
            for k, _, _ in spec:
                m += d.weight(self.table, k)
                miss += ~d.has(k)
            miss /= len(spec)
            denom = m + TV_A * miss + TV_B * np.maximum(1 - m, 0)
            nm = np.divide(m, denom, out=np.zeros(d.n), where=denom > 0)
        elif self.note == "hybrid":
            # 지금의 층·계열·희귀도 일치도에, 향수 안에서 고른 노트의 강도 몫을 곱한 뒤 섞는다
            base, total, m = np.zeros(d.n), 0.0, np.zeros(d.n)
            for k, layer, _ in spec:
                wn = np.log(1 + d.n / d.eng.notes[k]["df"])
                c = np.where(d.same(k, layer), 1.0, np.where(d.has(k), 0.5, np.where(d.has_fam[d.fam_of[k]], 0.3, 0.0)))
                base += wn * c
                total += wn
                m += d.weight(self.table, k)
            nm = base / total * (TV_B + (1 - TV_B) * np.minimum(m, 1) ** 0.5)
        else:
            nm = np.zeros(d.n)
        if not self.accord_on:
            s = nm
        elif self.note == "none":
            s = cos
        else:
            s = (E.W_ACCORD * cos + E.W_NOTE * nm) / (E.W_ACCORD + E.W_NOTE)
        if self.bayes is not None:
            s = (1 - BAYES_WEIGHT) * s + BAYES_WEIGHT * self.bayes
        return s

    def order(self, spec) -> np.ndarray:
        s = self.score(spec)
        return np.lexsort((self.d.key_rank, -self.d.votes, -np.floor(s * 1e9 + 0.5)))

    def top(self, spec, k=10) -> list[int]:
        out, groups, brands = [], set(), defaultdict(int)
        for i in self.order(spec):
            p = self.d.perfumes[i]
            if p["group"] in groups or brands[p["brand"]] >= E.BRAND_CAP:
                continue
            groups.add(p["group"])
            brands[p["brand"]] += 1
            out.append(int(i))
            if len(out) == k:
                break
        return out


def spec_of(items, s=1.0):
    return [(k, layer, s) for k, layer in items]


def existing_tests(t: Trial) -> dict:
    d = t.d
    res = {}
    for n, picks in ((20, PICKS20), (100, PICKS100)):
        for variant in ("normal", "two_per_layer", "drop_one"):
            hit = 0
            for p, notes in picks:
                inp = notes if variant == "normal" else V.hard_inputs(p, notes, variant)
                groups = [d.perfumes[i]["group"] for i in t.top(spec_of(inp), 3)]
                hit += p["group"] in groups
            res[(n, variant)] = hit
    fam = d.eng.accord_family
    spec = [(k, l, E.intensity(s)) for k, l, s in V.BLIND_SPOT]
    res["blindspot"] = sum({"citrus", "floral"} <= {fam[a] for a in d.perfumes[i]["accords"]} for i in t.top(spec, 5))
    brands = set()
    for _, combo in V.BLENDS:
        brands |= {d.perfumes[i]["brand"] for i in t.top([(k, l, E.intensity(s)) for k, l, s in combo], 10)}
    res["brands"] = len(brands)
    return res


def queries(d: Data, holdout: bool) -> list[tuple[int, list[tuple[str, str]], list[int]]]:
    cold = sqlite3.connect(f"file:{(RAW / 'perfumes_cold.db').as_posix()}?mode=ro", uri=True)
    out = []
    for pid, key in sorted(d.key_of.items()):
        if (int(hashlib.sha1(key.encode()).hexdigest(), 16) % 2 == 1) != holdout:
            continue
        doc = cold.execute("select doc from perfumes_cold where id=?", (pid,)).fetchone()
        i = d.idx[key]
        pos = []
        for q, up, down in (json.loads(doc[0]).get("reminds_me_of") or []) if doc else []:
            if up >= POS_MIN_UP and up / max(up + down, 1) >= POS_MIN_RATIO and q in d.key_of:
                j = d.idx[d.key_of[q]]
                if d.group[j] != d.group[i]:
                    pos.append(j)
        p = d.perfumes[i]
        seen: set[str] = set()
        notes = [(k, layer) for layer in E.LAYERS for k in p[layer][:3]
                 if d.eng.notes[k]["tier"] != "hidden" and not (k in seen or seen.add(k))]
        if pos and notes:
            out.append((i, notes, pos))
    random.Random(20261102).shuffle(out)
    return out[:MAX_QUERIES]


def similar_test(t: Trial, qs, cross_brand=False, one_per_layer=False) -> np.ndarray:
    """쿼리마다 정답 향수 중 가장 높은 순위. 자기 자신과 같은 향수 묶음은 뺀다. 브랜드 상한은 적용하지 않는다.
    cross_brand: 다른 브랜드 정답만 본다(해당 정답이 없는 쿼리는 뺀다). one_per_layer: 층마다 첫 노트 1개만 넣는다(실제 사용에 가까운 입력)."""
    d = t.d
    best = []
    for i, notes, pos in qs:
        if cross_brand:
            pos = [j for j in pos if d.brand[j] != d.brand[i]]
            if not pos:
                continue
        if one_per_layer:
            seen: set[str] = set()
            notes = [(k, layer) for k, layer in notes if not (layer in seen or seen.add(layer))]
        s = t.score(spec_of(notes))
        s = np.where(d.group == d.group[i], -1.0, s)
        best.append(min(int((s > s[j]).sum()) + 1 for j in pos))
    return np.array(best)


def ci(a: np.ndarray, base: np.ndarray, k: int) -> str:
    """시험판 - 기준의 Top k 적중률 차이와 95% 부트스트랩 구간"""
    diff = (a <= k).astype(float) - (base <= k).astype(float)
    rng = np.random.default_rng(20261102)
    boots = [diff[rng.integers(0, len(diff), len(diff))].mean() for _ in range(1000)]
    lo, hi = np.percentile(boots, [2.5, 97.5])
    return f"{100 * diff.mean():+.1f} ({100 * lo:+.1f}~{100 * hi:+.1f})"


def main() -> None:
    global PICKS20, PICKS100
    ap = argparse.ArgumentParser()
    ap.add_argument("--holdout", action="store_true", help="남겨 둔 절반으로 마지막 확인")
    ap.add_argument("--only", default="", help="쉼표로 시험판 이름 일부 고르기")
    args = ap.parse_args()
    d = Data()
    PICKS20, PICKS100 = V.reproduction_picks(d.catalog, d.eng, 20), V.reproduction_picks(d.catalog, d.eng, 100)

    # 지금 엔진을 똑같이 계산하는지 먼저 확인
    v6 = Trial(d, "v6 지금")
    for _, combo in V.BLENDS:
        spec = [(k, l, E.intensity(s)) for k, l, s in combo]
        want = [r["perfumeKey"] for r in d.eng.recommend([E.BlendNote(*x) for x in spec], k=10)]
        assert [d.keys[i] for i in v6.top(spec, 10)] == want, "시험 코드가 지금 엔진과 다르다"

    print(f"후보 {d.n:,} · 이름 연결 {d.name_matched:,} · 노트까지 맞는 연결 {len(d.pid):,} ({100 * len(d.pid) / d.n:.1f}%) · "
          f"노트 강도 사용 {d.has_votes:,} ({100 * d.has_votes / d.n:.1f}%) · 어코드 강도 사용 {d.strength_used:,}")
    print("노트 겹침(자카드) 분위:", [round(x, 2) for x in st.quantiles(d.jaccards, n=4)])
    top_counts = [d.frag[p] for p in d.pid.values()]
    print("연결 향수의 Fragrantica 표 수 분위:", [int(x) for x in st.quantiles(top_counts, n=4)], "· 70표 이상", f"{100 * np.mean(np.array(top_counts) >= 70):.1f}%")

    # 5·4·3·2·1 추정치 점검
    fives, shares, cors, cosines = [], [], [], []
    rank = np.array([5, 4, 3, 2, 1]) / 15 * 100
    for i, p in enumerate(d.perfumes):
        pid = d.pid.get(p["key"])
        if not pid or not d.pa[pid]:
            continue
        s = sorted(d.pa[pid].values(), reverse=True)[:5]
        if len(s) == 5 and sum(s):
            shares.append(np.array(s) / sum(s) * 100)
        if len(p["accords"]) == 5 and d.A_strength[i].sum() > 0 and not np.array_equal(d.A_strength[i], d.A[i]):
            a, b = d.A[i], d.A_strength[i]
            cosines.append(a @ b / (np.linalg.norm(a) * np.linalg.norm(b)))
            x = [5 - n for n, acc in enumerate(p["accords"])]
            y = [b[d.accords.index(acc)] for acc in p["accords"]]
            if np.std(y) > 0:
                cors.append(np.corrcoef(x, y)[0, 1])
    print("Fragrantica 상위 5개 어코드 실제 몫(%):", np.round(np.mean(shares, axis=0), 1).tolist(), "vs 5·4·3·2·1", np.round(rank, 1).tolist())
    print(f"같은 향수에서 Parfumo 순서 vs Fragrantica 강도: 상관 중앙값 {st.median(cors):.2f} · 양의 상관 {100 * np.mean(np.array(cors) > 0):.0f}% · 어코드 벡터 코사인 중앙값 {st.median(cosines):.2f} (n={len(cors):,})")

    # 시험판
    print("프로필 다시 푸는 중…", flush=True)
    uniform_check = d.resolve_profiles(d.A, d.w_uniform)
    gap = np.mean([uniform_check[k] @ d.profile[k] / (np.linalg.norm(uniform_check[k]) * np.linalg.norm(d.profile[k]))
                   for k in d.profile if d.eng.notes[k]["df"] >= 20])
    print(f"점검: 같은 입력으로 다시 푼 프로필과 지금 프로필의 코사인 평균 {gap:.4f}")
    p_votes = d.resolve_profiles(d.A, d.w_votes)
    p_strength = d.resolve_profiles(d.A_strength, d.w_uniform)
    p_both = d.resolve_profiles(d.A_strength, d.w_votes)
    g = np.array(d.catalog["globalMean"]) / 100 + 1e-3
    p_lift = {k: (v / g) / (v / g).sum() for k, v in d.profile.items()}
    trials = [
        v6,
        Trial(d, "기준: 노트 일치도만", accord_on=False),
        Trial(d, "기준: 어코드 유사도만", note="none"),
        Trial(d, "한계1 Tversky (강도 없음)", note="tversky"),
        Trial(d, "한계1+6 Tversky + 노트 강도", note="tversky", table="votes"),
        Trial(d, "지금 일치도 × 노트 강도 몫", note="hybrid", table="votes"),
        Trial(d, "한계6 노트 강도로 프로필만", profile=p_votes),
        Trial(d, "한계7 어코드 강도", A=d.A_strength, profile=p_strength),
        Trial(d, "한계7+6 어코드·노트 강도로 프로필", A=d.A_strength, profile=p_both),
        Trial(d, "v2 전체 (7+6+1)", note="tversky", table="votes", A=d.A_strength, profile=p_both),
        Trial(d, "7+6 + 지금 일치도 × 강도 몫", note="hybrid", table="votes", A=d.A_strength, profile=p_both),
        Trial(d, "한계2 베이지안 평점 7.5%", bayes=True),
        Trial(d, "한계8 리프트", profile=p_lift),
        *[Trial(d, f"한계4 시간축 {t}", l=L_T[t]) for t in L_T],
    ]
    if args.only:
        want = args.only.split(",")
        trials = [t for t in trials if t is v6 or any(w in t.name for w in want)]

    qs = queries(d, args.holdout)
    print(f"\n사람 유사 시험 ({'남겨 둔 절반' if args.holdout else '개발 절반'}): 쿼리 {len(qs):,}개 · 정답 평균 {np.mean([len(q[2]) for q in qs]):.2f}개 · 무작위라면 Top 10 약 {100 * 10 / d.n * np.mean([len(q[2]) for q in qs]):.2f}%", flush=True)
    base = similar_test(v6, qs)
    base_cross = similar_test(v6, qs, cross_brand=True)
    base_one = similar_test(v6, qs, one_per_layer=True)
    base_one_cross = similar_test(v6, qs, cross_brand=True, one_per_layer=True)
    print(f"다른 브랜드 정답이 있는 쿼리 {len(base_cross):,}개")
    print("\n| 시험판 | Top 10 | Top 50 | Top 50 차이 % (95% 구간) | 다른 브랜드 Top 50 | 차이 % (구간) | 층마다 1개 Top 50 | 차이 % (구간) | 층마다 1개·다른 브랜드 Top 50 | 재현 20 | 재현 100 | 2개 100 | 하나 뺌 100 | 맹점 | 브랜드 | Top 10 겹침 |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    v6_tops = [v6.top([(k, l, E.intensity(s)) for k, l, s in c], 10) for _, c in V.BLENDS]
    for t in trials:
        r = base if t is v6 else similar_test(t, qs)
        rc = base_cross if t is v6 else similar_test(t, qs, cross_brand=True)
        ro = base_one if t is v6 else similar_test(t, qs, one_per_layer=True)
        roc = base_one_cross if t is v6 else similar_test(t, qs, cross_brand=True, one_per_layer=True)
        e = existing_tests(t)
        tops = [t.top([(k, l, E.intensity(s)) for k, l, s in c], 10) for _, c in V.BLENDS]
        overlap = sum(len(set(a) & set(b)) for a, b in zip(tops, v6_tops))
        same = t is v6
        print(f"| {t.name} | {100 * np.mean(r <= 10):.1f}% | {100 * np.mean(r <= 50):.1f}% | {'—' if same else ci(r, base, 50)} | "
              f"{100 * np.mean(rc <= 50):.1f}% | {'—' if same else ci(rc, base_cross, 50)} | {100 * np.mean(ro <= 50):.1f}% | {'—' if same else ci(ro, base_one, 50)} | "
              f"{100 * np.mean(roc <= 50):.1f}% | {e[(20, 'normal')]} | {e[(100, 'normal')]} | {e[(100, 'two_per_layer')]} | "
              f"{e[(100, 'drop_one')]} | {e['blindspot']} | {e['brands']} | {overlap}/120 |", flush=True)

    # 한계 3: 선호를 켰을 때 등급이 내려가는 수 (조합 12개 Top 10)
    drops = total = 0
    for _, combo in V.BLENDS:
        spec = [E.BlendNote(k, l, E.intensity(s)) for k, l, s in combo]
        for r in d.eng.recommend(spec, k=10):
            pref = {d.eng.accord_family[d.perfumes[d.idx[r["perfumeKey"]]]["accords"][0]]}  # 그 향수의 1위 계열을 좋아하는 사용자
            after = next(x for x in d.eng.recommend(spec, preferred=pref, k=d.n) if x["perfumeKey"] == r["perfumeKey"])
            total += 1
            drops += after["grade"] != r["grade"]
    print(f"\n한계 3: 조합 12개 Top 10 {total}개에서, 그 향수의 1위 계열을 선호로 켰더니 등급이 내려간 수 {drops}")


if __name__ == "__main__":
    main()
