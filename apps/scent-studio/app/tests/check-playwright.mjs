// tests/run.sh 4단계: Playwright JSON 결과를 읽어 판정한다.
// - regression 프로젝트: 하나라도 실패하면 실패
// - acceptance 프로젝트: tests/acceptance/pending.txt에 없는 테스트가 실패하면 실패, 있는데 통과하면 실패(목록에서 지우라고)
// 사용: node tests/check-playwright.mjs <결과.json>
import { readFileSync } from 'node:fs';

const report = JSON.parse(readFileSync(process.argv[2], 'utf-8'));
const pending = new Set(
  readFileSync(new URL('./acceptance/pending.txt', import.meta.url), 'utf-8')
    .split('\n')
    .map((l) => l.replace(/#.*/, '').trim())
    .filter(Boolean),
);

const tests = [];
const walk = (suite) => {
  for (const s of suite.suites ?? []) walk(s);
  for (const spec of suite.specs ?? []) {
    for (const t of spec.tests) {
      const last = t.results.at(-1);
      tests.push({ project: t.projectName, id: spec.title.split(' ')[0], title: spec.title, ok: last?.status === 'passed' || t.status === 'expected' });
    }
  }
};
for (const s of report.suites ?? []) walk(s);

const problems = [];
for (const t of tests) {
  if (t.project === 'regression' && !t.ok) problems.push(`회귀 테스트 실패: ${t.title}`);
  if (t.project === 'acceptance') {
    if (!t.ok && !pending.has(t.id)) problems.push(`승인 테스트 실패(목록 밖): ${t.title}`);
    if (t.ok && pending.has(t.id) && !tests.some((u) => u.id === t.id && !u.ok)) problems.push(`통과로 바뀜 → pending.txt에서 지우세요: ${t.id}`);
  }
}
if (report.errors?.length) problems.push(...report.errors.map((e) => `실행 오류: ${(e.message ?? '').slice(0, 200)}`));

const acc = tests.filter((t) => t.project === 'acceptance');
const reg = tests.filter((t) => t.project === 'regression');
console.log(
  `승인 ${acc.filter((t) => t.ok).length}/${acc.length} 통과(아직 화면 없음 ${acc.filter((t) => !t.ok && pending.has(t.id)).length}) · 회귀 ${reg.filter((t) => t.ok).length}/${reg.length} 통과`,
);
if (tests.length === 0) problems.push('테스트를 하나도 읽지 못했습니다');
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
