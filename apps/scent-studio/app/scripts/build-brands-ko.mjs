// 한글 브랜드 표 만들기: pipeline/manual/brands_ko.csv → src/catalog/brandsKo.json (앱에 들어가는 표).
// CSV를 고친 뒤(사장 검수) 앱 폴더에서: node scripts/build-brands-ko.mjs  — 테스트(brandNames.test.ts)가 둘이 같은지 확인한다.
// 칸: brand,name_ko,search_aliases(;로 여럿),confidence(높음/낮음),basis. 앱에는 brand → { ko, aliases }만 넣는다.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const APP = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const csv = readFileSync(join(APP, '..', 'pipeline', 'manual', 'brands_ko.csv'), 'utf8').trim().split(/\r?\n/);
const out = {};
for (const line of csv.slice(1)) {
  const cols = line.split(',');
  if (cols.length !== 5) throw new Error(`칸이 5개가 아닙니다: ${line}`);
  const [brand, ko, aliases] = cols;
  if (out[brand]) throw new Error(`브랜드가 두 번 있습니다: ${brand}`);
  out[brand] = { ko, aliases: aliases ? aliases.split(';') : [] };
}
writeFileSync(join(APP, 'src', 'catalog', 'brandsKo.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`brandsKo.json: ${Object.keys(out).length}개`);
