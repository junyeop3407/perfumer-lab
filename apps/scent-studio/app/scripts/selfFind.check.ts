/**
 * B7 "자기 자신 찾기" 근거 숫자를 다시 만드는 스크립트 (docs/tests.md B7 근거, 2026-11-12 처음 계산 → 2026-11-13 저장소에 남김).
 * 앱의 실제 엔진(src/engine)으로, 조건 맞는 향수마다 그 향수의 노트를 같은 층에 그대로(강도 모두 중) 넣고 추천 10개를 본다.
 *
 * 조건(2026-11-12 계산 때 실제로 쓴 것 그대로):
 *   1) 노트가 하나 이상  2) 층마다 노트 3개 이하  3) 모든 노트가 카탈로그에 있고 숨김(hidden)이 아님
 *   4) 같은 노트가 두 층에 겹쳐 있지 않음(앱에서는 한 노트를 한 층에만 넣을 수 있음)
 *   ※ tests.md의 "어코드가 노트와 맞는 향수"는 B7 테스트용 향수를 고를 때의 설명이고, 이 숫자에는 걸러 쓰지 않았다.
 *
 * 돌리기(앱 폴더에서, 실제 카탈로그는 assets/catalog/catalog.real.json.gz — 없으면 가짜만):
 *   npx vitest run -c vitest.scripts.config.mts
 * 출력은 개수·비율만(실제 향수 이름은 찍지 않는다. 공개 저장소).
 */
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';
import { test } from 'vitest';

import { RecommendationEngine } from '../src/engine/RecommendationEngine';
import { LAYERS, type BlendNote, type Catalog, type Perfume } from '../src/engine/types';

const APP = path.resolve(__dirname, '..');
const load = (p: string) => JSON.parse(gunzipSync(readFileSync(p)).toString('utf-8')) as Catalog;

export function selfFindCandidates(cat: Catalog): Perfume[] {
  const tier = new Map(cat.notes.map((n) => [n.key, n.tier]));
  return cat.perfumes.filter((p) => {
    const all = [...p.top, ...p.middle, ...p.base];
    return (
      all.length > 0 &&
      new Set(all).size === all.length &&
      LAYERS.every((l) => p[l].length <= 3) &&
      all.every((k) => tier.has(k) && tier.get(k) !== 'hidden')
    );
  });
}

function run(name: string, cat: Catalog, showKeys: boolean): string {
  const e = new RecommendationEngine(cat);
  const fit = selfFindCandidates(cat);
  const byKey = new Map(cat.perfumes.map((p) => [p.key, p]));
  let first = 0;
  let groupFirst = 0;
  let very = 0;
  const notFirst: string[] = [];
  for (const p of fit) {
    const blend: BlendNote[] = LAYERS.flatMap((l) => p[l].map((k) => ({ noteKey: k, layer: l, intensity: 1.0 })));
    const r = e.recommend(blend, new Set(), new Set(), 10);
    const i = r.findIndex((x) => x.perfumeKey === p.key);
    if (i === 0) first++;
    else if (showKeys && notFirst.length < 3) notFirst.push(`${p.key}→${i < 0 ? '10위 밖' : `${i + 1}위`}`);
    if (byKey.get(r[0].perfumeKey)!.group === p.group) groupFirst++;
    if (i >= 0 && r[i].grade === 'VERY_SIMILAR') very++;
  }
  const pct = (x: number) => `${((100 * x) / fit.length).toFixed(1)}%`;
  return (
    `${name}: 전체 향수 ${cat.perfumes.length}개 · 조건 맞는 향수 ${fit.length}개 | 자기 자신 1위 ${first} (${pct(first)}) · ` +
    `같은 향수 묶음(농도만 다름) 1위 ${groupFirst} (${pct(groupFirst)}) · 자기 자신 매우 비슷 ${very} (${pct(very)})` +
    (notFirst.length ? ` | 1위 아님 예: ${notFirst.join(' ; ')}` : '')
  );
}

test('B7 자기 자신 찾기 숫자', () => {
  const lines = [run('가짜', load(path.join(APP, '../tests/fixtures/test_catalog.json.gz')), true)];
  const real = path.join(APP, 'assets/catalog/catalog.real.json.gz');
  if (existsSync(real)) {
    const cat = load(real);
    lines.push(run(`실제(${cat.version})`, cat, false));
  } else lines.push('실제: assets/catalog/catalog.real.json.gz 없음(받기: cd backend && ./gradlew dbCatalogFetch -Papply=true)');
  process.stdout.write(lines.map((l) => `@@ ${l}\n`).join(''));
});
