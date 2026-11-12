/**
 * 클래스 이름을 쓸 수 없는 곳(탭 막대 색, 아이콘 색)에서 색 토큰 값을 읽는다. 값은 theme/colors.js 한 곳에만 있다.
 */
import { useColorScheme } from 'react-native';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const colors = require('../../theme/colors') as Record<string, { DEFAULT: string; dark: string }>;

export type ColorToken = keyof typeof colors & string;

export function useTokenColor(): (name: ColorToken) => string {
  const dark = useColorScheme() === 'dark';
  return (name) => (dark ? colors[name].dark : colors[name].DEFAULT);
}
