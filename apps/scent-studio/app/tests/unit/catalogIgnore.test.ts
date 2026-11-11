import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * 5-1 사장 지시(2026-11-11): 실제 카탈로그 쪽 파일은 계속 .gitignore에 들어 있어야 한다(git check-ignore로 확인).
 * 실제 데이터는 공개 저장소에 올리지 않는다. 샘플·빌드 자리·웹 사본도 빌드할 때 만드는 파일이라 커밋하지 않는다.
 */
const APP_ROOT = path.resolve(__dirname, '../../..'); // apps/scent-studio

const mustIgnore = [
  'app/assets/catalog/catalog.real.json.gz',
  'app/assets/catalog/catalog.real.release.json',
  'app/assets/catalog/catalog.sample.json.gz',
  'app/assets/catalog/catalog.json.gz',
  'app/assets/catalog/catalog.real.json.gz.tmp',
  'app/public/catalog.json.gz',
  'pipeline/data/out/real_name_hashes.txt',
];

describe('카탈로그 파일은 커밋되지 않는다', () => {
  for (const f of mustIgnore) {
    it(f, () => {
      const r = spawnSync('git', ['check-ignore', '-q', f], { cwd: APP_ROOT });
      expect(r.status, `${f}가 .gitignore에 없습니다`).toBe(0);
    });
  }

  it('저장소에 이미 올라간 카탈로그 파일이 없다(합성 테스트 카탈로그만)', () => {
    const r = spawnSync('git', ['ls-files', '--', '*.json.gz'], { cwd: APP_ROOT, encoding: 'utf8' });
    const tracked = r.stdout.split('\n').filter(Boolean);
    expect(tracked).toEqual(['tests/fixtures/test_catalog.json.gz']);
  });
});
