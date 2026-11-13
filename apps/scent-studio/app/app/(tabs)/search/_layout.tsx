import { Stack } from 'expo-router';
import { useReducedMotion } from 'react-native-reanimated';

/** 향수 찾기 탭 안: 검색 → 향수 상세 */
export default function SearchStack() {
  const reduceMotion = useReducedMotion();
  return <Stack screenOptions={{ headerShown: false, animation: reduceMotion ? 'none' : 'fade', animationDuration: 150 }} />;
}
