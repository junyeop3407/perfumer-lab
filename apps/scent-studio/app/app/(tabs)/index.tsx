import { Pressable, Text, View } from 'react-native';

import { S } from '@/src/strings.ko';
import { TestIds } from '@/src/ui/testIds';

// 뼈대(plan.md 단계 1). 새 조합 흐름은 단계 5에서 만든다.
export default function HomeScreen() {
  return (
    <View className="flex-1 bg-bg p-6 dark:bg-bg-dark">
      <Text className="text-2xl font-semibold text-ink dark:text-ink-dark">{S.appName}</Text>
      <Pressable
        testID={TestIds.NEW_BLEND}
        accessibilityRole="button"
        className="mt-6 min-h-12 items-center justify-center rounded-xl bg-primary px-5 dark:bg-primary-dark">
        <Text className="text-base text-bg dark:text-bg-dark">{S.newBlend}</Text>
      </Pressable>
    </View>
  );
}
