import { useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';

import { useRecs } from '@/src/recs/useRecs';
import { S } from '@/src/strings.ko';
import { goBack } from '@/src/ui/nav';
import { PerfumeDetail } from '@/src/ui/PerfumeDetail';
import { Header, Screen } from '@/src/ui/Screen';

/** 추천에서 연 향수 상세(폰). 지금 조합과 비교한다(승인 B5·B6). 추천 10개에 없는 향수면 비교 막대만, 등급 없이 */
export default function PerfumeFromRecs() {
  const { key } = useLocalSearchParams<{ key: string }>();
  const { blend, recs } = useRecs();
  const rec = recs.find((r) => r.perfumeKey === key);
  return (
    <Screen needsCatalog>
      <Header title={S.recsTitle} onBack={goBack} />
      <View>{key ? <PerfumeDetail perfumeKey={key} rec={rec} blend={blend} /> : null}</View>
    </Screen>
  );
}
