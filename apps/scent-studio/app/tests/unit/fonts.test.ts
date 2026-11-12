import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

import { S } from '../../src/strings.ko';
import { FONT_FILES_APP, FONT_FILES_WEB } from '../../src/ui/fontSet';

/** 글꼴 서브셋 (design_rules.md 2-1): 파일·용량·라이선스, 화면 문구가 서브셋 안에 있는지 */
const FONTS = path.resolve(__dirname, '../../assets/fonts');
const size = (f: string) => statSync(path.join(FONTS, f)).size;
/** build-fonts.py가 서브셋에 넣은 글자 목록 */
const charset = () => new Set([...readFileSync(path.join(FONTS, 'charset.txt'), 'utf-8')]);
const isSubsetChar = (c: string) => /\s/.test(c) || charset().has(c); // 띄어쓰기·줄바꿈은 글자 모양이 없다

describe('글꼴 파일 (app/scripts/build-fonts.py가 만든 것)', () => {
  it('앱용 4개(Pretendard 400·500·600 + Noto Serif KR 500)가 있고 합이 3MB 이하', () => {
    const files = Object.values(FONT_FILES_APP);
    expect(files).toHaveLength(4);
    for (const f of files) expect(existsSync(path.join(FONTS, f)), f).toBe(true);
    expect(files.reduce((a, f) => a + size(f), 0)).toBeLessThanOrEqual(3 * 1024 * 1024);
  });

  it('웹용 woff2 4개가 있고 세리프는 400KB 이하', () => {
    for (const f of Object.values(FONT_FILES_WEB)) expect(existsSync(path.join(FONTS, f)), f).toBe(true);
    expect(size(FONT_FILES_WEB.serif)).toBeLessThanOrEqual(400 * 1024);
  });

  it('OFL 라이선스 파일 두 개를 함께 둔다', () => {
    for (const f of ['OFL-Pretendard.txt', 'OFL-NotoSerifKR.txt']) {
      expect(readFileSync(path.join(FONTS, f), 'utf-8')).toContain('SIL OPEN FONT LICENSE');
    }
  });
});

describe('서브셋 글자 범위', () => {
  it('KS X 1001 한글 2,350자 + 조합 중 자모(ㄱ·ㅏ)를 포함하고 드문 글자(똠)는 뺀다', () => {
    expect(isSubsetChar('가')).toBe(true);
    expect(isSubsetChar('ㄱ')).toBe(true); // 한글 입력 중 보이는 자모 (D4)
    expect(isSubsetChar('ㅏ')).toBe(true);
    expect(isSubsetChar('똠')).toBe(false);
    expect(isSubsetChar('✓')).toBe(true);
    expect(isSubsetChar('·')).toBe(true);
  });

  it('화면 문구(strings.ko)의 모든 글자가 서브셋 안에 있다', () => {
    const outside = [...Object.values(S).join('')].filter((c) => !isSubsetChar(c));
    expect([...new Set(outside)]).toEqual([]);
  });
});
