/**
 * 글자 (design_rules.md 3절). 글자 크기 토큰과 글꼴(굵기)을 함께 고른다.
 * 색: 기본 ink, sub는 inkSub. 다른 색이 필요하면 className으로 덧붙인다.
 */
import { Text, type TextProps } from 'react-native';

export type TxtVariant = 'display' | 'title' | 'headline' | 'body' | 'label' | 'caption' | 'layer';

const VARIANT: Record<TxtVariant, string> = {
  display: 'font-serif text-display',
  title: 'font-serif text-title',
  headline: 'font-sans-semibold text-headline',
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
  const color = sub ? 'text-inkSub dark:text-inkSub-dark' : 'text-ink dark:text-ink-dark';
  return <Text {...rest} className={`${VARIANT[variant]} ${color} ${className}`} />;
}
