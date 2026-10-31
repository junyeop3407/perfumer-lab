"""Python 참조 엔진 (docs/design.md 8절). TypeScript 엔진(`app/src/engine`)은 이 결과와 똑같아야 한다(골든 테스트).

입력은 catalog.json(.gz)을 읽은 dict. Python 기본 기능만 쓴다.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

# 8-8절: 가중치는 이 한 곳에만 둔다. 바꾸면 TypeScript `app/src/engine/params.ts`·골든 파일도 함께 바꾸고 ENGINE_VERSION을 올린다
W_ACCORD, W_NOTE, W_PREF = 0.35, 0.55, 0.1  # 2026-10-31 사장 결정 v4: 45/45 → 35/55 (재현 실패 = 노트는 다 맞는데 어코드가 달라 밀림, docs/engine_weights.md)
C_SAME_LAYER, C_OTHER_LAYER, C_SAME_FAMILY = 1.0, 0.5, 0.3
LAYER_WEIGHT = {"top": 1.0, "middle": 1.0, "base": 1.0}
BRAND_CAP = 2
# 2026-10-31 v3: "그 향수의 노트를 그대로 넣었을 때 그 향수 자신의 점수" 중앙값·하위 25% (docs/engine_variants.md)
GRADE_VERY_SIMILAR, GRADE_SIMILAR = 0.87, 0.79
SCORE_QUANTUM = 1e9  # 정렬은 round(score × 1e9)로 (차이 1e-9 이하는 같은 점수)
RANK_WEIGHTS = [5, 4, 3, 2, 1]
LAYERS = ("top", "middle", "base")
# 약 · 중 · 강. 2026-10-31 사장 결정: 강 1.5 → 2.0 (v3) → 3.0 (v6, 어코드 쪽에만. docs/engine_strong_both.md)
INTENSITIES = (0.5, 1.0, 3.0)
STRENGTH_NAMES = ("weak", "medium", "strong")
# 시험용 스위치: 노트 일치도에도 강도를 반영할지 (pipeline/experiments/strong_both.py). 엔진은 꺼진 상태(False)가 기준
NOTE_MATCH_BY_INTENSITY = False


def intensity(level: str | float) -> float:
    """강도 이름(weak/medium/strong) → 숫자. 숫자는 그대로 (잘못된 값 시험용)."""
    return INTENSITIES[STRENGTH_NAMES.index(level)] if isinstance(level, str) else level
MAX_PER_LAYER = 3


@dataclass(frozen=True)
class BlendNote:
    note_key: str
    layer: str
    intensity: float


class Engine:
    def __init__(self, catalog: dict):
        self.accords = [a["key"] for a in catalog["accords"]]
        self.accord_family = {a["key"]: a["family"] for a in catalog["accords"]}
        self.wheel = {f["code"]: f["wheelOrder"] for f in catalog["families"]}
        self.notes = {n["key"]: n for n in catalog["notes"]}
        self.perfumes = catalog["perfumes"]
        self.n = len(self.perfumes)
        self.vec = {p["key"]: self.perfume_vector(p["accords"]) for p in self.perfumes}

    # 8-1절
    def perfume_vector(self, accords: list[str]) -> list[float]:
        w = RANK_WEIGHTS[: len(accords)]
        total = sum(w)
        v = [0.0] * len(self.accords)
        for a, x in zip(accords, w):
            v[self.accords.index(a)] = 100.0 * x / total
        return v

    def validate(self, blend: list[BlendNote]) -> None:
        if not blend:
            raise ValueError("EMPTY_BLEND")
        for layer in LAYERS:
            if sum(1 for b in blend if b.layer == layer) > MAX_PER_LAYER:
                raise ValueError("LAYER_LIMIT")
        if len({b.note_key for b in blend}) != len(blend):
            raise ValueError("DUPLICATE_NOTE")  # 같은 노트는 한 조합에 한 번만
        for b in blend:
            if b.layer not in LAYERS or b.intensity not in INTENSITIES:
                raise ValueError("BAD_BLEND_NOTE")
            note = self.notes.get(b.note_key)
            if note is None or note["tier"] == "hidden":
                raise ValueError("NOTE_NOT_FOUND")

    # 8-3절
    def blend_vector(self, blend: list[BlendNote]) -> list[float]:
        b = [0.0] * len(self.accords)
        for x in blend:
            prof = self.notes[x.note_key]["profile"]
            s = x.intensity * LAYER_WEIGHT[x.layer]
            for i, v in enumerate(prof):
                b[i] += s * v
        return b

    def display_ratios(self, blend: list[BlendNote]) -> list[tuple[str, int]]:
        """화면용 비율: 반올림해서 1% 이상인 어코드만, 합이 100이 되게 가장 큰 항목에서 맞춤. 큰 순서."""
        b = self.blend_vector(blend)
        total = sum(b)
        if total == 0:
            return []
        pct = [(self.accords[i], 100.0 * v / total) for i, v in enumerate(b)]
        shown = [(a, round_half_up(p)) for a, p in pct if round_half_up(p) >= 1]
        shown.sort(key=lambda t: (-t[1], self.accords.index(t[0])))
        diff = 100 - sum(p for _, p in shown)
        if shown and diff:
            a, p = shown[0]
            shown[0] = (a, p + diff)
        return shown

    # 8-7절
    def balance_hints(self, blend: list[BlendNote]) -> list[str]:
        hints = []
        layers = {x.layer for x in blend}
        hints += [f"EMPTY_LAYER:{layer}" for layer in LAYERS if layer not in layers]
        fams = {self.notes[x.note_key]["family"] for x in blend}
        if len(blend) >= 2 and len(fams) == 1:
            hints.append("ONE_FAMILY")
        b = self.blend_vector(blend)
        if sum(b) > 0 and max(b) / sum(b) >= 0.6:
            hints.append("DOMINANT")
        if len(fams) == 2:
            x, y = sorted(fams)
            if self.wheel_distance(x, y) >= 4:
                hints.append("FAR_FAMILIES")
        return hints

    def wheel_distance(self, x: str, y: str) -> int:
        d = abs(self.wheel[x] - self.wheel[y])
        return min(d, len(self.wheel) - d)

    # 8-4~8-6절
    def recommend(self, blend: list[BlendNote], disliked: set[str] = frozenset(),
                  preferred: set[str] = frozenset(), k: int = 10, note_only: bool = False) -> list[dict]:
        """note_only=True 는 엔진 검증(블라인드 비교)용: 노트 일치도만으로 순위를 매긴다."""
        self.validate(blend)
        b = self.blend_vector(blend)
        b_total = sum(b)
        # 시험용(기본 꺼짐, 2026-10-31): 켜면 노트 일치도에서도 고른 노트를 강도(약/중/강)로 가중한다
        weights = {x.note_key: math.log(1 + self.n / self.notes[x.note_key]["df"]) * (x.intensity if NOTE_MATCH_BY_INTENSITY else 1.0)
                   for x in blend}
        w_sum = sum(weights.values())

        scored = []
        for p in self.perfumes:
            layers = {layer: set(p[layer]) for layer in LAYERS}
            all_notes = layers["top"] | layers["middle"] | layers["base"]
            if all_notes & disliked:
                continue
            a = self.vec[p["key"]]
            accord_sim = cosine(a, b)
            fams = {self.notes[k]["family"] for k in all_notes}
            matched, nm = [], 0.0
            for x in blend:
                if x.note_key in layers[x.layer]:
                    c, kind = C_SAME_LAYER, "same_layer"
                elif x.note_key in all_notes:
                    c, kind = C_OTHER_LAYER, "other_layer"
                elif self.notes[x.note_key]["family"] in fams:
                    c, kind = C_SAME_FAMILY, "same_family"
                else:
                    c, kind = 0.0, None
                nm += weights[x.note_key] * c
                if kind:
                    matched.append({"noteKey": x.note_key, "layer": x.layer, "match": kind})
            note_match = nm / w_sum
            if note_only:
                score = note_match
            elif preferred:
                pref = sum(v for i, v in enumerate(a) if self.accord_family[self.accords[i]] in preferred) / 100.0
                score = W_ACCORD * accord_sim + W_NOTE * note_match + W_PREF * pref
            else:
                score = (W_ACCORD * accord_sim + W_NOTE * note_match) / (W_ACCORD + W_NOTE)
            scored.append((p, score, accord_sim, note_match, matched, all_notes))

        scored.sort(key=lambda t: (-round_half_up(t[1] * SCORE_QUANTUM), -t[0]["ratingCount"], t[0]["key"]))

        out, per_brand, groups = [], {}, set()
        for p, score, accord_sim, note_match, matched, all_notes in scored:
            if p["group"] in groups:  # 농도만 다른 같은 향수는 점수가 가장 높은 하나만
                continue
            if per_brand.get(p["brand"], 0) >= BRAND_CAP:
                continue
            groups.add(p["group"])
            per_brand[p["brand"]] = per_brand.get(p["brand"], 0) + 1
            a = self.vec[p["key"]]
            shared = sorted(
                ((self.accords[i], a[i], 100.0 * b[i] / b_total) for i in range(len(self.accords))),
                key=lambda t: (-min(t[1], t[2]), self.accords.index(t[0])),
            )[:3]
            blend_keys = {x.note_key for x in blend}
            missing = sorted(all_notes - blend_keys, key=lambda k: (-self.notes[k]["df"], k))[:3]
            out.append({
                "perfumeKey": p["key"],
                "score": score,
                "accordSim": accord_sim,
                "noteMatch": note_match,
                "grade": grade(score),
                "sharedAccords": [{"accord": s[0], "perfumePct": s[1], "blendPct": s[2]} for s in shared],
                "matchedNotes": matched,
                "missingNotes": missing,
            })
            if len(out) == k:
                break
        return out


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return 0.0 if na == 0 or nb == 0 else dot / (na * nb)


def grade(score: float) -> str:
    if score >= GRADE_VERY_SIMILAR:
        return "VERY_SIMILAR"
    if score >= GRADE_SIMILAR:
        return "SIMILAR"
    return "REFERENCE"


def round_half_up(x: float) -> int:
    """Java Math.round·TypeScript roundHalfUp과 같은 반올림 (Python round는 짝수 쪽으로 반올림해서 다름)."""
    return math.floor(x + 0.5)
