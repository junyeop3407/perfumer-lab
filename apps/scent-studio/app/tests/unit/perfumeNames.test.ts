import { describe, expect, it } from 'vitest';

import type { Catalog, Perfume } from '../../src/engine/types';
import { cleanPerfumeName, fixPerfumeNames } from '../../src/catalog/perfumeNames';
import { fixtureCatalog } from './testCatalogs';

/** 2026-11-13 사장 지시(5번): 이름 끝의 "자기 브랜드 [연도] [농도]"만 잘라 내고 원래 이름은 nameRaw. 파이프라인(test_review_fixes_5_3.py)과 같은 사례 */
const CASES: [string, string, number | null, string | null, string][] = [
  ['Dior Homme Sport Dior 2008 Eau de Toilette', 'Dior', 2008, 'Eau de Toilette', 'Dior Homme Sport'],
  ['Acqua di Giò Profondo Giorgio Armani 2020 Eau de Parfum', 'Giorgio Armani', 2020, 'Eau de Parfum', 'Acqua di Giò Profondo'],
  ['Santal 33 Le Labo 2011', 'Le Labo', 2011, null, 'Santal 33'],
  ['Aventus Creed Eau de Parfum', 'Creed', null, 'Eau de Parfum', 'Aventus'],
  ["Terre d'Hermès Hermès", 'Hermès', 2006, 'Eau de Toilette', "Terre d'Hermès Hermès"], // 브랜드만 붙은 끝 → 그대로
  ['212 Carolina Herrera', 'Carolina Herrera', 1997, 'Eau de Toilette', '212 Carolina Herrera'], // 진짜 이름
  ['1962 Houseo', 'Houseo', 1962, null, '1962 Houseo'], // 진짜 이름
  ['Dior Homme', 'Dior', 2005, 'Eau de Toilette', 'Dior Homme'],
  ['Lumière 2008 Eau de Toilette', 'Aurelle', 2008, 'Eau de Toilette', 'Lumière 2008 Eau de Toilette'],
  ['Dior 2008 Eau de Toilette', 'Dior', 2008, 'Eau de Toilette', 'Dior 2008 Eau de Toilette'],
  ['Bleu Chanel 2010 Parfum', 'Chanel', 2011, 'Parfum', 'Bleu Chanel 2010 Parfum'],
  ['Oud Wood Tom Ford', 'Tom Ford Private Blend', 2007, null, 'Oud Wood Tom Ford'],
];

describe('향수 이름 정리', () => {
  it.each(CASES)('%s', (name, brand, year, conc, want) => {
    expect(cleanPerfumeName(name, brand, year, conc)).toBe(want);
  });

  it('카탈로그에 적용: 바뀐 향수만 nameRaw에 원래 이름, 나머지는 같은 객체 그대로', () => {
    const base = fixtureCatalog();
    const dirty: Perfume = { ...base.perfumes[0], key: 'X1', name: 'Dior Homme Sport Dior 2008 Eau de Toilette', brand: 'Dior', year: 2008, concentration: 'Eau de Toilette' };
    const cat = { ...base, perfumes: [dirty, base.perfumes[1]] } as Catalog;
    const fixed = fixPerfumeNames(cat);
    expect(fixed.perfumes[0]).toMatchObject({ name: 'Dior Homme Sport', nameRaw: 'Dior Homme Sport Dior 2008 Eau de Toilette' });
    expect(fixed.perfumes[1]).toBe(base.perfumes[1]);
  });

  it('파이프라인이 만든 가짜 카탈로그는 이미 정리돼 있다: T041("… Aurelle 2021 Eau de Parfum")만 이름이 바뀌고 원래 이름은 nameRaw', () => {
    const changed = fixtureCatalog().perfumes.filter((p) => p.nameRaw).map((p) => [p.key, p.name, p.nameRaw]);
    expect(changed).toEqual([['T041', "Lumière d'Agrumes", "Lumière d'Agrumes Aurelle 2021 Eau de Parfum"]]);
  });

  it('이미 정리된 카탈로그(다음 실제 카탈로그도 이렇게 온다)는 고칠 것이 없어 그대로 돌려준다', () => {
    const cat = fixtureCatalog();
    expect(fixPerfumeNames(cat)).toBe(cat);
  });

  it('정리 안 된 옛 카탈로그(지금 실제 카탈로그 2026.11.05 모양)는 앱이 열 때 정리한다', () => {
    const base = fixtureCatalog();
    const old = { ...base, perfumes: base.perfumes.map((p) => (p.nameRaw ? { ...p, name: p.nameRaw, nameRaw: undefined } : p)) } as Catalog;
    const t041 = fixPerfumeNames(old).perfumes.find((p) => p.key === 'T041')!;
    expect([t041.name, t041.nameRaw]).toEqual(["Lumière d'Agrumes", "Lumière d'Agrumes Aurelle 2021 Eau de Parfum"]);
  });
});
