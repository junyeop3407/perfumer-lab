import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';
import { beforeEach, describe, expect, it } from 'vitest';

import { assertPublicSource, catalogPaths, prepareCatalog, releaseNotePath } from '../../scripts/prepare-catalog.mjs';
import { parseCatalogBytes, validateCatalog } from '../../src/catalog/loadCatalog';
import { RecommendationEngine } from '../../src/engine/RecommendationEngine';

/**
 * 5-1 빌드할 때 카탈로그 고르기 (QA팀 추가 테스트, plan_stage5.md 1-4).
 * 실제(catalog.real.json.gz)·샘플(catalog.sample.json.gz)은 따로 두고, 고른 쪽을 빌드 자리(catalog.json.gz, 웹 public)에 복사한다.
 * CATALOG_SOURCE=real: 받을 때 적은 catalog_releases 해시와 같아야 한다. sample: 가상 데이터 + 실제 이름 검사.
 * 공개 데모 빌드는 sample만, 실제 이름 검사를 건너뛰면 실패.
 */
const T = 120_000;
let root: string;
let dir: string;
let publicDir: string;
let p: ReturnType<typeof catalogPaths>;

const sha = (b: Buffer) => createHash('sha256').update(b).digest('hex');
const noSecret = () => {
  const env = { ...process.env };
  delete env.SCENT_KEY_SECRET;
  return env;
};
const opts = (o: Record<string, unknown>) => ({ dir, publicDir, ...o });

function writeReal(source = 'Parfumo', version = '2099.01.02') {
  const body = gzipSync(JSON.stringify({ version, engineVersion: 8, source: { name: source, note: 'x' }, perfumes: [] }));
  writeFileSync(p.real, body);
  return body;
}

function writeNote(body: Buffer, version = '2099.01.02', hash = sha(body)) {
  writeFileSync(p.realNote, JSON.stringify({ version, sha256: hash, size: body.length }));
}

beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), 'catalog-'));
  dir = join(root, 'assets', 'catalog');
  publicDir = join(root, 'public');
  mkdirSync(dir, { recursive: true });
  p = catalogPaths(dir, publicDir);
});

describe('자리 이름', () => {
  it('실제·샘플·빌드 자리가 다르고, 받기 기록은 실제 파일 옆', () => {
    expect(new Set([p.real, p.sample, p.active]).size).toBe(3);
    expect(p.real.endsWith('catalog.real.json.gz')).toBe(true);
    expect(p.sample.endsWith('catalog.sample.json.gz')).toBe(true);
    expect(p.active.endsWith('catalog.json.gz')).toBe(true);
    expect(releaseNotePath(p.real)).toBe(p.realNote);
    expect(p.realNote.endsWith('catalog.real.release.json')).toBe(true);
  });
});

describe('CATALOG_SOURCE 고르기', () => {
  it('값이 없거나 모르는 값이면 멈춘다', () => {
    expect(() => prepareCatalog(opts({ source: undefined }))).toThrow(/CATALOG_SOURCE/);
    expect(() => prepareCatalog(opts({ source: 'fake' }))).toThrow(/CATALOG_SOURCE/);
  });

  it('real: 파일이 없으면 dbCatalogFetch 안내를 내고 멈춘다', () => {
    expect(() => prepareCatalog(opts({ source: 'real' }))).toThrow(/dbCatalogFetch/);
  });

  it('real: 받을 때 적은 해시 기록이 없으면 멈춘다', () => {
    writeReal();
    expect(() => prepareCatalog(opts({ source: 'real' }))).toThrow(/dbCatalogFetch/);
  });

  it('real: 해시가 다르면 멈추고 빌드 자리는 건드리지 않는다', () => {
    const body = writeReal();
    writeNote(body, '2099.01.02', '0'.repeat(64));
    expect(() => prepareCatalog(opts({ source: 'real' }))).toThrow(/해시/);
    expect(existsSync(p.active)).toBe(false);
    expect(existsSync(p.web)).toBe(false);
  });

  it('real: 파일 안 버전이 기록과 다르면 멈춘다', () => {
    const body = writeReal('Parfumo', '2099.01.01');
    writeNote(body, '2099.01.02');
    expect(() => prepareCatalog(opts({ source: 'real' }))).toThrow(/버전/);
  });

  it('real: 샘플 파일이면 멈춘다', () => {
    const body = writeReal('sample');
    writeNote(body);
    expect(() => prepareCatalog(opts({ source: 'real' }))).toThrow(/샘플/);
  });

  it('real: 해시·버전이 맞으면 빌드 자리(폰·웹)에 복사', () => {
    const body = writeReal();
    writeNote(body);
    expect(prepareCatalog(opts({ source: 'real' })).source).toBe('real');
    expect(readFileSync(p.active).equals(body)).toBe(true);
    expect(readFileSync(p.web).equals(body)).toBe(true);
  });

  it('공개 데모 빌드는 real을 허용하지 않는다 — 정확한 문구로 막고, 빌드 자리(앱·웹 public)에 아무것도 복사하지 않는다', () => {
    const body = writeReal();
    writeNote(body);
    // 예전에는 /sample/만 봐서, 이 막기를 지워도 다른 오류의 파일 경로("catalog.sample.json.gz")로 통과했다(2026-11-13)
    expect(() => prepareCatalog(opts({ source: 'real', publicBuild: true }))).toThrow(
      '공개 데모 빌드는 CATALOG_SOURCE=sample만 허용합니다',
    );
    expect(existsSync(p.active), '앱 빌드 자리에 복사됨').toBe(false);
    expect(existsSync(p.web), '웹 public에 복사됨').toBe(false);
  });

  it('공개 빌드의 두 번째 막기는 복사하기 전에 막는다: 넣을 파일이 샘플과 다르면 빌드 자리에 아무것도 복사하지 않는다', () => {
    // 첫 막기를 지나도(예: 샘플을 만든 뒤 바뀌어 실제 파일이 된 경우) 복사 전에 멈춰야 한다
    writeFileSync(p.sample, writeReal()); // 샘플 자리에 둔 파일
    expect(() => assertPublicSource(p, Buffer.from('not-the-sample'))).toThrow(
      '공개 빌드에는 샘플 카탈로그만 넣을 수 있습니다(복사하지 않았습니다)',
    );
    expect(existsSync(p.active)).toBe(false);
    expect(existsSync(p.web)).toBe(false);
  });
});

describe('CATALOG_SOURCE=sample', () => {
  it('샘플을 따로 만들고 빌드 자리에 복사. 실제 파일·기록은 그대로라 real로 바로 돌아갈 수 있다', () => {
    const real = writeReal();
    writeNote(real);
    const r = prepareCatalog(opts({ source: 'sample', env: noSecret() }));
    expect(r.source).toBe('sample');
    expect(r.nameCheck).toBe('skipped');
    const sample = readFileSync(p.sample);
    expect(readFileSync(p.active).equals(sample)).toBe(true);
    expect(readFileSync(p.web).equals(sample)).toBe(true);
    expect(readFileSync(p.real).equals(real), '실제 파일은 그대로').toBe(true);
    expect(existsSync(p.realNote), '받기 기록도 그대로').toBe(true);
    // 다시 받지 않고 real로
    prepareCatalog(opts({ source: 'real' }));
    expect(readFileSync(p.active).equals(real)).toBe(true);
    // 다시 sample로: 같은 바이트
    prepareCatalog(opts({ source: 'sample', env: noSecret() }));
    expect(readFileSync(p.active).equals(sample)).toBe(true);
  }, T);

  it('샘플은 앱 모양 검사를 통과하고 TS 엔진으로 추천이 돈다', async () => {
    prepareCatalog(opts({ source: 'sample', env: noSecret() }));
    const catalog = validateCatalog(await parseCatalogBytes(new Uint8Array(readFileSync(p.active))));
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
    expect(() => prepareCatalog(opts({ source: 'sample', publicBuild: true, env: noSecret() }))).toThrow(/실제 이름 검사/);
  }, T);
});
