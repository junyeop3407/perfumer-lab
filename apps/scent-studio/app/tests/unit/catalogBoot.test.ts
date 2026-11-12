import { describe, expect, it } from 'vitest';

import { bootCatalog, l9Line } from '../../src/catalog/catalogBoot';
import { fixtureCatalog } from './testCatalogs';

/** 5-2: 앱 시작 때 카탈로그 열기 + 엔진 준비 + L9 시간 기록 (docs/device_setup.md 4절) */
describe('카탈로그 시작 (L9 시간)', () => {
  it('열기·엔진 시간을 따로 재고 합을 낸다', async () => {
    const ticks = [100, 1100, 1300]; // 시작, 열기 끝, 엔진 끝
    const now = () => ticks.shift()!;
    const r = await bootCatalog(async () => ({ catalog: fixtureCatalog(), origin: 'bundled' as const }), now);
    expect(r.timing).toEqual({ openMs: 1000, engineMs: 200, totalMs: 1200 });
    expect(r.origin).toBe('bundled');
    expect(r.engine.note('lemon')?.nameKo).toBe('레몬');
  });

  it('로그 한 줄: 기기에서 찾기 쉬운 [L9] 머리말과 출처', () => {
    expect(l9Line({ openMs: 1000.4, engineMs: 199.6, totalMs: 1200 }, 'bundled')).toBe(
      '[L9] catalog ready 1200ms (open 1000 · engine 200) origin=bundled',
    );
  });

  it('열기가 실패하면 그대로 던진다(화면이 오류 상태를 보여 줌)', async () => {
    await expect(bootCatalog(async () => Promise.reject(new Error('깨짐')), () => 0)).rejects.toThrow('깨짐');
  });

  it('가상(샘플) 카탈로그인지 알려 준다', async () => {
    const sample = { ...fixtureCatalog(), source: { name: 'sample', note: '가상 데이터' } };
    const r = await bootCatalog(async () => ({ catalog: sample, origin: 'bundled' as const }), () => 0);
    expect(r.isSample).toBe(true);
    const real = await bootCatalog(async () => ({ catalog: fixtureCatalog(), origin: 'bundled' as const }), () => 0);
    expect(real.isSample).toBe(false);
  });
});
