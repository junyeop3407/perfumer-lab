"""글꼴 서브셋 만들기 (docs/design/design_rules.md 2-1).

원본(커밋하지 않음)을 받은 폴더를 주면 app/assets/fonts/ 에 서브셋을 만든다.
  원본 폴더에 있어야 하는 것:
    Pretendard-Regular.otf, Pretendard-Medium.otf, Pretendard-SemiBold.otf, LICENSE.txt (Pretendard 1.3.9 배포 zip의 public/static/)
    NotoSerifKR.ttf (google/fonts ofl/notoserifkr 가변 글꼴 NotoSerifKR[wght].ttf), OFL.txt
  실행: py -I app/scripts/build-fonts.py <원본 폴더>
결과(커밋함): 앱용 otf/ttf 4개 + 웹용 woff2 4개 + OFL 2개 + charset.txt(넣은 글자 목록, 테스트가 읽음)
글자: KS X 1001 한글 2,350자 + 한글 자모(입력 중 보이는 ㄱ·ㅏ) + 라틴·문장부호 + 화면에 쓰는 기호.
"""
import shutil
import tempfile
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

OUT = Path(__file__).resolve().parent.parent / "assets" / "fonts"


def charset() -> str:
    chars = set()
    for cp in range(0xAC00, 0xD7A4):  # KS X 1001(EUC-KR)로 쓸 수 있는 완성형 한글 2,350자
        c = chr(cp)
        b = c.encode("euc-kr")  # 2,350자 밖은 8바이트 조합 표현으로 나온다
        if len(b) == 2 and 0xB0 <= b[0] <= 0xC8:
            chars.add(c)
    assert len(chars) == 2350, len(chars)
    ranges = [
        (0x20, 0x7E),  # 기본 라틴
        (0xA0, 0xFF),  # 라틴-1 (é 등 향수 이름)
        (0x2010, 0x2027),  # 대시·따옴표·…
        (0x3131, 0x318E),  # 한글 호환 자모 (입력 중)
    ]
    for a, b in ranges:
        chars.update(chr(c) for c in range(a, b + 1))
    chars.update("·✓○●←→↑↓×%")
    return "".join(sorted(chars))


def make(src: Path, out_name: str, text: str, flavor: str | None):
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    opts.hinting = False
    opts.desubroutinize = True
    if flavor:
        opts.flavor = flavor
    font = subset.load_font(str(src), opts)
    s = subset.Subsetter(opts)
    s.populate(text=text)
    s.subset(font)
    subset.save_font(font, str(OUT / out_name), opts)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    text = charset()
    (OUT / "charset.txt").write_text(text, encoding="utf-8")

    for weight in ("Regular", "Medium", "SemiBold"):
        f = src / f"Pretendard-{weight}.otf"
        make(f, f"Pretendard-{weight}.otf", text, None)
        make(f, f"Pretendard-{weight}.woff2", text, "woff2")

    # Noto Serif KR: 가변 글꼴을 500 하나로 고정한 뒤 서브셋
    var = TTFont(str(src / "NotoSerifKR.ttf"))
    fixed = instancer.instantiateVariableFont(var, {"wght": 500})
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as d:  # Windows는 읽던 파일을 바로 못 지운다
        tmp = Path(d) / "serif500.ttf"
        fixed.save(str(tmp))
        make(tmp, "NotoSerifKR-Medium.ttf", text, None)
        make(tmp, "NotoSerifKR-Medium.woff2", text, "woff2")

    shutil.copy(src / "LICENSE.txt", OUT / "OFL-Pretendard.txt")
    shutil.copy(src / "OFL.txt", OUT / "OFL-NotoSerifKR.txt")
    for p in sorted(OUT.iterdir()):
        print(f"{p.name:32} {p.stat().st_size / 1024:8.0f} KB")


if __name__ == "__main__":
    main()
