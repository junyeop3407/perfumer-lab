"""공개 빌드에 실제 브랜드·향수 이름이 있으면 실패 — plan_stage5.md 1-4.

실제 이름 목록은 공개 저장소에 두지 않는다(사장 조건). 비공개 카탈로그에서 만든 **해시 목록**만 쓴다.
- 이름을 소문자·악센트 제거·기호를 띄어쓰기로 바꿔 정리한 뒤, 비밀 키 SCENT_KEY_SECRET을 섞은 HMAC-SHA256으로 바꾼다
  (정리 목록 filtered_keys.csv와 같은 키). 목록 파일 pipeline/data/out/real_name_hashes.txt는 커밋하지 않는다.
- 검사: 파일 안 글자(JSON이면 모든 글자 값, 아니면 파일 전체)를 같은 방식으로 정리해
    · 1~6단어 묶음이 실제 **브랜드** 해시와 겹치거나
    · 2~6단어 묶음, 또는 글자 값 전체가 실제 **향수 이름** 해시와 겹치면 실패.
  한 단어짜리 향수 이름은 글자 값 전체가 같을 때만 본다(문장 속 흔한 낱말과 헷갈리지 않게).
- 노트·어코드·계열·농도 같은 일반 낱말과 똑같은 이름은 목록에서 뺀다(예: 이름이 "Rose"인 향수 → 노트 rose와 같아 구별 불가).
- 키나 목록이 없으면 '건너뜀'. 목록이 없어도 비공개 카탈로그와 키가 있으면 그 자리에서 목록을 만든다.
  공개 빌드 전 최종 검사는 --require(건너뛰면 실패)로 키가 있는 컴퓨터에서 돌린다.
- 결과에는 이름 글자를 출력하지 않는다(어디서 몇 곳인지만).

    py pipeline/real_names.py hashes [--private …catalog.json.gz] [--out …real_name_hashes.txt]
    py pipeline/real_names.py check <파일> [--hashes …] [--private …] [--require]
    종료 코드: 0 통과·건너뜀 / 1 실제 이름 있음 / 2 검사를 돌리지 못함(키·목록 불일치, 파일 깨짐) / 3 --require인데 건너뜀
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import hmac
import json
import os
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

PIPELINE = Path(__file__).resolve().parent
sys.path.insert(0, str(PIPELINE))

import catalog_rules as R  # noqa: E402

SECRET_ENV = "SCENT_KEY_SECRET"
DEFAULT_HASHES = PIPELINE / "data" / "out" / "real_name_hashes.txt"
DEFAULT_PRIVATE = PIPELINE / "data" / "out" / "catalog.json.gz"
MAX_WORDS = 6
CHECK_TAG = "perfumer-lab:real-names:v1"
GENERIC = {"eau de toilette", "eau de parfum", "parfum", "extrait de parfum", "eau de cologne", "cologne", "perfume",
           "edt", "edp", "edc", "extrait", "intense", "sample", "demo", "test"}


def normalize(text: str) -> str:
    t = unicodedata.normalize("NFKD", text)
    t = "".join(ch for ch in t if not unicodedata.combining(ch)).lower()
    return " ".join(re.sub(r"[\W_]+", " ", t).split())


def _secret() -> str | None:
    return os.environ.get(SECRET_ENV) or None


def _h(secret: str, text: str) -> str:
    return hmac.new(secret.encode("utf-8"), text.encode("utf-8"), hashlib.sha256).hexdigest()


def _load(path: Path) -> bytes:
    data = path.read_bytes()
    return gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data


class ToolError(SystemExit):
    """검사를 돌리지 못함(종료 코드 2). '실제 이름 있음'(1)과 구별한다(리뷰 5-1 QA 중간-1)"""

    def __init__(self, message: str):
        super().__init__(2)
        self.message = message


def _source_tag(secret: str, private: Path) -> str:
    """목록을 만든 비공개 카탈로그 파일(바이트)의 표시. 카탈로그가 바뀌면 목록을 다시 만든다(리뷰 5-1 QA 중간-2)"""
    return _h(secret, "source:" + hashlib.sha256(private.read_bytes()).hexdigest())


def make_hashes(private: Path, out: Path) -> int:
    """비공개 카탈로그 → 해시 목록 파일. 이름 글자는 파일에 남기지 않는다. 돌려주는 값: 줄 수
    b = 브랜드, n = 향수 이름, f = 정리 후 2자 이하인 브랜드·이름(그 칸 전체가 같을 때만 본다, 리뷰 5-1 QA 중간-3)"""
    secret = _secret()
    if not secret:
        raise ToolError(f"환경 변수 {SECRET_ENV}가 없습니다")
    try:
        cat = json.loads(_load(private))
        perfumes = cat["perfumes"]
    except (OSError, ValueError, KeyError, EOFError) as e:
        raise ToolError(f"비공개 카탈로그를 읽지 못했습니다({type(e).__name__})") from None
    generic = {normalize(a) for a in R.ACCORD_KEYS} | {normalize(c) for c, _ in R.FAMILIES}
    for phrase in GENERIC:  # 농도 표기의 부분 묶음도 일반 문구다(예: "eau de")
        w = phrase.split()
        generic |= {" ".join(w[i:j]) for i in range(len(w)) for j in range(i + 1, len(w) + 1)}
    for n in cat.get("notes", []):
        generic |= {normalize(n.get("key", "")), normalize(n.get("nameEn", ""))}
    brands, names, short = set(), set(), set()
    for p in perfumes:
        for text, bucket in ((normalize(p.get("brand") or ""), brands), (normalize(p.get("name") or ""), names)):
            if not text or text in generic:
                continue
            (short if len(text) <= 2 else bucket).add(text)
    lines = sorted({f"b {_h(secret, b)}" for b in brands} | {f"n {_h(secret, n)}" for n in names}
                   | {f"f {_h(secret, t)}" for t in short})
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_name(out.name + ".tmp")
    tmp.write_text(f"# check {_h(secret, CHECK_TAG)}\n# source {_source_tag(secret, private)}\n" + "".join(l + "\n" for l in lines), encoding="utf-8", newline="\n")
    tmp.replace(out)
    return len(lines)


def _read_hashes(path: Path, secret: str) -> tuple[set[str], set[str], set[str], str]:
    rows = path.read_text(encoding="utf-8").splitlines()
    if len(rows) < 2 or rows[0] != f"# check {_h(secret, CHECK_TAG)}" or not rows[1].startswith("# source "):
        raise ToolError(f"{path.name}가 지금 {SECRET_ENV}로 만든 목록이 아닙니다. `hashes`로 다시 만들어 주세요")
    sets: dict[str, set[str]] = {"b": set(), "n": set(), "f": set()}
    for r in rows[2:]:
        kind, _, h = r.partition(" ")
        if kind not in sets or len(h) != 64:
            raise ToolError(f"{path.name} 모양이 틀립니다. `hashes`로 다시 만들어 주세요")
        sets[kind].add(h)
    return sets["b"], sets["n"], sets["f"], rows[1].removeprefix("# source ")


NAME_FIELDS = {"brand", "name"}  # 한 단어 향수 이름은 이 칸 값 전체가 같을 때만 본다(흔한 낱말과 헷갈리지 않게)


@dataclass
class Result:
    status: str  # pass | fail | skipped
    hits: int = 0
    brand_hits: int = 0
    name_hits: int = 0
    places: list[str] = field(default_factory=list)  # 어디서(JSON 위치), 걸린 이름 글자는 담지 않는다
    reason: str = ""


class _Checker:
    def __init__(self, secret: str, brands: set[str], names: set[str], short: set[str]):
        self.secret, self.brands, self.names, self.short = secret, brands, names, short
        self.res = Result("pass")

    def hit(self, text: str, name_field: bool) -> tuple[bool, bool]:
        words = normalize(text).split()
        b_hit = n_hit = False
        if name_field and words:
            whole = _h(self.secret, " ".join(words))
            n_hit = whole in self.names or whole in self.short
        for size in range(1, MAX_WORDS + 1):
            for i in range(len(words) - size + 1):
                h = _h(self.secret, " ".join(words[i:i + size]))
                b_hit |= h in self.brands
                n_hit |= size >= 2 and h in self.names
        return b_hit, n_hit

    def record(self, where: str, b_hit: bool, n_hit: bool) -> bool:
        if b_hit or n_hit:
            self.res.hits += 1
            self.res.brand_hits += b_hit
            self.res.name_hits += n_hit
            if len(self.res.places) < 20:
                self.res.places.append(where)
        return b_hit or n_hit

    def walk(self, value, where: str = "$", key: str | None = None) -> None:
        if isinstance(value, str):
            self.record(where, *self.hit(value, key in NAME_FIELDS))
        elif isinstance(value, dict):
            for i, (k, v) in enumerate(value.items()):
                k = str(k)
                # 칸 이름도 글자다. 걸린 칸 이름은 위치에도 쓰지 않는다
                label = f"<칸 {i}>" if self.record(f"{where}.<칸 {i}>", *self.hit(k, False)) else k
                self.walk(v, f"{where}.{label}", k)
        elif isinstance(value, list):
            for i, v in enumerate(value):
                self.walk(v, f"{where}[{i}]", key)


def check(file: Path, hashes: Path = DEFAULT_HASHES, private: Path | None = None) -> Result:
    # 비공개 카탈로그를 따로 정하지 않으면 기본 목록일 때만 기본 비공개 카탈로그와 짝짓는다(남의 목록을 실제 카탈로그로 덮지 않게)
    if private is None and Path(hashes).resolve() == DEFAULT_HASHES.resolve():
        private = DEFAULT_PRIVATE
    have_private = private is not None and private.exists()
    secret = _secret()
    if not secret:
        return Result("skipped", reason=f"{SECRET_ENV} 없음")
    if not hashes.exists():
        if not have_private:
            return Result("skipped", reason="해시 목록과 비공개 카탈로그 없음")
        make_hashes(private, hashes)
    brands, names, short, source = _read_hashes(hashes, secret)
    if have_private and source != _source_tag(secret, private):  # 비공개 카탈로그가 바뀜 → 다시 만든다
        make_hashes(private, hashes)
        brands, names, short, source = _read_hashes(hashes, secret)
    c = _Checker(secret, brands, names, short)
    try:
        raw = _load(file)
    except (OSError, EOFError) as e:
        raise ToolError(f"검사할 파일을 읽지 못했습니다({type(e).__name__})") from None
    try:
        c.walk(json.loads(raw))
    except (json.JSONDecodeError, UnicodeDecodeError):
        c.record("파일 전체", *c.hit(raw.decode("utf-8", errors="ignore"), False))
    if c.res.hits:
        c.res.status = "fail"
    return c.res


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="공개 빌드의 실제 이름 검사 (해시 목록만 사용)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    h = sub.add_parser("hashes")
    h.add_argument("--private", type=Path, default=DEFAULT_PRIVATE)
    h.add_argument("--out", type=Path, default=DEFAULT_HASHES)
    c = sub.add_parser("check")
    c.add_argument("file", type=Path)
    c.add_argument("--hashes", type=Path, default=DEFAULT_HASHES)
    c.add_argument("--private", type=Path, default=None)
    c.add_argument("--require", action="store_true", help="건너뛰면 실패(공개 빌드 최종 검사)")
    a = ap.parse_args(argv)
    try:
        return _run(a)
    except ToolError as e:
        print(f"실제 이름 검사: 돌리지 못함: {e.message}")
        return 2


def _run(a) -> int:
    if a.cmd == "hashes":
        print(f"해시 목록 {make_hashes(a.private, a.out)}줄을 만들었습니다(이름 글자 없음, 커밋 안 함)")
        return 0
    r = check(a.file, a.hashes, a.private)
    if r.status == "skipped":
        print(f"실제 이름 검사: 건너뜀({r.reason})")
        return 3 if a.require else 0
    if r.status == "fail":
        print(f"실제 이름 검사: 실패: 실제 이름으로 보이는 글자 {r.hits}곳(브랜드 {r.brand_hits}, 향수 이름 {r.name_hits})")
        for p in r.places:
            print(f"  위치: {p}")
        return 1
    print("실제 이름 검사: 통과(실제 브랜드·향수 이름 0건)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
