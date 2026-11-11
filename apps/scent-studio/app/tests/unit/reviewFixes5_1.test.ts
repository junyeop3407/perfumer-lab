import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';
import { beforeEach, describe, expect, it } from 'vitest';

import { catalogPaths, prepareCatalog } from '../../scripts/prepare-catalog.mjs';

/** 5-1 리뷰(QA 낮음-6) 회귀 테스트: real 확인에서 기록·카탈로그가 깨졌을 때 알아듣기 쉬운 오류로 멈춘다 */
let dir: string;
let file: string;
let note: string;
let publicDir: string;
const sha = (b: Buffer) => createHash('sha256').update(b).digest('hex');

function put(body: Buffer, record: unknown = { version: '2099.01.02', sha256: sha(body), size: body.length }) {
  writeFileSync(file, body);
  writeFileSync(note, typeof record === 'string' ? record : JSON.stringify(record));
}

beforeEach(() => {
  dir = join(mkdtempSync(join(tmpdir(), 'catalog-fix-')), 'assets', 'catalog');
  mkdirSync(dir, { recursive: true });
  publicDir = join(dir, '..', '..', 'public');
  ({ real: file, realNote: note } = catalogPaths(dir, publicDir));
});

describe('real 확인: 깨진 입력', () => {
  it('기록 파일 모양이 틀리면(빈 객체·깨진 JSON) 기록 문제라고 알린다', () => {
    const body = gzipSync(JSON.stringify({ version: '2099.01.02', engineVersion: 8, source: { name: 'Parfumo' } }));
    put(body, {});
    expect(() => prepareCatalog({ source: 'real', dir, publicDir })).toThrow(/catalog\.real\.release\.json 모양/);
    put(body, '{not json');
    expect(() => prepareCatalog({ source: 'real', dir, publicDir })).toThrow(/catalog\.real\.release\.json 모양/);
  });

  it('카탈로그가 깨졌으면(해시는 맞아도) 읽지 못했다고 알린다', () => {
    put(Buffer.from([0x1f, 0x8b, 1, 2, 3]));
    expect(() => prepareCatalog({ source: 'real', dir, publicDir })).toThrow(/카탈로그를 읽지 못했습니다/);
  });

  it('engineVersion이 없으면 멈춘다', () => {
    put(gzipSync(JSON.stringify({ version: '2099.01.02', source: { name: 'Parfumo' } })));
    expect(() => prepareCatalog({ source: 'real', dir, publicDir })).toThrow(/engineVersion/);
  });
});
