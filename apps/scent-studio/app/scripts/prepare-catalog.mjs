// 빌드 전에 앱에 넣을 카탈로그를 고른다 (plan_stage5.md 1-4). `expo export`·`expo prebuild` 앞에 돈다.
//
// 자리 (app/assets/catalog/, 모두 커밋 안 함 — .gitignore):
//   catalog.real.json.gz        실제(backend `./gradlew dbCatalogFetch -Papply=true`가 받음)
//   catalog.real.release.json   받을 때 남긴 catalog_releases 기록(버전·sha256·크기)
//   catalog.sample.json.gz      샘플(pipeline/sample_catalog.py가 만듦)
//   catalog.json.gz             이번 빌드에 넣을 것(폰 앱) ← 고른 쪽을 복사. 웹은 app/public/catalog.json.gz에도 복사
// 실제·샘플 파일은 따로 두므로 CATALOG_SOURCE를 바꿔도 다시 받거나 다시 만들 필요가 없다(사장 결정 2026-11-11).
//
//   CATALOG_SOURCE=real   node scripts/prepare-catalog.mjs
//       실제 파일이 있고 · 기록과 해시·크기·버전이 같고 · 샘플이 아니어야 통과 → 복사.
//   CATALOG_SOURCE=sample node scripts/prepare-catalog.mjs [--public]
//       샘플을 만들고(같은 seed라 매번 같은 바이트) 실제 이름 검사(pipeline/real_names.py) → 복사.
//       --public(공개 데모 빌드, Plan B Pages): sample만 허용, 실제 이름 검사를 건너뛰면(키·목록 없음) 실패.
//
// 키·이름은 출력하지 않는다.
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync, renameSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gunzipSync } from 'node:zlib';

const APP = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const PIPELINE = resolve(APP, '..', 'pipeline');
export const CATALOG_DIR = join(APP, 'assets', 'catalog');
export const PUBLIC_DIR = join(APP, 'public');
const FETCH_HINT = '실제 카탈로그 받기: cd backend && ./gradlew dbCatalogFetch (미리 보기) → ./gradlew dbCatalogFetch -Papply=true';

/** 자리 이름들 */
export function catalogPaths(dir = CATALOG_DIR, publicDir = PUBLIC_DIR) {
  return {
    real: join(dir, 'catalog.real.json.gz'),
    realNote: join(dir, 'catalog.real.release.json'),
    sample: join(dir, 'catalog.sample.json.gz'),
    active: join(dir, 'catalog.json.gz'),
    web: join(publicDir, 'catalog.json.gz'),
  };
}

/** 받기 기록 이름: catalog.real.json.gz → catalog.real.release.json (backend CatalogFetch.releaseNote와 같은 규칙) */
export function releaseNotePath(file) {
  return join(dirname(file), basename(file).replace(/\.json\.gz$/, '') + '.release.json');
}

function readCatalog(bytes) {
  return JSON.parse((bytes[0] === 0x1f && bytes[1] === 0x8b ? gunzipSync(bytes) : bytes).toString('utf8'));
}

function findPython(env) {
  const tries = [env.SCENT_PY, 'py', 'python3', 'python'].filter(Boolean);
  for (const cmd of tries) {
    const r = spawnSync(cmd, ['-I', '-c', 'import sys; sys.exit(sys.version_info < (3, 12))'], { env, encoding: 'utf8' });
    if (r.status === 0) return cmd;
  }
  throw new Error('Python 3.12 이상을 찾지 못했습니다(샘플 카탈로그 생성에 필요). Windows: py / Mac: python3.12');
}

function py(python, args, env) {
  return spawnSync(python, ['-I', '-X', 'utf8', ...args], { env, encoding: 'utf8' });
}

function checkReal(file) {
  if (!existsSync(file)) throw new Error(`실제 카탈로그 파일이 없습니다. ${FETCH_HINT}`);
  const note = releaseNotePath(file);
  if (!existsSync(note)) throw new Error(`받을 때 남긴 기록(${basename(note)})이 없습니다. ${FETCH_HINT}`);
  let want;
  try {
    want = JSON.parse(readFileSync(note, 'utf8'));
  } catch {
    want = null;
  }
  if (!want || typeof want.version !== 'string' || !/^[0-9a-f]{64}$/.test(String(want.sha256)) || !Number.isInteger(want.size)) {
    throw new Error(`${basename(note)} 모양이 틀립니다. 다시 받아 주세요. ${FETCH_HINT}`);
  }
  const body = readFileSync(file);
  const sha = createHash('sha256').update(body).digest('hex');
  if (sha !== want.sha256 || body.length !== want.size) {
    throw new Error(`카탈로그 파일 해시가 catalog_releases 기록과 다릅니다. 다시 받아 주세요. ${FETCH_HINT}`);
  }
  let cat;
  try {
    cat = readCatalog(body);
  } catch (e) {
    throw new Error(`카탈로그를 읽지 못했습니다(${e.code ?? e.name}). 다시 받아 주세요. ${FETCH_HINT}`);
  }
  if (cat.version !== want.version) throw new Error(`파일 안 버전이 기록(${want.version})과 다릅니다. ${FETCH_HINT}`);
  // 앱의 새 버전 비교(catalogUpdate.ts)와 같은 모양이어야 한다. 모양이 다르면 매번 "더 새 버전"으로 보여 받기를 낭비한다(리뷰 5-1 QA 낮음-6)
  if (!/^[0-9]{4}\.(0[1-9]|1[0-2])\.(0[1-9]|[12][0-9]|3[01])(\.[1-9][0-9]{0,2})?$/.test(String(cat.version))) {
    throw new Error('실제 카탈로그 버전 모양이 틀립니다(YYYY.MM.DD 또는 YYYY.MM.DD.n)');
  }
  if (!Number.isInteger(cat.engineVersion) || cat.engineVersion < 1) throw new Error('카탈로그에 engineVersion이 없습니다');
  if (String(cat.source?.name ?? '').toLowerCase() === 'sample') throw new Error('샘플 카탈로그입니다. 실제 빌드에는 쓸 수 없습니다');
  return cat.version;
}

function makeSample(file, publicBuild, env, python) {
  const pyCmd = python ?? findPython(env);
  const gen = py(pyCmd, [join(PIPELINE, 'sample_catalog.py'), '--out', file], env);
  if (gen.status !== 0) throw new Error(`샘플 카탈로그를 만들지 못했습니다\n${gen.stderr}`);
  const args = [join(PIPELINE, 'real_names.py'), 'check', file];
  if (publicBuild) args.push('--require');
  const chk = py(pyCmd, args, env);
  if (chk.status === 1) throw new Error(`실제 이름 검사 실패: 샘플에 실제 브랜드·향수 이름이 있습니다\n${chk.stdout}`);
  if (chk.status === 3) {
    throw new Error('실제 이름 검사를 건너뛸 수 없습니다(공개 빌드). SCENT_KEY_SECRET과 비공개 카탈로그가 있는 컴퓨터에서 빌드해 주세요');
  }
  // 2 = 검사를 돌리지 못함(키·목록 불일치, 파일 깨짐). 원인은 표준 출력에 있다(리뷰 5-1 QA 중간-1)
  if (chk.status !== 0) throw new Error(`실제 이름 검사를 돌리지 못했습니다(종료 코드 ${chk.status})\n${chk.stdout}${chk.stderr}`);
  const cat = readCatalog(readFileSync(file));
  if (String(cat.source?.name ?? '').toLowerCase() !== 'sample') throw new Error('샘플 생성 결과의 source가 sample이 아닙니다');
  return { version: cat.version, nameCheck: chk.stdout.includes('건너뜀') ? 'skipped' : 'pass', message: chk.stdout.trim() };
}

/** 임시 파일에 쓴 뒤 바꿔치기(도중에 멈춰도 반쪽 파일이 남지 않게) */
function place(from, to) {
  mkdirSync(dirname(to), { recursive: true });
  const tmp = `${to}.tmp`;
  copyFileSync(from, tmp);
  renameSync(tmp, to);
}

/**
 * source: 'real' | 'sample'. 고른 쪽을 이번 빌드 자리(폰·웹)에 복사한다. 맞지 않으면 Error를 던진다
 * @param {{ source?: string, dir?: string, publicDir?: string, publicBuild?: boolean, env?: NodeJS.ProcessEnv, python?: string }} [opts]
 * @returns {{ source: string, version: string, nameCheck?: 'pass' | 'skipped', message?: string }}
 */
export function prepareCatalog({ source, dir = CATALOG_DIR, publicDir = PUBLIC_DIR, publicBuild = false, env = process.env, python } = {}) {
  if (source !== 'real' && source !== 'sample') {
    throw new Error('CATALOG_SOURCE를 real 또는 sample로 정해 주세요 (예: CATALOG_SOURCE=sample npm run export:web)');
  }
  if (publicBuild && source !== 'sample') throw new Error('공개 데모 빌드는 CATALOG_SOURCE=sample만 허용합니다');
  const p = catalogPaths(dir, publicDir);
  let result;
  if (source === 'real') {
    result = { source, version: checkReal(p.real) };
    place(p.real, p.active);
    place(p.real, p.web);
  } else {
    result = { source, ...makeSample(p.sample, publicBuild, env, python) };
    place(p.sample, p.active);
    place(p.sample, p.web);
  }
  if (publicBuild) {
    const want = readFileSync(p.sample);
    for (const f of [p.active, p.web]) {
      if (!readFileSync(f).equals(want)) throw new Error('공개 빌드 자리에 샘플이 아닌 파일이 있습니다');
    }
  }
  return result;
}

/**
 * 공개 빌드가 끝난 뒤 결과물(dist)의 카탈로그가 샘플과 같은지 대조한다.
 * `npx expo export`를 준비 없이 바로 돌려 남아 있던 실제 파일이 들어가는 경로를 막는다(리뷰 5-1 QA 중간-5).
 * @param {string} distDir
 * @param {string} [dir]
 */
export function verifyPublicDist(distDir, dir = CATALOG_DIR) {
  const out = join(distDir, 'catalog.json.gz');
  if (!existsSync(out)) throw new Error('공개 빌드 결과물에 catalog.json.gz가 없습니다');
  const sample = catalogPaths(dir).sample;
  if (!existsSync(sample) || !readFileSync(out).equals(readFileSync(sample))) {
    throw new Error('공개 빌드 결과물의 카탈로그가 샘플이 아닙니다. 배포하지 마세요(npm run export:web:public으로 다시 빌드)');
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const at = process.argv.indexOf('--verify-dist');
  if (at > 0) {
    try {
      verifyPublicDist(resolve(process.argv[at + 1] ?? 'dist'));
      console.log('공개 빌드 결과물 확인: 카탈로그 = 샘플');
    } catch (e) {
      console.error(`공개 빌드 결과물 확인 실패: ${e.message}`);
      process.exit(1);
    }
    process.exit(0);
  }
  try {
    const publicBuild = process.argv.includes('--public') || process.env.CATALOG_PUBLIC === '1';
    const r = prepareCatalog({ source: process.env.CATALOG_SOURCE, publicBuild });
    console.log(`카탈로그 준비: ${r.source} (${r.version})${r.message ? `\n${r.message}` : ''}`);
  } catch (e) {
    console.error(`카탈로그 준비 실패: ${e.message}`);
    process.exit(1);
  }
}
