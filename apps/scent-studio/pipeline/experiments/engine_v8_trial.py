"""엔진 v8 후보 측정 (2026-11-04 사장 요청). 엔진·카탈로그·골든은 바꾸지 않는다.

앱 폴더에서:  py pipeline/experiments/engine_v8_trial.py   (실제 카탈로그, 원본 CSV, Decant 데이터 필요)
만드는 파일: docs/engine_v8_trial.md

재는 것: ① 등급 방식 ② 강도 데이터 쏠림과 보정 방안 ③ 브랜드 반복 ④ 프레시 계열 v6/v7 비교.
빠르게 돌리려고 numpy로 계산하되, 시작할 때 참조 엔진(engine_ref)과 점수가 같은지 먼저 확인한다.
"""
from __future__ import annotations

import gzip
import json
import math
import sys
from collections import Counter
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_rules import load_old_catalog, private_doc as R_private  # noqa: E402
import engine_ref as E  # noqa: E402
import estimate as EST  # noqa: E402
from estimate import is_dev  # noqa: E402,F401
import evaluate as V  # noqa: E402
import similar_votes as SV  # noqa: E402

APP = Path(__file__).resolve().parent.parent.parent
OUT = R_private("engine_v8_trial.md")  # 전체판(향수 이름·Decant 값)은 비공개. 공개 docs/engine_v8_trial.md는 손질한 사본(plan_b_portfolio.md 1-3 ①)
SEED = 20261104
SHIFT_TOP = 50  # P2: 두 묶음을 맞출 때 보는 묶음별 상위 향수 수

# 프레시 점검에 더 넣는 조합 (마린·민트·시트러스 계열). 사장에게 보인 적 없는 조합이다
EXTRA_FRESH = [
    ("자몽 · 민트 · 베티버", [("grapefruit", "top", "medium"), ("mint", "middle", "medium"), ("vetiver", "base", "medium")]),
    ("마린 노트 · 로즈메리 · 머스크", [("marine notes", "top", "medium"), ("rosemary", "middle", "medium"), ("musk", "base", "medium")]),
    ("유자 · 민트 · 머스크", [("yuzu", "top", "medium"), ("mint", "middle", "medium"), ("musk", "base", "medium")]),
    ("레몬 · 마린 노트 · 시더우드", [("lemon", "top", "medium"), ("marine notes", "middle", "medium"), ("cedarwood", "base", "medium")]),
    ("베르가못 · 세이지 · 앰버그리스", [("bergamot", "top", "medium"), ("sage", "middle", "medium"), ("ambergris", "base", "medium")]),
    ("민트 · 그린 티 · 시더우드", [("mint", "top", "medium"), ("green tea", "middle", "medium"), ("cedarwood", "base", "medium")]),
]
FRESH_IN_V6_COMBOS = {2, 4, 5, 12}  # V6_COMPARE_COMBOS 번호 (레몬·녹차, 마린, 레몬 강, 민트)
FRESH_FAMS, WARM_ACCORDS = {"citrus", "fresh", "green"}, {"sweet", "gourmand", "oriental", "resinous", "synthetic", "animal"}
BUCKET = {"citrus": "프레시", "fresh": "프레시", "green": "프레시", "floral": "플로럴", "fruity": "플로럴", "soft": "플로럴",
          "woody": "우디·오리엔탈", "amber": "우디·오리엔탈", "spicy": "우디·오리엔탈", "sweet": "구르망"}


# 어코드와 상관없이 고르게 흩뿌려진 불쾌한 단어 (원본에 단어마다 50~90번, 2026-11-04 Claude 분류. 사장 확인 전 임시 목록)
FOUL = set("spoil|infestation|mold|bilge|nausea|putrescence|sludge|offense|dankness|funk|carcass|repulse|contamination|stink|foulness|grease|rancid oil|rankness|rotting flower|smog|disgust|dust|rot|diesel|noxiousness|pungency|pungidity|chemical|grunge|mud|sewage|spoiled meat|stalebread|sulfur|swamp|moldy wallpaper|garbage|odor|rancidness|carrion|manure|scum|filth|gymwear|rust|sourmilk|acid|chlorine|harshness|decay|acridity|malodor|sour milk|mildew|nastiness|sock|decomposing leaf|grim|sewer|melting plastic|rotten onion|pollution|fume|putridity|spoiled spice|spoilage|rotten egg|skunk|staleness|damp|stench|moldy leather|burnt electronics|gasoline|burnt toast|fish|rubber|asphalt".split("|"))


def spec_of(spec) -> list[tuple[str, str, float]]:
    return [(k, layer, E.intensity(s)) for k, layer, s in spec]


class Scorer:
    """engine_ref와 같은 계산. estimate: 강도 없는 향수에 쓰는 추정 (None = 지금 방식, 5·4·3·2·1과 1/노트 수)"""

    def __init__(self, cat: dict, strength: bool, estimate: tuple | None = None, shift: bool = False):
        self.cat, self.strength, self.shift = cat, strength, shift
        self.eng = E.Engine(cat)
        ps = self.ps = cat["perfumes"]
        self.n = len(ps)
        self.idx = {p["key"]: i for i, p in enumerate(ps)}
        self.rating = np.array([p["ratingCount"] for p in ps])
        order = sorted(range(self.n), key=lambda i: ps[i]["key"])
        self.key_rank = np.empty(self.n, int)
        self.key_rank[order] = np.arange(self.n)
        self.has = np.array([bool(p.get("noteWeights")) for p in ps]) if strength else np.zeros(self.n, bool)
        self.data = self.has  # 강도 데이터 보유 표시용 (v6 계산기는 main에서 v7 기준으로 채운다)
        self.all_notes = [set(p["top"]) | set(p["middle"]) | set(p["base"]) for p in ps]
        self.n_notes = np.array([max(len(s), 1) for s in self.all_notes], float)
        self.foul = np.array([bool(s & FOUL) for s in self.all_notes])
        self.inv: dict[str, list[tuple[int, str]]] = {}
        for i, p in enumerate(ps):
            for layer in E.LAYERS:
                for k in p[layer]:
                    self.inv.setdefault(k, []).append((i, layer))
        self.fam = {}
        for f in cat["families"]:
            self.fam[f["code"]] = np.zeros(self.n, bool)
        for i, notes in enumerate(self.all_notes):
            for k in notes:
                self.fam[self.eng.notes[k]["family"]][i] = True
        rank_vec = np.array([self.eng.perfume_vector(p["accords"]) for p in ps])
        real_vec = np.array([self.eng.vec[p["key"]] for p in ps])
        self.note_prior = None
        est_vec = rank_vec
        if estimate:
            est_vec, self.note_prior = estimate
        self.est_vec = est_vec
        # 어코드 강도만 있고 노트 강도는 없는 향수가 3개 있어서 따로 본다
        has_accord = np.array([bool(p.get("accordShares")) for p in ps]) if strength else np.zeros(self.n, bool)
        self.real_vec = np.where(has_accord[:, None], real_vec, est_vec)
        self.est_norm = np.linalg.norm(self.est_vec, axis=1)
        self.real_norm = np.linalg.norm(self.real_vec, axis=1)
        self._cache: dict[str, tuple] = {}
        if self.note_prior is not None:
            self.prior_sum = np.array([sum(self.note_prior.get(k, 1.0) for k in s) or 1.0 for s in self.all_notes])

    def note_arrays(self, k: str) -> tuple:
        """(층별 있음, 어디든 있음, 실제 몫, 추정 몫)"""
        if k not in self._cache:
            layers = {layer: np.zeros(self.n, bool) for layer in E.LAYERS}
            real = np.zeros(self.n)
            for i, layer in self.inv.get(k, []):
                layers[layer][i] = True
                w = self.ps[i].get("noteWeights")
                if self.strength and w:
                    real[i] = w[k]
            anywhere = layers["top"] | layers["middle"] | layers["base"]
            if self.note_prior is not None:
                est = np.where(anywhere, self.note_prior.get(k, 1.0) / self.prior_sum, 0.0)
            else:
                est = np.where(anywhere, 1.0 / self.n_notes, 0.0)
            real = np.where(self.has, real, est)
            self._cache[k] = (layers, anywhere, real, est)
        return self._cache[k]

    def parts(self, spec) -> dict:
        """cos, note(실제/추정 두 벌), 기본 일치도"""
        b = np.zeros(self.real_vec.shape[1])
        weights, base = [], np.zeros(self.n)
        held_real, held_est = np.zeros(self.n), np.zeros(self.n)
        all_in = np.ones(self.n, bool)
        for k, layer, s in spec:
            b += s * np.array(self.eng.notes[k]["profile"])
            w = math.log(1 + self.n / self.eng.notes[k]["df"])
            weights.append(w)
            layers, anywhere, real, est = self.note_arrays(k)
            fam = self.fam[self.eng.notes[k]["family"]]
            base += w * np.where(layers[layer], 1.0, np.where(anywhere, 0.5, np.where(fam, 0.3, 0.0)))
            held_real += real
            held_est += est
            all_in &= anywhere
        base /= sum(weights)
        nb = np.linalg.norm(b)
        cos_real = (self.real_vec @ b) / (self.real_norm * nb)
        cos_est = (self.est_vec @ b) / (self.est_norm * nb)
        if self.strength:
            nm_real = base * (E.STRENGTH_BASE + (1 - E.STRENGTH_BASE) * np.sqrt(np.minimum(held_real, 1.0)))
            nm_est = base * (E.STRENGTH_BASE + (1 - E.STRENGTH_BASE) * np.sqrt(np.minimum(held_est, 1.0)))
        else:
            nm_real = nm_est = base
        real = (E.W_ACCORD * cos_real + E.W_NOTE * nm_real) / (E.W_ACCORD + E.W_NOTE)
        est = (E.W_ACCORD * cos_est + E.W_NOTE * nm_est) / (E.W_ACCORD + E.W_NOTE)
        return {"sim": real, "sim_est": est, "cos": cos_real, "nm": nm_real, "held": held_real, "all_in": all_in}

    def group_shift(self, sim: np.ndarray) -> float:
        """P2: 강도 없는 묶음의 상위 점수를 강도 있는 묶음의 상위 점수에 맞추는 값"""
        if not self.shift:
            return 0.0
        a, b = sim[self.has], sim[~self.has]
        return float(np.sort(a)[-SHIFT_TOP:].mean() - np.sort(b)[-SHIFT_TOP:].mean())

    def scored(self, spec) -> tuple[np.ndarray, np.ndarray]:
        """(순위 점수, 정답을 가린 경우의 점수)"""
        p = self.parts(spec)
        d = self.group_shift(p["sim"])
        if d:
            return p["sim"] + np.where(self.has, 0.0, d), p["sim_est"] + d
        return p["sim"], p["sim_est"]

    def top(self, sim: np.ndarray, k: int = 10, cap: bool = True) -> list[int]:
        cand = np.argpartition(-sim, min(600, self.n - 1))[:600]
        rounded = np.floor(sim[cand] * E.SCORE_QUANTUM + 0.5)
        order = cand[np.lexsort((self.key_rank[cand], -self.rating[cand], -rounded))]
        out, brands, groups = [], Counter(), set()
        for i in order:
            p = self.ps[i]
            if p["group"] in groups or (cap and brands[p["brand"]] >= E.BRAND_CAP):
                continue
            groups.add(p["group"])
            brands[p["brand"]] += 1
            out.append(int(i))
            if len(out) == k:
                break
        return out

    def recommend(self, spec, k: int = 10, cap: bool = True) -> list[int]:
        return self.top(self.scored(spec)[0], k, cap)


def fit_estimate(cat: dict, eng: E.Engine) -> tuple[np.ndarray, dict[str, float]]:
    """P1 추정치 보정. 계산은 pipeline/estimate.py로 옮겼다 (v8 7단계)"""
    return EST.fit(cat)


def check_same(s: Scorer, ref_scores, specs) -> None:
    for name, spec in specs:
        ref = ref_scores(spec)
        mine = s.parts(spec_of(spec))["sim"]
        worst = max(abs(ref[p["key"]] - mine[i]) for i, p in enumerate(s.ps))
        assert worst < 1e-9, f"참조 엔진과 다름: {name} {worst}"


def random_blends(cat: dict, eng: E.Engine, n: int, seed: int) -> list[list[tuple[str, str, str]]]:
    """실제 향수에서 층마다 노트 하나씩 뽑은 조합 (실제 쓰임새에 가까운 빈도). 보이는 노트만"""
    rng = np.random.default_rng(seed)
    ps, out = cat["perfumes"], []
    while len(out) < n:
        spec = []
        for layer in E.LAYERS:
            notes = [k for k in ps[int(rng.integers(len(ps)))][layer] if eng.notes[k]["tier"] != "hidden"]
            if notes:
                spec.append((notes[int(rng.integers(len(notes)))], layer, "medium"))
        if len(spec) == 3 and len({k for k, _, _ in spec}) == 3:
            out.append(spec)
    return out


def human_ranks(s: Scorer, qs: list[dict], one_per_layer: bool = False, masked: bool = False) -> dict[str, int]:
    """쿼리 → 정답 중 가장 높은 순위. masked=True면 정답 향수의 강도를 가리고 잰다(강도 없는 향수가 받는 손해)"""
    out = {}
    for q in qs:
        me = s.ps[s.idx[q["key"]]]
        notes = q["notes"]
        if one_per_layer:
            seen: set[str] = set()
            notes = [(k, layer) for k, layer in notes if not (layer in seen or seen.add(layer))]
        sim, est = s.scored([(k, layer, 1.0) for k, layer in notes])
        rest = sim[s.group_mask(me["group"])]
        out[q["key"]] = min(int((rest > (est if masked else sim)[s.idx[k]]).sum()) + 1 for k in q["pos"])
    return out


def install_group_mask(s: Scorer) -> None:
    groups = np.array([p["group"] for p in s.ps])
    cache: dict[str, np.ndarray] = {}

    def mask(g: str) -> np.ndarray:
        if g not in cache:
            cache[g] = groups != g
        return cache[g]
    s.group_mask = mask


def hit_rate(r: dict[str, int], keys: list[str], k: int) -> float:
    return 100.0 * sum(r[q] <= k for q in keys) / max(len(keys), 1)


def diff_ci(a: dict[str, int], b: dict[str, int], keys: list[str], k: int) -> str:
    d = np.array([float(a[q] <= k) - float(b[q] <= k) for q in keys])
    rng = np.random.default_rng(SEED)
    boots = [d[rng.integers(0, len(d), len(d))].mean() for _ in range(1000)]
    lo, hi = np.percentile(boots, [2.5, 97.5])
    return f"{100 * d.mean():+.1f} ({100 * lo:+.1f}~{100 * hi:+.1f})"


# ---------- ① 등급 ----------
def grade_section(s: Scorer, lists: list, qs: list[dict]) -> list[str]:
    pool_specs = random_blends(s.cat, s.eng, 500, SEED)
    test_specs = random_blends(s.cat, s.eng, 500, SEED + 1)
    pool = np.concatenate([s.parts(spec_of(sp))["sim"][s.recommend(spec_of(sp))] for sp in pool_specs])
    p90, p60 = float(np.percentile(pool, 90)), float(np.percentile(pool, 60))

    def context(spec) -> dict:
        p = s.parts(spec)
        top = s.top(p["sim"])
        bound = (E.W_ACCORD * p["cos"].max() + E.W_NOTE * p["nm"].max()) / (E.W_ACCORD + E.W_NOTE)
        return {"p": p, "top": top, "bound": bound, "first": p["sim"][top[0]], "sorted": np.sort(p["sim"])}

    two = lambda hi, lo: np.asarray(hi, int) + np.asarray(lo, int)  # noqa: E731  numpy에서 참+참은 참이라 정수로 바꿔 더한다
    methods = [("지금 고정 0.87/0.81", lambda v, c: two(v >= .87, v >= .81)),
               ("고정 0.80/0.72", lambda v, c: two(v >= .80, v >= .72)),
               ("고정 0.75/0.65", lambda v, c: two(v >= .75, v >= .65)),
               ("고정 0.70/0.60", lambda v, c: two(v >= .70, v >= .60)),
               ("(a) 최고 가능 점수 대비 ≥90%/80%", lambda v, c: two(v / c["bound"] >= .90, v / c["bound"] >= .80)),
               ("(a′) 그 조합 1위 대비 ≥97%/92%", lambda v, c: two(v / c["first"] >= .97, v / c["first"] >= .92)),
               ("(b) 조합 안 전체 향수 상위 0.05%/0.1%",
                lambda v, c: two((1 - np.searchsorted(c["sorted"], v) / s.n) <= .0005, (1 - np.searchsorted(c["sorted"], v) / s.n) <= .001)),
               (f"(b′) 무작위 조합 Top 10 분포 상위 10%/40% (={p90:.3f}/{p60:.3f})", lambda v, c: two(v >= p90, v >= p60))]
    rows = ["## ① 등급 방식 비교", "",
            "Top 10 240개(블라인드에 쓴 조합 24개 × 10). **문턱값은 비교를 위한 임시값**이고, 어떤 방식이든 눈으로 평가 뒤 확정합니다.", "",
            "| 방식 | 매우 비슷 | 비슷 | 참고 | 노트 전부 든 향수 중 '참고' | 무작위 500조합 1위가 '참고' | 노트가 빠진 1위인데 '매우 비슷' | 사람 유사 정답 비율: 매우 비슷 / 비슷 / 참고 |",
            "|---|---:|---:|---:|---:|---:|---:|---:|"]
    held = [q for q in qs if q["holdout"]][:800]
    ctx24 = [(context(spec_of(sp))) for _, sp in lists]
    ctx_test = [context(spec_of(sp)) for sp in test_specs]
    ctx_q = []
    for q in held:
        seen: set[str] = set()
        notes = [(k, layer) for k, layer in q["notes"] if not (layer in seen or seen.add(layer))]
        ctx_q.append((q, context([(k, layer, 1.0) for k, layer in notes])))
    for name, fn in methods:
        count, all_ref, all_n = Counter(), 0, 0
        for c in ctx24:
            vals = c["p"]["sim"][c["top"]]
            g = np.asarray(fn(vals, c), int)
            count.update(g.tolist())
            ai = c["p"]["all_in"][c["top"]]
            all_n += int(ai.sum())
            all_ref += int((ai & (g == 0)).sum())
        first_ref, miss_very, miss_n = 0, 0, 0
        for c in ctx_test:
            i = c["top"][0]
            g = int(np.asarray(fn(np.array([c["p"]["sim"][i]]), c), int)[0])
            first_ref += g == 0
            if not c["p"]["all_in"][i]:
                miss_n += 1
                miss_very += g == 2
        hit, tot = Counter(), Counter()
        for q, c in ctx_q:
            me = s.ps[s.idx[q["key"]]]
            pos = set(q["pos"])
            top = [i for i in c["top"] if s.ps[i]["group"] != me["group"] and s.has[i]]  # 정답은 강도 있는 향수에만 있다
            if not top:
                continue
            for i, g in zip(top, np.asarray(fn(c["p"]["sim"][top], c), int).tolist()):
                tot[g] += 1
                hit[g] += s.ps[i]["key"] in pos
        prec = " / ".join(f"{100 * hit[g] / tot[g]:.1f}%" if tot[g] else "—" for g in (2, 1, 0))
        rows.append(f"| {name} | {count[2]} | {count[1]} | {count[0]} | {all_ref}/{all_n} | {first_ref}/500 | {miss_very}/{miss_n} | {prec} |")
        print(rows[-1], flush=True)
    rows +=["", "- **'최고 가능 점수'**: 그 조합에서 어떤 향수든 낼 수 있는 어코드 최고값과 노트 최고값을 합친 값. 이론상 최고(1.0)는 강도 몫 때문에 도달할 수 없어서 쓰지 않았습니다.",
             "- **사람 유사 정답 비율**: 남겨 둔 절반 쿼리 중 800개(층마다 1개 입력), Top 10의 강도 있는 향수 가운데 사람들이 '비슷하다'고 투표한 비율. 등급이 높을수록 높아야 등급이 의미 있습니다.", ""]
    return rows


# ---------- ② 쏠림 ----------
def share_of(s: Scorer, specs: list, top_k: int = 10) -> float:
    picked = [i for sp in specs for i in s.recommend(spec_of(sp), top_k)]
    return 100.0 * float(s.has[picked].mean())


def repro(s: Scorer, picks: list, k: int = 3) -> int:
    hit = 0
    for p, notes in picks:
        top = s.recommend([(n, layer, 1.0) for n, layer in notes], k)
        hit += p["group"] in {s.ps[i]["group"] for i in top}
    return hit


def bias_section(variants: dict[str, Scorer], lists: list, qs: list[dict]) -> list[str]:
    base = variants["v7 (지금)"]
    rand = random_blends(base.cat, base.eng, 300, SEED + 2)
    specs24 = [sp for _, sp in lists]
    has_share = 100.0 * float(base.has.mean())
    picks_all = V.reproduction_picks(base.cat, base.eng, 2000)
    picks_has = [x for x in picks_all if x[0].get("noteWeights")][:100]
    picks_no = [x for x in picks_all if not x[0].get("noteWeights")][:100]
    med = lambda ps: int(np.median([p["ratingCount"] for p, _ in ps]))  # noqa: E731
    r = ["## ② 강도 데이터 쏠림", "", f"전체 향수 중 강도 데이터 보유 {has_share:.1f}%.", "",
         "### 지금 손해가 얼마인가", "",
         "강도 없는 향수에는 Fragrantica 투표도 없어서 그대로는 잴 수 없습니다. 그래서 **가림 시험**을 만들었습니다: 사람이 '비슷하다'고 뽑은 정답 향수의 강도 데이터만 가리고(추정치로 바꾸고) 같은 시험을 합니다. 같은 향수, 같은 정답에서 강도 데이터 유무만 다르므로 그 차이가 손해입니다.", ""]
    rows, stored = [], {}
    dev = [q["key"] for q in qs if not q["holdout"]]
    hold = [q["key"] for q in qs if q["holdout"]]
    for name, s in variants.items():
        real = human_ranks(s, qs)
        masked = human_ranks(s, qs, masked=True)
        stored[name] = (real, masked)
        rows.append((name, s))
        print(name, f"real {hit_rate(real, hold, 50):.1f} masked {hit_rate(masked, hold, 50):.1f}", flush=True)
    r0, m0 = stored["v7 (지금)"]
    r += ["| 쿼리 묶음 | 쿼리 수 | 정답 강도 그대로 Top 50 | 정답 강도 가림 Top 50 | 손해 %p (95% 구간) | 그대로 Top 10 | 가림 Top 10 |", "|---|---:|---:|---:|---:|---:|---:|"]
    for label, keys in (("남겨 둔 절반", hold), ("개발 절반", dev)):
        r.append(f"| {label} | {len(keys):,} | {hit_rate(r0, keys, 50):.1f}% | {hit_rate(m0, keys, 50):.1f}% | {diff_ci(m0, r0, keys, 50)} | {hit_rate(r0, keys, 10):.1f}% | {hit_rate(m0, keys, 10):.1f}% |")
    r += ["", f"재현 시험(자기 노트를 넣어 자기가 Top 3): 강도 있는 인기 향수 100개 {repro(base, picks_has)}/100 (평가 수 중앙값 {med(picks_has)}), 강도 없는 인기 향수 100개 {repro(base, picks_no)}/100 (평가 수 중앙값 {med(picks_no)}).", "",
          "### 왜 쏠리는가", ""]
    a = np.array([base.eng.vec[p["key"]] for p in base.ps])[base.has]
    rank = np.array([base.eng.perfume_vector(p["accords"]) for p in base.ps])[base.has]
    cr, cm, hr, he = [], [], [], []
    for sp in rand:
        spec = spec_of(sp)
        b = sum(np.array(base.eng.notes[k]["profile"]) for k, _, _ in spec)
        cr.append(float(np.percentile((a @ b) / (np.linalg.norm(a, axis=1) * np.linalg.norm(b)), 99)))
        cm.append(float(np.percentile((rank @ b) / (np.linalg.norm(rank, axis=1) * np.linalg.norm(b)), 99)))
        p = base.parts(spec)
        m = p["all_in"] & base.has
        if m.any():
            hr.append(float(p["held"][m].mean()))
            he.append(float(sum(base.note_arrays(k)[3] for k, _, _ in spec)[m].mean()))
    inside =np.mean([sum(p["accordShares"].get(x, 0) for x in p["accords"][:5]) / sum(p["accordShares"].values()) for p in base.ps if p.get("accordShares")])
    r += [f"- **어코드 벡터가 원인입니다.** 강도 있는 향수의 실제 비중은 평균 {100 * (1 - inside):.0f}%가 목록 5개 밖 어코드에 있는데, 5·4·3·2·1 추정치는 5개만 채웁니다. 같은 향수를 두 방식으로 재면 코사인 상위 1%가 실제 강도 {np.mean(cr):.3f}, 추정치 {np.mean(cm):.3f}입니다.",
          f"- 노트 몫은 큰 원인이 아닙니다: 노트 전부 든 향수의 몫 합 평균이 실제 {np.mean(hr):.3f}, 균등 추정 {np.mean(he):.3f}입니다.", "",
          "### 보정 방안", "",
          "- **P1 추정치 보정**: 강도 있는 향수(개발 절반)로 '어코드 목록 → 전체 21개 비중'을 학습해 강도 없는 향수의 벡터를 채우고, 노트 몫도 '노트별 평균 강도'로 추정합니다. 카탈로그를 만들 때 한 번만 계산하므로 앱 계산은 그대로입니다.",
          f"- **P2 데이터 유무 정규화**: 조합마다 강도 없는 묶음의 상위 {SHIFT_TOP}개 평균 점수를 강도 있는 묶음에 맞춰 올립니다. 비율은 잘 맞지만 점수에 보정값이 섞입니다.",
          "- **P3 = P1 + P2**",
          "- **M1 연결표 수정**: Fragrantica `aromatic`을 `green` 대신 `fresh`로 연결하고 카탈로그를 다시 만듭니다(④ 참고).",
          "- **M1 + P1**: 둘을 함께 적용합니다.", "",
          "| 방안 | Top 10 강도 보유 (비교 24조합) | Top 10 강도 보유 (무작위 300조합) | Top 10 오염 의심 (무작위 300조합) | 사람 유사 Top 50 남겨 둔 절반 | v7 대비 %p (95% 구간) | 정답 강도 가림 Top 50 남겨 둔 절반 | 가림 v7 대비 %p | 재현 20 | 맹점 | 다양성 |",
          "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    picks20 = V.reproduction_picks(base.cat, base.eng, 20)
    for name, s in variants.items():
        real, masked = stored[name]
        fam = s.eng.accord_family
        blind_top = s.recommend(spec_of(V.BLIND_SPOT), 5)
        blind = sum({"citrus", "floral"} <= {fam[a] for a in s.ps[i]["accords"]} for i in blind_top)
        brands = {s.ps[i]["brand"] for _, sp in V.BLENDS for i in s.recommend(spec_of(sp))}
        r.append(f"| {name} | {share_of(s, specs24):.1f}% | {share_of(s, rand):.1f}% | {100 * float(s.foul[[i for sp in rand for i in s.recommend(spec_of(sp))]].mean()):.1f}% | {hit_rate(real, hold, 50):.1f}% | {diff_ci(real, r0, hold, 50)} | "
                 f"{hit_rate(masked, hold, 50):.1f}% | {hit_rate(masked, hold, 50) - hit_rate(m0, hold, 50):+.1f} | {repro(s, picks20)}/20 | {blind}/5 | {len(brands)} |")
        print(r[-1], flush=True)
    r += ["", "- 남겨 둔 절반은 v7 확인에도 한 번 썼으므로 완전히 새 자료는 아닙니다.",
          "- 정답이 모두 강도 있는 향수라서, 보정으로 강도 없는 향수가 Top에 올라오면 '그대로' 점수는 구조적으로 조금 내려갑니다. 그래서 '가림' 점수와 함께 봐야 합니다.", ""]
    return r


# ---------- ③ 브랜드 ----------
def brand_section(v6: Scorer, variants: dict[str, Scorer]) -> list[str]:
    r = ["## ③ 브랜드 반복 (블라인드 12조합)", "",
         "같은 브랜드 최대 2개 상한은 이미 엔진에 있습니다. '상한 없음'은 그 상한을 빼고 다시 뽑은 Top 10입니다.", "",
         "| 엔진 | 상한 있음: 2개 나온 브랜드 수 합 | 상한 있음: 고유 브랜드 평균 | 상한 없음: 3개 이상 나온 조합 수 | 상한 없음: 한 브랜드 최대 | 상한 없음: 고유 브랜드 평균 | 상한 때문에 밀려난 향수 합 |",
         "|---|---:|---:|---:|---:|---:|---:|"]
    scorers = {"v6": v6, **variants}
    for name, s in scorers.items():
        twos, uniq, over, worst, uniq2, dropped = 0, [], 0, 0, [], 0
        for _, sp in V.V6_COMPARE_COMBOS:
            capped = s.recommend(spec_of(sp))
            free = s.recommend(spec_of(sp), cap=False)
            c1, c2 = Counter(s.ps[i]["brand"] for i in capped), Counter(s.ps[i]["brand"] for i in free)
            twos += sum(v == 2 for v in c1.values())
            uniq.append(len(c1))
            over += max(c2.values()) >= 3
            worst = max(worst, max(c2.values()))
            uniq2.append(len(c2))
            dropped += len(set(free) - set(capped))
        r.append(f"| {name} | {twos} | {np.mean(uniq):.1f} | {over}/12 | {worst} | {np.mean(uniq2):.1f} | {dropped} |")
    v7 = variants["v7 (지금)"]
    r += ["", "### 조합별 (상한 있음 Top 10, 2개 나온 브랜드 · 강도 보유 비율)", "",
          "| 조합 | v6 | v7 | v6·v7 Top 10 겹침 |", "|---|---|---|---:|"]
    fmt = lambda s, top: ", ".join(b for b, v in Counter(s.ps[i]["brand"] for i in top).items() if v == 2) or "없음"  # noqa: E731
    for i, (name, sp) in enumerate(V.V6_COMPARE_COMBOS, 1):
        t6, t7 = v6.recommend(spec_of(sp)), v7.recommend(spec_of(sp))
        same = len({v6.ps[j]["key"] for j in t6} & {v7.ps[j]["key"] for j in t7})
        r.append(f"| {i}. {name} | {V.cell(fmt(v6, t6))} ({100 * v6.data[t6].mean():.0f}%) | {V.cell(fmt(v7, t7))} ({100 * v7.data[t7].mean():.0f}%) | {same}/10 |")
    return r + [""]


# ---------- ④ 프레시 ----------
def family_share(s: Scorer, top: list[int], accords: set[str] | None = None, fams: set[str] | None = None) -> float:
    idx = [j for j, a in enumerate(s.eng.accords) if (accords and a in accords) or (fams and s.eng.accord_family[a] in fams)]
    return float(np.mean([s.eng.vec[s.ps[i]["key"]][j] for i in top for j in idx]) * len(idx))


def fresh_section(v6: Scorer, v7: Scorer, m1: Scorer, qs: list[dict]) -> list[str]:
    fresh = [c for i, c in enumerate(V.V6_COMPARE_COMBOS, 1) if i in FRESH_IN_V6_COMBOS] + \
            [c for c in EXTRA_FRESH if all(k in v7.eng.notes and v7.eng.notes[k]["tier"] != "hidden" for k, _, _ in c[1])]
    other = [c for i, c in enumerate(V.V6_COMPARE_COMBOS, 1) if i not in FRESH_IN_V6_COMBOS]
    r = ["## ④ 프레시 계열 v6/v7 비교", "", f"프레시 조합 {len(fresh)}개(블라인드 2·4·5·12번 + 새 조합), 나머지 블라인드 조합 {len(other)}개.", "",
         "| 묶음 | 엔진 | Top 10 프레시 계열 비중 | Top 10 달콤·따뜻한 어코드 비중 | 코사인 평균 | 노트 일치 평균 | 노트 전부 든 비율 | 강도 보유 비율 | 조합과 v6의 Top 10 겹침 |",
         "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for label, group in (("프레시", fresh), ("그 외", other)):
        for name, s in (("v6", v6), ("v7", v7), ("M1", m1)):
            f, w, c, n, a, h, same = [], [], [], [], [], [], []
            for _, sp in group:
                spec = spec_of(sp)
                p = s.parts(spec)
                top = s.top(p["sim"])
                f.append(family_share(s, top, fams=FRESH_FAMS))
                w.append(family_share(s, top, accords=WARM_ACCORDS))
                c.append(p["cos"][top].mean())
                n.append(p["nm"][top].mean())
                a.append(p["all_in"][top].mean())
                h.append(s.data[top].mean())
                t6 = {v6.ps[j]["key"] for j in v6.recommend(spec)}
                same.append(len(t6 & {s.ps[j]["key"] for j in top}))
            r.append(f"| {label} | {name} | {np.mean(f):.1f}% | {np.mean(w):.1f}% | {np.mean(c):.3f} | {np.mean(n):.3f} | {100 * np.mean(a):.0f}% | {100 * np.mean(h):.0f}% | {np.mean(same):.1f}/10 |")
    r += ["", "### 사람 유사 시험을 쿼리 향수 계열별로 (남겨 둔 절반)", "",
          "쿼리 향수의 첫째 어코드로 계열을 나눕니다.", "",
          "| 입력 | 계열 | 쿼리 수 | v6 Top 50 | v7 Top 50 | M1 Top 50 | v7 − v6 %p (95% 구간) | M1 − v7 %p (95% 구간) |", "|---|---|---:|---:|---:|---:|---:|---:|"]
    fam = v7.eng.accord_family
    hold = [q for q in qs if q["holdout"]]
    for one, label in ((False, "층마다 3개"), (True, "층마다 1개")):
        r6, r7, rm = human_ranks(v6, hold, one), human_ranks(v7, hold, one), human_ranks(m1, hold, one)
        for bucket in ("프레시", "플로럴", "우디·오리엔탈", "구르망"):
            keys = [q["key"] for q in hold if BUCKET[fam[v7.ps[v7.idx[q["key"]]]["accords"][0]]] == bucket]
            if keys:
                r.append(f"| {label} | {bucket} | {len(keys):,} | {hit_rate(r6, keys, 50):.1f}% | {hit_rate(r7, keys, 50):.1f}% | {hit_rate(rm, keys, 50):.1f}% | {diff_ci(r7, r6, keys, 50)} | {diff_ci(rm, r7, keys, 50)} |")
                print(r[-1], flush=True)
    r += ["", "### 사장이 짚은 향수: 왜 올라왔나", "",
          "| 조합 | 향수 | v6 순위 | v7 순위 | M1 순위 | 코사인 v6→v7 | 노트 일치 v6→v7 | 고른 노트 강도 몫 합 | 실제 어코드 상위 3 (비중) |",
          "|---|---|---:|---:|---:|---:|---:|---:|---|"]
    traces = [("마린 노트 · 세이지 · 시더우드 약", V.V6_COMPARE_COMBOS[3][1], ["Cool Water Night Dive", "Cool Water Into the Ocean"]),
              ("민트 · 라벤더 · 시더우드", V.V6_COMPARE_COMBOS[11][1], ["Kaleidoscope", "XIX March", "Ocean Hills"])]
    for name, sp, wanted in traces:
        spec = spec_of(sp)
        p6, p7, pm = v6.parts(spec), v7.parts(spec), m1.parts(spec)
        for i, per in enumerate(v7.ps):
            if not any(w in per["name"] for w in wanted) or not p7["all_in"][i] and p7["sim"][i] < 0.5:
                continue
            rank6 = int((p6["sim"] > p6["sim"][i]).sum()) + 1
            rank7 = int((p7["sim"] > p7["sim"][i]).sum()) + 1
            if rank7 > 300:
                continue
            vec = v7.eng.vec[per["key"]]
            top3 = sorted(zip(v7.eng.accords, vec), key=lambda t: -t[1])[:3]
            r.append(f"| {name} | {V.cell(per['brand'] + ' — ' + per['name'])} | {rank6} | {rank7} | {int((pm['sim'] > pm['sim'][i]).sum()) + 1} | {p6['cos'][i]:.3f}→{p7['cos'][i]:.3f} | {p6['nm'][i]:.3f}→{p7['nm'][i]:.3f} | {p7['held'][i]:.3f} | "
                     + ", ".join(f"{a} {v:.0f}" for a, v in top3) + " |")
    r += ["", "### 원인: 연결표가 프레시를 그린으로 옮겼습니다", "",
          "| 노트 | v6 프로필 | v7 프로필 | M1 프로필 |", "|---|---|---|---|"]
    top_of = lambda s, k: ", ".join(f"{a} {100 * v / sum(s.eng.notes[k]['profile']):.0f}%" for a, v in sorted(zip(s.eng.accords, s.eng.notes[k]['profile']), key=lambda t: -t[1])[:3])  # noqa: E731
    for k in ("mint", "lavender", "marine notes", "sage", "lemon", "green tea"):
        r.append(f"| {k} | {top_of(v6, k)} | {top_of(v7, k)} | {top_of(m1, k)} |")
    r += ["", "| 벡터 (강도 있는 향수 평균) | fresh 계열 | green 계열 |", "|---|---:|---:|"]
    for label, s, real in (("Parfumo 목록 순위 (v6 방식)", v7, False), ("Fragrantica 강도, 지금 연결표 (v7)", v7, True), ("Fragrantica 강도, M1", m1, True)):
        vecs = np.array([s.eng.vec[p["key"]] if real else s.eng.perfume_vector(p["accords"]) for p in s.ps if p.get("accordShares")]).mean(axis=0)
        by = Counter()
        for a, x in zip(s.eng.accords, vecs):
            by[s.eng.accord_family[a]] += float(x)
        r.append(f"| {label} | {by['fresh']:.1f}% | {by['green']:.1f}% |")
    return r + [""]


def data_section(s: Scorer) -> list[str]:
    # 공개 저장소이므로 향수 이름·평가 수는 싣지 않는다 (복사 오류 목록과 같은 규칙, 67a9e6d)
    return ["## ⑤ 오염 의심 행", "",
            f"후보 {s.n:,}개 중 **{int(s.foul.sum()):,}개({100 * s.foul.mean():.1f}%)**에 sewer, nausea, carcass 같은 불쾌한 단어가 노트로 들어 있습니다(단어 {len(FOUL)}개, Claude 임시 분류).",
            f"강도 있는 향수 중 {100 * s.foul[s.has].mean():.1f}%, 강도 없는 향수 중 {100 * s.foul[~s.has].mean():.1f}%입니다. 평가 수 중앙값은 오염 의심 {int(np.median(s.rating[s.foul]))}, 나머지 {int(np.median(s.rating[~s.foul]))}입니다.", ""]


def alt_catalog() -> dict:
    """M1: 연결표에서 aromatic만 fresh로 바꾼 카탈로그. 파일에는 쓰지 않는다"""
    import build_catalog as B
    import catalog_rules as R
    import decant_join as D
    manual = APP / "pipeline" / "manual"
    amap = D.read_accord_map(manual / "fragrantica_accords.csv")
    assert amap["aromatic"] == "green"
    amap["aromatic"] = "fresh"
    return B.build(B.read_csv(APP / "pipeline" / "data" / "raw" / "parfumo_data_clean.csv"), R.REAL,
                   B.read_aliases(manual / "aliases.csv"), B.read_overrides(manual / "note_family_override.csv"),
                   B.read_korean(manual / "notes_ko.csv"), version="m1", source_name="m1", source_note="m1",
                   decant={"rows": D.load_rows(APP / "pipeline" / "data" / "raw" / "decant" / "perfumes.db"), "accord_map": amap,
                           "aliases": D.read_note_aliases(manual / "fragrantica_note_aliases.csv")},
                   junk=B.read_junk(manual / "junk_notes.csv"), brand_fixes=B.read_brand_fixes(manual / "brand_fixes.csv")).catalog


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = load_old_catalog(APP / "pipeline" / "data" / "out" / "catalog.json.gz")
    eng = E.Engine(cat)
    eng6, cat6, with_v6 = V.v6_engine()
    v6 = Scorer(cat6, strength=False)
    v7 = Scorer(cat, strength=True)
    est = fit_estimate(cat, eng)
    variants = {"v7 (지금)": v7, "P1 추정치 보정": Scorer(cat, True, estimate=est),
                "P2 유무 정규화": Scorer(cat, True, shift=True), "P3 = P1 + P2": Scorer(cat, True, estimate=est, shift=True)}
    cat_m = alt_catalog()
    assert [p["key"] for p in cat_m["perfumes"]] == [p["key"] for p in cat["perfumes"]]
    m1 = Scorer(cat_m, True)
    variants["M1 연결표 수정"] = m1
    variants["M1 + P1"] = Scorer(cat_m, True, estimate=fit_estimate(cat_m, m1.eng))
    v6.data = v7.has
    for s in (v6, *variants.values()):
        install_group_mask(s)

    checks = V.V6_COMPARE_COMBOS[:3]
    check_same(v7, lambda sp: eng.scores(V.blend(sp)), checks)
    check_same(v6, lambda sp: with_v6(lambda: eng6.scores(V.blend(sp))), checks)
    print("참조 엔진과 일치", flush=True)

    lists = list(V.BLENDS) + list(V.V6_COMPARE_COMBOS)
    qs = SV.build_queries(cat, eng)
    print("쿼리", len(qs), flush=True)

    doc = ["# 엔진 v8 후보 측정", "",
           f"> 카탈로그 `{cat['version']}` · 기준 엔진 v{cat['engineVersion']} · 2026-11-04 · `py pipeline/experiments/engine_v8_trial.py`로 만든 문서. **엔진은 바꾸지 않았습니다.**",
           ""]
    doc += grade_section(v7, lists, qs)
    doc += bias_section(variants, lists, qs)
    doc += brand_section(v6, variants)
    doc += fresh_section(v6, v7, m1, qs)
    doc += data_section(v7)
    OUT.write_text("\n".join(doc) + "\n", encoding="utf-8")
    print("저장", OUT, flush=True)


if __name__ == "__main__":
    main()
