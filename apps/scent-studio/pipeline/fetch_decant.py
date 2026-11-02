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

# 시험(docs/engine_v2_trial.md)에 쓴 버전으로 고정한다. 데이터가 바뀌면 추천도 바뀌므로, 올릴 때는 일부러 여기를 고친다
COMMIT = "65030c47794c2eb3adf2834b542d1a97e04b8ba5"
BASE = f"https://codeberg.org/decant/pages/raw/commit/{COMMIT}/"
FILES = {
    "perfumes.db": "23cf96485384f7ec2cd718bb75604659cbab7aea8c77e3255f98bbb0011561b2",
    "perfumes_cold.db": "2b3d3517819c881f319293f4a58b5d58035b4d90ec90ff05aa38244683b1b314",
}
RAW = Path(__file__).resolve().parent / "data" / "raw" / "decant"
LICENSE = "CC BY-NC-SA 4.0 (https://www.kaggle.com/datasets/ledecanteur/fragrantica-perfumes)"


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    RAW.mkdir(parents=True, exist_ok=True)
    lines = [f"fetched: {dt.datetime.now().isoformat(timespec='seconds')}", f"license: {LICENSE}"]
    for name, expected in FILES.items():
        url = f"{BASE}{name}.gz"
        with urllib.request.urlopen(url, timeout=300) as r:
            packed = r.read()
        sha = hashlib.sha256(packed).hexdigest()
        if sha != expected:  # 해시를 확인하기 전에는 풀거나 저장하지 않는다
            raise SystemExit(f"{name}: 해시가 다릅니다 ({sha}). 받은 파일을 저장하지 않았습니다.")
        (RAW / name).write_bytes(gzip.decompress(packed))
        lines.append(f"{url} sha256: {sha}")
        print(f"받음: {name} {len(packed) / 1e6:.1f}MB sha256={sha[:12]}…")
    (RAW / "SOURCE.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
