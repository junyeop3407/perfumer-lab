import { gzipSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';

import {
  compareAppVersion,
  compareCatalogVersion,
  openCatalog,
  updateCatalog,
  type CatalogCache,
  type CatalogMeta,
  type NetworkKind,
} from '../../src/catalog/catalogUpdate';
import { CatalogError, parseCatalogBytes, sha256Hex, validateCatalog } from '../../src/catalog/loadCatalog';
import { EngineParams } from '../../src/engine/params';
import type { Catalog } from '../../src/engine/types';
import { fixtureCatalog } from './testCatalogs';

/**
 * 5-1 앱 카탈로그 로더 (QA팀 추가 테스트, design.md 10절 "카탈로그", plan_stage5.md 5-1).
 * - 읽을 때 모양 확인, 받은 파일은 sha256·크기·버전 확인. 깨졌으면 앱 안 파일을 쓴다.
 * - 새 버전은 받아 두었다가 다음 실행부터 쓴다(폰은 와이파이에서만). 실패해도 앱은 멈추지 않는다(예외 없음).
 */
const ENGINE = EngineParams.ENGINE_VERSION;
const APP = '1.0.0';
const base = (): Catalog => ({ ...fixtureCatalog(), version: '2026.11.05', engineVersion: ENGINE });
const bytesOf = (c: unknown) => new Uint8Array(gzipSync(Buffer.from(JSON.stringify(c))));
const clone = <T>(x: T): T => JSON.parse(JSON.stringify(x));

class MemoryCache implements CatalogCache {
  entry: { meta: CatalogMeta; bytes: Uint8Array } | null = null;
  saves = 0;
  clears = 0;
  async read() {
    return this.entry;
  }
  async save(bytes: Uint8Array, meta: CatalogMeta) {
    this.saves++;
    this.entry = { bytes, meta };
  }
  async clear() {
    this.clears++;
    this.entry = null;
  }
}

function metaFor(bytes: Uint8Array, c: Catalog, appVersion = APP): CatalogMeta {
  return { version: c.version, sha256: sha256Hex(bytes), size: bytes.length, engineVersion: c.engineVersion, appVersion };
}

describe('모양 확인 (validateCatalog)', () => {
  it('합성 테스트 카탈로그는 통과', () => {
    expect(() => validateCatalog(fixtureCatalog())).not.toThrow();
  });

  const broken: [string, (c: any) => void][] = [
    ['version 없음', (c) => delete c.version],
    ['engineVersion 문자', (c) => (c.engineVersion = '8')],
    ['source 없음', (c) => delete c.source],
    ['perfumes 비어 있음', (c) => (c.perfumes = [])],
    ['notes 배열 아님', (c) => (c.notes = {})],
    ['노트 프로필 길이가 어코드 수와 다름', (c) => c.notes[0].profile.pop()],
    ['노트 프로필에 숫자 아닌 값', (c) => (c.notes[0].profile[0] = 'x')],
    ['노트 계열이 계열 목록에 없음', (c) => (c.notes[0].family = 'nope')],
    ['노트 key 겹침', (c) => (c.notes[1].key = c.notes[0].key)],
    ['향수 key 겹침', (c) => (c.perfumes[1].key = c.perfumes[0].key)],
    ['향수 노트가 노트 목록에 없음', (c) => (c.perfumes[0].top = ['no-such-note'])],
    ['향수 brand 없음', (c) => delete c.perfumes[0].brand],
    ['향수 accordShares 값이 숫자 아님', (c) => (c.perfumes[0].accordShares = { citrus: 'a' })],
    ['향수 accords에 모르는 어코드', (c) => (c.perfumes[0].accords = ['nope'])],
    ['globalMean 길이 틀림', (c) => (c.globalMean = [1, 2])],
  ];
  for (const [name, breakIt] of broken) {
    it(`깨진 카탈로그는 거부: ${name}`, () => {
      const c = clone(fixtureCatalog());
      breakIt(c);
      expect(() => validateCatalog(c)).toThrow(CatalogError);
    });
  }
});

describe('파일 읽기·해시', () => {
  it('gzip과 일반 JSON 둘 다 읽는다(폰에서도 되는 순수 JS 압축 풀기)', async () => {
    const c = base();
    expect((await parseCatalogBytes(bytesOf(c))).version).toBe(c.version);
    expect((await parseCatalogBytes(new TextEncoder().encode(JSON.stringify(c)))).version).toBe(c.version);
  });

  it('깨진 gzip은 CatalogError', async () => {
    await expect(parseCatalogBytes(new Uint8Array([0x1f, 0x8b, 1, 2, 3]))).rejects.toThrow(CatalogError);
  });

  it('sha256 (알려진 값)', () => {
    expect(sha256Hex(new TextEncoder().encode('abc'))).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });
});

describe('버전 비교', () => {
  it('카탈로그 버전: 날짜 + 같은 날 번호', () => {
    expect(compareCatalogVersion('2026.11.05.2', '2026.11.05')).toBeGreaterThan(0);
    expect(compareCatalogVersion('2026.12.01', '2026.11.30')).toBeGreaterThan(0);
    expect(compareCatalogVersion('2026.11.05', '2026.11.05')).toBe(0);
    expect(compareCatalogVersion('2026.11.04', '2026.11.05')).toBeLessThan(0);
    expect(compareCatalogVersion('2026.11.05', 'test-1')).toBeGreaterThan(0); // 모양이 다른 버전(테스트·샘플)은 가장 낮게
  });

  it('앱 버전: 숫자로 비교', () => {
    expect(compareAppVersion('1.10.0', '1.9.0')).toBeGreaterThan(0);
    expect(compareAppVersion('1.0.0', '1.0.0')).toBe(0);
    expect(compareAppVersion('0.9.9', '1.0.0')).toBeLessThan(0);
  });
});

describe('열기 (openCatalog): 받아 둔 파일 → 없거나 깨졌으면 앱 안 파일', () => {
  const bundledCat = base();
  const bundled = async () => bytesOf(bundledCat);

  it('받아 둔 파일이 없으면 앱 안 파일', async () => {
    const r = await openCatalog({ bundled, cache: new MemoryCache(), appVersion: APP });
    expect(r.origin).toBe('bundled');
    expect(r.catalog.version).toBe('2026.11.05');
  });

  it('받아 둔 파일이 맞으면 그것을 쓴다', async () => {
    const newer = { ...base(), version: '2026.11.20' };
    const bytes = bytesOf(newer);
    const cache = new MemoryCache();
    cache.entry = { bytes, meta: metaFor(bytes, newer) };
    const r = await openCatalog({ bundled, cache, appVersion: APP });
    expect(r.origin).toBe('cache');
    expect(r.catalog.version).toBe('2026.11.20');
  });

  const badCache: [string, (e: { meta: CatalogMeta; bytes: Uint8Array }) => void][] = [
    ['해시가 다름', (e) => (e.meta.sha256 = '0'.repeat(64))],
    ['크기가 다름', (e) => (e.meta.size += 1)],
    ['파일 안 버전이 기록과 다름', (e) => (e.meta.version = '2026.11.21')],
    ['앱 엔진과 다른 엔진', (e) => (e.meta.engineVersion = ENGINE + 1)],
    ['앱을 업데이트함(다른 앱 버전에서 받음)', (e) => (e.meta.appVersion = '0.9.0')],
  ];
  for (const [name, breakIt] of badCache) {
    it(`받아 둔 파일이 ${name} → 지우고 앱 안 파일`, async () => {
      const newer = { ...base(), version: '2026.11.20' };
      const bytes = bytesOf(newer);
      const cache = new MemoryCache();
      cache.entry = { bytes, meta: metaFor(bytes, newer) };
      breakIt(cache.entry);
      const r = await openCatalog({ bundled, cache, appVersion: APP });
      expect(r.origin).toBe('bundled');
      expect(r.cacheProblem).toBeTruthy();
      expect(cache.clears).toBe(1);
    });
  }

  it('받아 둔 파일의 모양이 깨졌으면 → 앱 안 파일', async () => {
    const broken = { ...base(), version: '2026.11.20', perfumes: [] };
    const bytes = bytesOf(broken);
    const cache = new MemoryCache();
    cache.entry = { bytes, meta: metaFor(bytes, broken as Catalog) };
    const r = await openCatalog({ bundled, cache, appVersion: APP });
    expect(r.origin).toBe('bundled');
  });

  it('받아 둔 파일 읽기가 실패해도 → 앱 안 파일', async () => {
    const cache = new MemoryCache();
    cache.read = async () => {
      throw new Error('disk');
    };
    const r = await openCatalog({ bundled, cache, appVersion: APP });
    expect(r.origin).toBe('bundled');
  });

  it('웹처럼 받아 둘 곳이 없으면 앱 안 파일', async () => {
    expect((await openCatalog({ bundled, cache: null, appVersion: APP })).origin).toBe('bundled');
  });

  it('앱 안 파일이 깨졌으면 CatalogError (화면에서 오류 안내)', async () => {
    await expect(openCatalog({ bundled: async () => bytesOf({ version: 'x' }), cache: null, appVersion: APP })).rejects.toThrow(CatalogError);
  });
});

/** 가짜 서버: /catalog/latest 와 /catalog/{version} */
function fakeServer(opts: {
  latest?: unknown;
  latestStatus?: number;
  file?: Uint8Array;
  fileStatus?: number;
  throwOn?: 'latest' | 'file';
}) {
  const calls: { url: string; headers: Record<string, string> }[] = [];
  const fetchFn = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, headers: Object.fromEntries(new Headers(init?.headers).entries()) });
    if (url.endsWith('/catalog/latest')) {
      if (opts.throwOn === 'latest') throw new TypeError('network');
      return new Response(JSON.stringify(opts.latest), { status: opts.latestStatus ?? 200, headers: { 'content-type': 'application/json' } });
    }
    if (opts.throwOn === 'file') throw new TypeError('network');
    return new Response(opts.file ? new Blob([opts.file as BlobPart]) : null, { status: opts.fileStatus ?? 200 });
  }) as typeof fetch;
  return { fetchFn, calls };
}

describe('새 버전 받기 (updateCatalog): 받아 두고 다음 실행부터', () => {
  const current = base();
  const newer = { ...base(), version: '2026.11.20' };
  const newerBytes = bytesOf(newer);
  const latestOk = {
    version: '2026.11.20',
    engineVersion: ENGINE,
    sha256: sha256Hex(newerBytes),
    sizeBytes: newerBytes.length,
    minAppVersion: '1.0.0',
  };
  const deps = (server: ReturnType<typeof fakeServer>, cache = new MemoryCache(), network: NetworkKind = 'wifi', wifiOnly = true) => ({
    cache,
    fetch: server.fetchFn,
    apiBaseUrl: 'https://api.test/api/v1',
    installId: '11111111-2222-4333-8444-555555555555',
    appVersion: APP,
    network: async () => network,
    wifiOnly,
  });

  it('더 새 버전이면 받아서 해시 확인 뒤 저장(설치 id 헤더 포함)', async () => {
    const s = fakeServer({ latest: latestOk, file: newerBytes });
    const cache = new MemoryCache();
    const r = await updateCatalog(current, deps(s, cache));
    expect(r.status).toBe('downloaded');
    expect(cache.entry?.meta).toEqual({ ...metaFor(newerBytes, newer) });
    expect(s.calls[1].url).toBe('https://api.test/api/v1/catalog/2026.11.20');
    expect(s.calls[1].headers['x-install-id']).toBe('11111111-2222-4333-8444-555555555555');
    // 다음 실행: 받아 둔 파일을 쓴다
    const opened = await openCatalog({ bundled: async () => bytesOf(current), cache, appVersion: APP });
    expect(opened.origin).toBe('cache');
    expect(opened.catalog.version).toBe('2026.11.20');
  });

  it('같은 버전이면 파일은 받지 않는다', async () => {
    const s = fakeServer({ latest: { ...latestOk, version: current.version } });
    const r = await updateCatalog(current, deps(s));
    expect(r.status).toBe('up-to-date');
    expect(s.calls).toHaveLength(1);
  });

  it('이미 같은 것을 받아 두었으면 다시 받지 않는다(하루 3회 제한)', async () => {
    const cache = new MemoryCache();
    cache.entry = { bytes: newerBytes, meta: metaFor(newerBytes, newer) };
    const s = fakeServer({ latest: latestOk, file: newerBytes });
    const r = await updateCatalog(current, deps(s, cache));
    expect(r.status).toBe('already-downloaded');
    expect(s.calls).toHaveLength(1);
  });

  const fails: [string, Parameters<typeof fakeServer>[0], string][] = [
    ['해시가 다름', { latest: { ...latestOk, sha256: '0'.repeat(64) }, file: newerBytes }, 'hash'],
    ['크기가 다름', { latest: { ...latestOk, sizeBytes: newerBytes.length + 1 }, file: newerBytes }, 'hash'],
    ['파일 안 버전이 다름', { latest: latestOk, file: undefined }, 'hash'],
    ['받기 제한(429)', { latest: latestOk, file: newerBytes, fileStatus: 429 }, 'rate-limited'],
    ['서버 오류(503)', { latest: latestOk, latestStatus: 503 }, 'server'],
    ['latest 모양이 틀림', { latest: { version: '../x', sha256: 'zz' } }, 'bad-latest'],
    ['네트워크 끊김(latest)', { latest: latestOk, throwOn: 'latest' }, 'network'],
    ['네트워크 끊김(파일)', { latest: latestOk, throwOn: 'file' }, 'network'],
  ];
  for (const [name, server, reason] of fails) {
    it(`실패해도 멈추지 않고 저장하지 않음: ${name}`, async () => {
      const opts = name === '파일 안 버전이 다름' ? (() => {
        const wrong = bytesOf({ ...newer, version: '2026.11.19' });
        return { latest: { ...latestOk, sha256: sha256Hex(wrong), sizeBytes: wrong.length }, file: wrong };
      })() : server;
      const cache = new MemoryCache();
      const r = await updateCatalog(current, deps(fakeServer(opts), cache));
      expect(r.status).toBe('failed');
      expect(r.reason).toBe(reason);
      expect(cache.saves).toBe(0);
    });
  }

  it('받은 파일의 모양이 깨졌으면 저장하지 않음', async () => {
    const bad = bytesOf({ ...newer, perfumes: [] });
    const s = fakeServer({ latest: { ...latestOk, sha256: sha256Hex(bad), sizeBytes: bad.length }, file: bad });
    const cache = new MemoryCache();
    const r = await updateCatalog(current, deps(s, cache));
    expect(r.status).toBe('failed');
    expect(cache.saves).toBe(0);
  });

  const skips: [string, () => Parameters<typeof updateCatalog>, string][] = [
    ['앱 엔진과 다른 엔진', () => [current, deps(fakeServer({ latest: { ...latestOk, engineVersion: ENGINE + 1 } }))], 'engine'],
    ['앱이 최소 버전보다 낮음', () => [current, deps(fakeServer({ latest: { ...latestOk, minAppVersion: '2.0.0' } }))], 'app-too-old'],
    ['인터넷 없음', () => [current, deps(fakeServer({ latest: latestOk }), new MemoryCache(), 'offline')], 'offline'],
    ['폰 데이터(와이파이만)', () => [current, deps(fakeServer({ latest: latestOk }), new MemoryCache(), 'cellular', true)], 'not-wifi'],
    ['샘플 카탈로그(공개 데모)', () => [{ ...current, source: { name: 'sample', note: '가상' } }, deps(fakeServer({ latest: latestOk }))], 'sample'],
  ];
  for (const [name, args, reason] of skips) {
    it(`건너뜀: ${name}`, async () => {
      const [cur, d] = args();
      const r = await updateCatalog(cur, d);
      expect(r.status).toBe('skipped');
      expect(r.reason).toBe(reason);
      expect((d.cache as MemoryCache).saves).toBe(0);
    });
  }

  it('웹(와이파이 구분 없음)은 폰 데이터여도 받는다', async () => {
    const s = fakeServer({ latest: latestOk, file: newerBytes });
    const r = await updateCatalog(current, deps(s, new MemoryCache(), 'cellular', false));
    expect(r.status).toBe('downloaded');
  });

  it('인터넷 없음·샘플이면 서버에 묻지도 않는다', async () => {
    const s = fakeServer({ latest: latestOk });
    await updateCatalog(current, deps(s, new MemoryCache(), 'offline'));
    await updateCatalog({ ...current, source: { name: 'sample', note: '' } }, deps(s));
    expect(s.calls).toHaveLength(0);
  });
});
