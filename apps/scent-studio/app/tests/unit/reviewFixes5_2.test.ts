import { describe, expect, it, vi } from 'vitest';

import { makeTapLock } from '../../src/ui/tapLock';

/** 5-2 망가뜨려 보기에서 나온 문제의 회귀 테스트 */
describe('연속 클릭 잠금 (새 조합 만들기 두 번 → 화면 두 번 쌓임)', () => {
  it('같은 이동을 잠금 시간 안에 또 누르면 무시하고, 지나면 다시 된다', () => {
    let t = 0;
    const lock = makeTapLock(() => t, 600);
    const calls: string[] = [];
    lock('/families', () => calls.push('a'));
    t = 100;
    lock('/families', () => calls.push('b'));
    expect(calls).toEqual(['a']);
    t = 700;
    lock('/families', () => calls.push('c'));
    expect(calls).toEqual(['a', 'c']);
  });

  it('다른 이동은 바로 이어서 눌러도 된다 (계열 화면을 열자마자 "다음")', () => {
    let t = 0;
    const lock = makeTapLock(() => t, 600);
    const calls: string[] = [];
    lock('/families', () => calls.push('families'));
    t = 50;
    lock('/notes', () => calls.push('notes'));
    expect(calls).toEqual(['families', 'notes']);
  });
});

import { readdirSync, readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';

import { addNote, applyFamilies, emptyDraft } from '../../src/blend/blendDraft';

describe('QA 5-2 중간: "새 조합 만들기"를 눌러도 계열 화면에서 "다음"을 누르기 전까지 만들던 조합이 남는다', () => {
  const withLemon = () => {
    const r = addNote(emptyDraft(['citrus']), 'top', 'lemon');
    if (!r.ok) throw new Error('준비 실패');
    return r.draft;
  };
  it('새 조합으로 "다음"을 누르면 고른 계열만 남고 노트는 비운다', () => {
    expect(applyFamilies(withLemon(), ['woody'], true)).toEqual({ families: ['woody'], notes: [] });
  });
  it('이어 하기에서 계열만 바꾸면 노트는 그대로', () => {
    const d = applyFamilies(withLemon(), ['woody'], false);
    expect(d.families).toEqual(['woody']);
    expect(d.notes.map((n) => n.noteKey)).toEqual(['lemon']);
  });
});

describe('QA 5-2 낮음: 화면 코드 속 한글 문구와 카탈로그 이름도 글꼴 서브셋 안', () => {
  const APP = path.resolve(__dirname, '../..');
  const charset = new Set([...readFileSync(path.join(APP, 'assets/fonts/charset.txt'), 'utf-8')]);
  const outside = (text: string) => [...new Set([...text].filter((c) => !/\s/.test(c) && !charset.has(c)))];
  const walk = (dir: string): string[] =>
    readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
      e.isDirectory() ? walk(path.join(dir, e.name)) : /\.tsx?$/.test(e.name) ? [path.join(dir, e.name)] : [],
    );

  it('app/·src/의 문자열 안 한글·기호', () => {
    const bad: string[] = [];
    for (const f of [...walk(path.join(APP, 'app')), ...walk(path.join(APP, 'src'))]) {
      const src = readFileSync(f, 'utf-8');
      // 따옴표 안 글자만 본다(주석은 화면에 안 나온다)
      const strings = src.match(/'[^'\n]*'|`[^`]*`|"[^"\n]*"/g) ?? [];
      const o = outside(strings.join(''));
      if (o.length) bad.push(`${path.relative(APP, f)}: ${o.join('')}`);
    }
    expect(bad).toEqual([]);
  });

  it('실제 카탈로그(있을 때만)의 노트·계열·어코드 한글 이름', () => {
    const p = path.join(APP, 'assets/catalog/catalog.real.json.gz');
    if (!existsSync(p)) return;
    const c = JSON.parse(gunzipSync(readFileSync(p)).toString('utf-8'));
    const names = [
      ...c.notes.filter((n: { tier: string }) => n.tier !== 'hidden').flatMap((n: { nameKo: string; description: string }) => [n.nameKo, n.description]),
      ...c.families.map((f: { nameKo: string }) => f.nameKo),
      ...c.accords.map((a: { nameKo: string }) => a.nameKo),
    ];
    expect(outside(names.join(''))).toEqual([]);
  });
});

import { familiesNext, pairingsFor } from '../../src/blend/blendDraft';
import { fixtureCatalog } from './testCatalogs';

describe('Gemini 5-2 중간: 새 조합으로 들어와 노트를 고른 뒤 계열 화면으로 돌아와 [다음]을 다시 눌러도 노트가 남는다', () => {
  it('처음 [다음]만 초안을 비우고, 그다음부터는 계열만 바꾼다', () => {
    let d = emptyDraft(['citrus']);
    let fresh = true;
    ({ draft: d, freshPending: fresh } = familiesNext(d, ['citrus'], fresh));
    const r = addNote(d, 'top', 'lemon');
    if (!r.ok) throw new Error('준비 실패');
    d = r.draft;
    ({ draft: d, freshPending: fresh } = familiesNext(d, ['citrus', 'woody'], fresh));
    expect(d.families).toEqual(['citrus', 'woody']);
    expect(d.notes.map((n) => n.noteKey)).toEqual(['lemon']);
    expect(fresh).toBe(false);
  });
});

describe('Gemini 5-2 중간: 궁합 계산이 노트 카드를 열 때마다 전체 노트를 정렬하지 않는다', () => {
  // 시간으로 재면 다른 테스트와 같이 돌 때 흔들려서(2026-11-12 33ms > 30ms), 정렬 횟수를 센다
  it('같은 카탈로그에서 두 번째부터는 전체 노트를 다시 정렬하지 않는다', () => {
    const base = fixtureCatalog();
    const many = Array.from({ length: 3000 }, (_, i) => ({ ...base.notes[0], key: `n${i}`, df: i % 50, pairings: [] }));
    const cat = { ...base, notes: [...base.notes, ...many] };
    pairingsFor(cat, cat.notes[0]); // 첫 번에만 준비(정렬)
    const sort = vi.spyOn(Array.prototype, 'sort');
    try {
      for (let i = 0; i < 300; i++) pairingsFor(cat, cat.notes[i % cat.notes.length]);
      expect(sort).not.toHaveBeenCalled();
    } finally {
      sort.mockRestore();
    }
  });
});
