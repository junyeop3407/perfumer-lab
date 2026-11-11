import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';
import { afterEach, describe, expect, it } from 'vitest';

import { catalogPaths, prepareCatalog, verifyPublicDist } from '../../scripts/prepare-catalog.mjs';
import { catalogStarter, updateCatalog, type CatalogCache, type CatalogMeta, type FailureNote } from '../../src/catalog/catalogUpdate';
import { parseCatalogBytes, sha256Hex } from '../../src/catalog/loadCatalog';
import { readUriBytes } from '../../src/catalog/uriBytes';
import { EngineParams } from '../../src/engine/params';
import type { Catalog } from '../../src/engine/types';
import { fixtureCatalog } from './testCatalogs';

/** 5-1 나머지 리뷰(QA 중간 1~5·낮음 1·6) 회귀 테스트 */
const ENGINE = EngineParams.ENGINE_VERSION;
const bytesOf = (c: unknown) => new Uint8Array(gzipSync(Buffer.from(JSON.stringify(c))));
const current = (): Catalog => ({ ...fixtureCatalog(), version: '2026.11.05', engineVersion: ENGINE });

class MemoryCache implements CatalogCache {
  entry: { meta: CatalogMeta; bytes: Uint8Array } | null = null;
  failure: FailureNote | null = null;
  async read() {
    return this.entry;
  }
  async save(bytes: Uint8Array, meta: CatalogMeta) {
    this.entry = { bytes, meta };
  }
  async clear() {
    this.entry = null;
  }
  async readFailure() {
    return this.failure;
  }
  async saveFailure(f: FailureNote | null) {
    this.failure = f;
  }
}

function server(latest: unknown, file: Uint8Array) {
  let fileCalls = 0;
  const fetchFn = (async (input: RequestInfo | URL) => {
    if (String(input).endsWith('/catalog/latest')) return new Response(JSON.stringify(latest), { status: 200 });
    fileCalls++;
    return new Response(new Blob([file as BlobPart]), { status: 200 });
  }) as typeof fetch;
  return { fetchFn, files: () => fileCalls };
}

const deps = (fetchFn: typeof fetch, cache: CatalogCache, now: () => number, appVersion = '1.0.0') => ({
  cache,
  fetch: fetchFn,
  apiBaseUrl: 'https://api.test/api/v1',
  installId: '11111111-2222-4333-8444-555555555555',
  appVersion,
  network: async () => 'wifi' as const,
  wifiOnly: true,
  now,
});

describe('QA 중간-1: 받은 뒤 실패하면 하루 동안 같은 파일을 다시 받지 않는다(설치 id 하루 3회)', () => {
  const newer = { ...current(), version: '2026.11.20' };
  const good = bytesOf(newer);
  const broken = { version: '2026.11.20', engineVersion: ENGINE, sha256: '0'.repeat(64), sizeBytes: good.length, minAppVersion: '1.0.0' };

  it('해시가 틀린 파일: 세 번 실행해도 파일 요청은 한 번', async () => {
    const s = server(broken, good);
    const cache = new MemoryCache();
    let t = 1_000_000;
    for (let i = 0; i < 3; i++) {
      const r = await updateCatalog(current(), deps(s.fetchFn, cache, () => t));
      if (i > 0) expect(r).toEqual({ status: 'skipped', reason: 'recent-failure', version: '2026.11.20' });
      t += 60_000;
    }
    expect(s.files()).toBe(1);
  });

  it('하루가 지나면 다시 시도하고, 서버가 고친 파일(다른 sha256)은 바로 받는다', async () => {
    const cache = new MemoryCache();
    let t = 1_000_000;
    const bad = server(broken, good);
    await updateCatalog(current(), deps(bad.fetchFn, cache, () => t));
    t += 24 * 3600_000 + 1;
    await updateCatalog(current(), deps(bad.fetchFn, cache, () => t));
    expect(bad.files()).toBe(2);
    const fixed = server({ ...broken, sha256: sha256Hex(good) }, good);
    const r = await updateCatalog(current(), deps(fixed.fetchFn, cache, () => t + 1));
    expect(r.status).toBe('downloaded');
    expect(cache.failure, '성공하면 실패 기록을 지운다').toBeNull();
  });
});

describe('QA 중간-2: 새 버전 확인은 한 번 실행에 한 번만', () => {
  it('앱 시작을 겹쳐 두 번 불러도 서버에는 한 번만 묻는다', async () => {
    let latestCalls = 0;
    const fetchFn = (async () => {
      latestCalls++;
      return new Response(JSON.stringify({ version: '2026.11.05', engineVersion: ENGINE, sha256: '0'.repeat(64), sizeBytes: 1, minAppVersion: '1.0.0' }));
    }) as typeof fetch;
    const results: string[] = [];
    const start = catalogStarter({
      bundled: async () => bytesOf(current()),
      cache: new MemoryCache(),
      network: async () => 'wifi',
      wifiOnly: true,
      appVersion: '1.0.0',
      apiBaseUrl: 'https://api.test/api/v1',
      fetch: fetchFn,
    });
    const onUpdate = (r: { status: string }) => results.push(r.status);
    const [a, b] = await Promise.all([start({ installId: 'x', onUpdate }), start({ installId: 'x', onUpdate })]);
    await start({ installId: 'x', onUpdate });
    await new Promise((r) => setTimeout(r, 20));
    expect(a.origin).toBe('bundled');
    expect(b.catalog.version).toBe('2026.11.05');
    expect(latestCalls).toBe(1);
    expect(results).toEqual(['up-to-date']);
  });

  it('웹(받아 둘 곳 없음)은 서버에 묻지 않는다', async () => {
    let calls = 0;
    const start = catalogStarter({
      bundled: async () => bytesOf(current()),
      cache: null,
      network: async () => 'wifi',
      wifiOnly: false,
      appVersion: '1.0.0',
      apiBaseUrl: 'https://api.test/api/v1',
      fetch: (async () => {
        calls++;
        return new Response('{}');
      }) as typeof fetch,
    });
    await start({ installId: 'x' });
    expect(calls).toBe(0);
  });
});

describe('QA 중간-3: 폰(Hermes)에 TextDecoder가 없어도 읽는다', () => {
  const saved = globalThis.TextDecoder;
  afterEach(() => {
    globalThis.TextDecoder = saved;
  });
  it('한글이 든 gzip 카탈로그', async () => {
    // @ts-expect-error 일부러 없앤다
    delete globalThis.TextDecoder;
    const c = await parseCatalogBytes(bytesOf({ version: '한글 버전' }));
    expect(c.version).toBe('한글 버전');
  });
});

describe('QA 중간-4: 앱 안 파일 주소가 개발 서버(http)여도 읽는다', () => {
  it('http·https는 fetch, file은 파일 읽기', async () => {
    const seen: string[] = [];
    const d = {
      fetch: (async (u: RequestInfo | URL) => {
        seen.push(`fetch ${String(u)}`);
        return new Response(new Uint8Array([1, 2]));
      }) as typeof fetch,
      fileBytes: async (u: string) => {
        seen.push(`file ${u}`);
        return new Uint8Array([3]);
      },
    };
    expect([...(await readUriBytes('http://192.168.0.2:8081/assets/catalog.json.gz', d))]).toEqual([1, 2]);
    expect([...(await readUriBytes('file:///data/catalog.json.gz', d))]).toEqual([3]);
    expect(seen).toEqual(['fetch http://192.168.0.2:8081/assets/catalog.json.gz', 'file file:///data/catalog.json.gz']);
  });

  it('http에서 실패 응답이면 오류', async () => {
    const d = { fetch: (async () => new Response('x', { status: 404 })) as typeof fetch, fileBytes: async () => new Uint8Array() };
    await expect(readUriBytes('http://x/catalog.json.gz', d)).rejects.toThrow();
  });
});

describe('QA 중간-5: 공개 빌드 결과물(dist)의 카탈로그가 샘플인지 마지막에 대조', () => {
  function setup() {
    const root = mkdtempSync(join(tmpdir(), 'dist-'));
    const dir = join(root, 'assets', 'catalog');
    mkdirSync(dir, { recursive: true });
    const dist = join(root, 'dist');
    mkdirSync(dist);
    const p = catalogPaths(dir, join(root, 'public'));
    const sample = gzipSync(JSON.stringify({ version: 'sample-1', source: { name: 'sample' } }));
    writeFileSync(p.sample, sample);
    return { dir, dist, sample };
  }
  it('샘플과 같으면 통과', () => {
    const { dir, dist, sample } = setup();
    writeFileSync(join(dist, 'catalog.json.gz'), sample);
    expect(() => verifyPublicDist(dist, dir)).not.toThrow();
  });
  it('다른 파일(예: 남아 있던 실제 파일)이면 실패', () => {
    const { dir, dist } = setup();
    writeFileSync(join(dist, 'catalog.json.gz'), gzipSync(JSON.stringify({ version: '2026.11.05', source: { name: 'Parfumo' } })));
    expect(() => verifyPublicDist(dist, dir)).toThrow(/샘플/);
  });
  it('결과물에 카탈로그가 없으면 실패', () => {
    const { dir, dist } = setup();
    expect(() => verifyPublicDist(dist, dir)).toThrow(/없습니다/);
  });
});

describe('QA 낮음-1: 앱 버전 모양이 틀리면 최소 앱 버전 검사를 통과시키지 않는다', () => {
  it('1.0.0-rc1 → 건너뜀', async () => {
    const newer = bytesOf({ ...current(), version: '2026.11.20' });
    const s = server({ version: '2026.11.20', engineVersion: ENGINE, sha256: sha256Hex(newer), sizeBytes: newer.length, minAppVersion: '2.0.0' }, newer);
    const r = await updateCatalog(current(), deps(s.fetchFn, new MemoryCache(), () => 0, '1.0.0-rc1'));
    expect(r.status).toBe('skipped');
    expect(r.reason).toBe('bad-app-version');
    expect(s.files()).toBe(0);
  });
});

describe('QA 낮음-6: 실제 카탈로그 버전 모양 확인(YYYY.MM.DD(.n))', () => {
  it('모양이 다르면 real 준비가 멈춘다', () => {
    const root = mkdtempSync(join(tmpdir(), 'ver-'));
    const dir = join(root, 'assets', 'catalog');
    mkdirSync(dir, { recursive: true });
    const p = catalogPaths(dir, join(root, 'public'));
    const body = gzipSync(JSON.stringify({ version: '2026.11.11x', engineVersion: 8, source: { name: 'Parfumo' } }));
    writeFileSync(p.real, body);
    writeFileSync(p.realNote, JSON.stringify({ version: '2026.11.11x', sha256: createHash('sha256').update(body).digest('hex'), size: body.length }));
    expect(() => prepareCatalog({ source: 'real', dir, publicDir: join(root, 'public') })).toThrow(/버전 모양/);
  });
});
