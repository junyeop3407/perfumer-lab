/**
 * 만드는 중인 조합(초안)을 화면들이 함께 쓴다(계열 → 노트 → 요약 → 노트 수정으로 돌아와도 그대로, 승인 A10).
 * nextLevel: 다음에 고르는 노트가 시작할 강도 = 마지막에 누른 강도. 새 조합을 시작할 때만 중으로(2026-11-13 사장).
 * 앱을 끄면 사라진다(이어 하기 저장은 5-6).
 */
import { createContext, useContext, useState, type ReactNode } from 'react';

import type { IntensityLevel } from '../ui/testIds';
import { emptyDraft, type Draft } from './blendDraft';

type DraftCtx = {
  draft: Draft;
  setDraft: (d: Draft) => void;
  nextLevel: IntensityLevel;
  setNextLevel: (l: IntensityLevel) => void;
};

const Ctx = createContext<DraftCtx>({
  draft: emptyDraft([]),
  setDraft: () => undefined,
  nextLevel: 'medium',
  setNextLevel: () => undefined,
});

export function DraftProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<Draft>(emptyDraft([]));
  const [nextLevel, setNextLevel] = useState<IntensityLevel>('medium');
  return <Ctx.Provider value={{ draft, setDraft, nextLevel, setNextLevel }}>{children}</Ctx.Provider>;
}

export function useDraft(): DraftCtx {
  return useContext(Ctx);
}
