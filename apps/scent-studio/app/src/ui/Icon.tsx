/**
 * 선 아이콘 (design_rules.md 5절: 선 아이콘, 크기 24, 채운 아이콘 없음).
 * Material Symbols 대신 같은 모양의 Material Design Icons 선 버전(@expo/vector-icons)을 쓴다.
 */
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';

import { useTokenColor, type ColorToken } from './tokens';

export type IconName = 'home' | 'search' | 'library' | 'my' | 'back' | 'info' | 'close' | 'check';

const GLYPH = {
  home: 'home-outline',
  search: 'magnify',
  library: 'bookmark-outline',
  my: 'account-outline',
  back: 'arrow-left',
  info: 'information-outline',
  close: 'close',
  check: 'check',
} as const;

export const ICON_FONT = MaterialCommunityIcons.font;

export function Icon({ name, color = 'ink', size = 24 }: { name: IconName; color?: ColorToken; size?: number }) {
  const c = useTokenColor();
  return <MaterialCommunityIcons name={GLYPH[name]} size={size} color={c(color)} accessible={false} />;
}

/** 색을 직접 받는 버전 (탭 막대처럼 색이 밖에서 정해질 때) */
export function RawIcon({ name, color, size = 24 }: { name: IconName; color: string; size?: number }) {
  return <MaterialCommunityIcons name={GLYPH[name]} size={size} color={color} accessible={false} />;
}
