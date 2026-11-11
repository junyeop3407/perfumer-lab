/**
 * 카탈로그 열기와 새 버전 받기 (design.md 10절 "카탈로그", plan_stage5.md 5-1).
 * - openCatalog: 받아 둔 파일(폰)이 있고 sha256·크기·버전·엔진·앱 버전이 맞으면 그것을, 아니면 앱 안에 넣은 파일을 쓴다.
 *   받아 둔 파일이 깨졌으면 지운다. 앱 안 파일이 깨졌으면 CatalogError(화면에서 안내).
 * - updateCatalog: 서버 /catalog/latest를 보고 더 새 버전이면 /catalog/{version}을 받아 확인 뒤 저장한다.
 *   지금 실행에는 바꾸지 않고 다음 실행부터 쓴다. 폰은 와이파이에서만. 실패해도 예외를 던지지 않는다(앱은 그대로).
 * 파일·네트워크는 밖에서 넣어 준다(platform.ts / platform.native.ts). 여기는 순수 로직이라 Vitest로 확인한다.
 */
import { EngineParams } from '../engine/params';
import type { Catalog } from '../engine/types';
import { CatalogError, parseCatalogBytes, sha256Hex, validateCatalog } from './loadCatalog';

/** 받아 둔 파일 옆에 적는 기록 */
export type CatalogMeta = { version: string; sha256: string; size: number; engineVersion: number; appVersion: string };

/** 받은 뒤 실패한 파일(같은 version·sha256은 하루 동안 다시 받지 않는다, 리뷰 5-1 QA 중간-1) */
export type FailureNote = { version: string; sha256: string; at: number };

export interface CatalogCache {
  read(): Promise<{ meta: CatalogMeta; bytes: Uint8Array } | null>;
  save(bytes: Uint8Array, meta: CatalogMeta): Promise<void>;
  clear(): Promise<void>;
  readFailure?(): Promise<FailureNote | null>;
  saveFailure?(f: FailureNote | null): Promise<void>;
}

const FAILURE_WAIT_MS = 24 * 3600_000;

export type NetworkKind = 'wifi' | 'cellular' | 'offline' | 'unknown';

export type OpenResult = { catalog: Catalog; origin: 'cache' | 'bundled'; cacheProblem?: string };

export type UpdateResult = {
  status: 'downloaded' | 'up-to-date' | 'already-downloaded' | 'skipped' | 'failed';
  reason?: 'sample' | 'offline' | 'not-wifi' | 'engine' | 'app-too-old' | 'network' | 'server' | 'rate-limited' | 'bad-latest' | 'hash' | 'bad-file' | 'storage' | 'recent-failure' | 'bad-app-version';
  version?: string;
};

const CATALOG_VERSION = /^([0-9]{4})\.(0[1-9]|1[0-2])\.(0[1-9]|[12][0-9]|3[01])(?:\.([1-9][0-9]{0,2}))?$/;
const APP_VERSION = /^(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,3})$/;
const HEX64 = /^[0-9a-f]{64}$/;
const MAX_BYTES = 50_000_000;
const TIMEOUT_MS = 60_000; // 무료 서버가 깨어나는 데 약 43초

function catalogParts(v: string): number[] {
  const m = CATALOG_VERSION.exec(v);
  return m ? [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4] ?? 0)] : [-1, 0, 0, 0];
}

function compareParts(a: number[], b: number[]): number {
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const d = (a[i] ?? 0) - (b[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}

/** YYYY.MM.DD(.n). 모양이 다른 버전(테스트·샘플)은 가장 낮게 본다 */
export function compareCatalogVersion(a: string, b: string): number {
  return compareParts(catalogParts(a), catalogParts(b));
}

export function compareAppVersion(a: string, b: string): number {
  return compareParts(a.split('.').map(Number), b.split('.').map(Number));
}

async function checkedBytes(bytes: Uint8Array): Promise<Catalog> {
  const catalog = validateCatalog(await parseCatalogBytes(bytes));
  if (catalog.engineVersion !== EngineParams.ENGINE_VERSION) throw new CatalogError('앱 엔진과 다른 엔진 버전의 카탈로그입니다');
  return catalog;
}

async function fromCache(cache: CatalogCache, appVersion: string): Promise<Catalog> {
  const entry = await cache.read();
  if (!entry) throw new CatalogError('없음');
  const { meta, bytes } = entry;
  if (meta.appVersion !== appVersion) throw new CatalogError('다른 앱 버전에서 받은 파일');
  if (meta.engineVersion !== EngineParams.ENGINE_VERSION) throw new CatalogError('다른 엔진');
  if (bytes.length !== meta.size || sha256Hex(bytes) !== meta.sha256) throw new CatalogError('해시·크기가 기록과 다름');
  const catalog = await checkedBytes(bytes);
  if (catalog.version !== meta.version) throw new CatalogError('파일 안 버전이 기록과 다름');
  return catalog;
}

export async function openCatalog(deps: {
  bundled: () => Promise<Uint8Array>;
  cache: CatalogCache | null;
  appVersion: string;
}): Promise<OpenResult> {
  let cacheProblem: string | undefined;
  if (deps.cache) {
    try {
      return { catalog: await fromCache(deps.cache, deps.appVersion), origin: 'cache' };
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg !== '없음') {
        cacheProblem = msg;
        await deps.cache.clear().catch(() => undefined);
      }
    }
  }
  const catalog = await checkedBytes(await deps.bundled());
  return { catalog, origin: 'bundled', cacheProblem };
}

type Latest = { version: string; engineVersion: number; sha256: string; sizeBytes: number; minAppVersion: string };

function isLatest(v: unknown): v is Latest {
  const x = v as Latest;
  return (
    typeof x === 'object' && x !== null &&
    typeof x.version === 'string' && CATALOG_VERSION.test(x.version) &&
    Number.isInteger(x.engineVersion) && x.engineVersion > 0 &&
    typeof x.sha256 === 'string' && HEX64.test(x.sha256) &&
    Number.isInteger(x.sizeBytes) && x.sizeBytes > 0 && x.sizeBytes <= MAX_BYTES &&
    typeof x.minAppVersion === 'string' && APP_VERSION.test(x.minAppVersion)
  );
}

async function timedFetch(fetchFn: typeof fetch, url: string, init: RequestInit = {}): Promise<Response> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    return await fetchFn(url, { ...init, signal: ctrl.signal });
  } finally {
    clearTimeout(timer);
  }
}

export async function updateCatalog(
  current: Catalog,
  deps: {
    cache: CatalogCache;
    fetch: typeof fetch;
    apiBaseUrl: string;
    installId: string;
    appVersion: string;
    network: () => Promise<NetworkKind>;
    /** 폰: true(와이파이에서만). 웹: false */
    wifiOnly: boolean;
    /** 지금 시각(ms). 테스트용 */
    now?: () => number;
  },
): Promise<UpdateResult> {
  if (current.source.name.toLowerCase() === 'sample') return { status: 'skipped', reason: 'sample' };
  const net = await deps.network().catch((): NetworkKind => 'unknown');
  if (net === 'offline') return { status: 'skipped', reason: 'offline' };
  if (deps.wifiOnly && net !== 'wifi') return { status: 'skipped', reason: 'not-wifi' };

  const base = deps.apiBaseUrl.replace(/\/+$/, '');
  let latest: unknown;
  try {
    const res = await timedFetch(deps.fetch, `${base}/catalog/latest`);
    if (res.status === 429) return { status: 'failed', reason: 'rate-limited' };
    if (res.status !== 200) return { status: 'failed', reason: 'server' };
    latest = await res.json();
  } catch {
    return { status: 'failed', reason: 'network' };
  }
  if (!isLatest(latest)) return { status: 'failed', reason: 'bad-latest' };
  if (latest.engineVersion !== EngineParams.ENGINE_VERSION) return { status: 'skipped', reason: 'engine', version: latest.version };
  // 앱 버전 모양이 틀리면(예: 1.0.0-rc1) 비교가 NaN이 되어 최소 버전 검사를 통과해 버린다(리뷰 5-1 QA 낮음-1)
  if (!APP_VERSION.test(deps.appVersion)) return { status: 'skipped', reason: 'bad-app-version', version: latest.version };
  if (compareAppVersion(deps.appVersion, latest.minAppVersion) < 0) return { status: 'skipped', reason: 'app-too-old', version: latest.version };
  if (compareCatalogVersion(latest.version, current.version) <= 0) return { status: 'up-to-date', version: current.version };

  const have = await deps.cache.read().catch(() => null);
  if (have && have.meta.version === latest.version && have.meta.sha256 === latest.sha256 && have.meta.appVersion === deps.appVersion) {
    return { status: 'already-downloaded', version: latest.version };
  }

  const now = deps.now ?? Date.now;
  const failed = await deps.cache.readFailure?.().catch(() => null);
  if (failed && failed.version === latest.version && failed.sha256 === latest.sha256 && now() - failed.at < FAILURE_WAIT_MS) {
    return { status: 'skipped', reason: 'recent-failure', version: latest.version };
  }

  // 파일 요청을 보낸 뒤의 실패는 서버가 이미 하루 3회에서 셌다 → 기록해 두고 하루 동안 같은 파일은 다시 받지 않는다
  const latestRef = latest;
  const fail = async (reason: NonNullable<UpdateResult['reason']>): Promise<UpdateResult> => {
    await deps.cache.saveFailure?.({ version: latestRef.version, sha256: latestRef.sha256, at: now() }).catch(() => undefined);
    return { status: 'failed', reason, version: latestRef.version };
  };

  let bytes: Uint8Array;
  try {
    const res = await timedFetch(deps.fetch, `${base}/catalog/${encodeURIComponent(latest.version)}`, {
      headers: { 'X-Install-Id': deps.installId },
    });
    if (res.status === 429) return fail('rate-limited');
    if (res.status !== 200) return fail('server');
    bytes = new Uint8Array(await res.arrayBuffer());
  } catch {
    return fail('network');
  }
  if (bytes.length !== latest.sizeBytes || sha256Hex(bytes) !== latest.sha256) return fail('hash');
  let catalog: Catalog;
  try {
    catalog = await checkedBytes(bytes);
  } catch {
    return fail('bad-file');
  }
  if (catalog.version !== latest.version) return fail('hash');
  try {
    await deps.cache.save(bytes, {
      version: latest.version,
      sha256: latest.sha256,
      size: bytes.length,
      engineVersion: catalog.engineVersion,
      appVersion: deps.appVersion,
    });
  } catch {
    return fail('storage');
  }
  await deps.cache.saveFailure?.(null).catch(() => undefined);
  return { status: 'downloaded', version: latest.version };
}

/**
 * 앱 시작용: 카탈로그를 열고, 받아 둘 곳이 있으면(폰) 뒤에서 새 버전 확인을 **한 번 실행에 한 번만** 돌린다.
 * 화면이 다시 그려지거나 개발 모드에서 두 번 불려도 하루 3회 제한을 두 번 쓰거나 같은 임시 파일로 경쟁하지 않는다(리뷰 5-1 QA 중간-2).
 */
export function catalogStarter(env: {
  bundled: () => Promise<Uint8Array>;
  cache: CatalogCache | null;
  network: () => Promise<NetworkKind>;
  wifiOnly: boolean;
  appVersion: string;
  apiBaseUrl: string;
  fetch: typeof fetch;
}) {
  let started = false;
  return async (opts: { installId: string; onUpdate?: (r: UpdateResult) => void }): Promise<OpenResult> => {
    const opened = await openCatalog({ bundled: env.bundled, cache: env.cache, appVersion: env.appVersion });
    if (env.cache && !started) {
      started = true;
      void updateCatalog(opened.catalog, {
        cache: env.cache,
        fetch: env.fetch,
        apiBaseUrl: env.apiBaseUrl,
        installId: opts.installId,
        appVersion: env.appVersion,
        network: env.network,
        wifiOnly: env.wifiOnly,
      }).then((r) => opts.onUpdate?.(r));
    }
    return opened;
  };
}
