/**
 * 카탈로그 파일(catalog.json.gz) 읽기와 확인 (plan_stage5.md 5-1, design.md 10절 "카탈로그").
 * - 압축 풀기는 순수 JS(fflate): 폰(Hermes)에는 DecompressionStream이 없어서 웹·폰 모두 같은 코드로 푼다.
 * - validateCatalog: 화면·엔진이 믿고 쓸 수 있는 모양인지 확인한다. 깨졌으면 CatalogError.
 * - sha256Hex: 서버에서 받은 파일·받아 둔 파일이 기록과 같은지 확인할 때 쓴다(순수 JS, @noble/hashes).
 */
import { sha256 } from '@noble/hashes/sha2';
import { bytesToHex } from '@noble/hashes/utils';
import { gunzipSync, strFromU8 } from 'fflate';

import type { Catalog } from '../engine/types';

export type { Catalog };

export class CatalogError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'CatalogError';
  }
}

function isGzip(bytes: Uint8Array): boolean {
  return bytes.length >= 2 && bytes[0] === 0x1f && bytes[1] === 0x8b;
}

export function sha256Hex(bytes: Uint8Array): string {
  return bytesToHex(sha256(bytes));
}

/** gzip이면 풀고, 아니면 그대로 JSON으로 읽는다. 모양 확인은 하지 않는다(validateCatalog) */
export async function parseCatalogBytes(bytes: Uint8Array): Promise<Catalog> {
  try {
    const raw = isGzip(bytes) ? gunzipSync(bytes) : bytes;
    // TextDecoder 대신 fflate의 UTF-8 변환: 폰(Hermes)에 TextDecoder가 없을 수 있다(리뷰 5-1 QA 중간-3)
    return JSON.parse(strFromU8(raw)) as Catalog;
  } catch (e) {
    throw new CatalogError(`카탈로그 파일을 읽지 못했습니다(${(e as Error).name})`);
  }
}

export async function fetchCatalog(url: string): Promise<Catalog> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`카탈로그를 읽지 못했어요 (${res.status})`);
  return parseCatalogBytes(new Uint8Array(await res.arrayBuffer()));
}

// ---------- 모양 확인 ----------

const TIERS = new Set(['browse', 'search', 'hidden']);
const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const isStr = (v: unknown): v is string => typeof v === 'string';
const isNum = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);
const isInt = (v: unknown): v is number => Number.isInteger(v);
const isStrList = (v: unknown): v is string[] => Array.isArray(v) && v.every(isStr);

function need(ok: boolean, what: string): asserts ok {
  if (!ok) throw new CatalogError(`카탈로그 모양이 틀립니다: ${what}`);
}

function list(v: unknown, what: string): unknown[] {
  need(Array.isArray(v) && v.length > 0, `${what} 목록`);
  return v as unknown[];
}

function uniqueKeys(items: unknown[], field: string, what: string): Set<string> {
  const keys = new Set<string>();
  items.forEach((it, i) => {
    need(isObj(it) && isStr(it[field]) && it[field] !== '', `${what}[${i}].${field}`);
    const k = (it as Record<string, string>)[field];
    need(!keys.has(k), `${what} ${field} 겹침`);
    keys.add(k);
  });
  return keys;
}

/** 모양이 맞으면 그대로 돌려주고, 아니면 CatalogError. 어디가 틀렸는지만 알리고 값은 출력하지 않는다 */
export function validateCatalog(raw: unknown): Catalog {
  need(isObj(raw), '최상위');
  const c = raw as Record<string, unknown>;
  need(isStr(c.version) && c.version.length > 0 && c.version.length <= 40, 'version');
  need(isInt(c.engineVersion) && (c.engineVersion as number) > 0, 'engineVersion');
  need(isObj(c.source) && isStr(c.source.name) && isStr(c.source.note), 'source');

  const families = list(c.families, 'families');
  const famCodes = uniqueKeys(families, 'code', 'families');
  families.forEach((f, i) => need(isStr((f as any).nameKo) && isNum((f as any).wheelOrder), `families[${i}]`));

  const accords = list(c.accords, 'accords');
  const accordKeys = uniqueKeys(accords, 'key', 'accords');
  accords.forEach((a, i) => need(isStr((a as any).nameKo) && famCodes.has((a as any).family), `accords[${i}]`));
  if (c.globalMean !== undefined) {
    need(Array.isArray(c.globalMean) && c.globalMean.length === accords.length && c.globalMean.every(isNum), 'globalMean');
  }

  const notes = list(c.notes, 'notes');
  const noteKeys = uniqueKeys(notes, 'key', 'notes');
  notes.forEach((n, i) => {
    const x = n as Record<string, unknown>;
    need(isStr(x.nameEn) && isStr(x.nameKo) && isStr(x.description), `notes[${i}] 이름`);
    need(isStr(x.family) && famCodes.has(x.family), `notes[${i}].family`);
    need(isNum(x.df) && isStr(x.tier) && TIERS.has(x.tier), `notes[${i}] df·tier`);
    need(Array.isArray(x.profile) && x.profile.length === accords.length && x.profile.every(isNum), `notes[${i}].profile`);
    need(isStrList(x.pairings) && x.pairings.every((k) => noteKeys.has(k)), `notes[${i}].pairings`);
  });

  const perfumes = list(c.perfumes, 'perfumes');
  uniqueKeys(perfumes, 'key', 'perfumes');
  perfumes.forEach((p, i) => {
    const x = p as Record<string, unknown>;
    need(isStr(x.brand) && isStr(x.name) && isStr(x.group), `perfumes[${i}] 이름`);
    need(x.year === null || isInt(x.year), `perfumes[${i}].year`);
    need(x.concentration === null || isStr(x.concentration), `perfumes[${i}].concentration`);
    need((x.rating === null || isNum(x.rating)) && isNum(x.ratingCount), `perfumes[${i}] 평점`);
    need(isStrList(x.accords) && x.accords.every((a) => accordKeys.has(a)), `perfumes[${i}].accords`);
    for (const layer of ['top', 'middle', 'base'] as const) {
      need(isStrList(x[layer]) && (x[layer] as string[]).every((k) => noteKeys.has(k)), `perfumes[${i}].${layer}`);
    }
    if (x.accordShares !== undefined) {
      need(isObj(x.accordShares) && Object.entries(x.accordShares).every(([k, v]) => accordKeys.has(k) && isNum(v)), `perfumes[${i}].accordShares`);
    }
    if (x.noteWeights !== undefined) {
      need(isObj(x.noteWeights) && Object.entries(x.noteWeights).every(([k, v]) => noteKeys.has(k) && isNum(v)), `perfumes[${i}].noteWeights`);
    }
    for (const flag of ['estimatedAccords', 'estimatedNotes'] as const) {
      need(x[flag] === undefined || typeof x[flag] === 'boolean', `perfumes[${i}].${flag}`);
    }
  });
  return raw as Catalog;
}
