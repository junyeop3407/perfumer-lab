/**
 * 지금 만드는 조합(초안)의 추천 10개. 요약 미리보기·추천 목록·향수 상세가 같은 결과를 본다.
 * 추천은 기기 안에서 계산한다(서버 없이 바로, spec D1).
 */
import { useMemo } from 'react';

import { toEngineBlend } from '../blend/blendDraft';
import { useDraft } from '../blend/DraftContext';
import { useCatalog } from '../catalog/CatalogContext';
import type { BlendNote, Recommendation } from '../engine/types';
import { recommendFor } from './perfumeView';

export function useRecs(): { blend: BlendNote[]; recs: Recommendation[] } {
  const catalog = useCatalog();
  const { draft } = useDraft();
  const engine = catalog.status === 'ready' ? catalog.engine : null;
  return useMemo(() => {
    const blend = toEngineBlend(draft);
    if (!engine || blend.length === 0) return { blend, recs: [] };
    try {
      return { blend, recs: recommendFor(engine, blend) };
    } catch (e) {
      console.warn(`[recs] 추천을 계산하지 못함: ${(e as Error).message}`);
      return { blend, recs: [] };
    }
  }, [engine, draft]);
}
