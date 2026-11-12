/**
 * 노트 이름 표시. 한글 이름이 다른 노트와 겹치면 영어 이름을 작게 붙인다(사장 결정 2026-11-12, src/catalog/noteNames.ts).
 */
import { useCallback } from 'react';

import { useCatalog } from '../catalog/CatalogContext';
import { noteLabel } from '../catalog/noteNames';
import type { Note } from '../engine/types';
import { Txt, type TxtVariant } from './Txt';

const EMPTY: ReadonlySet<string> = new Set();

/** 겹치면 영어 이름, 아니면 null / 글자 하나로 쓸 이름("배 Pear") */
export function useNoteNames() {
  const catalog = useCatalog();
  const amb = catalog.status === 'ready' ? catalog.ambiguous : EMPTY;
  const en = useCallback((n: Note) => (amb.has(n.key) ? n.nameEn : null), [amb]);
  const label = useCallback((n: Note) => noteLabel(n, amb), [amb]);
  return { en, label };
}

/** 한글 이름 + (겹치면) 작은 영어 이름 */
export function NoteNameText({
  ko,
  en,
  variant = 'label',
  className = '',
}: {
  ko: string;
  en: string | null;
  variant?: TxtVariant;
  className?: string;
}) {
  return (
    <Txt variant={variant} className={className}>
      {ko}
      {en ? <Txt variant="caption" className={className}>{` ${en}`}</Txt> : null}
    </Txt>
  );
}
