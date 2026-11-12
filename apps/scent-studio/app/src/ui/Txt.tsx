/**
 * 글자 (design_rules.md 3절). 글자 크기 토큰과 글꼴(굵기)을 함께 고른다.
 * 색: 기본 ink, sub는 inkSub. 다른 색이 필요하면 className으로 덧붙인다.
 */
import { Text, type TextProps } from 'react-native';

/** noteName: 처방전·노트 카드의 노트 이름(세리프 18, headline 크기) */
export type TxtVariant = 'display' | 'title' | 'headline' | 'noteName' | 'body' | 'label' | 'caption' | 'layer';

const VARIANT: Record<TxtVariant, string> = {
  display: 'font-serif text-display',
  title: 'font-serif text-title',
  headline: 'font-sans-semibold text-headline',
  noteName: 'font-serif text-headline',
  body: 'font-sans text-body',
  label: 'font-sans-medium text-label',
  caption: 'font-sans text-caption',
  layer: 'font-sans-semibold text-layer tracking-layer',
};

export function Txt({
  variant = 'body',
  sub = false,
  className = '',
  ...rest
}: TextProps & { variant?: TxtVariant; sub?: boolean; className?: string }) {
  // 색 클래스를 따로 주면 기본 색을 넣지 않는다(두 색 클래스가 같이 있으면 어느 쪽이 이길지 순서로 정해지지 않음)
  const ownColor = /(^|\s)text-(?!(display|title|headline|body|label|caption|layer)(\s|$))/.test(className);
  const color = ownColor ? '' : sub ? 'text-inkSub dark:text-inkSub-dark' : 'text-ink dark:text-ink-dark';
  return <Text {...rest} className={`${VARIANT[variant]} ${color} ${className}`} />;
}
