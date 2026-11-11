# 레드팀 결과 — scent-studio 서버

## 2026-11-11 (묶음 9, Render 3bd0961과 같은 코드)
- 방법: `red-team` 에이전트가 이 컴퓨터에서 띄운 서버(`RedTeamRig`: 내장 Postgres·가짜 로그인·가짜 Storage·관리 API)만 공격. 외부로 보낸 것 없음, 저장소 파일 수정 없음. 관리자 역할·주소가 없는 앱이라 관리자 계정은 만들지 않음.
- 공격한 주소 26 / 26 (blends 5, favorites 3, feedback 3, me 3, preferences 3, reports, events, catalog 2, health 2, _debug 2, openapi). 못 본 주소 없음. 노출된 키 없음.

| # | 판정 | 위치 | 공격 방법 | 결과 | 고치는 방법 |
|---|---|---|---|---|---|
| 1 | **뚫림**(2번 재현, 중간) | `SecurityConfig` tokenFailed + `LimitFilters.BlockFilter` | 같은 IP에서 틀린 토큰 20번 → 같은 IP의 정상 사용자가 요청 | 정상 토큰 `GET /me`도 429 `Retry-After: 300`. 통신사 공유 IP(CGNAT)·공용 와이파이에서 한 명이 같은 IP 사용자 전원을 5분 막을 수 있음. 만료·로그아웃 토큰으로 재시도하는 정상 앱도 실패로 세어짐 | 막힌 IP에서도 서명이 맞는 토큰·토큰 없는 요청은 통과, 틀린 토큰만 429. 서명은 맞는데 만료·세션 끝남은 실패로 세지 않음 → **사장 확인 중**(묶음 3 동작·테스트 변경) |
| 2 | 의심(인프라) | `ClientIp.resolve` | 서버에 직접 붙어 XFF 맨 오른쪽 값을 바꿔 가며 70번 | 로컬에서는 IP 제한을 피함. 운영은 Render 프록시 뒤라 재현 안 됨 | 2026-11-11 IP 확인 절차로 Render가 오른쪽 첫 값에 실제 IP를 붙이는 것을 확인(`TRUSTED_PROXY_HOPS=1`, NOTES "묶음 9"). Render 웹 서비스는 공개 주소(프록시)로만 닿음 → 할 일 없음 |
| 3 | 의심(재현 불가) | 토큰 클레임 검사 | 만료·틀린 iss/aud·is_anonymous·role·이메일 없는 토큰 | 서명 키가 없어 만들 수 없음. 서명을 두고 클레임만 바꾸면 모두 401 | 서버 테스트(`AuthApiTest`·`KakaoLoginApiTest`)가 이미 같은 키로 서명한 토큰으로 확인 중 → 할 일 없음 |
| 4 | 낮음 | 경로에 `%2f`·`%00` | `GET /catalog/..%2f..%2fetc%2fpasswd` | 막힘(400). 다만 JSON이 아니라 Tomcat 기본 HTML 400(버전은 안 보임) | NOTES에 기록, 단계 5 전에 JSON으로(남은 것) |

### 막힌 것 (2번씩 확인)
- 토큰 위조: alg none, 서명 지우기, sub 바꾸기, role=service_role, HS256(빈 키·"secret"), kid 조작(`../`·SQL·URL), jku·jwk 끼워 넣기, ES384, crit, 0 서명, `?access_token=` → 모두 401.
- 남의 데이터: B가 A의 조합 조회·수정·삭제, A의 시향 기록 삭제, A의 조합 id로 시향 기록 → 404. `;user_id=`·`?userId=`·본문 userId 무시.
- 동의 우회: 동의 안 한 C의 쓰기 6개 → 403, 옛 버전 409, 일부만 동의 403.
- 동시 요청: 50개 동시 쓰기 → 정확히 30 성공(분당 한도). 조합 99개에서 25개 동시 → 1개만 성공, 최종 100. 같은 찜 20개 동시 → 1줄. 합치기 5/분·신고 20/시간·읽기 120/분·events 10·30/분·카탈로그 IP 10/시간 정확.
- IP 바꾸기 헤더: True-Client-IP·CF-Connecting-IP·X-Real-IP·XFF 왼쪽 끼워 넣기 효과 없음. IPv6 /64 묶음, `::ffff:`·0 채운 표기 같은 IP.
- 입력: 깊이 5,000·100,000 JSON 400, 300KB 413, 중복 칸·NaN·큰 숫자·NUL·짝 없는 서로게이트 400, SQL 조각 → UNKNOWN_KEY(파라미터 바인딩). `<script>`는 글자 그대로 저장(앱에서 HTML로 넣지 않으면 됨).
- 카탈로그: `..`·`%252f`·`%00`·URL·keys 파일 이름으로 경로 이탈·SSRF → 400/404.
- 숨은 주소: `/_debug/*` 토큰 있어도 404(꺼짐), `/openapi` 401, actuator·swagger·`/v3/api-docs` 404, CORS 허용 헤더 없음, TRACE 405.
- 계정 삭제: C가 `?userId=A`로 지워도 C만 지워짐. 지운 토큰은 바로·90초 뒤에도 401(catalog·events 포함). 저장 19 + 삭제 1 동시 → 삭제 204 뒤 401. 동시 삭제 10 → 204 3, 나머지 429(시간당 3회).
- 비밀값: 응답·서버 로그에 `sb_secret_`·Bearer·키 없음. `git log -p --all`(커밋 126개)에 `.env`·실제 `sb_secret_`·`SCENT_KEY_SECRET` 값·JWT 없음. `app/src`·웹 번들에도 없음.
