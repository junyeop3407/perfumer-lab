import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';

import { pairingsFor } from '../../src/blend/blendDraft';
import { ambiguousNoteKeys, fixNoteNames, noteLabel } from '../../src/catalog/noteNames';
import type { Catalog } from '../../src/engine/types';
import { fixtureCatalog } from './testCatalogs';

/** 사장 결정 2026-11-12: 잘 어울리는 노트 3개 채우기, 같은 한글 이름 구분 */
const REAL = path.resolve(__dirname, '../../assets/catalog/catalog.real.json.gz');
const realCatalog = (): Catalog | null =>
  existsSync(REAL) ? (JSON.parse(gunzipSync(readFileSync(REAL)).toString('utf-8')) as Catalog) : null;
const catalogs = (): [string, Catalog][] => {
  const r = realCatalog();
  return [['가짜', fixtureCatalog()] as [string, Catalog], ...(r ? [['실제', r] as [string, Catalog]] : [])];
};

describe('잘 어울리는 노트: 실제 함께 쓰인 노트 먼저, 모자라면 같은 계열에서 많이 쓰는 노트로 3개', () => {
  const cat = fixtureCatalog();
  const note = (k: string) => cat.notes.find((n) => n.key === k)!;

  it('실제 궁합이 3개면 그대로, 채운 것 없음', () => {
    const p = pairingsFor(cat, note('lemon'));
    expect(p.map((x) => x.note.key)).toEqual(['grapefruit', 'sandalwood', 'rose']);
    expect(p.every((x) => !x.filled)).toBe(true);
  });

  it('2개뿐이면 실제 2개 뒤에 같은 계열(많이 쓰는 순) 1개를 채우고 표시', () => {
    const p = pairingsFor(cat, note('iris')); // 아이리스: 궁합 sandalwood·musk, 계열 soft
    expect(p).toHaveLength(3);
    expect(p.slice(0, 2).map((x) => [x.note.key, x.filled])).toEqual([['sandalwood', false], ['musk', false]]);
    expect(p[2].filled).toBe(true);
    expect(p[2].note.key).not.toBe('iris');
  });

  it('0개인 노트도 3개 (검색 전용 히노키)', () => {
    const p = pairingsFor(cat, note('hinoki'));
    expect(p).toHaveLength(3);
    expect(p.every((x) => x.filled)).toBe(true);
    expect(p[0].note.family).toBe('woody');
    expect(p.map((x) => x.note.key)).not.toContain('hinoki');
  });

  it.each(catalogs())('%s 카탈로그: 숨김 아닌 모든 노트가 서로 다른 3개, 자기 자신·숨김 노트 없음', (_, c) => {
    for (const n of c.notes.filter((x) => x.tier !== 'hidden')) {
      const p = pairingsFor(c, n);
      expect(p, n.key).toHaveLength(3);
      expect(new Set(p.map((x) => x.note.key)).size, n.key).toBe(3);
      for (const x of p) {
        expect(x.note.key, n.key).not.toBe(n.key);
        expect(x.note.tier, n.key).not.toBe('hidden');
      }
    }
  });
});

describe('같은 한글 이름 구분', () => {
  it('Oud = "우드(침향)", Woods = "나무향", White oud = "화이트 우드(침향)"', () => {
    const r = realCatalog();
    if (!r) return;
    const c = fixNoteNames(r);
    const ko = (k: string) => c.notes.find((n) => n.key === k)!.nameKo;
    expect(ko('oud')).toBe('우드(침향)');
    expect(ko('woods')).toBe('나무향');
    expect(ko('white oud')).toBe('화이트 우드(침향)');
  });

  it('겹치는 나머지(배 등)는 영어 이름이 붙는다', () => {
    const r = realCatalog();
    if (!r) return;
    const c = fixNoteNames(r);
    const amb = ambiguousNoteKeys(c);
    for (const k of ['pear', 'nashi pear', 'water lily', 'liquorice']) expect(amb.has(k), k).toBe(true);
    expect(amb.has('oud')).toBe(false);
    const pear = c.notes.find((n) => n.key === 'pear')!;
    expect(noteLabel(pear, amb)).toBe('배 Pear');
  });

  it.each(catalogs())('%s 카탈로그: 화면에 보이는 노트 이름(구분 표시 포함)이 두 개 이상 같은 것이 없다', (_, raw) => {
    const c = fixNoteNames(raw);
    const amb = ambiguousNoteKeys(c);
    const seen = new Map<string, string>();
    const dups: string[] = [];
    for (const n of c.notes.filter((x) => x.tier !== 'hidden')) {
      const label = noteLabel(n, amb);
      if (seen.has(label)) dups.push(`${label}: ${seen.get(label)} / ${n.key}`);
      seen.set(label, n.key);
    }
    expect(dups).toEqual([]);
  });

  it('고친 이름은 원본 카탈로그를 바꾸지 않는다(새 객체)', () => {
    const f = fixtureCatalog();
    const before = JSON.stringify(f);
    fixNoteNames(f);
    expect(JSON.stringify(f)).toBe(before);
  });
});
