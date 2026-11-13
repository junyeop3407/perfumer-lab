"""2026-11-13 사장 지시(5번): 향수 이름 끝에 붙은 "자기 브랜드 [연도] [농도]"만 잘라 내고 원래 이름은 nameRaw로 보관.

앱(app/src/catalog/perfumeNames.ts)도 같은 규칙으로 지금 카탈로그를 정리한다 — 두 쪽 사례가 같다(app/tests/unit/perfumeNames.test.ts).
"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_catalog as B  # noqa: E402

CASES = [
    # (이름, 브랜드, 연도, 농도, 기대 이름)
    ("Dior Homme Sport Dior 2008 Eau de Toilette", "Dior", 2008, "Eau de Toilette", "Dior Homme Sport"),
    ("Acqua di Giò Profondo Giorgio Armani 2020 Eau de Parfum", "Giorgio Armani", 2020, "Eau de Parfum", "Acqua di Giò Profondo"),
    ("Santal 33 Le Labo 2011", "Le Labo", 2011, None, "Santal 33"),                # 브랜드 + 연도
    ("Aventus Creed Eau de Parfum", "Creed", None, "Eau de Parfum", "Aventus"),  # 브랜드 + 농도
    ("Terre d'Hermès Hermès", "Hermès", 2006, "Eau de Toilette", "Terre d'Hermès Hermès"),  # 브랜드만 붙은 끝 → 그대로
    ("212 Carolina Herrera", "Carolina Herrera", 1997, "Eau de Toilette", "212 Carolina Herrera"),  # 진짜 이름(숫자 + 브랜드)
    ("1962 Houseo", "Houseo", 1962, None, "1962 Houseo"),  # 진짜 이름(연도 + 브랜드)
    ("Dior Homme", "Dior", 2005, "Eau de Toilette", "Dior Homme"),               # 브랜드가 원래 이름 앞부분 → 그대로
    ("Lumière 2008 Eau de Toilette", "Aurelle", 2008, "Eau de Toilette", "Lumière 2008 Eau de Toilette"),  # 브랜드 없이 연도·농도만 → 그대로
    ("Dior 2008 Eau de Toilette", "Dior", 2008, "Eau de Toilette", "Dior 2008 Eau de Toilette"),  # 자르면 빈 이름 → 그대로
    ("Bleu Chanel 2010 Parfum", "Chanel", 2011, "Parfum", "Bleu Chanel 2010 Parfum"),  # 연도가 자기 연도와 다름 → 그대로
    ("Oud Wood Tom Ford", "Tom Ford Private Blend", 2007, None, "Oud Wood Tom Ford"),  # 브랜드 글자가 정확히 같지 않음 → 그대로
]


@pytest.mark.parametrize("name,brand,year,conc,want", CASES)
def test_이름_끝의_자기_브랜드_연도_농도만_자른다(name, brand, year, conc, want):
    assert B.clean_perfume_name(name, brand, year, conc) == want


def test_카탈로그_줄에는_정리한_이름과_원래_이름이_함께():
    row = B.perfume_name_fields("Dior Homme Sport Dior 2008 Eau de Toilette", "Dior", 2008, "Eau de Toilette")
    assert row == {"name": "Dior Homme Sport", "nameRaw": "Dior Homme Sport Dior 2008 Eau de Toilette"}
    assert B.perfume_name_fields("Dior Homme", "Dior", 2005, None) == {"name": "Dior Homme"}  # 안 바뀌면 nameRaw 없음
