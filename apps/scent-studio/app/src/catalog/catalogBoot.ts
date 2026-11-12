/**
 * 앱 시작 때 카탈로그 열기 + 추천 엔진 준비, 그리고 L9(첫 로딩 2초) 시간 재기 (docs/device_setup.md 4절).
 * 기기에서는 `adb logcat -s ReactNativeJS | Select-String "L9"`로 l9Line 한 줄을 읽는다.
 */
import { RecommendationEngine } from '../engine/RecommendationEngine';
import type { Catalog } from '../engine/types';
import { ambiguousNoteKeys, fixNoteNames } from './noteNames';

export type CatalogTiming = { openMs: number; engineMs: number; totalMs: number };
export type Booted = {
  catalog: Catalog;
  engine: RecommendationEngine;
  origin: 'cache' | 'bundled';
  isSample: boolean;
  /** 한글 이름이 다른 노트와 겹쳐 영어 이름을 같이 보여 줄 노트 key */
  ambiguous: ReadonlySet<string>;
  timing: CatalogTiming;
};

export async function bootCatalog(
  open: () => Promise<{ catalog: Catalog; origin: 'cache' | 'bundled' }>,
  now: () => number,
): Promise<Booted> {
  const t0 = now();
  const opened = await open();
  const catalog = fixNoteNames(opened.catalog);
  const t1 = now();
  const engine = new RecommendationEngine(catalog);
  const t2 = now();
  return {
    catalog,
    engine,
    ambiguous: ambiguousNoteKeys(catalog),
    origin: opened.origin,
    isSample: opened.catalog.source?.name === 'sample',
    timing: { openMs: t1 - t0, engineMs: t2 - t1, totalMs: t2 - t0 },
  };
}

export function l9Line(t: CatalogTiming, origin: string): string {
  const r = Math.round;
  return `[L9] catalog ready ${r(t.totalMs)}ms (open ${r(t.openMs)} · engine ${r(t.engineMs)}) origin=${origin}`;
}
