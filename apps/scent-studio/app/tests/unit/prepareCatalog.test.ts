import { createHash } from 'node:crypto';
import { mkdtempSync, existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';
import { beforeEach, describe, expect, it } from 'vitest';

import { prepareCatalog, releaseNotePath } from '../../scripts/prepare-catalog.mjs';
import { parseCatalogBytes } from '../../src/catalog/loadCatalog';
import { RecommendationEngine } from '../../src/engine/RecommendationEngine';

/**
 * 5-1 빌드할 때 카탈로그 고르기 (QA팀 추가 테스트, plan_stage5.md 1-4).
 * CATALOG_SOURCE=real: 받은 실제 파일이 있고 · 샘플이 아니고 · 받을 때 적은 catalog_releases 해시와 같아야 한다.
 * CATALOG_SOURCE=sample: 가상 데이터를 만들어 같은 자리에 둔다. 공개 데모 빌드는 sample만, 실제 이름 검사를 건너뛰면 실패.
 */
const T = 120_000;
let dir: string;
let file: string;

const sha = (b: Buffer) => createHash('sha256').update(b).digest('hex');
const noSecret = () => {
  const env = { ...process.env };
  delete env.SCENT_KEY_SECRET;
  return env;
};

function writeReal(source = 'Parfumo', version = '2099.01.02') {
  const body = gzipSync(JSON.stringify({ version, engineVersion: 8, source: { name: source, note: 'x' }, perfumes: [] }));
  mkdirSync(dir, { recursive: true });
  writeFileSync(file, body);
  return body;
}

function writeNote(body: Buffer, version = '2099.01.02', hash = sha(body)) {
  writeFileSync(releaseNotePath(file), JSON.stringify({ version, sha256: hash, size: body.length }));
}

beforeEach(() => {
  dir = join(mkdtempSync(join(tmpdir(), 'catalog-')), 'assets', 'catalog');
  file = join(dir, 'catalog.json.gz');
});

describe('CATALOG_SOURCE 고르기', () => {
  it('값이 없거나 모르는 값이면 멈춘다', () => {
    expect(() => prepareCatalog({ source: undefined, file })).toThrow(/CATALOG_SOURCE/);
    expect(() => prepareCatalog({ source: 'fake', file })).toThrow(/CATALOG_SOURCE/);
  });

  it('real: 파일이 없으면 dbCatalogFetch 안내를 내고 멈춘다', () => {
    expect(() => prepareCatalog({ source: 'real', file })).toThrow(/dbCatalogFetch/);
  });

  it('real: 받을 때 적은 해시 기록이 없으면 멈춘다', () => {
    writeReal();
    expect(() => prepareCatalog({ source: 'real', file })).toThrow(/dbCatalogFetch/);
  });

  it('real: 해시가 다르면 멈춘다', () => {
    const body = writeReal();
    writeNote(body, '2099.01.02', '0'.repeat(64));
    expect(() => prepareCatalog({ source: 'real', file })).toThrow(/해시/);
  });

  it('real: 파일 안 버전이 기록과 다르면 멈춘다', () => {
    const body = writeReal('Parfumo', '2099.01.01');
    writeNote(body, '2099.01.02');
    expect(() => prepareCatalog({ source: 'real', file })).toThrow(/버전/);
  });

  it('real: 샘플 파일이면 멈춘다', () => {
    const body = writeReal('sample');
    writeNote(body);
    expect(() => prepareCatalog({ source: 'real', file })).toThrow(/샘플/);
  });

  it('real: 해시·버전이 맞으면 통과', () => {
    const body = writeReal();
    writeNote(body);
    expect(prepareCatalog({ source: 'real', file }).source).toBe('real');
  });

  it('공개 데모 빌드는 real을 허용하지 않는다', () => {
    const body = writeReal();
    writeNote(body);
    expect(() => prepareCatalog({ source: 'real', file, publicBuild: true })).toThrow(/sample/);
  });
});

describe('CATALOG_SOURCE=sample', () => {
  it('가상 카탈로그를 같은 자리에 만들고, 실제 카탈로그 기록은 지운다', () => {
    const body = writeReal();
    writeNote(body);
    const r = prepareCatalog({ source: 'sample', file, env: noSecret() });
    expect(r.source).toBe('sample');
    expect(r.nameCheck).toBe('skipped');
    expect(existsSync(releaseNotePath(file))).toBe(false);
    const again = readFileSync(file);
    prepareCatalog({ source: 'sample', file, env: noSecret() });
    expect(readFileSync(file).equals(again), '다시 만들어도 바이트까지 같다').toBe(true);
    // 실제 파일이 아니므로 real로는 통과하지 않는다
    expect(() => prepareCatalog({ source: 'real', file })).toThrow(/dbCatalogFetch/);
  }, T);

  it('TS 엔진으로 추천이 돈다', async () => {
    prepareCatalog({ source: 'sample', file, env: noSecret() });
    const catalog = await parseCatalogBytes(new Uint8Array(readFileSync(file)));
    expect(catalog.source.name).toBe('sample');
    const engine = new RecommendationEngine(catalog);
    const browse = catalog.notes.filter((n) => n.tier === 'browse');
    const pick = (fam: string) => browse.find((n) => n.family === fam)!.key;
    const recs = engine.recommend(
      [
        { noteKey: pick('citrus'), layer: 'top', intensity: 1.0 },
        { noteKey: pick('floral'), layer: 'middle', intensity: 1.0 },
        { noteKey: pick('woody'), layer: 'base', intensity: 1.0 },
      ],
      new Set(),
      new Set(),
      10,
    );
    expect(recs.length).toBe(10);
    expect(recs.every((r) => r.perfumeKey.startsWith('sample-'))).toBe(true);
    expect(recs[0].similarity).toBeGreaterThan(0.3);
  }, T);

  it('공개 데모 빌드에서 실제 이름 검사를 건너뛰면(키 없음) 실패한다', () => {
    expect(() => prepareCatalog({ source: 'sample', file, publicBuild: true, env: noSecret() })).toThrow(/실제 이름 검사/);
  }, T);
});
