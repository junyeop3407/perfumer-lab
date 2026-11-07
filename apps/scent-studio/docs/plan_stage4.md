# scent-studio 단계 4(서버) 계획 — 작은 묶음 9개

> 2026-11-06 계획 모드에서 세워 사장 승인. 승인 때 추가 지시 3개는 맨 아래 "승인 때 추가 (2026-11-06)". 진행 상태는 `plan.md` 진행 표.

## Context
- 단계 3(DB)은 끝났다: 실제 Supabase(Postgres 17.11)에 V1(표 6개, RLS, `scent_server`)이 적용됐고, 예약 작업 keepalive가 돈다.
- 서버 설계안 2판(`apps/scent-studio/docs/server_design.md`)은 사장 OK를 받았다: Render 무료, 요청 제한 표대로, 오류 신고는 로그인한 사람만.
- 이번에는 그 설계를 **작은 묶음으로 나눠** 하나씩 만든다. 사장 지시:
  - 첫 묶음은 512MB·CPU 0.1 실측이다. 이 컴퓨터에는 Docker·WSL이 없어서 **Render에서 직접 잰다**(사장 결정).
  - 묶음마다 QA 리뷰 + Gemini 교차 리뷰를 한다.
  - V2 마이그레이션은 실제 DB 적용 전에 멈춘다.
  - 사장 할 일은 묶음마다 표시한다.

## 모든 묶음에 공통
- 순서: **테스트 먼저 → 구현 → `bash tests/run.sh` 통과 → 일부러 망가뜨려 테스트가 잡는지 1번 → QA팀 에이전트 리뷰 + Gemini 교차 리뷰 1라운드** → 맞는 지적은 회귀 테스트 먼저 넣고 고침(`DbReviewFixesTest`처럼 `*ReviewFixesTest`) → `docs/review.md`에 묶음별 절 → plan.md·HANDOFF 갱신 → 커밋·푸시 → 사장에게 짧게 보고.
- Gemini는 `bash .claude/skills/build-app/cross-review.sh apps/scent-studio <묶음 시작 커밋>`. 심각도 높음인데 애매하면 고치기 전에 묻는다.
- 서버 테스트는 지금 `backend/`의 JUnit + 내장 Postgres(`DbTestBase`, `supabase_stub.sql`)를 그대로 넓혀 쓴다. 가짜 JWKS는 테스트 안에서 키를 만들어 띄운다. 훅 5분 안에 끝나게 내장 Postgres는 테스트 클래스끼리 한 번만 띄우는 방향.
- 로그인·토큰·회원·계정 삭제 파일을 고치면 `.redteam-needed`가 생긴다 → 묶음 9 레드팀 전까지 남겨 둔다.
- 비밀값은 `backend/.env`(이 컴퓨터)와 Render 환경 변수에만 둔다. 출력·커밋하지 않는다.

## 묶음
| # | 만드는 것 | 먼저 쓸 테스트 | 끝 기준 | 사장 할 일 |
|---|---|---|---|---|
| **1 실측** | Spring Boot 4.1 뼈대(최종에 쓸 부품을 미리 다 넣음: web, security, oauth2-resource-server, jdbc(JdbcClient), validation, springdoc), `GET /api/v1/health`(DB `select 1`, `scent_server`로), JVM 설정(`server_design.md` 6-1), Dockerfile(여러 단계, JRE 21, AOT + CDS), `render.yaml`, `IP_DEBUG`·`MEM_DEBUG` 환경 변수일 때만 켜지는 측정 주소(메모리 RSS·힙·시작 시간, IP 헤더 모양) | health: DB 됨 200 / DB 안 됨 503 / 측정 주소는 환경 변수 없으면 404 / 그 밖 주소는 401·404 / 응답에 비밀값 없음 | **Render 무료(싱가포르)에서:** ① 10분 동안 health 반복 호출 중 최대 RSS **420MB 이하** ② 시작 → health 200 시간 5번 ③ 15분 넘게 쉰 뒤 첫 요청 시간 3번 → NOTES. **못 넘으면 여기서 멈추고 대안(네이티브 이미지 / Render 유료 / Cloud Run) 보고** | **★ Render 가입**(GitHub 계정) → Web Service 만들기(저장소 연결, 브랜치 `wip/scent-studio-stage4`, Root `apps/scent-studio/backend`, Docker, Singapore, Free — 화면별 안내 드림) → 환경 변수 3개(`DB_URL`, `DB_SERVER_USER`, `DB_SERVER_PASSWORD`) 입력 → 배포 승인 |
| **2 V2** | `V2__...sql`: `app.catalog_releases`, `app.session_alive(uuid, uuid)`(security definer, `scent_server`만), `events.client_event_id` + 유일 인덱스. 테스트 준비에 `auth.sessions` 흉내 | `server_design.md` 3-1·7-1 테스트 목록(세션 함수 10개, 카탈로그 표 모양·지금 버전 한 줄·권한·RLS, client_event_id 중복 무시) | 내장 Postgres 통과 + 변조 확인 + 리뷰. **실제 DB 적용 전에 멈추고 보고** → OK 뒤 `./gradlew dbMigrate` + `dbCheck` | V2 보고 확인 후 적용 OK |
| **3 로그인·제한** | 토큰 확인(JWKS, ES256 고정, iss·aud·exp·role·is_anonymous), 세션 확인(30초 보관), 오류 형식, Bucket4j 요청 제한(`server_design.md` 4절 표), IP 규칙(4-1) | 401 경우 9가지, IP 헤더 조작 6가지, 제한 표대로 429 + Retry-After, 401 잦은 IP 5분 막음 | 서버 테스트 통과 + 리뷰. (선택) 실제 Supabase 토큰 1개로 ES256·JWKS 확인 | (선택) Supabase 대시보드에서 **테스트 계정 1개**(이메일·비밀번호) 만들고 `.env`에 → 묶음 9 뒤 지움 |
| **4 취향·동의** | `GET /me`, `POST /me/consents`, 취향 GET·PUT, `merge`(불호 합집합·선호 서버 우선·200 상한), 동의 없으면 403 | 합치기 규칙 표(`db_design.md` 5절), 동의 없음 403, 카탈로그에 없는 노트 400, 남의 것 못 봄 | 서버 테스트 통과 + 리뷰 | 없음 |
| **5 조합·찜·시향** | 조합 CRUD, 찜, 시향 기록(본인 삭제), 1인당 한도 100·300·1,000(사용자 단위 잠금) | 남의 id 404, 검사 400들, 동시에 20번 저장해도 100 안 넘음, 찜 중복 성공 | 서버 테스트 통과 + 리뷰 | 없음 |
| **6 사용 기록·카탈로그** | `POST /events`(묶음 100, 잘못된 것만 빼고 저장, client_event_id 중복 무시), Storage 비공개 버킷 + `./gradlew dbCatalogPublish`, `GET /catalog/latest`·`/catalog/{version}`(서버 경유, key 목록만 시작 때, 전체는 첫 요청 때 메모리 보관, sha256 확인) | 묶음 101개 400, 같은 묶음 두 번 → 한 번 저장, install_id 없음 400, 하루 4번째·IP 11번째 429, 해시 다른 파일 안 내줌, 공개 주소 없음 | 서버 테스트 통과 + 리뷰 + 실제 카탈로그 v2026.11.05를 비공개 버킷에 올림 | **Supabase Secret key**를 `backend/.env`(`SUPABASE_SECRET_KEY`)와 Render 환경 변수에 넣기(대화창 금지) |
| **7 오류 신고** | `V3__error_reports.sql`(같은 방식) + `POST /reports`(로그인만, 시간당 20회) | 표 검사·권한·RLS·계정 삭제 시 같이 삭제, API 401·400·429 | 내장 Postgres + 서버 테스트 + 리뷰. **V3도 실제 DB 적용 전에 멈추고 보고** | V3 적용 OK |
| **8 계정 삭제** | `DELETE /me`: Supabase 관리 API로 계정 삭제 → DB 연쇄 → 남은 줄 0 확인, 옛 토큰 401 | 삭제 후 모든 표 0줄, 관리 API 실패 시 아무것도 안 지워지고 오류, 옛 토큰(세션 없음) 401, 시간당 3회 | 서버 테스트 통과 + 리뷰 + (테스트 계정으로) 실제 삭제 1번 확인 | 없음(묶음 3 테스트 계정 사용) |
| **9 마무리·배포** | OpenAPI 문서, 전체 API로 Render 512MB 재측정(10분 부하), **레드팀**(배포된 서버를 직접 공격) → 고침, IP 확인 절차(`IP_DEBUG`로 몇 번째 값인지 확인 후 끔), keepalive 예약 작업을 `/health`로 바꿈(main에도) | 레드팀에서 찾은 구멍마다 회귀 테스트 | 레드팀 뚫림 0, `.redteam-needed` 지움, 배포 전 체크리스트 12개 ✓/✗, RSS 420MB 이하 다시 확인 | **★ 배포 승인**(`!` 한 줄), 테스트 계정 지우기 |

## 바꿀·만들 주요 파일
- 새로 만듦: `backend/src/main/java/com/perfumerlab/scentstudio/**`(서버), `backend/src/main/resources/application.yml`, `backend/Dockerfile`, `backend/render.yaml`, `backend/src/main/resources/db/migration/V2__*.sql`·`V3__*.sql`, `backend/src/test/java/.../api/**`
- 고침: `backend/build.gradle.kts`(Spring Boot 플러그인·부품), `backend/src/test/resources/supabase_stub.sql`(auth.sessions), `backend/src/ops/.../DbOps.java`(dbCatalogPublish), `tests/run.sh`(서버 테스트는 이미 `./gradlew test`에 포함), `docs/plan.md`·`docs/NOTES.md`·`HANDOFF.md`·`docs/review.md`, 묶음 9에서 `.github/workflows/supabase-keepalive.yml`
- 그대로 다시 씀: `DbTestBase`(내장 Postgres + 마이그레이션), `DbOps`(`dbMigrate`·`dbCheck`·`dbServerCheck`), `scripts/api_check.sh`, `cross-review.sh`
- 승인 후 design.md 1·11·16절을 `server_design.md` 10절대로 고친다(JPA → JdbcClient, 서버는 마이그레이션 안 함, 카탈로그 서버 경유).

## 확인 방법
- 묶음마다 `bash tests/run.sh`(파이프라인 + Vitest + 서버/DB JUnit) 통과, 변조 1번, 리뷰 표.
- 묶음 1·9: Render 대시보드 메모리 그래프 + 측정 주소 값, `curl -w '%{time_total}'`로 시작·깨어나는 시간.
- 묶음 2·7: `./gradlew dbCheck`로 실제 DB 표·권한·RLS, `bash backend/scripts/api_check.sh`로 공개 키 막힘(새 표 포함하도록 늘림).
- 승인 목록(Playwright 41개)은 화면 단계(단계 5)에서 다룬다. 이번 단계에서는 1/41 그대로(정상).

## 멈추는 곳 (사장 보고)
묶음 1 실측 결과(못 넘으면 대안 보고) / 묶음 2 V2 실제 적용 전 / 묶음 7 V3 실제 적용 전 / 묶음 9 배포 승인 전. 그 밖의 묶음도 끝날 때마다 짧게 보고하고 다음 묶음은 OK 뒤에 시작.

## 승인 때 추가 (2026-11-06 사장 지시)
1. **측정 주소(`MEM_DEBUG`·`IP_DEBUG`)는 무작위 토큰 헤더(`X-Debug-Token`)가 맞을 때만 응답**한다(틀리거나 없으면 없는 주소와 같은 404). 토큰(`DEBUG_TOKEN`, 영문·숫자 40자)은 Render 환경 변수와, 직원이 부르려고 이 컴퓨터 `backend/.env`(커밋 안 함)에만 둔다. **묶음 1 실측이 끝나면 바로 끈다**(Render 환경 변수 `MEM_DEBUG`·`IP_DEBUG`·`DEBUG_TOKEN` 삭제, `.env`에서도 지움). 묶음 9까지 두지 않는다. Render 뒤 IP 헤더 모양(4-1 확인 절차)도 묶음 1에서 함께 재고 끈다 → 묶음 9에서 다시 필요하면 새 토큰으로 잠깐 켠다.
2. **테스트 계정은 Supabase 대시보드 Authentication → Users → Add user로만** 만든다(앱·API로 가입하지 않음). 묶음 9 배포 전 체크리스트에 **"Supabase 이메일 가입·로그인 끄기(구글 로그인만)"**를 넣는다 — 테스트 계정을 지운 뒤에 끈다(끄면 이메일 테스트 계정도 못 씀). `scripts/api_check.sh`에 공개 키로 **이메일 가입이 막히는지** 확인을 넣는다: 먼저 `/auth/v1/settings`(읽기만)로 이메일 가입이 꺼졌는지 보고, 꺼져 있을 때만 실제 가입 시도로 막힘을 확인한다(켜져 있을 때 시도하면 진짜 계정이 생기므로 하지 않고 "아직 켜져 있음"으로 알림).
3. **묶음 6의 Secret key는 기본 키를 쓰지 않고 서버 전용 키를 새로 만든다**: Supabase **Project Settings → API Keys → Secret keys → New secret key**, 이름 `render-server`. 이 키만 `backend/.env`(`SUPABASE_SECRET_KEY`)와 Render 환경 변수에. 유출되면 이 키만 지우고 새로 만든다. **키가 응답·로그·오류 메시지에 안 나오는 테스트**를 묶음 6에 넣는다(Storage·관리 API가 실패하는 경우를 일부러 만들어 응답 본문·로그 출력에 키 문자열이 없는지 확인, 묶음 8 계정 삭제 실패에도 같은 테스트).

## 묶음별 상태 (2026-11-07)
| # | 상태 |
|---|---|
| 1 실측 | ✅ 완료(2026-11-07, RSS 최대 178MB 통과, 사장 OK, 측정 주소 꺼짐 확인, NOTES) |
| 2 V2 | ✅ 완료(2026-11-07): 리뷰(QA 9 + Gemini 0) 뒤 사장 OK로 실제 DB 적용, dbCheck·dbServerCheck·api_check 기대대로(NOTES). 살아 있는 세션 true 확인만 묶음 3 테스트 계정 뒤 |
| 3 로그인·제한 | ✅ 완료(2026-11-07): 토큰(ES256 고정)·세션 확인·오류 모양·요청 제한·IP 규칙, 서버·DB 231. 변조 8/8, 리뷰 QA 11 + Gemini 3(높음 2 반영). 실제 세션 true 확인. /events·카탈로그 제한은 묶음 6. **묶음 9 사장 할 일에 Render 환경 변수 `SUPABASE_URL` 추가** |
| 4 취향·동의 | 대기 |
| 5 조합·찜·시향 | 대기 |
| 6 사용 기록·카탈로그 | 대기 |
| 7 오류 신고 | 대기 |
| 8 계정 삭제 | 대기 |
| 9 마무리·배포 | 대기 |
