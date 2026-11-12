import { describe, expect, it } from 'vitest';

import { makeTapLock } from '../../src/ui/tapLock';

/** 5-2 망가뜨려 보기에서 나온 문제의 회귀 테스트 */
describe('연속 클릭 잠금 (새 조합 만들기 두 번 → 화면 두 번 쌓임)', () => {
  it('같은 이동을 잠금 시간 안에 또 누르면 무시하고, 지나면 다시 된다', () => {
    let t = 0;
    const lock = makeTapLock(() => t, 600);
    const calls: string[] = [];
    lock('/families', () => calls.push('a'));
    t = 100;
    lock('/families', () => calls.push('b'));
    expect(calls).toEqual(['a']);
    t = 700;
    lock('/families', () => calls.push('c'));
    expect(calls).toEqual(['a', 'c']);
  });

  it('다른 이동은 바로 이어서 눌러도 된다 (계열 화면을 열자마자 "다음")', () => {
    let t = 0;
    const lock = makeTapLock(() => t, 600);
    const calls: string[] = [];
    lock('/families', () => calls.push('families'));
    t = 50;
    lock('/notes', () => calls.push('notes'));
    expect(calls).toEqual(['families', 'notes']);
  });
});
