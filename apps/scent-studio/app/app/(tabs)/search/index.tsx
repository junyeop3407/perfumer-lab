import { useDeferredValue, useMemo, useState } from 'react';
import { Pressable, TextInput, View } from 'react-native';

import { brandLabel } from '@/src/catalog/brandNames';
import { useCatalog } from '@/src/catalog/CatalogContext';
import { searchPerfumes } from '@/src/recs/perfumeView';
import { S } from '@/src/strings.ko';
import { Icon } from '@/src/ui/Icon';
import { go } from '@/src/ui/nav';
import { Header, Screen } from '@/src/ui/Screen';
import { TestIds } from '@/src/ui/testIds';
import { useTokenColor } from '@/src/ui/tokens';
import { Txt } from '@/src/ui/Txt';

/** 향수 찾기 (승인 F1): 이름·브랜드 일부로 찾고, 누르면 향수 상세(조합 없이, F2) */
export default function SearchScreen() {
  const catalog = useCatalog();
  const color = useTokenColor();
  const [query, setQuery] = useState('');
  const [focused, setFocused] = useState(false);
  const ready = catalog.status === 'ready';
  // 입력창은 바로 반응하고, 결과 계산은 뒤로 미룬다(노트 고르기 검색과 같은 방식, 5-3 Gemini 리뷰)
  const deferred = useDeferredValue(query);
  const results = useMemo(() => (ready ? searchPerfumes(catalog.catalog, deferred) : []), [ready, catalog, deferred]);

  return (
    <Screen needsCatalog>
      <Header title={S.searchTitle} />
      <View
        className={`min-h-touch flex-row items-center gap-2 rounded border bg-surfaceAlt px-3 dark:bg-surfaceAlt-dark ${
          focused ? 'border-primary dark:border-primary-dark' : 'border-surfaceAlt dark:border-surfaceAlt-dark'
        }`}>
        <Icon name="search" color="inkSub" />
        <TextInput
          testID={TestIds.PERFUME_SEARCH}
          value={query}
          onChangeText={setQuery}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          placeholder={S.perfumeSearchPlaceholder}
          placeholderTextColor={color('inkSub')}
          accessibilityLabel={S.perfumeSearchLabel}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
          className="min-h-touch flex-1 font-sans text-body text-ink dark:text-ink-dark"
        />
        {query !== '' && (
          <Pressable
            onPress={() => setQuery('')}
            accessibilityRole="button"
            accessibilityLabel={S.clearSearch}
            className="-mr-3 h-12 w-12 items-center justify-center">
            <Icon name="close" color="inkSub" size={20} />
          </Pressable>
        )}
      </View>

      {deferred.trim() === '' ? (
        <Txt sub className="mt-6">
          {S.perfumeSearchHint}
        </Txt>
      ) : results.length === 0 ? (
        <Txt testID={TestIds.EMPTY_STATE} sub className="mt-6" accessibilityLiveRegion="polite">
          {S.perfumeSearchEmpty}
        </Txt>
      ) : (
        <View className="mt-4 border-t border-line dark:border-line-dark">
          {results.map((p) => (
            <Pressable
              key={p.key}
              testID={TestIds.searchResult(p.key)}
              accessibilityRole="button"
              accessibilityLabel={`${brandLabel(p.brand)} ${p.name}`}
              onPress={() => go(`/search/${encodeURIComponent(p.key)}`)}
              className="min-h-touch justify-center border-b border-line py-3 active:bg-surfaceAlt dark:border-line-dark dark:active:bg-surfaceAlt-dark">
              <Txt variant="caption" sub>
                {brandLabel(p.brand)}
              </Txt>
              <Txt variant="noteName">{p.name}</Txt>
              {(p.concentration || p.year) && (
                <Txt variant="caption" sub>
                  {[p.concentration, p.year].filter(Boolean).join(' · ')}
                </Txt>
              )}
            </Pressable>
          ))}
        </View>
      )}
    </Screen>
  );
}
