import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

import { brandKo, brandLabel } from '../../src/catalog/brandNames';
import type { Catalog } from '../../src/engine/types';
import { searchPerfumes } from '../../src/recs/perfumeView';
import { fixtureCatalog } from './testCatalogs';

/** 한글 브랜드 표(2026-11-13 사장: 평가 수 상위 200, pipeline/manual/brands_ko.csv) */
const CSV = path.resolve(__dirname, '../../../pipeline/manual/brands_ko.csv');
const rows = readFileSync(CSV, 'utf-8').trim().split(/\r?\n/).slice(1).map((l) => l.split(','));

describe('브랜드 한글 표', () => {
  it('앱 표(src/catalog/brandsKo.json)가 CSV와 같다 — CSV를 고치면 node scripts/build-brands-ko.mjs로 다시 만든다', () => {
    const json = JSON.parse(readFileSync(path.resolve(__dirname, '../../src/catalog/brandsKo.json'), 'utf-8')) as Record<string, { ko: string; aliases: string[] }>;
    expect(Object.keys(json).length).toBe(rows.length);
    for (const [brand, ko, aliases] of rows) {
      expect(json[brand], brand).toEqual({ ko, aliases: aliases ? aliases.split(';') : [] });
    }
  });

  it('CSV는 200줄, 칸 5개, 확신도는 높음/낮음, 한글 이름은 앱 글꼴(2,350자) 안', () => {
    expect(rows).toHaveLength(200);
    const charset = new Set([...readFileSync(path.resolve(__dirname, '../../assets/fonts/charset.txt'), 'utf-8')]);
    for (const r of rows) {
      expect(r, r[0]).toHaveLength(5);
      expect(['높음', '낮음'], r[0]).toContain(r[3]);
      for (const ch of r[1] + r[2]) if (!/\s/.test(ch)) expect(charset.has(ch), `${r[0]}의 "${ch}"`).toBe(true);
    }
  });

  it('표에 있으면 "영어 (한글)", 없으면 영어만', () => {
    expect(brandLabel('Acqua di Parma')).toBe('Acqua di Parma (아쿠아 디 파르마)');
    expect(brandKo('Chanel')).toBe('샤넬');
    expect(brandLabel('Aurelle')).toBe('Aurelle');
    expect(brandKo('Aurelle')).toBeNull();
  });
});

describe('한글로 향수 찾기', () => {
  const base = fixtureCatalog();
  const cat = {
    ...base,
    perfumes: base.perfumes.map((p) => (p.key === 'T001' ? { ...p, brand: 'Acqua di Parma' } : p.key === 'T002' ? { ...p, brand: 'Chanel' } : p)),
  } as Catalog;

  it('한글 브랜드 이름, 띄어쓰기 없이도, 다른 이름(별칭)으로도', () => {
    expect(searchPerfumes(cat, '아쿠아 디 파르마').map((p) => p.key)).toContain('T001');
    expect(searchPerfumes(cat, '아쿠아디파르마').map((p) => p.key)).toContain('T001');
    expect(searchPerfumes(cat, '파르마').map((p) => p.key)).toContain('T001');
    expect(searchPerfumes(cat, '샤넬').map((p) => p.key)).toEqual(['T002']);
    expect(searchPerfumes(fixtureCatalog(), '샤넬')).toEqual([]); // 표에 없는 브랜드는 한글로 안 찾아진다
  });
});

describe('사장 검수(2026-11-13): 표기 6개 고침, 원래 표기는 검색어로 남김', () => {
  const FIXED: [string, string, string[]][] = [
    ['Mancera', '만세라', ['맨세라']],
    ['Tiziana Terenzi', '티지아나 테렌치', ['티지아나 테렌지']],
    ["L'Artisan Parfumeur", '라티잔 파퓨머', ['라티잔 퍼퓨머', '아티잔']],
    ['Histoires de Parfums', '이스뜨와 드 퍼퓸', ['히스토아 드 퍼퓸']],
    ['Joop!', '죠프', ['욥', '조프']],
    ['Stéphane Humbert Lucas', '스테판 움베르 루카스', ['스테판 험버트 루카스']],
  ];
  it.each(FIXED)('%s → %s, 옛 표기로도 찾아진다', (brand, ko, olds) => {
    expect(brandKo(brand)).toBe(ko);
    const base = fixtureCatalog();
    const cat = { ...base, perfumes: base.perfumes.map((p) => (p.key === 'T001' ? { ...p, brand } : p)) } as Catalog;
    for (const q of [ko, ...olds]) {
      expect(searchPerfumes(cat, q).map((p) => p.key), q).toContain('T001');
      expect(searchPerfumes(cat, q.replace(/\s+/g, '')).map((p) => p.key), `${q} 띄어쓰기 없이`).toContain('T001');
    }
  });
});
