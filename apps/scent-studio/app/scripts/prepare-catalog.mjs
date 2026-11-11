// 빌드 전에 앱에 넣을 카탈로그를 고른다 (plan_stage5.md 1-4). `expo export`·`expo prebuild` 앞에 돈다.
//
//   CATALOG_SOURCE=real   node scripts/prepare-catalog.mjs
//       backend `./gradlew dbCatalogFetch -Papply=true`로 받은 실제 카탈로그를 확인만 한다:
//       파일이 있고 · 받을 때 남긴 catalog.release.json(catalog_releases의 버전·sha256·크기)과 같고 · 샘플이 아니어야 통과.
//   CATALOG_SOURCE=sample node scripts/prepare-catalog.mjs [--public]
//       pipeline/sample_catalog.py로 가상 카탈로그를 같은 자리에 만들고, 실제 이름 검사(pipeline/real_names.py)를 돌린다.
//       --public(공개 데모 빌드, Plan B Pages): sample만 허용하고, 실제 이름 검사를 건너뛰면(키·목록 없음) 실패.
//
// 키·이름은 출력하지 않는다. 받은 파일·만든 파일은 커밋하지 않는다(.gitignore).
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gunzipSync } from 'node:zlib';

const APP = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const PIPELINE = resolve(APP, '..', 'pipeline');
export const DEFAULT_FILE = join(APP, 'assets', 'catalog', 'catalog.json.gz');
const FETCH_HINT = '실제 카탈로그 받기: cd backend && ./gradlew dbCatalogFetch (미리 보기) → ./gradlew dbCatalogFetch -Papply=true';

export function releaseNotePath(file) {
  return join(dirname(file), 'catalog.release.json');
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
  if (!existsSync(note)) throw new Error(`받을 때 남긴 기록(catalog.release.json)이 없습니다. ${FETCH_HINT}`);
  let want;
  try {
    want = JSON.parse(readFileSync(note, 'utf8'));
  } catch {
    want = null;
  }
  if (!want || typeof want.version !== 'string' || !/^[0-9a-f]{64}$/.test(String(want.sha256)) || !Number.isInteger(want.size)) {
    throw new Error(`catalog.release.json 모양이 틀립니다. 다시 받아 주세요. ${FETCH_HINT}`);
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
  if (!Number.isInteger(cat.engineVersion) || cat.engineVersion < 1) throw new Error('카탈로그에 engineVersion이 없습니다');
  if (String(cat.source?.name ?? '').toLowerCase() === 'sample') throw new Error('샘플 카탈로그입니다. 실제 빌드에는 쓸 수 없습니다');
  return { source: 'real', version: cat.version };
}

function makeSample(file, publicBuild, env, python) {
  const pyCmd = python ?? findPython(env);
  rmSync(releaseNotePath(file), { force: true }); // 실제 카탈로그 기록이 남아 있으면 샘플이 real로 통과하지 않게
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
  return { source: 'sample', version: cat.version, nameCheck: chk.stdout.includes('건너뜀') ? 'skipped' : 'pass', message: chk.stdout.trim() };
}

/**
 * source: 'real' | 'sample'. 맞지 않으면 Error를 던진다
 * @param {{ source?: string, file?: string, publicBuild?: boolean, env?: NodeJS.ProcessEnv, python?: string }} [opts]
 * @returns {{ source: string, version: string, nameCheck?: 'pass' | 'skipped', message?: string }}
 */
export function prepareCatalog({ source, file = DEFAULT_FILE, publicBuild = false, env = process.env, python } = {}) {
  if (source !== 'real' && source !== 'sample') {
    throw new Error('CATALOG_SOURCE를 real 또는 sample로 정해 주세요 (예: CATALOG_SOURCE=sample npm run export:web)');
  }
  if (publicBuild && source !== 'sample') throw new Error('공개 데모 빌드는 CATALOG_SOURCE=sample만 허용합니다');
  return source === 'real' ? checkReal(file) : makeSample(file, publicBuild, env, python);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const publicBuild = process.argv.includes('--public') || process.env.CATALOG_PUBLIC === '1';
    const r = prepareCatalog({ source: process.env.CATALOG_SOURCE, publicBuild });
    console.log(`카탈로그 준비: ${r.source} (${r.version})${r.message ? `\n${r.message}` : ''}`);
  } catch (e) {
    console.error(`카탈로그 준비 실패: ${e.message}`);
    process.exit(1);
  }
}
