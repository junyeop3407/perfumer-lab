/**
 * 앱 여는 속도: 향수 이름 정리(fixPerfumeNames)를 넣기 전·후 (2026-11-13 사장 5번 "숫자로 알려 줘").
 * 실제 카탈로그로 bootCatalog와 같은 순서(풀기·검사 → 노트 이름 → [향수 이름] → 엔진)를 15번씩 재서 가운데 값. 이 PC(Node) 기준이라 폰은 몇 배 느릴 수 있다.
 * 돌리기(앱 폴더): CHECK=scripts/bootTime.check.ts npx vitest run -c vitest.scripts.config.mts
 */
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'vitest';

import { parseCatalogBytes, validateCatalog } from '../src/catalog/loadCatalog';
import { fixNoteNames } from '../src/catalog/noteNames';
import { fixPerfumeNames } from '../src/catalog/perfumeNames';
import { RecommendationEngine } from '../src/engine/RecommendationEngine';

test('여는 속도', async () => {
  const bytes = new Uint8Array(readFileSync(path.resolve(__dirname, '../assets/catalog/catalog.real.json.gz')));
  const med = (xs: number[]) => [...xs].sort((a, b) => a - b)[xs.length >> 1];
  const run = async (withNames: boolean) => {
    const t0 = performance.now();
    let cat = fixNoteNames(validateCatalog(await parseCatalogBytes(bytes)));
    const t1 = performance.now();
    if (withNames) cat = fixPerfumeNames(cat);
    const t2 = performance.now();
    new RecommendationEngine(cat);
    const t3 = performance.now();
    return { total: t3 - t0, names: t2 - t1 };
  };
  for (let i = 0; i < 3; i++) { await run(false); await run(true); } // 데우기
  const off: number[] = [], on: number[] = [], names: number[] = [];
  for (let i = 0; i < 15; i++) {
    off.push((await run(false)).total);
    const r = await run(true);
    on.push(r.total);
    names.push(r.names);
  }
  const cat = fixPerfumeNames(fixNoteNames(validateCatalog(await parseCatalogBytes(bytes))));
  const changed = cat.perfumes.filter((p) => p.nameRaw).length;
  process.stdout.write(
    `@@ 실제 카탈로그 ${cat.perfumes.length}개 중 이름 정리 ${changed}개 | 여는 시간 가운데 값: 정리 없이 ${med(off).toFixed(0)}ms → 정리 넣고 ${med(on).toFixed(0)}ms (정리 자체 ${med(names).toFixed(1)}ms)\n`,
  );
});
