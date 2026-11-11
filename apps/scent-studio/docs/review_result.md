리뷰 결과를 보고합니다.

### [심각도: 중간] app/src/catalog/loadCatalog.ts (L30-128)
- **문제 이유**: `gunzipSync`, `JSON.parse`, `validateCatalog` 등 대용량 데이터(최대 50MB 압축, 해제 시 수십~백여 MB 예상)를 처리하는 모든 과정이 **JavaScript 메인 스레드에서 동기적(Synchronous)**으로 실행됩니다. 약 12,000개의 향수 데이터를 검증하고 파싱하는 동안 앱의 UI가 완전히 멈추는 현상(Jank)이 발생하며, 특히 저사양 기기에서는 OS에 의해 앱이 응답 없음(ANR)으로 판단될 위험이 있습니다.
- **수정 제안**: 
    1. 해제 및 파싱 과정을 별도의 Background Thread(예: `expo-worklets` 또는 Web Worker 계열)에서 수행하도록 분리하십시오.
    2. 또는 `validateCatalog` 내부의 루프를 `setImmediate` 등을 이용해 청크 단위로 나누어 실행하여 메인 스레드에 숨통을 틔워주십시오.
    3. 빌드 타임에 이미 검증된 `bundled` 카탈로그의 경우, 런타임 검증 강도를 낮추는 방안도 검토하십시오.

### [심각도: 중간] app/src/catalog/catalogUpdate.ts (L172-184)
- **문제 이유**: 메모리 효율성 문제입니다. `res.arrayBuffer()`로 전체 바이트를 메모리에 올린 후, `gunzipSync`로 또 다른 대형 버퍼를 만들고, 이를 `TextDecoder`와 `JSON.parse`를 거쳐 객체화합니다. 50MB 압축 파일 기준, 이 과정에서 순간적으로 **압축 크기의 수 배에 달하는 메모리 점유(Memory Spike)**가 발생하여 메모리가 부족한 기기에서 크래시가 발생할 수 있습니다.
- **수정 제안**: 
    1. 큰 데이터를 메모리에 직접 올리지 말고 파일 스트림을 지원하는 라이브러리를 사용하여 단계별로 처리하십시오.
    2. 중간 단계의 큰 객체(Buffer, String)들은 사용 직후 `null`을 대입하여 가비지 컬렉션이 빠르게 이루어지도록 유도하십시오.
    3. 장기적으로는 12,000개의 객체를 하나의 JS Object로 관리하기보다 SQLite 등에 넣어 필요한 부분만 조회하는 방식을 권장합니다.

### [심각도: 낮음] app/src/catalog/platform.native.ts (L26)
- **문제 이유**: `meta.text()`를 읽어 `JSON.parse` 하는 과정에서 파일이 존재하지만 내용이 깨져있거나 비어있을 경우 예외가 발생합니다. `openCatalog`에서 catch하고 있지만, `cache.read()` 수준에서 더 안전하게 처리하는 것이 좋습니다.
- **수정 제안**: `try-catch`로 `JSON.parse`를 감싸거나, 파일 크기가 0인지 먼저 확인하는 가드 코드를 추가하십시오.

### [심각도: 낮음] backend/src/ops/java/com/perfumerlab/scentstudio/ops/CatalogFetch.java (L134)
- **추정**: `in.readNBytes((int) r.size() + 1)` 코드는 기록된 크기보다 큰 파일이 들어오는 것을 감지하려는 의도로 보이나, `(int)` 캐스팅 시 `r.size()`가 2GB를 넘으면 오버플로우가 발생할 수 있습니다. 현재 `MAX_BYTES`가 50MB로 설정되어 있어 안전하지만, 상수가 변경될 경우 잠재적 버그가 될 수 있습니다.
- **수정 제안**: `r.size()`가 `Integer.MAX_VALUE`를 넘지 않는지 먼저 검사하거나, `long` 기반의 스트림 처리를 유지하십시오.

**요약**: 전체적인 로직은 견고하나, 모바일 환경에서의 **메인 스레드 차단 및 메모리 스파이크**에 대한 대비가 더 필요합니다.
