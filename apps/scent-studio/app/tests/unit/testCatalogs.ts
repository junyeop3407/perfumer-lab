import { readFileSync } from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';

import type { BlendNote, Catalog } from '../../src/engine/types';

/** 테스트용: 가짜 카탈로그와 골든 파일 읽기 (앱 폴더 기준 상대 경로) */
const APP_DIR = path.resolve(__dirname, '../../..');

export function fixtureCatalog(): Catalog {
  const p = path.join(APP_DIR, 'tests/fixtures/test_catalog.json.gz');
  return JSON.parse(gunzipSync(readFileSync(p)).toString('utf-8')) as Catalog;
}

export type GoldenCase = {
  name: string;
  blend: BlendNote[];
  disliked: string[];
  preferred: string[];
  k: number;
  displayRatios: { accord: string; percent: number }[];
  balanceHints: string[];
  recommendations: {
    perfumeKey: string;
    score: number;
    accordSim: number;
    noteMatch: number;
    grade: string;
    sharedAccords: { accord: string; perfumePct: number; blendPct: number }[];
    matchedNotes: { noteKey: string; layer: string; match: string }[];
    missingNotes: string[];
  }[];
};

export type Golden = {
  catalogVersion: string;
  cases: GoldenCase[];
  errors: { name: string; blend: BlendNote[]; error: string }[];
};

export function golden(): Golden {
  const p = path.join(APP_DIR, 'tests/golden/recommend_cases.json');
  return JSON.parse(readFileSync(p, 'utf-8')) as Golden;
}
