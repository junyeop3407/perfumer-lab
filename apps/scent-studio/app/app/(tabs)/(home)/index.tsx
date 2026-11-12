import { View } from 'react-native';

import { useDraft } from '@/src/blend/DraftContext';
import { useCatalog } from '@/src/catalog/CatalogContext';
import { S } from '@/src/strings.ko';
import { Button } from '@/src/ui/Button';
import { go } from '@/src/ui/nav';
import { Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { Txt } from '@/src/ui/Txt';

export default function HomeScreen() {
  const catalog = useCatalog();
  const { draft } = useDraft();
  const ready = catalog.status === 'ready';

  return (
    <Screen>
      <Txt variant="display" accessibilityRole="header" className="mt-2">
        {S.homeTitle}
      </Txt>
      <Txt sub className="mt-4">
        {S.homeLead}
      </Txt>

      <View className="mt-8 gap-2">
        <Button
          testID={TestIds.NEW_BLEND}
          label={S.newBlend}
          disabled={!ready}
          onPress={() => go('/families?fresh=1')}
        />
        {ready && draft.notes.length > 0 && (
          <Button kind="secondary" testID={TestIds.RESUME_BLEND} label={S.resumeBlend} onPress={() => go('/notes')} />
        )}
        {catalog.status === 'loading' && (
          <Txt variant="caption" sub accessibilityLiveRegion="polite">
            {S.catalogLoading}
          </Txt>
        )}
        {catalog.status === 'error' && (
          <View className="mt-2 gap-2">
            <Txt variant="label" testID={TestIds.ERROR_MESSAGE} accessibilityLiveRegion="polite">
              {S.catalogError}
            </Txt>
            <Button kind="secondary" testID={TestIds.ERROR_RETRY} label={S.retry} onPress={catalog.retry} />
          </View>
        )}
      </View>

      <View className="mt-8">
        <Txt variant="headline" accessibilityRole="header">
          {S.homeLayersTitle}
        </Txt>
        <Txt sub className="mt-2">
          {S.homeLayersBody}
        </Txt>
      </View>
    </Screen>
  );
}
