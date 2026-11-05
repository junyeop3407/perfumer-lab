"""10단계 등급 기준값 부품 (규칙: docs/engine_grade_v8_rules.md). 기준값 계산과 검증 판정."""
from __future__ import annotations

import numpy as np

GRADES = ("매우 비슷", "비슷", "참고")
MIN_COUNT = 5  # 이보다 적은 등급은 순서 판정에서 뺀다
VERY_MIN_MEAN = 4.0


def thresholds(pool: list[float]) -> tuple[float, float]:
    """Top 10 유사도 모음의 상위 10%, 상위 40% 값. 소수 셋째 자리."""
    return round(float(np.percentile(pool, 90)), 3), round(float(np.percentile(pool, 60)), 3)


def grade_of(similarity: float, cut: tuple[float, float]) -> str:
    return GRADES[0] if similarity >= cut[0] else GRADES[1] if similarity >= cut[1] else GRADES[2]


def verdict(scores_by_grade: dict[str, list[float]]) -> tuple[bool, str]:
    """(통과 여부, 이유). 향수 5개 미만인 등급은 순서에서 빼고, 판정할 등급이 2개 미만이거나 매우 비슷이 5개 미만이면 통과가 아니다."""
    means = {g: float(np.mean(scores_by_grade[g])) for g in GRADES if len(scores_by_grade.get(g, [])) >= MIN_COUNT}
    if len(means) < 2:
        return False, f"판정할 등급이 {len(means)}개라 순서를 볼 수 없음"
    if GRADES[0] not in means:
        return False, "매우 비슷이 5개 미만이라 평균 4.0 조건을 판정할 수 없음"
    ordered = [means[g] for g in GRADES if g in means]
    if not all(a > b for a, b in zip(ordered, ordered[1:])):
        return False, f"순서 조건 실패: {means}"
    if means[GRADES[0]] < VERY_MIN_MEAN:
        return False, f"매우 비슷 평균 {means[GRADES[0]]:.2f}가 4.0 미만"
    return True, f"통과: {means}"
