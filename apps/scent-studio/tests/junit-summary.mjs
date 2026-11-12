// 서버 JUnit XML 결과를 한 줄로 센다. 결과 파일이 없거나 0개, 실패·오류가 있으면 실패로 끝난다(tests/run.sh 3단계).
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

const dir = process.argv[2];
let files = [];
try {
  files = readdirSync(dir).filter((f) => f.startsWith('TEST-') && f.endsWith('.xml'));
} catch {
  /* 아래에서 0개로 실패 */
}
let tests = 0, failures = 0, errors = 0, skipped = 0;
const failed = [];
for (const f of files) {
  const head = readFileSync(path.join(dir, f), 'utf-8').match(/<testsuite\b[^>]*>/)?.[0] ?? '';
  const num = (k) => Number(head.match(new RegExp(String.raw`\s${k}="(\d+)"`))?.[1] ?? 0);
  tests += num('tests'); failures += num('failures'); errors += num('errors'); skipped += num('skipped');
  if (num('failures') + num('errors') > 0) failed.push(f.slice(5, -4));
}
if (tests === 0) {
  console.error(`서버 테스트 결과가 없습니다(${dir}). 서버 테스트가 돌지 않았습니다.`);
  process.exit(1);
}
const ran = tests - skipped;
const bad = failures + errors;
console.log(`서버: ${ran - bad}/${ran} 통과${skipped ? ` (건너뜀 ${skipped})` : ''}${bad ? ` · 실패 ${bad}` : ''}`);
if (bad) {
  console.error(`실패한 서버 테스트: ${failed.join(', ')}`);
  process.exit(1);
}
