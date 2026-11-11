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
| **8 계정 삭제** | `DELETE /me`: Supabase 관리 API로 계정 삭제 → DB 연쇄 → 남은 줄 0 확인, 옛 토큰 401. **삭제가 끝나면 그 사용자의 세션 확인 보관 기록(30초)을 바로 지운다**(사장 지시 2026-11-07: 30초 틈 없이) | 삭제 후 모든 표 0줄, 관리 API 실패 시 아무것도 안 지워지고 오류, 옛 토큰(세션 없음) 401, 시간당 3회, **삭제 직후(시계를 돌리지 않고) 같은 토큰으로 부르면 바로 401** | 서버 테스트 통과 + 리뷰 + (테스트 계정으로) 실제 삭제 1번 확인 | 없음(묶음 3 테스트 계정 사용) |
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

## 묶음별 상태 (2026-11-08)
| # | 상태 |
|---|---|
| 1 실측 | ✅ 완료(2026-11-07, RSS 최대 178MB 통과, 사장 OK, 측정 주소 꺼짐 확인, NOTES) |
| 2 V2 | ✅ 완료(2026-11-07): 리뷰(QA 9 + Gemini 0) 뒤 사장 OK로 실제 DB 적용, dbCheck·dbServerCheck·api_check 기대대로(NOTES). 살아 있는 세션 true 확인만 묶음 3 테스트 계정 뒤 |
| 3 로그인·제한 | ✅ 완료(2026-11-07): 토큰(ES256 고정)·세션 확인·오류 모양·요청 제한·IP 규칙, 서버·DB 231. 변조 8/8, 리뷰 QA 11 + Gemini 3(높음 2 반영). 실제 세션 true 확인. /events·카탈로그 제한은 묶음 6. **묶음 9 사장 할 일에 Render 환경 변수 `SUPABASE_URL` 추가** |
| 4 취향·동의 | ✅ 완료(2026-11-07): /me·동의·취향·합치기, 동의 버전 설정 파일(draft-1)·가입일 없음(사장 결정), 서버·DB 325. 변조 6/6, 리뷰 QA 9 + Gemini 0. 카탈로그 key 목록은 묶음 6에서 Storage로. 사장 지시로 IP 글자 해석(InetAddress 안 씀) |
| 5 조합·찜·시향 | ✅ 완료(2026-11-08): 조합 CRUD·찜·시향 기록(본인 삭제), 남의 id 404, 한도 100·300·1,000(사용자 단위 잠금, 2초 대기 뒤 503), 한도 넘으면 409 LIMIT_REACHED. 변조 5/5(+잠금 확인 테스트), 리뷰 QA 9(중간 2 반영) + Gemini 5(반영 0). NOTES "묶음 5" |
| 6 사용 기록·카탈로그 | ✅ 코드 완료(2026-11-08): /events·카탈로그·Storage 키 목록·배포 도구(미리 보기만 실행). 변조 7/7, 리뷰 QA 13(중간 4 반영) + Gemini 4(1·3 반영). 실제 카탈로그 2026.11.05 배포 완료(2026-11-09, 남은 것 5). **Render 배포 완료(2026-11-09, 892a79f)**: health 200, `/catalog/latest`·`/catalog/2026.11.05` 200(해시 같음), 토큰 없이 401, 테스트 계정 찜(실제 key) 204·없는 key 400·취향 200/400(되돌림). 첫 빌드는 Gradle 받기 시간 초과로 실패 → 래퍼 networkTimeout 60초·retries 3(사장 지시). NOTES "묶음 6" |
| 7 오류 신고 | ✅ 완료(코드 2026-11-09, 적용·배포 2026-11-10): V3 `app.error_reports`(서버는 넣기 11칸만 — 2026-11-10 신고 당시 조합 notes 추가, 계정 삭제 시 같이 삭제), `POST /reports`(로그인·동의, 시간당 20회, key 확인, 204). 서버·DB 560. 변조 4/4, 리뷰 QA 9(낮음 2 반영) + Gemini 0 + 커밋 뒤 보안 검사 1(인코딩 주소로 한도 피하기, 반영). **V3 실제 DB 적용 완료(2026-11-10 09:15 KST, 27335e7)**: dbMigrate 1개(지금 버전 3) → dbCheck 넣기 11칸·규칙 server_insert → api_check 모두 기대대로. **Render 6694790 배포·확인(2026-11-10)**: health 200(db ok), 신고 토큰 없이 401·테스트 계정 204(조합 칸 포함, 확인용 신고 1건 남음 — 계정 삭제 때 같이 지워짐), 찜 204(되돌림). NOTES "묶음 7" |
| 8 계정 삭제 | ✅ 완료(2026-11-10): `DELETE /me`(관리 API → 외래 키 정리 → 보관 지우고 60초 "지워짐" 표시 → 남은 줄 0 → 204, 실패 503·아무것도 안 지움, 동의 불필요, 시간당 3회). 서버·DB 614, 변조 8/8 + 4/4, 리뷰 QA 중간 2 반영·낮음 4 + Gemini 1 반영. **실제 삭제 확인 완료(2026-11-10, 테스트 계정): 204, 같은 토큰 401, 남은 줄 0.** 테스트 계정 다시 만들기 대기. NOTES "묶음 8" |
| 9 마무리·배포 | 🔄 진행 중: **OpenAPI 정리 완료(2026-11-11)** — `docs/openapi.json`(운영은 문서 주소 꺼짐 401), `OpenApiDocTest` 8, 리뷰 QA(중간 5 반영) + Gemini 4(1 반영). 다음: Render 배포 → 재측정 → 레드팀 → 체크리스트 |

## 남은 것 (2026-11-08 묶음 6 끝, 빠짐없이)
| # | 할 일 | 언제 | 상태 |
|---|---|---|---|
| 1 | IP 값은 IPv4·IPv6 형식으로만 해석(DNS 조회 없음), 형식이 아니면 연결 IP. 테스트 포함 (사장 지시, 묶음 4 시작 전) | 묶음 4 | ✅ 처리됨(a61712d): `ClientIp` 글자 해석, InetAddress 안 씀(클래스 파일 검사 테스트), 이름 모양 값 DNS 없음 테스트 |
| 2 | 계정 삭제 때 그 사용자의 세션 확인 보관 기록을 바로 지워 30초 틈 없애기 + "삭제 직후 같은 토큰 바로 401" 테스트 (사장 지시) | 묶음 8 | ✅ 2026-11-10 (`sameTokenIs401RightAfter`, 60초 지워짐 표시) |
| 3 | 한도 분류를 경로 문자열 대신 컨트롤러 패턴 기준으로 + 경로 변형 테스트 (리뷰 묶음 3 QA-6) | 묶음 6·8 (특별 한도 API가 생길 때. 묶음 5 API는 모두 기본 분류라 바뀐 것 없음) | ✅ 2026-11-09 처리(묶음 7): 인코딩 주소로 신고·합치기 한도를 피하는 것을 재현 → 푼 주소로 분류, 회귀 테스트 `Bundle7ReviewFixesTest`. 컨트롤러 패턴 기준까지는 안 감(묶음 8 `DELETE /me`도 같은 방식으로 됨) |
| 4 | 삭제된 계정 토큰이 30초 보관 중 저장하면 FK 위반 500 → 2번으로 해결되는지 확인 (리뷰 묶음 4 QA-5·8, 묶음 5 QA-3: 시향 기록은 blendId가 있으면 404로 보임) | 묶음 8 | ✅ 2026-11-10 (동의 필요한 저장 403, 동의 기록 외래 키 → 401, `savingWithTokenOfAccountDeletedElsewhereIs401Not500`) |
| 5 | 실제 카탈로그 배포(비공개 버킷 만들기 + 두 파일 올리기 + catalog_releases) — 사장 OK 뒤 `dbCatalogPublish -Papply=true`, 그 뒤 `bash scripts/api_check.sh`로 공개 주소 막힘 확인. 서버 새 코드를 Render에 올리기 전에 해야 함(안 하면 저장이 503) | 묶음 7 전 또는 묶음 9 전 | ✅ 2026-11-09 완료: 비공개 버킷 catalog 만듦, 2026.11.05(엔진 8) 전체 2,341,384바이트·key 목록 118,077바이트 올림(sha256 확인), catalog_releases 지금 버전 2026.11.05, 공개 주소 400. 이 컴퓨터에서 서버를 띄워 진짜 Storage로 `/catalog/latest`·`/catalog/2026.11.05` 200(해시 같음), 로그에 키 없음. key 목록 파일은 서버 키로 직접 받아 해시 같음(테스트 계정이 이 컴퓨터 .env에 없어 key 목록을 쓰는 로그인 API는 못 불러 봄) |
| 13 | **Render에 `SUPABASE_URL` 넣기(묶음 6 코드 배포 전)**: 서버는 이 주소로 Storage·로그인 키(JWKS)를 부른다. 없으면 카탈로그·저장 API 503, 로그인 API 401. 비밀값 아님(`https://<참조값>.supabase.co`). 묶음 9 체크리스트에 있던 것을 앞당김 | Render 배포 전 | ✅ 2026-11-09 사장이 넣음, 묶음 6 코드 Render 배포(892a79f) 뒤 확인 끝 |
| 6 | 요청 제한 바구니 지도(5만 개)를 한도 종류별로 나눌지 부하 측정에서 보기 (리뷰 묶음 6 QA-6) | 묶음 9 | 대기 |
| 7 | 앱 안 카탈로그 파일(`app/assets/catalog/catalog.json.gz`)이 옛 버전(2026.10.31, 엔진 6) → 배포한 버전으로 교체 | 단계 5 | 대기 |
| 8 | 예전 커밋 4개에 실제 향수 key 1개가 남아 있음 — 기록 고쳐 쓰기 금지라 그대로 둠(비밀값 아님) | — | 그대로 둠 |
| 9 | 2026-11-08 사장 결정: 1차 기능 7개 추가(카카오 로그인·로그아웃·동의 화면·데이터 출처 화면·"추천이 이상해요" 신고·첫 실행 안내·앱 정보), Play 비공개 테스트(12명 이상, 14일) 뒤 공개, 세로 고정 → spec·requirements 반영(프롬프트 A). **카카오 로그인·동의 화면은 묶음 8 전에 계획에 넣기** | 다음 세션 | ✅ 2026-11-09 반영(requirements "1차 추가", spec 기능 12~17, tests J1~J8·K1~K7 사장 승인 15개 + G2 보강(2026-11-09), 승인 목록 56개, app.json portrait). 계획은 아래 10·11 |
| 10 | **카카오 로그인 서버 쪽**: Supabase 카카오 공급자 켜기 + "이메일 없는 사용자 허용"(사장 할 일, 화면별 안내). 서버: 카카오 로그인 토큰도 같은 iss·aud·ES256인지 실제 토큰 1개로 확인, `GET /me`는 email 칸이 없거나 빈 값이어도 200 + `email: null`(빈 문자열은 null로) 테스트, 구글·카카오 계정은 서로 다른 user id라 합치지 않음(테스트로 남의 데이터 안 보임 확인) | 묶음 8 (삭제와 함께, 시작 전 사장 OK) | ⏸ **보류(2026-11-10 사장 결정)**: 서버 코드·테스트 완료(7f63b85, 이메일 null·계정 분리). 실제 로그인이 invalid_scope(account_email, Supabase가 늘 요청·일반 앱) → 비즈 앱 전환 → 이메일 선택 동의 → 재시도 → 토큰 확인. 개인정보처리방침 "카카오 로그인 시 이메일(선택)". **Play 비공개 테스트 전까지.** 대안·자세히 `kakao_setup.md` "카카오 로그인 보류" |
| 11 | **동의 화면**: 서버(묶음 4 `POST /me/consents`, `consents.complete`)는 그대로 쓴다. 화면 S13은 단계 5. 실제 약관·방침 문구 → `draft-1`을 날짜 버전으로(묶음 9 체크리스트에 이미 있음). 버전이 오르면 다시 동의받는지 서버 테스트 확인(이미 있음, 묶음 4) | 문구 묶음 9 전 · 화면 단계 5 | 대기 |
| 12 | "추천이 이상해요" 신고: 서버 `POST /reports`는 묶음 7(V3), 화면 버튼은 단계 5(K3·K4) | 묶음 7 · 단계 5 | 서버 코드 완료, V3 적용 대기 |

| 14 | 묶음 6 배포(카탈로그 메모리 보관 포함) 뒤 메모리 420MB 기준 확인 — Render 무료는 Metrics 메모리 그래프가 유료 전용이라 못 봄(사장 확인 2026-11-09) → 묶음 9 재측정 때 `DEBUG_TOKEN` 측정 주소 + 10분 부하로 RSS 최대값 | 묶음 9 | ✅ 2026-11-11 전체 API 10분: RSS 최대 208MB·컨테이너 216MB(NOTES "묶음 9") |
| 16 | **단계 5 앱 타입**: `docs/openapi.json`으로 openapi-typescript. 그때 응답 record 칸에 required 표시(지금은 모든 칸이 선택값으로 나옴, 리뷰 묶음 9 QA 낮음) | 단계 5 | 대기 |
| 15 | **V3에 신고 당시 조합(노트·강도) 칸 추가**(사장 결정 2026-11-09): 없어도 되는 칸(검색으로 연 상세에서 신고할 때), 검사는 조합 저장(blends.notes)과 같은 규칙. 테스트 먼저 → 리뷰 → V3 실제 적용(사장 OK 받음) | 묶음 7 (V3 적용 전) | 대기 |

### 배포 전 점검 (2026-11-09, `launch_checklist.md`에서 옮김)
- **[묶음 9]** 앱·서버 로그에 토큰·이메일이 안 찍히는지(서버 로그 검사 테스트 + 배포 뒤 Render 로그 확인). *Secret key가 응답·로그에 안 나오는 테스트는 묶음 6에 이미 있음 → 토큰·이메일로 넓힘*
- **[묶음 9]** Supabase 돌아올 주소(Redirect URLs)에 운영 웹 주소·앱 스킴 `perfumerlab://` 등록(구글·카카오)
- **[묶음 9]** 공개 저장소 커밋 기록 전체 비밀값 검사(`.env`·`SCENT_KEY_SECRET`·`sb_secret_`). *예전 커밋 4개의 향수 key는 비밀값 아님, 그대로(위 8)*
- **[묶음 9]** 레드팀 범위에 카카오 토큰 포함(카카오 파일을 고치면 `.redteam-needed`)
- **[단계 5]** 로그인 창에 갔다 오는 사이 앱이 정리돼도 조합·불호 유지(E1에 더함)
- **[단계 5]** 로그인 직후 서버가 자고 있을 때 취향 합치기 — *"서버 깨어나는 동안 안내·다시 시도"는 이미 있음(아래 단계 5 할 일, 승인 E4)* → 합치기 경우만 더함
- **[단계 5]** 시간 초과 → 다시 시도(무한 로딩 금지) — *이미 있음(같은 줄, E3·E4)*, 모든 서버 호출에 시간 제한을 둔다는 점만 더함
- **[단계 5]** 저장·찜·계정 삭제 버튼 연속 클릭 잠금(서버 한도·찜 중복 처리는 이미 있음)
- **[단계 5]** 키보드가 검색창을 가리지 않음 / 안드로이드 뒤로 가기가 조합 흐름에서 꼬이지 않음 / safe area(노치·하단 제스처)
- **[단계 5]** 저사양 폰에서 첫 실행 시간·추천 계산 시간·메모리(카탈로그 풀면 10.7MB)
- **[Play 제출 전]** targetSdk 36(2026-08-31부터 의무)
- **[Play 제출 전]** 릴리스 빌드에 필요 없는 권한이 붙지 않았는지
- **[Play 제출 전]** 앱 번들에 Secret key 없음(`sb_secret_`·`service_role` 검색) — *배포 전 체크리스트 1번과 같은 검사, 앱 번들에도 돌림*
- **[Play 제출 전]** Google Cloud OAuth 동의 화면 "테스트" → "프로덕션", (네이티브 로그인이면) Play 앱 서명 키 SHA-1 등록
- **[Play 제출 전]** Play Console 데이터 보안(이메일·취향·install_id)·콘텐츠 등급·타깃 연령(만 14세와 맞춤)·계정 삭제 웹 주소 — *계정 삭제 웹 안내는 이미 있음(G3)*
- **[Play 제출 전]** Decant 출처 표시(CC BY-NC-SA) — *화면은 승인 K5*
- **[Play 공개 전]** 비공개 테스트 12명 이상 · 14일(넉넉히 15명 초대)

### 묶음 9 배포 전 체크리스트 (사장 할 일 포함)
- [ ] 테스트 계정 삭제(Supabase 대시보드) → **그 뒤** 이메일 가입·로그인 끄기(구글 로그인만), `api_check.sh`로 막힘 확인
- [ ] Render 환경 변수에 `SUPABASE_URL`(=`https://<참조값>.supabase.co`) 추가 (없으면 로그인 API가 모두 401)
- [ ] 실제 이용약관·개인정보 처리 방침 문구 작성 → `application.yml` `scent.consent.*`를 `draft-1`에서 날짜 버전으로 올리기
- [ ] 재측정용 `DEBUG_TOKEN` 새로 만들기(Render + `.env`, 끝나면 다시 지움)
- [ ] 베이스 이미지 digest 고정·의존성 검증 (리뷰 묶음 1 QA-6)
- [ ] CORS(웹 앱 출처 허용 목록), 403 응답 모양(accessDeniedHandler), 한도 분류 정리 (리뷰 묶음 3 QA-6·7·11)
- [ ] X-Forwarded-For 대체(연결 주소) 횟수 경고 로그 + 배포 후 "0인지" 확인 (리뷰 묶음 3 QA-9)
- [ ] 세션 보관 몰림(1만 개 가득 시 비움)을 부하 측정에서 다시 보기 (리뷰 묶음 3 QA-8)

### 단계 5(화면) 할 일
- 사용 기록: 400을 받은 묶음은 버리고(다시 보내도 같음), 413이면 반으로 나눠 보냄(리뷰 묶음 6 QA-8·9). props는 얕고 짧게.
- 앱에 넣은 카탈로그(`app/assets/catalog`)가 옛 2026.10.31(엔진 6) → 배포한 버전으로 바꿔 넣기.
- 서버가 깨어나는 약 34초(실측 33.6초) 동안 "연결 중" 안내, 실패하면 다시 시도. 추천·상세는 서버 없이 바로 뜬다.
- 웹에서 서버를 부르려면 CORS(위 체크리스트)가 먼저 필요.

