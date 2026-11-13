import { useLocalSearchParams } from 'expo-router';

import { S } from '@/src/strings.ko';
import { goBack } from '@/src/ui/nav';
import { PerfumeDetail } from '@/src/ui/PerfumeDetail';
import { Header, Screen } from '@/src/ui/Screen';

/** 검색에서 연 향수 상세: 조합과 비교하지 않는다(승인 F2) */
export default function PerfumeFromSearch() {
  const { key } = useLocalSearchParams<{ key: string }>();
  return (
    <Screen needsCatalog>
      <Header title={S.searchTitle} onBack={goBack} />
      <PerfumeDetail perfumeKey={key ?? ''} />
    </Screen>
  );
}
