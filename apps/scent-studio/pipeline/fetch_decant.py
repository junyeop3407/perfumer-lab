"""Decant 데이터(Fragrantica 어코드·노트 강도, 비슷한 향수 투표)를 받는다. 앱 폴더에서:  py pipeline/fetch_decant.py

받은 파일은 pipeline/data/raw/decant/ 에 두고 커밋하지 않는다.
라이선스: Kaggle 표기 CC BY-NC-SA 4.0 (비상업, 출처 표시, 동일 조건 공유). docs/design.md D4 참고.
"""
from __future__ import annotations

import datetime as dt
import gzip
import hashlib
import sys
import urllib.request
from pathlib import Path

BASE = "https://codeberg.org/decant/pages/raw/branch/pages/"
FILES = ("perfumes.db", "perfumes_cold.db")
RAW = Path(__file__).resolve().parent / "data" / "raw" / "decant"
LICENSE = "CC BY-NC-SA 4.0 (https://www.kaggle.com/datasets/ledecanteur/fragrantica-perfumes)"


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    RAW.mkdir(parents=True, exist_ok=True)
    lines = [f"fetched: {dt.datetime.now().isoformat(timespec='seconds')}", f"license: {LICENSE}"]
    for name in FILES:
        url = f"{BASE}{name}.gz"
        with urllib.request.urlopen(url, timeout=300) as r:
            packed = r.read()
        (RAW / name).write_bytes(gzip.decompress(packed))
        sha = hashlib.sha256(packed).hexdigest()
        lines.append(f"{url} sha256: {sha}")
        print(f"받음: {name} {len(packed) / 1e6:.1f}MB sha256={sha[:12]}…")
    (RAW / "SOURCE.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
