리뷰 결과, 성능 최적화와 관련된 2건의 지적 사항이 있습니다. 그 외 보안이나 예외 처리상의 결함은 발견되지 않았습니다.

### [심각도: 중간] 성능 문제
**파일명 및 줄 번호:** `apps/scent-studio/app/src/recs/perfumeView.ts` (100-106행)

**문제 이유:**
`searchPerfumes` 함수는 카탈로그 내의 모든 향수를 순회하며 검색을 수행합니다. 이때 루프 내부에서 `[p.name, p.brand, ...].map(fold)`를 호출하는데, `fold` 함수 내부의 `normalize('NFD')`와 정규식 처리는 비용이 매우 큰 작업입니다. 사용자가 타이핑을 할 때마다 수천 개의 향수에 대해 이 변환 작업을 반복하는 것은 저사양 기기에서 심각한 입력 지연(Typing Lag)을 유발할 수 있습니다.

**수정 제안:**
향수 데이터(이름, 브랜드)의 `fold` 결과값을 카탈로그 데이터 준비 시점에 미리 계산하여 인덱싱해 두거나, 최소한 루프 외부에서 한 번 계산된 값을 메모리에 캐싱하여 재사용해야 합니다.

---

### [심각도: 낮음] 성능 문제
**파일명 및 줄 번호:** `apps/scent-studio/app/app/(tabs)/search/index.tsx` (17-21행)

**문제 이유:**
`NotesScreen`에서는 성능을 위해 `useDeferredValue`를 사용하여 검색 결과를 도출하지만, `SearchScreen`에서는 `query` 상태를 직접 `useMemo`의 의존성으로 사용하여 검색을 수행합니다. 향수 카탈로그의 양이 많아질 경우, 위에서 지적한 O(N) 검색 연산이 렌더링 스레드를 점유하여 텍스트 입력창의 반응 속도가 떨어질 수 있습니다.

**수정 제안:**
`NotesScreen`의 구현 방식과 동일하게 `useDeferredValue`를 도입하여, 사용자의 입력 반응성(UI responsiveness)과 결과 계산(Background computation)을 분리하십시오.

---

**그 외:**
- `useRecs.ts`의 예외 처리(`try-catch`), `PerfumeDetail.tsx`의 데이터 누락 대응(`perfumeMissing`), URL 파라미터 인코딩(`encodeURIComponent`) 등은 적절히 구현되어 있습니다.
- 보안 측면에서 민감한 정보 노출이나 권한 위반 위험은 발견되지 않았습니다.

## Claude 판단 (2026-11-13)
- [중간] searchPerfumes 매번 fold: **맞음, 고침.** 카탈로그마다 한 번 만드는 검색 색인(`perfumeSearchIndex`, WeakMap). 회귀 테스트 `reviewFixes5_3.test.ts`.
- [낮음] useDeferredValue 없음: **맞음, 고침.** 노트 검색과 같은 방식으로.
