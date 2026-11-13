/**
 * 향수 이름 끝에 붙은 "자기 브랜드 [연도] [농도]" 잘라 내기 (2026-11-13 사장 결정).
 * 예: "Dior Homme Sport Dior 2008 Eau de Toilette" → "Dior Homme Sport" (원래 이름은 nameRaw).
 * 규칙: 이름 끝이 그 향수 자신의 " 브랜드 연도 농도" / " 브랜드 연도" / " 브랜드 농도"와 **정확히** 같을 때만. 자르면 빈 이름이 되면 그대로.
 * 브랜드만 붙은 끝은 자르지 않는다 — "212 Carolina Herrera"·"1270 Frapin"처럼 진짜 이름이다(실제 32개).
 * 파이프라인(pipeline/build_catalog.py clean_perfume_name)도 같은 규칙으로 다음 카탈로그부터 정리해 둔다 → 그때는 고칠 것이 없어 그대로 돌려준다.
 */
import type { Catalog, Perfume } from '../engine/types';

export function cleanPerfumeName(name: string, brand: string, year: number | null, concentration: string | null): string {
  const ends: string[] = [];
  if (year && concentration) ends.push(` ${brand} ${year} ${concentration}`);
  if (year) ends.push(` ${brand} ${year}`);
  if (concentration) ends.push(` ${brand} ${concentration}`);
  for (const end of ends) {
    if (name.endsWith(end) && name.slice(0, -end.length).trim() !== '') return name.slice(0, -end.length).trimEnd();
  }
  return name;
}

export function fixPerfumeNames(catalog: Catalog): Catalog {
  let changed = false;
  const perfumes = catalog.perfumes.map((p): Perfume => {
    const name = cleanPerfumeName(p.name, p.brand, p.year, p.concentration);
    if (name === p.name) return p;
    changed = true;
    return { ...p, name, nameRaw: p.nameRaw ?? p.name };
  });
  return changed ? { ...catalog, perfumes } : catalog;
}
