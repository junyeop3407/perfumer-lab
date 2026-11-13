/**
 * "노트를 많이 고르면 늘 참고" 문제의 고치는 방법 비교 (2026-11-13 사장 지시 3번, 엔진·기준값은 바꾸지 않는다 — 비교만).
 * 결과 문서: docs/engine_always_reference_compare.md (이 스크립트 출력을 옮김). 실제 향수 이름은 찍지 않는다(사장 조합 Top 5만 브랜드·이름 대신 key 해시 앞 6자리).
 * 돌리기(앱 폴더): CHECK=scripts/gradeVariants.check.ts npx vitest run -c vitest.scripts.config.mts   (실제 카탈로그 필요, 이 PC 약 7분)
 * 24조합: scripts/combos24.json = pipeline/evaluate.py의 BLENDS + V6_COMPARE_COMBOS를 옮긴 것.
 *
 * 방법:
 *  지금  — 엔진 v8 그대로
 *  ㉮    — 노트 일치를 "고른 노트 중 가장 무거운 4개까지만 다 맞으면 만점"으로(노트가 많아도 불리하지 않게)
 *  ㉯    — 노트 일치 비중 55% → 35%(어코드 35% → 55%로 바꿔 합은 그대로)
 *  ㉰    — 점수는 그대로, 등급 기준을 고른 노트 수마다 따로(기준값 만든 방법 그대로: 그 노트 수의 무작위 조합 Top 10의 상위 10%·40%)
 *  ㉱    — (추가) 노트 일치 비중을 고른 노트 수에 따라 줄이기: 3개 이하 55%, 그보다 많으면 55% × 3/n (합은 어코드로)
 */
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';
import { expect, test } from 'vitest';

import { noteSections } from '../src/blend/blendDraft';
import { fixNoteNames } from '../src/catalog/noteNames';
import { EngineParams as P } from '../src/engine/params';
import { RecommendationEngine, cosine } from '../src/engine/RecommendationEngine';
import { LAYERS, type BlendNote, type Catalog, type Grade, type Layer, type Perfume } from '../src/engine/types';

const APP = path.resolve(__dirname, '..');
const cat = fixNoteNames(JSON.parse(gunzipSync(readFileSync(path.join(APP, 'assets/catalog/catalog.real.json.gz'))).toString('utf-8')) as Catalog);
const engine = new RecommendationEngine(cat);
const notes = new Map(cat.notes.map((n) => [n.key, n]));
const accKeys = cat.accords.map((a) => a.key);
type Ix = { p: Perfume; v: number[]; layers: Record<Layer, Set<string>>; all: Set<string>; fams: Set<string> };
const IX: Ix[] = cat.perfumes.map((p) => {
  const all = new Set([...p.top, ...p.middle, ...p.base]);
  return {
    p,
    v: p.accordShares ? accKeys.map((a) => p.accordShares![a] ?? 0) : engine.perfumeVector(p.accords),
    layers: { top: new Set(p.top), middle: new Set(p.middle), base: new Set(p.base) },
    all,
    fams: new Set([...all].map((k) => notes.get(k)!.family)),
  };
});
const N = IX.length;
const roundHalfUp = (x: number) => Math.floor(x + 0.5);

/** ga3·ga4·ga5·ga6: ㉮의 "가장 무거운 K개까지 맞으면 만점"에서 K */
type Variant = 'now' | 'ga3' | 'ga4' | 'ga5' | 'ga6' | 'na' | 'da' | 'ra';
type Rec = { key: string; sim: number; accordSim: number; noteMatch: number };

/** 엔진 recommend와 같은 순서·묶음·브랜드 제한으로 Top k (선호·불호 없음) */
function recommend(blend: BlendNote[], v: Variant, k = 10): Rec[] {
  const b = engine.blendVector(blend);
  const w = new Map(blend.map((x) => [x.noteKey, Math.log(1 + N / notes.get(x.noteKey)!.df)]));
  const wSum = [...w.values()].reduce((a, x) => a + x, 0);
  const K = v.startsWith('ga') ? Number(v.slice(2)) : 4;
  const topK = [...w.values()].sort((a, c) => c - a).slice(0, K).reduce((a, x) => a + x, 0);
  const n = blend.length;
  const wNote = v === 'na' ? 0.35 : v === 'ra' && n > 3 ? (P.W_NOTE * 3) / n : P.W_NOTE;
  const wAcc = v === 'na' ? 0.55 : P.W_ACCORD + P.W_NOTE - wNote;
  const scored = IX.map((ix) => {
    const accordSim = cosine(ix.v, b);
    let nm = 0;
    for (const x of blend) {
      const c = ix.layers[x.layer].has(x.noteKey) ? P.C_SAME_LAYER : ix.all.has(x.noteKey) ? P.C_OTHER_LAYER : ix.fams.has(notes.get(x.noteKey)!.family) ? P.C_SAME_FAMILY : 0;
      nm += w.get(x.noteKey)! * c;
    }
    let held = 0;
    for (const x of blend) if (ix.all.has(x.noteKey)) held += ix.p.noteWeights ? ix.p.noteWeights[x.noteKey] : 1.0 / ix.all.size;
    const base = v.startsWith('ga') ? Math.min(1, nm / topK) : nm / wSum;
    const noteMatch = base * (P.STRENGTH_BASE + (1.0 - P.STRENGTH_BASE) * Math.sqrt(Math.min(held, 1.0)));
    return { ix, sim: (wAcc * accordSim + wNote * noteMatch) / (wAcc + wNote), accordSim, noteMatch };
  });
  scored.sort(
    (s, t) => roundHalfUp(t.sim * P.SCORE_QUANTUM) - roundHalfUp(s.sim * P.SCORE_QUANTUM) || t.ix.p.ratingCount - s.ix.p.ratingCount || (s.ix.p.key < t.ix.p.key ? -1 : s.ix.p.key > t.ix.p.key ? 1 : 0),
  );
  const out: Rec[] = [];
  const groups = new Set<string>();
  const brands = new Map<string, number>();
  for (const s of scored) {
    if (groups.has(s.ix.p.group) || (brands.get(s.ix.p.brand) ?? 0) >= P.BRAND_CAP) continue;
    groups.add(s.ix.p.group);
    brands.set(s.ix.p.brand, (brands.get(s.ix.p.brand) ?? 0) + 1);
    out.push({ key: s.ix.p.key, sim: s.sim, accordSim: s.accordSim, noteMatch: s.noteMatch });
    if (out.length === k) break;
  }
  return out;
}

const gradeFix = (s: number): Grade => (s >= P.GRADE_VERY_SIMILAR ? 'VERY_SIMILAR' : s >= P.GRADE_SIMILAR ? 'SIMILAR' : 'REFERENCE');

// ---------- 조합 만들기 ----------
let seed = 0;
const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
const pickN = <T,>(a: T[], k: number) => { const c = [...a]; const o: T[] = []; while (o.length < k && c.length) o.push(c.splice(Math.floor(rnd() * c.length), 1)[0]); return o; };
/** 화면처럼: 계열 k개, 층마다 그 층 목록 앞쪽(고른 계열의 많이 쓰는 노트)에서 per개, 강도 중 */
function userLike(k: number, per: number): BlendNote[] {
  const fams = pickN(cat.families.map((f) => f.code), k);
  const used = new Set<string>();
  const out: BlendNote[] = [];
  for (const l of LAYERS) {
    const pool = noteSections(cat, fams, l).filter((s) => !s.rare && fams.includes(s.family.code)).flatMap((s) => s.notes).filter((n) => !used.has(n.key));
    for (const nn of pickN(pool, per)) { used.add(nn.key); out.push({ noteKey: nn.key, layer: l, intensity: 1.0 }); }
  }
  return out;
}
/** 기준값 만든 방법과 같은 무작위 조합: 실제 향수에서 층마다 노트를 뽑아 n개(층 돌아가며), 강도 중 */
function fromPerfume(n: number): BlendNote[] | null {
  const p = IX[Math.floor(rnd() * N)];
  const used = new Set<string>();
  const out: BlendNote[] = [];
  const pools = LAYERS.map((l) => [...p.layers[l]].filter((k) => notes.get(k)!.tier !== 'hidden'));
  for (let i = 0; out.length < n && i < 30; i++) {
    const li = i % 3;
    const cand = pools[li].filter((k) => !used.has(k));
    if (!cand.length || out.filter((x) => x.layer === LAYERS[li]).length >= 3) continue;
    const kk = cand[Math.floor(rnd() * cand.length)];
    used.add(kk);
    out.push({ noteKey: kk, layer: LAYERS[li], intensity: 1.0 });
  }
  return out.length === n ? out : null;
}

const pct = (x: number, n: number) => `${((100 * x) / n).toFixed(1)}%`;
const hash6 = (k: string) => createHash('sha256').update(k).digest('hex').slice(0, 6);

test('방법 비교', () => {
  const t0 = Date.now();
  const lines: string[] = [];
  // 0) "지금" 재구현이 실제 엔진과 같은지 (같지 않으면 비교가 무의미)
  seed = 7;
  for (let i = 0; i < 20; i++) {
    const b = userLike(2 + (i % 2), 1 + (i % 3));
    const mine = recommend(b, 'now').map((r) => `${r.key}:${r.sim.toFixed(9)}`);
    const real = engine.recommend(b, new Set(), new Set(), 10).map((r) => `${r.perfumeKey}:${r.similarity.toFixed(9)}`);
    expect(mine).toEqual(real);
  }
  lines.push('재구현 확인: "지금"이 실제 엔진과 20조합 Top 10 점수까지 같음');

  // ㉰ 기준값: 노트 수 n마다 무작위 조합 300개(시드 20261104) Top 10 유사도의 상위 10%·40%
  const perN = new Map<number, [number, number]>();
  seed = 20261104;
  for (let n = 1; n <= 9; n++) {
    const sims: number[] = [];
    let made = 0;
    for (let tries = 0; made < 300 && tries < 5000; tries++) {
      const b = fromPerfume(n);
      if (!b) continue;
      made++;
      for (const r of recommend(b, 'now')) sims.push(r.sim);
    }
    sims.sort((a, c) => c - a);
    const q = (f: number) => Math.round(1000 * sims[Math.floor(f * sims.length)]) / 1000;
    perN.set(n, [q(0.1), q(0.4)]);
  }
  lines.push(`㉰ 노트 수별 기준(매우 비슷/비슷): ${[...perN].map(([n, [a, c]]) => `${n}개 ${a}/${c}`).join(' · ')} (지금 하나로 ${P.GRADE_VERY_SIMILAR}/${P.GRADE_SIMILAR})`);

  const gradeOf = (v: Variant, s: number, n: number): Grade => {
    if (v !== 'da') return gradeFix(s);
    const [vs, si] = perN.get(Math.min(9, Math.max(1, n)))!;
    return s >= vs ? 'VERY_SIMILAR' : s >= si ? 'SIMILAR' : 'REFERENCE';
  };
  // VARIANTS=ga3,ga4 처럼 골라 돌릴 수 있다(없으면 전부)
  const ALL: [Variant, string][] = [['now', '지금'], ['ga3', '㉮ K=3'], ['ga4', '㉮ 노트 4개까지만 만점 기준(K=4)'], ['ga5', '㉮ K=5'], ['ga6', '㉮ K=6'], ['na', '㉯ 노트 비중 55→35%'], ['da', '㉰ 노트 수별 등급 기준'], ['ra', '㉱ 노트 많으면 노트 비중 줄이기']];
  const VARS = process.env.VARIANTS ? ALL.filter(([v]) => process.env.VARIANTS!.split(',').includes(v)) : ALL;

  // 같은 조합 묶음을 모든 방법에 쓴다
  seed = 20261113;
  const user400 = Array.from({ length: 400 }, () => userLike(2 + Math.floor(rnd() * 2), 3));
  seed = 20261114;
  const user3 = Array.from({ length: 400 }, () => userLike(2 + Math.floor(rnd() * 2), 1));
  const combos = JSON.parse(readFileSync(path.join(APP, 'scripts/combos24.json'), 'utf-8')) as { name: string; blend: { noteKey: string; layer: Layer; level: 'weak' | 'medium' | 'strong' }[] }[];
  const c24 = combos.map((c) => c.blend.map((x) => ({ noteKey: x.noteKey, layer: x.layer, intensity: P.INTENSITIES[x.level] })));
  const byKo = (ko: string) => cat.notes.find((n) => n.nameKo === ko && n.tier !== 'hidden')!.key;
  const owner: BlendNote[] = ([['top', ['베르가못', '자몽', '오렌지']], ['middle', ['버베나', '튀니지안 네롤리', '헬리오트로프']], ['base', ['퍼 발삼', '마테', '건초']]] as [Layer, string[]][])
    .flatMap(([l, ks]) => ks.map((k) => ({ noteKey: byKo(k), layer: l, intensity: 1.0 })));
  const tierOf = new Map(cat.notes.map((n) => [n.key, n.tier]));
  const self = IX.filter(({ p }) => {
    const all = [...p.top, ...p.middle, ...p.base];
    return all.length > 0 && new Set(all).size === all.length && LAYERS.every((l) => p[l].length <= 3) && all.every((k) => tierOf.has(k) && tierOf.get(k) !== 'hidden');
  });
  const groupOf = new Map(cat.perfumes.map((p) => [p.key, p.group]));

  for (const [v, label] of VARS) {
    const tv = Date.now();
    const dist = (set: BlendNote[][]) => {
      const c = { VERY_SIMILAR: 0, SIMILAR: 0, REFERENCE: 0 } as Record<Grade, number>;
      for (const b of set) c[gradeOf(v, recommend(b, v, 1)[0].sim, b.length)]++;
      return `매우 ${pct(c.VERY_SIMILAR, set.length)} · 비슷 ${pct(c.SIMILAR, set.length)} · 참고 ${pct(c.REFERENCE, set.length)}`;
    };
    const t10 = { VERY_SIMILAR: 0, SIMILAR: 0, REFERENCE: 0 } as Record<Grade, number>;
    let first24Very = 0;
    for (const b of c24) {
      const r = recommend(b, v);
      r.forEach((x, i) => { const g = gradeOf(v, x.sim, b.length); t10[g]++; if (i === 0 && g === 'VERY_SIMILAR') first24Very++; });
    }
    let sf1 = 0, sfG = 0, sfV = 0;
    for (const { p } of self) {
      const b = LAYERS.flatMap((l) => p[l].map((k) => ({ noteKey: k, layer: l, intensity: 1.0 })));
      const r = recommend(b, v);
      const i = r.findIndex((x) => x.key === p.key);
      if (i === 0) sf1++;
      if (groupOf.get(r[0].key) === p.group) sfG++;
      if (i >= 0 && gradeOf(v, r[i].sim, b.length) === 'VERY_SIMILAR') sfV++;
    }
    const ow = recommend(owner, v, 5).map((x) => `${hash6(x.key)} ${Math.round(100 * x.sim)}% ${gradeOf(v, x.sim, owner.length)[0]}`);
    lines.push(
      `## ${label}`,
      `- 화면 모양 9노트(계열 2~3, 층마다 3) 400개 1위: ${dist(user400)}`,
      `- 화면 모양 3노트(층마다 1) 400개 1위: ${dist(user3)}`,
      `- B7 자기 자신 찾기(${self.length}개): 1위 ${pct(sf1, self.length)} · 묶음 1위 ${pct(sfG, self.length)} · 매우 비슷 ${pct(sfV, self.length)}`,
      `- 손으로 고른 24조합: 1위 매우 비슷 ${first24Very}/24 · Top 10 매우 ${pct(t10.VERY_SIMILAR, 240)} · 비슷 ${pct(t10.SIMILAR, 240)} · 참고 ${pct(t10.REFERENCE, 240)}`,
      `- 사장 조합 Top 5(향수 key 해시·점수·등급 V/S/R): ${ow.join(' | ')}`,
      `- 계산 ${((Date.now() - tv) / 1000).toFixed(0)}초`,
    );
  }
  lines.push(`전체 ${((Date.now() - t0) / 1000).toFixed(0)}초`);
  process.stdout.write(lines.map((l) => `@@ ${l}\n`).join(''));
});
