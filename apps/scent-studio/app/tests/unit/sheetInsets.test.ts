import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * 아래 시트(노트 설명·등급 설명) 밑 빈 공간이 두 배이던 문제(2026-11-13 사장 6번).
 * 안드로이드 Modal은 navigationBarTranslucent 없이는 내비게이션 바 위에서 끝나는데, 시트가 insets.bottom(바 높이)을 또 더했다.
 * 웹에서는 이 속성이 무시되어 화면으로 잴 수 없으므로, 앱 코드의 모든 Modal이 바 아래까지 그리는지 코드로 확인한다(실제 확인은 폰).
 */
const APP = path.resolve(__dirname, '../..');
const files = (dir: string): string[] =>
  readdirSync(dir).flatMap((f) => {
    const p = path.join(dir, f);
    return statSync(p).isDirectory() ? files(p) : /\.tsx?$/.test(f) ? [p] : [];
  });

describe('시트 아래 빈 공간', () => {
  const modals = [...files(path.join(APP, 'app')), ...files(path.join(APP, 'src'))].flatMap((f) =>
    [...readFileSync(f, 'utf-8').matchAll(/<Modal\b[^>]*>/gs)].map((m) => ({ file: path.relative(APP, f), tag: m[0] })),
  );

  it('Modal이 있다(노트 설명·등급 설명)', () => {
    expect(modals.length).toBeGreaterThanOrEqual(2);
  });

  it('모든 Modal이 안드로이드 내비게이션·상태 바 아래까지 그린다(insets.bottom을 한 번만 쓰게)', () => {
    for (const m of modals) {
      expect(m.tag, m.file).toMatch(/\bnavigationBarTranslucent\b/);
      expect(m.tag, m.file).toMatch(/\bstatusBarTranslucent\b/);
    }
  });
});
