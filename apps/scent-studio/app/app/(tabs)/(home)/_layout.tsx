import { Stack } from 'expo-router';

/** 홈 탭 안의 새 조합 흐름: 홈 → 계열 → 노트 → 요약 (하단 탭은 그대로 보인다, 시안 5-0) */
export default function HomeStack() {
  return <Stack screenOptions={{ headerShown: false, animation: 'fade', animationDuration: 150 }} />;
}
