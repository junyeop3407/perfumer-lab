"""카탈로그를 만드는 규칙과 표 (docs/design.md 5~8절).

Python 기본 기능만 쓴다. 이 파일의 규칙은 TypeScript 엔진(`app/src/engine`)과 똑같아야 한다:
  - 향수 어코드 벡터: 목록 순서대로 5·4·3·2·1, 합 100 (rank_weights)
"""
from __future__ import annotations

import gzip
import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

# 6절: 계열 10개 (휠 순서, 원형)
FAMILIES = [
    ("citrus", "시트러스"),
    ("fruity", "프루티"),
    ("floral", "플로럴"),
    ("soft", "머스크·파우더리"),
    ("sweet", "스위트·구르망"),
    ("spicy", "스파이시"),
    ("amber", "앰버·레진"),
    ("woody", "우디"),
    ("green", "그린·아로마틱"),
    ("fresh", "프레시·아쿠아틱"),
]

# 6절: Parfumo 어코드 21개 → (한글명, 계열). 키는 영문 소문자
ACCORDS = {
    "citrus": ("시트러스", "citrus"),
    "fruity": ("프루티", "fruity"),
    "floral": ("플로럴", "floral"),
    "powdery": ("파우더리", "soft"),
    "creamy": ("크리미", "soft"),
    "synthetic": ("신세틱", "soft"),
    "animal": ("애니멀릭", "soft"),
    "sweet": ("스위트", "sweet"),
    "gourmand": ("구르망", "sweet"),
    "spicy": ("스파이시", "spicy"),
    "oriental": ("오리엔탈", "amber"),
    "resinous": ("레진", "amber"),
    "woody": ("우디", "woody"),
    "smoky": ("스모키", "woody"),
    "leathery": ("레더", "woody"),
    "earthy": ("어시", "woody"),
    "chypre": ("시프레", "woody"),
    "green": ("그린", "green"),
    "fougère": ("푸제르", "green"),
    "fresh": ("프레시", "fresh"),
    "aquatic": ("아쿠아틱", "fresh"),
}
ACCORD_KEYS = list(ACCORDS)  # 벡터 차원 순서 (카탈로그 파일에도 이 순서로 적는다)

# 데이터에 잘못 들어간 어코드 값 (5절)
JUNK_ACCORDS = {"main accords"}

RANK_WEIGHTS = [5, 4, 3, 2, 1]


@dataclass(frozen=True)
class Params:
    """파이프라인 기준값. 실제 데이터용(REAL)과 가짜 테스트 데이터용(FIXTURE)."""
    min_votes: int            # 후보 최소 평가 수 (7절 3번)
    browse_min_df: int        # 목록에 보이는 노트 (7절 8번)
    search_min_df: int        # 검색으로만 고를 수 있는 노트
    pairing_min_co: int       # 궁합 노트: 함께 나온 최소 횟수 (7절 9번)
    min_profile_df: int       # 이보다 적은 향수에 나오는 노트는 계열 평균 프로필을 쓴다 (8-2절, 2026-10-31)
    shrink_k: float = 20.0    # 노트 프로필 끌어당기기 K (8-2절)
    # "deconv" 함께 든 노트의 몫 빼기(2026-10-31, v5) / "ppmi" 특징 강조 m·max(0, ln(m/g)) / "diff" 평균 초과분 (8-2절)
    profile_method: str = "deconv"
    deconv_iterations: int = 400


REAL = Params(min_votes=10, browse_min_df=30, search_min_df=5, pairing_min_co=20, min_profile_df=20)
FIXTURE = Params(min_votes=10, browse_min_df=3, search_min_df=2, pairing_min_co=2, min_profile_df=3)

NA = {"", "NA", "N/A", "nan"}


def is_na(value: str | None) -> bool:
    return value is None or value.strip() in NA


def split_list(value: str | None) -> list[str]:
    if is_na(value):
        return []
    return [x.strip() for x in value.split(",") if x.strip()]


def note_key(name: str) -> str:
    """노트 이름 → 바뀌지 않는 key: 소문자, 악센트 제거, 공백 하나로."""
    s = re.sub(r"\s+", " ", name.strip().lower())
    folded = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    folded = re.sub(r"\s+", " ", folded).strip()
    return folded or s  # 라틴 문자가 아닌 이름은 그대로


def perfume_key(url: str) -> str:
    """향수 key: 상품 페이지 주소에서 "/Perfumes/" 뒤 부분 (바뀌지 않고 겹치지 않음).
    예: https://www.parfumo.com/Perfumes/Fake_Maison/123-sample-scent → Fake_Maison:123-sample-scent
    가짜 데이터(https://example.invalid/T001)는 마지막 부분(T001)."""
    path = url.strip().rstrip("/")
    marker = "/Perfumes/"
    key = path.split(marker, 1)[1] if marker in path else path.rsplit("/", 1)[-1]
    return key.replace("/", ":")  # 서버 주소(/favorites/{key})에 넣을 수 있게


def accord_key(name: str) -> str:
    return name.strip().lower()


_CONCENTRATION_WORDS = re.compile(
    r"\b(eau de parfum|eau de toilette|eau de cologne|edp|edt|edc|extrait de parfum|extrait|parfum)\b"
)


def restore_name(number: str | None, name: str) -> str:
    """원본 CSV는 이름 맨 앞 숫자를 Number 칸으로 떼어 놓았다(2026-11-04). 숫자로 시작할 때만 다시 붙인다."""
    number, name = (number or "").strip(), name.strip()
    if not number[:1].isdigit():
        return name
    glue = "" if number.endswith(".") or re.match(r"[^\w\s]", name) else " "
    return f"{number}{glue}{name}"


def split_lead_number(text: str, brand: str) -> tuple[str, str]:
    """이름 맨 앞 번호(1962 등)를 떼어 낸다. 연도 지우기에서 보호하려는 것. 브랜드가 그 숫자로 시작하면 번호가 아니다."""
    m = re.match(r"\s*\d+\b", text)
    return ("", text) if not m or text.strip().startswith(brand) else (m.group().strip(), text[m.end():])


def dedupe_name(brand: str, name: str) -> tuple[str, str]:
    """같은 향수의 농도만 다른 것을 묶는 key (7절 4번)."""
    n = _CONCENTRATION_WORDS.sub("", name.lower())
    n = re.sub(r"[^\w]+", " ", n).strip()
    return brand.strip().lower(), n


_GROUP_WORDS = re.compile(
    r"\b(eau de parfum intense|extrait de parfum|eau de parfum|eau de toilette|eau de cologne|"
    r"perfume oil|body spray|fragrance mist|after shave lotion|after shave|"
    r"extrait|parfum|perfume|profumo|cologne|edp|edt|edc)\b"
)


# 같은 이름 묶음 안에서 노트가 이만큼 이상 겹쳐야 같은 향수(농도만 다른 버전)로 본다 (자카드, 2026-10-31)
GROUP_MIN_NOTE_OVERLAP = 0.5


# 향수 이름·향수별 값이 든 실험 문서·고정본은 공개 저장소에 두지 않는다(plan_b_portfolio.md 1-4, 2026-11-11).
# 커밋하지 않는 이 폴더에 쓰고 읽는다. 다른 컴퓨터에서는 비공개 저장소 pipeline-data-out/private_docs/에서 받는다.
PRIVATE_DOCS = Path(__file__).resolve().parent / "data" / "out" / "private_docs"


def private_doc(name: str) -> Path:
    """비공개 문서 경로(쓸 때 폴더를 만든다)"""
    PRIVATE_DOCS.mkdir(parents=True, exist_ok=True)
    return PRIVATE_DOCS / name


def load_old_catalog(path) -> dict:
    """v7까지의 카탈로그를 전제한 옛 스크립트용. v8은 모든 향수에 강도 값(추정치 포함)이 있어 v7처럼 읽으면 측정이 틀어지므로 멈춘다."""
    with gzip.open(path, "rt", encoding="utf-8") as f:
        cat = json.load(f)
    if cat.get("engineVersion", 0) >= 8:
        raise SystemExit(f"경고: {path}는 엔진 v{cat['engineVersion']} 카탈로그입니다. 이 스크립트는 v7까지를 전제합니다. "
                         "v7 기준으로 재려면 pipeline/data/out/catalog_v7_compare.json.gz를 읽도록 바꾸세요.")
    return cat


def hold_list_keys(candidates, ratings: dict[str, int], always, top: int = 10) -> list[str]:
    """보류 목록: candidates 중 평가 수 상위 top개 + 항상 남길 행(always). 다시 만들어도 always는 사라지지 않는다."""
    order = lambda k: (-ratings[k], k)  # noqa: E731
    head = sorted(candidates, key=order)[:top]
    return head + sorted(set(always) - set(head), key=order)


def same_notes(a: set[str], b: set[str]) -> bool:
    """노트 겹침(자카드)이 기준 이상이면 같은 향수로 본다. 합치기와 묶음 나누기가 같은 기준을 쓴다."""
    return bool(a | b) and len(a & b) / len(a | b) >= GROUP_MIN_NOTE_OVERLAP


def split_groups_by_notes(items: list[tuple[str, str, set[str], int]]) -> dict[str, str]:
    """(향수 key, 이름 묶음 group_key, 노트 집합, 평가 수) → {향수 key: 최종 묶음}.
    이름에서 연도를 지우므로 "Joop! Homme 1989"와 노트가 전혀 다른 "Joop! Homme 2021", 해마다 다른
    "CK One Summer 2004/2006"이 한 묶음이 되던 문제(2026-10-31)를 막는다. 이름 묶음 안에서 평가 많은 순으로,
    앞 묶음의 대표(가장 평가 많은 향수)와 노트가 GROUP_MIN_NOTE_OVERLAP 이상 겹치면 그 묶음에, 아니면 새 묶음.
    첫 묶음은 이름 묶음 값 그대로, 다음 묶음은 "#2", "#3"을 붙인다."""
    by_base: dict[str, list[tuple[str, set[str], int]]] = {}
    for key, base, notes, votes in items:
        by_base.setdefault(base, []).append((key, notes, votes))
    out: dict[str, str] = {}
    for base, members in by_base.items():
        clusters: list[tuple[str, set[str]]] = []  # (묶음 값, 대표 노트)
        for key, notes, _ in sorted(members, key=lambda m: (-m[2], m[0])):
            for group, rep in clusters:
                if same_notes(rep, notes):
                    out[key] = group
                    break
            else:
                group = base if not clusters else f"{base}#{len(clusters) + 1}"
                clusters.append((group, notes))
                out[key] = group
    return out


def group_key(brand: str, name: str, concentration: str | None) -> str:
    """추천에서 하나로 묶는 key: 이름에서 브랜드·연도·농도를 뺀 것 (design.md 8-5절).
    예: "Mediterraneo Carthusia 2018 Eau de Parfum" → "carthusia|mediterraneo"
    Intense·Sport처럼 이름에 남는 말은 다른 향수로 본다."""
    b = brand.strip().lower()
    lead, n = split_lead_number(name.lower(), b) if b else ("", name.lower())
    if b:
        n = n.replace(b, " ")
    if concentration:
        n = n.replace(concentration.strip().lower(), " ")
    n = re.sub(r"\b(19|20)\d{2}\b", " ", n)
    n = _GROUP_WORDS.sub(" ", n)
    n = f'{lead} {re.sub(r"[^\w]+", " ", n).strip()}'.strip()
    return f"{b}|{n or name.strip().lower()}"


def rank_weights(accords: list[str]) -> dict[str, float]:
    """향수 어코드 벡터 a_p: 목록 순서대로 5·4·3·2·1, 합 100 (8-1절)."""
    w = RANK_WEIGHTS[: len(accords)]
    total = sum(w)
    return {a: 100.0 * x / total for a, x in zip(accords, w)}
