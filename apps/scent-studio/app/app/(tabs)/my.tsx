import { View } from 'react-native';

import { S } from '@/src/strings.ko';
import { Button } from '@/src/ui/Button';
import { go } from '@/src/ui/nav';
import { Header, Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { Txt } from '@/src/ui/Txt';

/** 아직 만들지 않은 탭: 빈 화면 대신 "곧 열려요" 빈 상태 (design_rules.md 7절, 5-2 기기 확인 버그 2) */
export default function SoonScreen() {
  return (
    <Screen>
      <Header title={S.tabMy} />
      <View testID={TestIds.EMPTY_STATE} className="mt-8 gap-6">
        <Txt sub>{S.soonMy}</Txt>
        <Button kind="secondary" label={S.newBlend} onPress={() => go('/families?fresh=1')} />
      </View>
    </Screen>
  );
}
