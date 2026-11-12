리뷰를 마쳤습니다. 전반적으로 코드 품질이 높고 성능 및 예외 처리가 꼼꼼하게 되어 있습니다. 다만, 실제 카탈로그 규모(3,000개 이상)를 고려할 때 모바일 기기에서 성능 저하나 사용자 경험(UX) 문제를 일으킬 수 있는 몇 가지 사항을 발견했습니다.

### [심각도: 중간]

**1. `apps/scent-studio/app/src/blend/blendDraft.ts` (133줄)**
- **문제:** `pairingsFor` 함수 내부에서 호출될 때마다 `catalog.notes` 전체(약 3,000개)를 복사하고 정렬(`[...catalog.notes].sort(byUse)`)합니다.
- **이유:** 이 함수는 `NoteCardBody` 렌더링 시 매번 실행됩니다. 모바일(특히 저사양 안드로이드)에서 노트 정보를 볼 때마다 3,000개 요소를 정렬하면 UI 프리징(Jank)이 발생할 수 있습니다.
- **수정 제안:** 정렬된 리스트를 `catalog` 로드 시점에 미리 만들어 두거나, `useMemo` 등을 사용하여 정렬된 리스트를 재사용하십시오.

**2. `apps/scent-studio/app/app/(tabs)/(home)/families.tsx` (70줄)**
- **문제:** `fresh=1` 파라미터가 유지된 상태에서 화면을 오갈 때 데이터가 의도치 않게 초기화될 수 있습니다.
- **이유:** 사용자가 '새 조합 만들기'로 진입(`fresh=1`) -> 계열 선택 -> 노트 선택 -> 다시 '계열 선택'으로 뒤로가기 -> 계열 수정 후 '다음' 클릭 시, URL에 여전히 `fresh=1`이 있어 `applyFamilies`가 기존에 골라둔 노트를 모두 비워버립니다.
- **수정 제안:** `fresh` 플래그는 `FamiliesScreen`의 초기 상태 설정에만 사용하고, `setDraft` 시점에는 `fresh` 여부와 상관없이 현재 상태를 유지할 수 있는 로직(예: 이미 노트를 골랐다면 `fresh` 무시)을 검토하거나, `NotesScreen`으로 진입한 이후에는 URL 파라미터를 제거해야 합니다.

### [심각도: 낮음]

**1. `apps/scent-studio/app/src/catalog/noteNames.ts` (24줄)**
- **문제:** `ambiguousNoteKeys` 함수에서 Map의 Value를 갱신할 때 매번 전개 연산자(`[...]`)를 사용하여 배열을 새로 생성합니다.
- **이유:** `catalog.notes`가 클 경우 불필요한 가비지 컬렉션(GC) 부하를 줍니다.
- **수정 제안:** 
  ```typescript
  // 기존
  byName.set(n.nameKo, [...(byName.get(n.nameKo) ?? []), n.key]);
  // 수정
  const list = byName.get(n.nameKo) ?? [];
  list.push(n.key);
  byName.set(n.nameKo, list);
  ```

**2. `apps/scent-studio/app/src/catalog/CatalogContext.tsx` (21-35줄)**
- **문제:** `AsyncStorage` 오류 발생 시 `installId`가 영구 저장되지 않아 매 실행 시 새로운 ID가 생성될 수 있습니다.
- **이유:** 저장소가 가득 찼거나 읽기 권한 문제가 있을 경우, 앱은 계속 실행되지만 서버 측의 버전별 받기 제한 기능이 무력화됩니다. (운영 상의 문제)
- **수정 제안:** 치명적인 오류는 아니나, `AsyncStorage` 실패 로그를 남기거나 사용자에게 저장 공간 부족 등을 알리는 처리가 있으면 좋습니다.

**3. `apps/scent-studio/backend/src/ops/java/com/perfumerlab/scentstudio/ops/CatalogFetch.java` (125줄)**
- **문제:** 최대 50MB(`MAX_BYTES`)에 달하는 카탈로그 파일을 한 번에 힙 메모리에 할당(`readNBytes`)합니다.
- **이유:** 운영 도구이므로 큰 문제는 없으나, 로컬 PC 사양에 따라 JVM 힙 메모리 부족이 발생할 가능성이 있습니다.
- **수정 제안:** 파일에 직접 스트리밍(`BodyHandlers.ofFile`)하며 쓰거나, `InputStream`을 직접 `GZIPInputStream`으로 넘겨서 처리하는 방식을 권장합니다.

**4. `apps/scent-studio/app/app/(tabs)/(home)/notes.tsx` (125줄) [추정]**
- **문제:** 검색 결과 리스트 렌더링 시 `key`로 `n.key`를 사용하는데, 검색 결과가 매우 많을 경우(예: 단일 자모 검색 등) `NoteListItem`이 대량으로 다시 그려질 수 있습니다.
- **이유:** `useDeferredValue`로 부하를 분산했지만, `results.map`이 수백 개를 한 번에 렌더링하면 여전히 끊김이 생길 수 있습니다.
- **수정 제안:** `FlatList`를 사용하여 가상 리스트(Windowing)를 적용하는 것을 검토하십시오. (현재는 `ScrollView` 내 `View.map` 방식)

## Claude 판단 (2026-11-12, 기준 b30fcb0 → 9363608)
- 중간 1 `pairingsFor` 매번 정렬: **맞음 → 고침.** 카탈로그마다 한 번만 찾기 표·정렬을 만들어 둔다(WeakMap). 회귀 테스트 `reviewFixes5_2.test.ts`(노트 3,000개에서 300번 30ms 안, 고치기 전 약 90ms).
- 중간 2 `fresh=1`이 남아 뒤로 갔다 [다음]을 다시 누르면 고른 노트가 지워짐: **맞음 → 고침.** 첫 [다음]에서만 비운다(`familiesNext`, 화면 상태 `freshPending`). 회귀 테스트 같은 파일.
- 낮음 1 `ambiguousNoteKeys` 배열 새로 만들기: 맞음 → 고침(push).
- 낮음 2 설치 id 저장 실패 기록 없음: 맞음 → 경고 로그 추가. 사용자 알림은 하지 않음(카탈로그 받기 횟수 제한만 영향, 앱 사용에는 영향 없음).
- 낮음 3 `CatalogFetch` 50MB 한 번에 읽기: 반영 안 함. 5-1 운영 도구(개발 PC에서만 실행)이고 실제 파일 2.3MB, 50MB는 상한 검사값이다(5-1 Gemini 리뷰에서 같은 판단).
- 낮음 4 검색 결과 가상 목록: 반영 안 함. 검색 결과는 40개로 잘린다(`SEARCH_LIMIT`). 목록 325개는 `memo`로 다시 그리기를 줄였고, 저사양 폰 열기 시간은 기기 확인(8-3·D11)에서 재고 넘으면 FlatList 등을 검토한다.
