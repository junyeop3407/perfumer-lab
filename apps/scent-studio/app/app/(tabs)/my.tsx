import { Text, View } from 'react-native';

import { S } from '@/src/strings.ko';

// 뼈대(plan.md 단계 1). 화면은 단계 5에서 만든다.
export default function Screen() {
  return (
    <View className="flex-1 bg-bg p-6 dark:bg-bg-dark">
      <Text className="text-2xl font-semibold text-ink dark:text-ink-dark">{S.tabMy}</Text>
    </View>
  );
}
