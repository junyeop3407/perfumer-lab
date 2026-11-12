/**
 * 만드는 중인 조합(초안)의 순수 로직. 화면(5-2)은 이 함수들만 부른다.
 * - 층마다 3개까지, 같은 노트는 조합 전체에서 한 번만 (엔진 validate와 같은 규칙, design.md 8절)
 * - 노트 목록은 많이 쓰는 노트(tier browse)만, 고른 계열 먼저. 검색은 검색 전용(search)까지
 * - 균형 안내 코드(엔진) → 화면 문구 (design.md 8-7)
 */
import { EngineParams } from '../engine/params';
import type { RecommendationEngine } from '../engine/RecommendationEngine';
import type { BlendNote, Catalog, Family, Layer, Note } from '../engine/types';
import type { IntensityLevel } from '../ui/testIds';

export type DraftNote = { noteKey: string; layer: Layer; level: IntensityLevel };
export type Draft = { families: string[]; notes: DraftNote[] };

export type AddResult =
  | { ok: true; draft: Draft }
  | { ok: false; error: 'LAYER_LIMIT' | 'NOT_FOUND' }
  | { ok: false; error: 'IN_OTHER_LAYER'; layer: Layer };

export const LAYER_LABEL: Record<Layer, string> = { top: 'TOP', middle: 'MIDDLE', base: 'BASE' };
export const LAYER_KO: Record<Layer, string> = { top: '탑', middle: '미들', base: '베이스' };
export const LEVEL_KO: Record<IntensityLevel, string> = { weak: '약', medium: '중', strong: '강' };
export const LEVELS: readonly IntensityLevel[] = ['weak', 'medium', 'strong'];

export function emptyDraft(families: string[]): Draft {
  return { families: [...families], notes: [] };
}

const usable = (n: Note | undefined): n is Note => !!n && n.tier !== 'hidden';

export function addNote(draft: Draft, layer: Layer, noteKey: string, catalog?: Catalog): AddResult {
  if (catalog && !usable(catalog.notes.find((n) => n.key === noteKey))) return { ok: false, error: 'NOT_FOUND' };
  const existing = draft.notes.find((n) => n.noteKey === noteKey);
  if (existing) {
    if (existing.layer === layer) return { ok: true, draft };
    return { ok: false, error: 'IN_OTHER_LAYER', layer: existing.layer };
  }
  if (draft.notes.filter((n) => n.layer === layer).length >= EngineParams.MAX_PER_LAYER) {
    return { ok: false, error: 'LAYER_LIMIT' };
  }
  return { ok: true, draft: { ...draft, notes: [...draft.notes, { noteKey, layer, level: 'medium' }] } };
}

export function removeNote(draft: Draft, noteKey: string): Draft {
  return { ...draft, notes: draft.notes.filter((n) => n.noteKey !== noteKey) };
}

export function setLevel(draft: Draft, noteKey: string, level: IntensityLevel): Draft {
  return { ...draft, notes: draft.notes.map((n) => (n.noteKey === noteKey ? { ...n, level } : n)) };
}

export function toEngineBlend(draft: Draft): BlendNote[] {
  return draft.notes.map((n) => ({ noteKey: n.noteKey, layer: n.layer, intensity: EngineParams.INTENSITIES[n.level] }));
}

// ---------- 노트 목록 · 검색 ----------

export type NoteSection = { family: Family; notes: Note[] };

const byUse = (a: Note, b: Note) => b.df - a.df || (a.nameKo < b.nameKo ? -1 : a.nameKo > b.nameKo ? 1 : 0);

/** 많이 쓰는 노트(browse)를 계열별로. 고른 계열(고른 순서) 먼저, 나머지는 휠 순서. 묶음 안은 많이 쓰는 순 */
export function noteSections(catalog: Catalog, chosen: readonly string[]): NoteSection[] {
  const byFamily = new Map<string, Note[]>();
  for (const n of catalog.notes) {
    if (n.tier !== 'browse') continue;
    const list = byFamily.get(n.family) ?? [];
    list.push(n);
    byFamily.set(n.family, list);
  }
  const rest = [...catalog.families].filter((f) => !chosen.includes(f.code)).sort((a, b) => a.wheelOrder - b.wheelOrder);
  const first = chosen.map((c) => catalog.families.find((f) => f.code === c)).filter((f): f is Family => !!f);
  return [...first, ...rest]
    .map((family) => ({ family, notes: [...(byFamily.get(family.code) ?? [])].sort(byUse) }))
    .filter((s) => s.notes.length > 0);
}

/** 검색용 정리: 한글 조합형(NFC)으로 맞추고, 소문자, 띄어쓰기 없앰 */
export function normalizeQuery(s: string): string {
  return s.normalize('NFC').toLowerCase().replace(/\s+/g, '');
}

const SEARCH_LIMIT = 40;

/** 한글 이름·영문 이름의 일부로 찾는다(검색 전용 노트 포함, 숨김 제외). 앞부분이 맞는 것 먼저, 그다음 많이 쓰는 순 */
export function searchNotes(catalog: Catalog, query: string): Note[] {
  const q = normalizeQuery(query);
  if (q === '') return [];
  const hits: { note: Note; prefix: boolean }[] = [];
  for (const n of catalog.notes) {
    if (!usable(n)) continue;
    const names = [normalizeQuery(n.nameKo), normalizeQuery(n.nameEn), normalizeQuery(n.key)];
    if (!names.some((x) => x.includes(q))) continue;
    hits.push({ note: n, prefix: names.some((x) => x.startsWith(q)) });
  }
  hits.sort((a, b) => Number(b.prefix) - Number(a.prefix) || byUse(a.note, b.note));
  return hits.slice(0, SEARCH_LIMIT).map((h) => h.note);
}

// ---------- 균형 안내 ----------

const EMPTY_LAYER_TEXT: Record<Layer, string> = {
  top: '탑이 비어 있어 첫인상이 조용하게 느껴질 수 있어요',
  middle: '미들이 비어 있어 향의 중심이 옅게 느껴질 수 있어요',
  base: '베이스가 비어 있어 잔향이 가볍게 느껴질 수 있어요',
};

/** 엔진의 균형 안내 코드를 화면 문구로 (design.md 8-7). 코드 순서 그대로 */
export function hintTexts(engine: RecommendationEngine, blend: BlendNote[]): string[] {
  if (blend.length === 0) return [];
  const cat = engine.catalog;
  const famName = (code: string) => cat.families.find((f) => f.code === code)?.nameKo ?? code;
  const byWheel = [...cat.families].sort((a, b) => a.wheelOrder - b.wheelOrder);
  const at = (i: number) => byWheel[((i % byWheel.length) + byWheel.length) % byWheel.length];
  const wheelIndex = (code: string) => byWheel.findIndex((f) => f.code === code);
  const families = [...new Set(blend.map((b) => engine.note(b.noteKey)!.family))];

  const out: string[] = [];
  for (const code of engine.balanceHints(blend)) {
    if (code.startsWith('EMPTY_LAYER:')) {
      out.push(EMPTY_LAYER_TEXT[code.slice('EMPTY_LAYER:'.length) as Layer]);
    } else if (code === 'ONE_FAMILY') {
      const i = wheelIndex(families[0]);
      out.push(`한 계열로만 이루어져 단조로울 수 있어요. 이웃 계열(${at(i - 1).nameKo} · ${at(i + 1).nameKo})을 더해 보세요`);
    } else if (code === 'DOMINANT') {
      const top = engine.displayRatios(blend)[0];
      const name = cat.accords.find((a) => a.key === top?.accord)?.nameKo ?? '한 어코드';
      out.push(`${name} 느낌이 아주 강한 조합이에요`);
    } else if (code === 'FAR_FAMILIES') {
      const [x, y] = families.map(wheelIndex).sort((a, b) => a - b);
      const n = byWheel.length;
      // 휠에서 짧은 쪽 길의 가운데 계열
      const mid = y - x <= n - (y - x) ? Math.floor((x + y) / 2) : Math.floor((y + x + n) / 2);
      out.push(`대비가 큰 조합이에요. 사이를 잇는 ${famName(at(mid).code)} 계열을 더하면 부드러워져요`);
    }
  }
  return out;
}
