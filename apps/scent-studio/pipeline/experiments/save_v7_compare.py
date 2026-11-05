"""v8 적용 전 비교본: 정리 데이터 + 옛 연결 + 강도 추정 없음 (v8 7단계). 8단계 블라인드의 v7 쪽으로 쓴다.

앱 폴더에서:  py pipeline/experiments/save_v7_compare.py
만드는 파일: pipeline/data/out/catalog_v7_compare.json.gz (커밋 안 함)
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_catalog as B  # noqa: E402
import engine_v8_final as F  # noqa: E402

OUT = F.OUT_DIR / "catalog_v7_compare.json.gz"


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    cat = F.build(True, False)
    cat["version"], cat["engineVersion"] = "v7-compare", 7
    B.write_catalog(cat, OUT)
    ps = cat["perfumes"]
    print(f"{OUT.name}: 후보 {len(ps):,} · 강도 보유 {100 * sum(bool(p.get('noteWeights')) for p in ps) / len(ps):.1f}% · {OUT.stat().st_size / 1024:.0f}KB")


if __name__ == "__main__":
    main()
