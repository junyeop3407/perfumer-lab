/**
 * 브랜드 한글 이름 (2026-11-13 사장 결정: 평가 수 상위 200개, pipeline/manual/brands_ko.csv → brandsKo.json).
 * 화면: "Acqua di Parma (아쿠아 디 파르마)". 표에 없는 브랜드는 영어만. 향수 이름은 영어 그대로.
 * 검색: 한글 이름·별칭으로도(띄어쓰기 무시는 검색 쪽 정리에서).
 */
import table from './brandsKo.json';

const KO = table as Record<string, { ko: string; aliases: string[] }>;

export function brandKo(brand: string): string | null {
  return KO[brand]?.ko ?? null;
}

/** 화면에 쓸 브랜드: 표에 있으면 "영어 (한글)" */
export function brandLabel(brand: string): string {
  const ko = brandKo(brand);
  return ko ? `${brand} (${ko})` : brand;
}

/** 검색에 더할 이름: 한글 이름 + 별칭 */
export function brandSearchNames(brand: string): string[] {
  const e = KO[brand];
  return e ? [e.ko, ...e.aliases] : [];
}
