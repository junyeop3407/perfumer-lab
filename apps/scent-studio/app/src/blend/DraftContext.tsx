/**
 * 만드는 중인 조합(초안)을 화면들이 함께 쓴다(계열 → 노트 → 요약 → 노트 수정으로 돌아와도 그대로, 승인 A10).
 * 앱을 끄면 사라진다(이어 하기 저장은 5-6).
 */
import { createContext, useContext, useState, type ReactNode } from 'react';

import { emptyDraft, type Draft } from './blendDraft';

type DraftCtx = { draft: Draft; setDraft: (d: Draft) => void };

const Ctx = createContext<DraftCtx>({ draft: emptyDraft([]), setDraft: () => undefined });

export function DraftProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<Draft>(emptyDraft([]));
  return <Ctx.Provider value={{ draft, setDraft }}>{children}</Ctx.Provider>;
}

export function useDraft(): DraftCtx {
  return useContext(Ctx);
}
