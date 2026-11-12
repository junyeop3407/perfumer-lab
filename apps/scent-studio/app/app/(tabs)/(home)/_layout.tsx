import { Stack } from 'expo-router';
import { useReducedMotion } from 'react-native-reanimated';

/** 홈 탭 안의 새 조합 흐름: 홈 → 계열 → 노트 → 요약 (하단 탭은 그대로 보인다, 시안 5-0) */
export default function HomeStack() {
  const reduceMotion = useReducedMotion(); // "움직임 줄이기" 설정이면 전환 없음 (design_rules.md 5절)
  return <Stack screenOptions={{ headerShown: false, animation: reduceMotion ? 'none' : 'fade', animationDuration: 150 }} />;
}
