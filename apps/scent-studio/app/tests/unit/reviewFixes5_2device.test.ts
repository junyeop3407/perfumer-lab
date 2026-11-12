import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

import { noteSections } from '../../src/blend/blendDraft';
import { fixtureCatalog } from './testCatalogs';

/** 5-2 사장 기기 확인(2026-11-12, 안드로이드 Expo Go)에서 나온 버그의 회귀 테스트 */
const cat = fixtureCatalog();

describe('버그 3: 노트 목록이 지금 층에 맞게 (계열을 안 골라도 MIDDLE에서 시트러스부터 나오던 문제)', () => {
  it('계열을 안 고르면 그 층에서 많이 쓰는 계열이 먼저: TOP 시트러스 · MIDDLE 플로럴 · BASE 우디', () => {
    expect(noteSections(cat, [], 'top')[0].family.code).toBe('citrus');
    expect(noteSections(cat, [], 'middle')[0].family.code).toBe('floral');
    expect(noteSections(cat, [], 'base')[0].family.code).toBe('woody');
  });

  it('묶음 안은 그 층에서 많이 쓰는 노트 순: MIDDLE 플로럴은 장미부터, BASE 우디는 샌달우드부터', () => {
    expect(noteSections(cat, [], 'middle')[0].notes[0].key).toBe('rose');
    expect(noteSections(cat, [], 'base')[0].notes[0].key).toBe('sandalwood');
  });

  it('고른 계열은 층과 상관없이 먼저 (승인 A2)', () => {
    expect(noteSections(cat, ['citrus', 'woody'], 'middle').slice(0, 2).map((s) => s.family.code)).toEqual(['citrus', 'woody']);
  });

  it('층이 바뀌어도 목록에 나오는 노트는 같다(순서만 다름)', () => {
    const keys = (l: 'top' | 'middle' | 'base') =>
      noteSections(cat, [], l)
        .flatMap((s) => s.notes.map((n) => n.key))
        .sort();
    expect(keys('middle')).toEqual(keys('top'));
    expect(keys('base')).toEqual(keys('top'));
  });
});

describe('버그 1: 모든 탭 화면이 위쪽 안전 영역(상태 표시줄)을 처리하는 화면 틀(Screen)을 쓴다', () => {
  const dir = path.resolve(__dirname, '../../app/(tabs)');
  const screens = (d: string): string[] =>
    readdirSync(d, { withFileTypes: true }).flatMap((e) =>
      e.isDirectory() ? screens(path.join(d, e.name)) : e.name.endsWith('.tsx') && e.name !== '_layout.tsx' ? [path.join(d, e.name)] : [],
    );

  it.each(screens(dir).map((f) => [path.relative(dir, f), f]))('%s', (_, f) => {
    expect(readFileSync(f, 'utf-8')).toMatch(/<Screen[\s>]/);
  });
});

describe('버그 4·5 원인: 폰에서는 외부 라이브러리 부품의 className이 적용되지 않는다(NativeWind 연결 없음)', () => {
  // SafeAreaView(react-native-safe-area-context)에 준 "flex-1"이 폰에서 빠져 화면 틀이 화면보다 길어지고,
  // 목록·요약 끝이 아래 막대·탭 바 뒤로 숨어 스크롤로도 못 봤다. 웹은 className이 그대로 CSS라 재현되지 않는다.
  const APP = path.resolve(__dirname, '../..');
  const files = (d: string): string[] =>
    readdirSync(d, { withFileTypes: true }).flatMap((e) =>
      e.isDirectory() ? files(path.join(d, e.name)) : e.name.endsWith('.tsx') ? [path.join(d, e.name)] : [],
    );
  it('SafeAreaView에 className을 쓰지 않는다', () => {
    const bad = [...files(path.join(APP, 'app')), ...files(path.join(APP, 'src'))].filter((f) =>
      /<SafeAreaView[^>]*className=/.test(readFileSync(f, 'utf-8')),
    );
    expect(bad.map((f) => path.relative(APP, f))).toEqual([]);
  });
});
