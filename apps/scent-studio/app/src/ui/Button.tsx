/**
 * 버튼 (design_rules.md 6절): 주(primary 바탕) · 보조(테두리) · 글자 버튼. 높이 48 이상.
 */
import { Pressable, type PressableProps } from 'react-native';

import { Txt } from './Txt';

type Kind = 'primary' | 'secondary' | 'text';

const BOX: Record<Kind, string> = {
  primary: 'bg-primary dark:bg-primary-dark active:bg-primaryHover dark:active:bg-primaryHover-dark',
  secondary: 'border border-primary dark:border-primary-dark',
  text: 'px-3 -ml-3 items-start',
};
const LABEL: Record<Kind, string> = {
  primary: 'text-onPrimary dark:text-onPrimary-dark',
  secondary: 'text-primary dark:text-primary-dark',
  text: 'text-primary dark:text-primary-dark',
};

export function Button({
  kind = 'primary',
  label,
  disabled,
  className = '',
  ...rest
}: PressableProps & { kind?: Kind; label: string; className?: string }) {
  const box = disabled ? 'bg-surfaceAlt dark:bg-surfaceAlt-dark' : BOX[kind];
  const text = disabled ? 'text-inkSub dark:text-inkSub-dark' : LABEL[kind];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      disabled={disabled}
      {...rest}
      className={`min-h-touch justify-center rounded ${kind === 'text' ? '' : 'items-center px-5'} ${box} ${className}`}>
      <Txt variant="label" className={text}>
        {label}
      </Txt>
    </Pressable>
  );
}
