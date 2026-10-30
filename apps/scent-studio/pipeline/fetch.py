"""원본 Parfumo CSV를 받는다 (docs/design.md 7절 1번). 앱 폴더에서:  py pipeline/fetch.py

받은 파일은 pipeline/data/raw/ 에 두고 커밋하지 않는다(라이선스 표시가 없는 수집 데이터).
"""
from __future__ import annotations

import datetime as dt
import hashlib
import sys
import urllib.request
from pathlib import Path

URL = ("https://raw.githubusercontent.com/rfordatascience/tidytuesday/main/"
       "data/2024/2024-12-10/parfumo_data_clean.csv")
RAW = Path(__file__).resolve().parent / "data" / "raw"


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    RAW.mkdir(parents=True, exist_ok=True)
    out = RAW / "parfumo_data_clean.csv"
    with urllib.request.urlopen(URL, timeout=60) as r:
        data = r.read()
    out.write_bytes(data)
    sha = hashlib.sha256(data).hexdigest()
    (RAW / "SOURCE.txt").write_text(
        f"url: {URL}\nfetched: {dt.datetime.now().isoformat(timespec='seconds')}\nsha256: {sha}\n",
        encoding="utf-8",
    )
    print(f"받음: {out.name} {len(data) / 1e6:.1f}MB sha256={sha[:12]}…")


if __name__ == "__main__":
    main()
