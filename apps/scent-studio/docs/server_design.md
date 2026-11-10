# scent-studio 서버 설계안 (plan.md 단계 4) — 사장 검토용

> 2026-11-06 백엔드팀 작성. 1판 → 사장 답(올릴 곳 Render, 요청 제한 표대로 OK, 오류 신고는 로그인한 사람만) + 보완 8개 → **2판(이 문서)**. **사장 승인 전에는 서버 코드·V2 마이그레이션을 만들지 않는다.** 바탕: design.md 11·12-1·16절, `db_design.md` 3판(실제 DB 적용 완료), 보안 규칙 `.claude/rules/security.md`.
> "(확인 필요)"는 서비스 정책·요금이 바뀔 수 있어 고를 때 공식 문서로 다시 확인할 것. "(추정)"은 직접 재기 전 어림값 — 단계 4에서 재서 NOTES에 적는다.

## 1. 큰 그림
- **Java 21 + Spring Boot 4.1**, `backend/`에 그대로 더한다(지금 있는 Flyway 마이그레이션·DB 테스트 위에).
- 서버가 하는 일: ① 로그인한 사용자 데이터(취향·조합·찜·시향 기록·동의·오류 신고) ② 카탈로그 새 버전 알려 주고 내려 주기 ③ 사용 기록 받기. **추천 계산은 하지 않는다**(앱 안에서, D1).
- DB는 `scent_server` 계정으로만. Supabase Secret key는 **계정 삭제와 카탈로그 파일 읽기**에만 쓰고 서버 비밀 환경 변수에만 둔다.
- DB 접근은 **Spring JDBC(JdbcClient)**로 한다(design.md 1절의 JPA 대신). JPA·Hibernate는 시작이 느리고 메모리를 많이 써서 512MB·저사양 CPU에 불리하다(6절). 표가 6~8개이고 SQL이 단순해서 손해가 없다.
- 모든 응답 JSON, 주소 앞에 `/api/v1`. 오류는 `{"error": {"code": "...", "message": "..."}}`.

## 2. API 목록
"로그인"은 Supabase 로그인 토큰(`Authorization: Bearer <토큰>`)이 필요하다는 뜻. 사용자 id는 **토큰에서만** 꺼낸다.

| 주소 | 하는 일 | 로그인 | 승인 항목 |
|---|---|---|---|
| `GET /health` | 서버와 DB가 살아 있나(`select 1`). 서버가 생기면 일시 정지 방지 예약 작업이 이걸 부른다 | — | — |
| `GET /catalog/latest` | 최신 카탈로그 정보 `{version, sha256, size, minAppVersion}` (작음, 수백 바이트) | — | E2 |
| `GET /catalog/{version}` | 카탈로그 파일(gzip). **서버 경유만**, 공개 주소 없음(5절) | — (설치 id 필요) | E2 |
| `GET /me` | 내 계정(이메일, 가입일), 지금 동의해야 할 문서 버전과 내 동의 상태 | 필요 | S11 |
| `DELETE /me` | **계정 삭제**: Supabase에 계정 삭제 요청 → DB가 내 데이터 한 번에 정리 → "남은 줄 0개" 확인 후 응답 | 필요 | G1·G2 |
| `POST /me/consents` | 약관·방침·만 14세 확인 동의 기록 | 필요 | — |
| `GET /me/preferences` · `PUT /me/preferences` | 불호 노트·선호 계열 | 필요 | C1~C3 |
| `POST /me/preferences/merge` | 로그인 직후 기기 값과 합치기(서버가 합침) | 필요 | D6 |
| `GET /blends` · `POST /blends` | 내 조합 목록 · 저장(한도 100) | 필요 | D2·D5 |
| `GET` · `PUT` · `DELETE /blends/{id}` | 조합 하나 (내 것만, 남의 id면 404) | 필요 | D5 |
| `GET /favorites` · `PUT` · `DELETE /favorites/{perfumeKey}` | 찜(한도 300, 이미 있으면 그대로 성공) | 필요 | D3 |
| `GET /feedback` · `POST /feedback` · `DELETE /feedback/{id}` | 시향 기록(한도 1,000), 본인 기록 지우기 | 필요 | D4 |
| `POST /events` | 사용 기록 **묶음**(4절) | 선택 | I1·I2 |
| `POST /reports` | 오류 신고 — **로그인한 사람만**(사장 결정 2026-11-06). 새 표 `app.error_reports`(V3) | 필요 | — |

- **서버에서 다시 검사:** 노트·향수 key는 지금 카탈로그에 있는 것만(서버가 시작할 때 **key 목록 파일**을 읽어 메모리에 둠, 5절), 층당 3개·같은 노트 한 번·강도 weak/medium/strong, 이름 40자, 메모 500자, 만족도 1~5. DB 검사(V1)는 두 번째 그물.
- **동의 확인:** 쓰는 API는 지금 버전의 세 동의가 다 있어야 받는다(`/me/consents`·`DELETE /me` 제외). 없으면 403 `CONSENT_REQUIRED`.
- 앱용 TypeScript 타입은 서버의 OpenAPI 문서에서 만든다(springdoc + openapi-typescript).

## 3. 로그인 토큰 확인
- Supabase 로그인 토큰(JWT)을 **Spring Security OAuth2 Resource Server**로 확인한다. 직접 해독 코드를 쓰지 않는다.
  | 항목 | 값 |
  |---|---|
  | 서명 | 공개 키 목록(JWKS) `https://<참조값>.supabase.co/auth/v1/.well-known/jwks.json`. **허용 알고리즘 서버 고정**(새 프로젝트 기본 비대칭 키 ES256 — 첫 실제 토큰 머리글로 확인). `none`·HS256 거부 |
  | 발급자 `iss` · 대상 `aud` · 만료 `exp` | `https://<참조값>.supabase.co/auth/v1` · `authenticated` · 시계 오차 30초까지 |
  | `role` · `is_anonymous` | `authenticated`만 · true면 거부 |
  | `session_id` | 아래 세션 확인 |

### 3-1. 세션 확인 함수 (V2, 사장 지시 4)
로그아웃·계정 삭제 뒤 옛 토큰을 막으려고, 토큰의 세션이 아직 살아 있는지만 DB에 묻는다. **`scent_server`의 auth 권한은 넓히지 않는다**(단계 3에서 auth 읽기 막힘 확인, 지금도 그대로).
```sql
create function app.session_alive(p_session_id uuid, p_user_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce((
    select true from auth.sessions s
    where s.id = p_session_id and s.user_id = p_user_id
      and (s.not_after is null or s.not_after > now())
    limit 1), false)
$$;
revoke all on function app.session_alive(uuid, uuid) from public, anon, authenticated;
grant execute on function app.session_alive(uuid, uuid) to scent_server;
```
- 주인은 `postgres`(마이그레이션 계정). 실제 DB에서 `postgres`는 `auth.sessions`를 읽을 수 있고 `scent_server`는 못 읽는 것을 확인했다(2026-11-06 `./gradlew dbAuthCheck`: 칸 `id, user_id, not_after …`, 주인 `supabase_auth_admin`).
- **돌려주는 것은 true/false 하나뿐.** 세션 id와 사용자 id가 **둘 다** 맞아야 true → 남의 세션 id를 넣어 봐도 알 수 있는 게 없다. 입력이 null이면 false.
- `security definer`의 위험(호출자가 search_path로 다른 표를 끼워 넣기)은 `search_path = ''`와 `auth.sessions` 전체 이름으로 막는다.
- 서버는 결과를 **30초** 메모리에 보관(같은 토큰으로 연달아 부를 때 DB를 덜 부름). 로그아웃하면 늦어도 30초 뒤 옛 토큰이 막힌다.
- **테스트(V2 DB 테스트, 테스트 준비 코드에 `auth.sessions` 흉내 추가):** 살아 있는 세션 → true / 지운 세션 → false / 남의 사용자 id와 짝 → false / `not_after` 지남 → false / null → false / `scent_server` 실행 됨 / anon·authenticated 실행 안 됨 / `scent_server`가 `auth.sessions`를 직접 읽으면 여전히 42501 / 함수가 security definer이고 search_path가 비어 있는지(`pg_proc`) / 계정 삭제(auth.users 줄 삭제 → 세션도 사라짐) 뒤 false.
- 서버 테스트: 세션 없는 토큰 401.

## 4. 요청 횟수 제한 (표는 사장 OK, 2026-11-06)
- **Bucket4j(토큰 바구니) + 서버 메모리.** Render 무료는 1대라 충분. 2대 이상이 되면 DB로 옮긴다(그때 결정).
  | 대상 | 기준 | 한도 |
  |---|---|---|
  | 저장·찜·시향 기록·취향 바꾸기 (POST·PUT·DELETE) | 사용자 id | 분당 30회 |
  | 취향 합치기 | 사용자 id | 분당 5회 |
  | 오류 신고 | 사용자 id | 시간당 20회 |
  | 계정 삭제 | 사용자 id | 시간당 3회 |
  | 읽기 (GET) | 사용자 id | 분당 120회 |
  | 사용 기록 `POST /events` | install_id + IP | **요청(묶음) 분당 10회** |
  | 카탈로그 파일 `GET /catalog/{version}` | install_id 하루 3회 + IP 시간당 10회 | (5절) |
  | 로그인 없이 부르는 것 | IP | 분당 60회 |
  | 토큰 확인 실패(401) | IP | 분당 20회 실패 뒤 5분 막음 |
- 넘으면 429 `RATE_LIMITED` + `Retry-After`(초).

### 4-1. Render 뒤에서 실제 IP 읽기 (사장 지시 5)
- Render는 요청을 자체 프록시(부하 분산기)를 거쳐 서버로 넘기고, 사용자 IP를 `X-Forwarded-For` **맨 끝에 덧붙인다**(확인 필요). 사용자가 보낸 `X-Forwarded-For` 값은 그 **앞쪽**에 남는다.
- **규칙:** `X-Forwarded-For`를 쉼표로 나눠 **오른쪽에서 `TRUSTED_PROXY_HOPS`번째**(기본 1 = 맨 끝) 값만 쓴다. 여러 줄로 온 헤더는 순서대로 이어 붙인 뒤 같은 규칙. 그 값이 IP 모양(IPv4·IPv6)이 아니거나 헤더가 없으면 연결 주소(remote address)를 쓴다. `X-Real-IP`·`True-Client-IP`·`CF-Connecting-IP`·`Forwarded`는 **읽지 않는다**(Render가 덮어쓴다는 확인이 없으면 사용자가 마음대로 넣을 수 있음).
- Spring의 `server.forward-headers-strategy`는 켜지 않는다(맨 왼쪽 값을 믿는 설정이 섞일 수 있음). 위 규칙을 필터 하나로 직접 둔다.
- **첫 배포 때 확인 절차:** 환경 변수 `IP_DEBUG=1`일 때만 켜지는 `/api/v1/_debug/ip`가 "헤더 이름 목록, `X-Forwarded-For` 항목 수, 고른 IP"를 돌려준다 → 이 컴퓨터에서 가짜 `X-Forwarded-For: 1.2.3.4`를 붙여 불러 고른 IP가 실제 공인 IP인지 확인 → 맞으면 `IP_DEBUG`를 지우고 다시 배포. 항목 수가 예상과 다르면 `TRUSTED_PROXY_HOPS`를 맞춘다. 결과는 NOTES(값 대신 "몇 번째였는지"만).
- **테스트(헤더 조작으로 제한 피하기, 서버 테스트):** 프록시가 덧붙인 모양을 흉내 내 연결 주소 = 프록시, `X-Forwarded-For: <가짜>, <진짜>`로 보낸다.
  1. 가짜 값 `1.2.3.4`를 앞에 넣어도 기준 IP는 진짜 값
  2. 요청마다 가짜 값을 바꿔 61번 → 61번째가 429 (가짜 값을 바꿔도 같은 바구니)
  3. `X-Forwarded-For`를 여러 줄로 보내기·쉼표 수십 개·빈 값 → 맨 끝 규칙 그대로, 서버 오류 없음
  4. `abc`, `999.1.1.1`, 포트 붙은 값 → 연결 주소로 대체
  5. `True-Client-IP`·`X-Real-IP`·`Forwarded`를 바꿔 가며 61번 → 429 (안 읽음)
  6. 로그인한 요청은 IP를 바꿔도 사용자 id 바구니로 429

### 4-2. 사용 기록 묶어 보내기 (사장 지시 6)
- **앱은 한 요청에 여러 개를 묶어 보낸다**(design.md 13절): 기기 대기열에 쌓았다가 30초마다, 또는 앱이 뒤로 가거나 탭이 가려질 때 보낸다. 인터넷이 끊겼으면 최대 1,000개까지 쌓아 두고(넘으면 오래된 것부터 버림), 다시 연결되면 **100개씩 묶어 차례로** 보낸다.
- **분당 10회는 묶음(요청) 기준이다.** 한 요청 = 최대 100개 → 분당 최대 1,000개. 끊긴 동안 꽉 찬 1,000개도 10번이면 1분 안에 다 간다. 429를 받으면 앱은 대기열을 지우지 않고 `Retry-After`만큼 기다렸다 보낸다.
- 한 요청 본문은 256KB 이하, 기록 하나의 props 2KB 이하(V1 검사와 같음). 묶음 안에 잘못된 기록이 섞이면 **그것만 빼고** 나머지를 저장하고 `{accepted, rejected:[번호]}`로 답한다(잘못된 기록 하나 때문에 대기열이 영원히 막히지 않게). 앱은 보낸 것을 모두 대기열에서 지운다.
- **다시 보낼 때 두 번 저장되지 않게:** 앱이 기록마다 무작위 `client_event_id`(uuid)를 붙이고, DB가 (install_id, client_event_id) 중복을 무시한다 → 응답을 못 받아 같은 묶음을 다시 보내도 한 번만 저장. **V2에서 events에 칸 추가**(7절).
- 승인 테스트 I2(끊겼다 다시 연결되면 기록이 서버로 감)는 그대로이고, 묶음·중복 무시는 서버 테스트와 앱 부품 테스트로 확인한다.

## 5. 카탈로그 배포 (사장 지시 7)
- **공개 주소를 두지 않는다.** 파일은 Supabase Storage **비공개 버킷** `catalog`에 두고, 앱은 **서버를 거쳐서만** 받는다. (서명된 임시 주소도 검토했지만 아래 전송량 계산에서 서버 경유가 훨씬 낫다.)
- 버킷에는 버전마다 두 파일: `catalog-<버전>.json.gz`(전체, 지금 **2,341,384바이트 ≈ 2.34MB**)와 `catalog-<버전>.keys.json.gz`(노트·향수·계열 key 목록만, **118,479바이트 ≈ 0.12MB**, 2026-11-06 실제 카탈로그로 잼).
- **서버:** 깨어날 때 key 목록 파일(0.12MB)만 읽어 검사에 쓴다. 전체 파일은 **누가 처음 내려받기를 요청할 때 한 번만** Storage에서 읽어 메모리에 보관하고(2.3MB), 다음 요청부터는 메모리에서 준다. sha256을 `catalog_releases` 값과 맞춰 본 뒤에만 쓴다.
- **앱:** 앱에는 출시 때 카탈로그가 들어 있다. 하루 한 번(인터넷이 될 때) `GET /catalog/latest`(수백 바이트)로 버전만 묻고, **서버 버전이 더 새롭고 앱 버전이 `minAppVersion` 이상일 때만** 전체를 받는다 → sha256이 맞으면 기기에 저장하고 바꿔 끼운다. 실패하면 갖고 있던 것을 계속 쓴다.
- **쉽게 받아 가기 막기:** 공개 주소가 없고, `X-Install-Id`(앱이 만든 uuid)가 있어야 하며, install_id 하루 3회 + IP 시간당 10회 제한, 목록 보기 없음, 응답에 `Cache-Control: private`. **한계도 적어 둔다:** 앱 안에 카탈로그가 들어 있으므로 앱을 뜯으면 꺼낼 수 있다. 이 장치는 "주소 하나로 대량으로 긁어 가기"를 막는 것이지 완전한 보호가 아니다.
- **알려진 한계 (사장 지시 2026-11-06):** 설치 id(`X-Install-Id`)는 앱이 만드는 값이라 누구나 바꿔 보낼 수 있다. 그래서 install_id 하루 3회 제한은 정상 앱의 실수를 막는 정도이고, **카탈로그 내려받기의 실제 방어는 IP 제한(시간당 10회)**이다. IP를 여러 개 쓰는 상대는 막지 못한다.

### 5-1. 무료 전송량으로 몇 번까지 되나
| 방식 | Supabase 전송량(월 5GB) 쓰는 곳 | 계산 | 한 달 전체 내려받기 |
|---|---|---|---|
| 서명된 임시 주소 (Storage에서 바로) | 내려받을 때마다 2.34MB | 5,000,000,000 ÷ 2,341,384 | **약 2,135번** |
| **서버 경유 + 서버 메모리 보관 (추천)** | 서버가 깨어날 때 key 목록 0.12MB + 그 깨어 있는 동안 첫 내려받기 때 한 번 2.34MB | 아래 | Supabase 쪽은 **깨어난 횟수에만** 비례 |
- 서버 경유일 때 Supabase 쪽 최악: 하루 30번 깨어남 × 30일 = 900번 × (0.12 + 2.34)MB ≈ **2.2GB/월**(매번 누가 내려받는다고 가정한 최악). 새 버전이 없는 달은 900 × 0.12MB ≈ **0.11GB**. 여기에 DB 응답·사용 기록 등 일반 전송이 더해지지만 작다.
- 사용자에게 나가는 전송은 Render 쪽: 무료 월 100GB(확인 필요) ÷ 2.34MB ≈ **약 42,700번**.
- 즉 서명 주소 방식은 "새 버전을 낼 때 사용자 약 2,100명"이 한계, 서버 경유는 Supabase 한도와 무관하게 Render 한도(약 4만 번)까지 간다. Supabase 무료 요금제의 전송량 구분(일반/캐시)은 확인 필요.

## 6. Render 무료에서 Spring 돌리기 (사장 지시 1)
Render 무료 웹 서비스: 메모리 512MB, CPU 0.1개, 15분 동안 요청이 없으면 잠듦(확인 필요).

### 6-1. 메모리 (512MB 안에서 안정적으로)
| 항목 | 설정 | 예상 크기 |
|---|---|---|
| 자바 힙 | `-XX:MaxRAMPercentage=55` (컨테이너 512MB 기준 약 280MB), `-XX:InitialRAMPercentage=25` | 최대 ~280MB |
| 클래스 정보(Metaspace) | `-XX:MaxMetaspaceSize=128m` | ~70~90MB(추정) |
| 컴파일 코드 | `-XX:ReservedCodeCacheSize=48m`, `-XX:TieredStopAtLevel=1`(빠른 컴파일만 — 시작 빠르고 메모리 적음, 최고 속도는 조금 손해) | ≤48MB |
| 쓰레드 | `-Xss512k`, Tomcat 최대 쓰레드 16, DB 연결 풀(Hikari) 최대 4 | ~10MB |
| GC | `-XX:+UseSerialGC`(CPU 0.1개에서는 단순한 GC가 유리, 메모리도 적음) | — |
| 카탈로그 | key 목록 + (내려받기 요청이 있으면) 전체 파일 2.3MB | ~10MB |
| 합 | | **평소 300~350MB, 최악 ~450MB(추정)** |
- `-XX:+ExitOnOutOfMemoryError`: 메모리가 넘치면 어설프게 버티지 않고 끝내서 Render가 다시 띄우게 한다.
- JPA 대신 JdbcClient(1절), 필요한 Spring 부품만(web, security, oauth2-resource-server, jdbc, flyway 없음 — 마이그레이션은 서버가 아니라 `./gradlew dbMigrate`로).
- **단계 4 관문:** 이 컴퓨터에서 Docker로 Render와 같은 조건(`--memory=512m --cpus=0.1`)을 만들어, 서버 테스트 흐름을 10분 돌리는 동안 **최대 메모리 사용량(RSS) 420MB 이하**인지 잰다. 넘으면 힙 비율을 낮추거나 6-3 대안. 결과는 NOTES.

### 6-2. 깨어나는 시간
| 단계 | 예상(추정) |
|---|---|
| Render가 잠든 서버를 다시 띄움(컨테이너 준비) | 10~30초 |
| JVM + Spring 시작 (CPU 0.1개라 일반 PC의 수 배 느림) | 20~60초 |
| 첫 요청 처리(key 목록 0.12MB 읽기, DB 연결) | 1~3초 |
| **합** | **약 30~90초** |
- 줄이는 방법(단계 4에서 적용하고 잰다): Spring **AOT + CDS(클래스 미리 읽어 둔 파일)**로 시작 시간을 줄인다(보통 30~50% 단축, 추정). 시작할 때 무거운 일을 하지 않는다(카탈로그 전체는 첫 내려받기 때).
- 앱은 이미 대비돼 있다: 서버가 3초 넘게 대답이 없으면 "서버를 깨우는 중이에요"(승인 E4), 추천은 서버 없이 된다.
- **단계 4 관문:** 같은 Docker 조건에서 "시작 명령 → `/health` 200"까지 시간을 5번 재서 NOTES에. 배포 후에는 15분 넘게 쉰 뒤 첫 요청 시간을 3번 잰다.

### 6-3. 너무 느리거나 메모리가 모자라면
1. GraalVM 네이티브 이미지(시작 1초 이내, 메모리 ~100MB, 추정) — 빌드가 복잡해지고 일부 라이브러리 설정이 필요
2. Render 유료(월 약 7달러, 잠들지 않음, 확인 필요)
3. Cloud Run 서울(카드 필요)
→ 그때 사장에게 보기와 추천을 드린다.

## 7. 마이그레이션 V2 (사장 지시 8, 단계 3과 같은 방식)
V1은 실제 DB에 적용됐으므로 고치지 않고 **V2를 더한다.** 같은 방식: RLS 켜기 + `scent_server`에만 규칙, 칸 단위 권한, 검사 함수, 내장 Postgres 테스트 먼저, 변조로 테스트가 잡는지 확인, 리뷰 뒤 실제 DB 적용.

### 7-1. `app.catalog_releases` — 카탈로그 버전 표
| 칸 | 타입 | 규칙 |
|---|---|---|
| version | text **기본 키** | `YYYY.MM.DD` 또는 `YYYY.MM.DD.n` 모양 |
| engine_version | integer | > 0 |
| sha256 · keys_sha256 | text | 소문자 16진수 64자 |
| size_bytes | integer | 1 ~ 50,000,000 |
| storage_path · keys_storage_path | text, 유일 | `catalog-<버전>.json.gz` / `catalog-<버전>.keys.json.gz` 모양만 |
| min_app_version | text | `숫자.숫자.숫자` |
| is_current | boolean, 기본 false | **true는 한 줄만**(부분 유일 인덱스) |
| published_at | timestamptz, 기본 지금 | |
| note | text | 200자 이하 |
- 권한: `scent_server`는 **읽기만**. 넣기·바꾸기는 마이그레이션 계정으로 도구 하나(`./gradlew dbCatalogPublish`: 파일 올리기 → sha256 계산 → 줄 넣기 → 지금 버전 바꾸기를 한 트랜잭션)로만. anon·authenticated 아무것도 없음, RLS 켬.
- 테스트: 모양 검사(잘못된 버전·해시·경로·앱 버전 거부), 지금 버전 두 줄 거부, `scent_server` 넣기·바꾸기·지우기 42501, anon·authenticated 권한 없음, RLS 켜짐.

### 7-2. 같은 V2에 넣는 것
- `app.session_alive(uuid, uuid)` (3-1절) + 테스트
- `app.events.client_event_id uuid` 칸 + (install_id, client_event_id) 유일 인덱스 + `scent_server` 그 칸 넣기 권한 (4-2절). 기존 줄은 비어 있어도 되게(null 허용, 새 앱 버전부터 필수는 서버가 확인).
- **V3(서버 구현 중):** `app.error_reports`(오류 신고: 신고자 user_id — 계정 삭제 시 같이 삭제, 향수·노트 key, 종류, 메모 500자, 신고 당시 조합 notes(선택, blends.notes와 같은 검사 — 사장 결정 2026-11-09), 처리 상태, 만든 때), 같은 방식.

## 8. 서버 테스트 (단계 4, JUnit + Spring Boot Test + 내장 Postgres + 가짜 JWKS)
- 토큰: 없음·서명 다름·none·HS256·만료·aud 다름·anon 토큰·익명 사용자·세션 없음 → 401
- 남의 데이터: 남의 조합 id 읽기·바꾸기·지우기 → 404, 남의 시향 기록 지우기 → 404
- 검사: 카탈로그에 없는 key 400, 층당 4개 400, 이름 41자 400, 동의 없음 403
- 한도: 동시에 20번 저장해도 100개 안 넘음, 찜 300개 뒤 `LIMIT_REACHED`
- 요청 제한: 4절 표대로 429, 4-1의 헤더 조작 6가지
- 사용 기록: 100개 넘는 묶음 400, 잘못된 것만 빼고 저장, 같은 묶음 두 번 → 한 번만 저장
- 카탈로그: 공개 주소 없음, install_id 없으면 400, 하루 4번째 429, sha256 안 맞는 파일은 내주지 않음
- 계정 삭제: 삭제 후 모든 표 0줄, 옛 토큰 401(세션 없음)

## 9. 단계 4 순서 (승인 뒤 계획 모드에서 자세히)
1. V2 마이그레이션 + DB 테스트 → 리뷰 → 실제 DB 적용
2. Spring Boot 뼈대 + `/health` + Docker 512MB·CPU 0.1 측정(6절 관문)
3. 토큰·세션 확인 + 요청 제한(IP 규칙 포함) + 오류 형식
4. 취향·합치기·동의 → 조합 → 찜 → 시향 기록 → 사용 기록 → 카탈로그 → 오류 신고(V3) → 계정 삭제
5. 리뷰(QA팀 + Gemini) → 레드팀(로그인·토큰·회원 파일이라 `.redteam-needed`) → Render 배포는 ★사장 `!` 한 줄 → IP 확인 절차(4-1) → 예약 작업을 `/health`로 바꿈

## 10. design.md와 다른 점 (승인되면 design.md 1·11·16절을 고친다)
| 무엇 | design.md | 이 안 | 이유 |
|---|---|---|---|
| DB 접근 | Spring Data JPA | Spring JDBC(JdbcClient) | 512MB·CPU 0.1에서 시작 시간·메모리 |
| 서버의 마이그레이션 | 서버를 처음 켜면 Flyway가 표를 만듦 | 서버는 마이그레이션하지 않음, `./gradlew dbMigrate`로만 | 서버 계정(`scent_server`)은 표를 만들 권한이 없음(그게 맞음) |
| 카탈로그 배포 | `GET /catalog/{version}` 파일, `url` 안내 | 서버 경유만, 공개 주소 없음, 비공개 Storage | 데이터 라이선스, 전송량 |
| 사용 기록 | 묶음 최대 100개 | + `client_event_id`로 중복 무시, 잘못된 것만 빼고 저장 | 다시 보낼 때 두 번 저장 방지 |
| 오류 신고 | 없음 | `POST /reports`, 로그인한 사람만, V3 | 사장 결정 |
