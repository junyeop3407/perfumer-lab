"""P1 추정치 보정 (엔진 v8, 2026-11-05. docs/plan_v8.md, docs/engine_v8_final.md).

강도 데이터가 없는 향수는 예전처럼 순위 비중과 같은 몫으로 계산하면 점수에서 손해를 본다.
강도가 있는 향수의 개발 절반에서 배운 값으로 채우고 estimatedAccords·estimatedNotes로 표시한다. 앱 계산은 그대로다.
- 어코드: 어코드 목록(순위 × 이름) → 전체 어코드 비중을 능형 회귀로 예측
- 노트: 노트마다 '평균보다 얼마나 강한가'를 배워, 그 향수의 노트끼리 나눈다
"""
from __future__ import annotations

import hashlib

import numpy as np

RIDGE = 1.0
PRIOR_WEIGHT = 20.0  # 데이터 적은 노트를 평균(1)으로 끌어당기는 힘


def is_dev(key: str) -> bool:
    return int(hashlib.sha1(key.encode()).hexdigest(), 16) % 2 == 0


def _notes(p: dict) -> set[str]:
    return {*p["top"], *p["middle"], *p["base"]}


def fit(cat: dict) -> tuple[np.ndarray, dict[str, float]]:
    """(향수마다 예측한 어코드 비중, 노트별 강도 계수). 실제 강도만 배운다."""
    ps = cat["perfumes"]
    accords = [a["key"] for a in cat["accords"]]
    idx = {a: i for i, a in enumerate(accords)}

    def features(p: dict) -> np.ndarray:
        x = np.zeros(5 * len(accords) + 1)
        x[-1] = 1.0
        for r, a in enumerate(p["accords"][:5]):
            x[r * len(accords) + idx[a]] = 1.0
        return x

    train = [p for p in ps if p.get("accordShares") and not p.get("estimatedAccords") and is_dev(p["key"])]
    X = np.array([features(p) for p in train]).reshape(len(train), 5 * len(accords) + 1)
    Y = np.array([[p["accordShares"].get(a, 0.0) for a in accords] for p in train]).reshape(len(train), len(accords))
    coef = np.linalg.solve(X.T @ X + RIDGE * np.eye(X.shape[1]), X.T @ Y)
    pred = np.clip(np.array([features(p) for p in ps]) @ coef, 0.0, None)
    pred = 100.0 * pred / np.maximum(pred.sum(axis=1, keepdims=True), 1e-9)

    ratio: dict[str, list[float]] = {}
    for p in train:
        w = {} if p.get("estimatedNotes") else p.get("noteWeights") or {}
        for k, v in w.items():
            ratio.setdefault(k, []).append(v * len(w))
    prior = {k: (sum(v) + PRIOR_WEIGHT) / (len(v) + PRIOR_WEIGHT) for k, v in ratio.items()}
    return pred, prior


def apply(cat: dict) -> dict[str, int]:
    """강도가 없는 향수에 추정치를 넣고 표시한다. 실제 강도는 건드리지 않는다. 카탈로그와 같은 자릿수로 반올림한다."""
    ps = cat["perfumes"]
    counts = {"estimated_accords": 0, "estimated_notes": 0}
    if not any(p.get("accordShares") and is_dev(p["key"]) for p in ps):
        return counts
    pred, prior = fit(cat)
    accords = [a["key"] for a in cat["accords"]]
    for p, row in zip(ps, pred):
        if not p.get("accordShares"):
            p["accordShares"] = {a: v for a, v in sorted((a, round(float(x), 3)) for a, x in zip(accords, row)) if v > 0}
            p["estimatedAccords"] = True
            counts["estimated_accords"] += 1
        if not p.get("noteWeights"):
            notes = sorted(_notes(p))
            total = sum(prior.get(k, 1.0) for k in notes) or 1.0
            p["noteWeights"] = {k: round(prior.get(k, 1.0) / total, 5) for k in notes}
            p["estimatedNotes"] = True
            counts["estimated_notes"] += 1
    return counts
