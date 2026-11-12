# scent-studio
현재 단계: 5 (화면) — 5-0 디자인·5-1 카탈로그 끝, **5-2 조합 흐름 코드·리뷰 끝(2026-11-12) → 기기 최소 확인(8-3) 사장 대기**. 계획 `docs/plan_stage5.md`(8절 기기 테스트), 기기 준비 `docs/device_setup.md`. 단계 4(서버)는 끝: **Render는 아직 8fb9e0a**. 카카오 로그인 ⏸ 보류(`docs/kakao_setup.md`). **11-25 Plan A/B 결정**(`docs/plan_b_portfolio.md`)
한 줄 설명: 탑·미들·베이스 노트를 골라 조합하면, 어코드 비중이 가장 비슷한 실제 향수를 추천하는 앱(Perfumer-LAB). 웹·안드로이드·아이폰 공통 (Expo/React Native 앱, Spring 서버, 데이터 작업은 Python)
실행 방법: `cd app && npx expo start`(폰 Expo Go QR / `a` 안드로이드 에뮬레이터 / `w` 웹), 처음엔 `CATALOG_SOURCE=real npm run catalog:prepare`. 자세히 `docs/device_setup.md`. 디자인 시안: `docs/design/mock.html`
테스트 실행: `bash tests/run.sh`
리뷰 기준: b30fcb0 (5-2 시작 직전) — 5-3 시작 때 새로 적는다
결과: 파이프라인 156/156 · Vitest 159/159 · 서버·DB(JUnit + 내장 Postgres) 657/657 통과 + 레드팀 도구(`RedTeamRig`) 1 건너뜀 (2026-11-12, `bash tests/run.sh`). **웹 승인 11/41**(A1~A10 + 1, `cd app && npx playwright test`). 승인 목록 65개(A~I 41 + J·K 15 + L 기기 9), 목표 웹 54/56 + 카카오 보류 2, 기기 L1~L9는 기기 4종 표로
사용자에게 남은 질문: ① 궁합 노트 3개 미만(목록 노트 325개 중 108개, 0개 67개) — A7 "3개"를 어떻게 채울지 ② 같은 한글 이름(우드 Oud·Woods, 배, 수련, 감초, White oud→화이트 우드) 표기 (둘 다 `docs/review.md` "5-2"). (5-9 전까지: 개발용 빌드 방식·아이폰 서명)
다음 할 일: **사장 기기 최소 확인(8-3, `docs/device_setup.md`)** 결과 받기 → 깨지면 5-2 안에서 고침 → OK면 **5-3 추천·상세·찾기**(B1~B6, F1·F2, H5, 등급 문구 질문). 다른 컴퓨터에서는 비공개 저장소 `pipeline-data-out/private_docs/`를 `pipeline/data/out/private_docs/`에 복사하고, 실제 카탈로그는 `cd backend && ./gradlew dbCatalogFetch -Papply=true`로 받는다

## ★ 다음 세션 시작 (2026-11-12 — 5-2 코드·리뷰 끝, 기기 최소 확인 사장 대기)
1. **오늘(2026-11-12) 한 것:**
   - 0번 환경 확인 ✓(backend/.env 11칸·DEBUG_TOKEN 빈칸 정상, TEST_USER 앞 공백 없음, SCENT_KEY_SECRET, 실제 카탈로그·받기 기록).
   - **기기 준비 안내** `docs/device_setup.md`(폰 QR·`--tunnel`, Windows 에뮬레이터 2대·가상화, 아이폰 무료 Apple ID 빌드·99달러 필요한 때, **L9 측정 방법**).
   - **5-2 조합 흐름**: 홈 → 계열 → 노트 고르기(층 탭·검색·강도·노트 카드 시트/PC 옆 패널·아래 고정 막대) → 조합 요약(처방전·어코드 막대·균형 안내). 글꼴 서브셋, 카탈로그 시작 + `[L9]` 로그, 연속 클릭 잠금. 승인 **A1~A10 10/10**. QA 리뷰 20개 중 코드 문제 반영(자세히 `docs/review.md` "5-2", `docs/NOTES.md`).
2. **멈춘 곳 = 기기 최소 확인(8-3).** 이 컴퓨터에 Android Studio가 없어 에뮬레이터 확인은 못 함 → 사장이 `device_setup.md` 1절(폰)·2절(에뮬레이터 ①)대로 확인: D1 열림·D2 safe area·D3 키보드·D4 한글 검색("베르가못"·"샌달우드")·D5 뒤로·D7 다크 + `[L9]` 숫자(참고). 캡처 2장(안드로이드·아이폰).
3. **사장 할 일:** 위 기기 확인 · 질문 2개(궁합 3개 미만, 같은 한글 이름) · 11-14 Fragrantica · 11-18 Parfumo 메일 · 11-25 Plan A/B.
4. **5-3에서 같이:** [추천 보기]·PC 요약 오른쪽 추천 미리보기(5-2에는 아직 없음), 등급 문구 질문.

## (이전) 다음 세션 시작 (2026-11-11 세션 끝 — 5-0 디자인·5-1 카탈로그 끝, 다음 기기 준비 안내 → 5-2)
1. **오늘(2026-11-11) 한 것:**
   - **5-0 디자인 최종 OK**: `docs/design/design_rules.md`(글꼴·크기·간격·컴포넌트), 토큰 `app/tailwind.config.js`, 시안 13장 `docs/design/shots/`. 다크 모드·PC 반영(PC 요약 오른쪽 추천 미리보기 3개, 다크 보조 글씨 대비 최저 4.98:1, "이 향수" 막대 회갈색 `compare`, 누르는 영역 48dp).
   - **글꼴 방식**(`design_rules.md` 2-1): 세리프 Noto Serif KR 500 하나, 본문 Pretendard 400·500·600. 웹은 Pretendard 동적 서브셋 + 세리프 2,350자 서브셋(355KB)을 직접 올림(외부 CDN 안 씀), 앱은 서브셋 약 2.5MB.
   - **이름 변경**: `docs/DESIGN.md` → `docs/design/design_rules.md`(Windows에서 `design.md`와 충돌).
   - **5-1 카탈로그**(커밋 5f71fc1·13b802c):
     - 실제 받기 도구 `./gradlew dbCatalogFetch`(기본 미리 보기, `-Papply=true`만 받음, sha256·크기·버전·샘플 아님 확인) → **실제 카탈로그 받음(2026.11.05)**.
     - 샘플 생성기 `pipeline/sample_catalog.py`(가상 브랜드 20·향수 300, 누가 봐도 가짜 이름, 실제 데이터 안 읽음, 같은 seed면 같은 바이트).
     - **실제/샘플 자리 분리**: `app/assets/catalog/catalog.real.json.gz`(+`catalog.real.release.json`) / `catalog.sample.json.gz` → `CATALOG_SOURCE=real|sample`(`app/scripts/prepare-catalog.mjs`)이 고른 쪽을 `catalog.json.gz`·`app/public/catalog.json.gz`에 복사. 모두 `.gitignore`(`catalogIgnore.test.ts`가 `git check-ignore`로 확인).
     - **실제 이름 검사** `pipeline/real_names.py`: `SCENT_KEY_SECRET` HMAC 해시 목록(`pipeline/data/out/real_name_hashes.txt`, 커밋 안 함)만, 브랜드·향수 이름 둘 다, 키·목록 없으면 건너뜀, 공개 빌드(`npm run export:web:public`)는 건너뛰면 실패 + 결과물 카탈로그 = 샘플 대조.
     - **앱 카탈로그 읽기** `app/src/catalog/`: 모양·해시 확인(fflate·@noble/hashes, 폰 Hermes 대비), 받아 둔 파일이 깨졌으면 지우고 앱 안 파일. **새 버전 받기**: 서버 `/catalog/latest` → 와이파이에서만 받아 다음 실행부터, 받은 뒤 실패한 파일은 하루 동안 다시 안 받음(설치 id 하루 3회), 시작당 한 번만. 폰 전용 코드(`platform.native.ts`)는 기기 테스트로 확인(5-2에서 화면에 연결된 뒤).
     - QA 리뷰(두 번, 중간 8·낮음 여럿 반영), Gemini(문제 없음 / 큰 파일 한 번에 풀기 → L9 측정 뒤 판단). `docs/review.md` "5-1".
   - **기기 테스트 계획** `plan_stage5.md` 8절: 기기 4종(작은·큰 안드로이드, SE급·노치 아이폰), D1~D11, 묶음 끝 최소 확인(8-3), 5-8 전체·5-9 개발용 빌드, Expo Go 한계(세로 고정·로그인 돌아오기·아이콘 등), Maestro 추천. **승인 L1~L9 추가**(`tests.md` "기기 승인 목록", L9 = 작은 안드로이드 첫 로딩 2초 이내).
   - Plan B 문서: 공개 빌드는 `SCENT_KEY_SECRET`(+해시 목록) 필요 → Actions Secrets 또는 로컬 빌드 후 업로드.
   - 사장 결정: 새 카탈로그 와이파이에서만 받기는 지금 그대로, **출시 전에 다시 본다**(NOTES).
2. **다음 세션 시작점 = 기기 준비 안내 문서 → 5-2 조합 화면.** 안내 문서에서 L9 측정 방법도 정한다.
3. **멈추는 곳(사장 확인):**
   - **5-2 끝**: 기기 최소 확인(8-3) 뒤 캡처·결과 보고 → OK 받고 5-3
   - 단계 5 **각 묶음 끝**(5-3~5-9), 5-5 실제 로그인 확인, 5-7 출처·약관 문구, 5-9 앱 써 보기
   - **L9 기준(첫 로딩 2초) 초과 시**: 대책을 제안하고 멈춤
   - **테스트 계정 삭제 → 이메일 새 가입 막기**는 **단계 5 끝(Plan B) 또는 출시 직전(Plan A)** — 그 전에 멈추고 묻는다(다시 만들지 않음)
4. **사장 할 일:**
   - 안드로이드폰·아이폰에 **Expo Go** 설치(폰과 컴퓨터 같은 와이파이)
   - **Mac에 Xcode 설치** → 처음 실행해 iOS 시뮬레이터 받기
   - **11-14** Fragrantica 추가 메일 · **11-18** Parfumo 추가 메일(답이 없으면) · **11-25** Plan A/B 결정(둘 다 허가면 A)
   - (5-9 전) 개발용 빌드 방식·아이폰 서명(Apple Developer 연 99달러 또는 Mac 무료 서명 7일)
5. **배포 상태:** Render는 아직 **8fb9e0a**(단계 5 코드는 앱 쪽이라 서버 재배포 없음. CORS를 고칠 때 다시 배포).
6. 남은 서버 일(단계 5 안에서): CORS(실제 웹 주소만, `*` 거부 → `.redteam-needed` → 5-9 레드팀), 응답 record required 표시(OpenAPI), 베이스 이미지 digest 고정, Render 로그에 토큰·이메일 없는지(사장 확인), Tomcat HTML 400 → JSON(낮음), 바구니 지도 나누기(남은 것 6). 레드팀 로컬 서버: `RedTeamRig`.

## (이전) 2026-11-11 세션 중간 — 5-0 디자인 최종 OK, 5-1 시작 전
1. **오늘(2026-11-11) 한 것:**
   - **묶음 9 서버 마무리:** OpenAPI 정리(`docs/openapi.json`, 운영은 문서 주소 401) → Render 3bd0961 배포·확인 → **전체 API 10분 재측정: RSS 최대 208MB · 컨테이너 216MB / 기준 420MB**, 응답 시간(깨어 있을 때 단건) 중앙값 214~299ms · p95 307~465ms → IP 확인(`TRUSTED_PROXY_HOPS=1` 맞음, X-Forwarded-For 두 줄도 Render가 합쳐 실제 IP 맨 오른쪽).
   - **레드팀**(26/26 주소, `docs/redteam.md`): 뚫림 1 = 틀린 토큰 20번이면 그 IP를 통째로 막아 같은 IP 정상 사용자도 429 → 사장 결정 "틀린 토큰만 막기"로 고침(맞는 토큰·토큰 없는 요청 통과, 만료·로그아웃 토큰은 세지 않고 IP 분당 60, 승인 테스트 4줄 사장 허락) → **8fb9e0a Render 배포·재공격 막힘** → 측정용 환경 변수 4개 삭제(사장)·`.env` DEBUG_TOKEN 비움 → `.redteam-needed` 지움. **배포 전 체크리스트 12개** ✓(해당 없음 3, 카카오 보류).
   - **Plan B 문서**(`docs/plan_b_portfolio.md`): 공개 저장소 데이터 확인 → Fragrantica 향수별 수치 2곳 지움(①), **Parfumo 향수별 데이터 P1~P12 정리**(대량 표는 비공개, 검증 결과 숫자는 남김, `copy_suspects.csv`·고정본 json은 공개에서 뺌). 원본은 비공개 저장소 `pipeline-data-out/private_docs/`(24개), 생성 스크립트도 로컬 `pipeline/data/out/private_docs/`(커밋 안 함)에 씀. Plan B 확정 시 1-5 `git filter-repo` 걸러낸 사본(실행 전 사장 확인, 기존 저장소 강제 푸시 없음).
   - **일정 변경**: Plan A = Fragrantica **와** Parfumo 둘 다 허가. **11-25 결정**. keepalive main 반영은 11-25에 Plan A일 때.
   - **단계 5 계획** `docs/plan_stage5.md` 사장 OK: J1·J2(카카오) 보류 → 목표 54/56 + 보류 2(승인 테스트 수정 허락, 5-5에서), 안드로이드 빌드 방식 11-25 뒤(그 전엔 웹으로 확인), 샘플 300개·20브랜드 가짜 이름, 실제 이름 검사는 해시·비공개일 때만, CORS 실제 주소만.
   - **5-0 디자인 최종 OK**: `docs/design/design_rules.md`(글꼴·크기·간격·컴포넌트), 토큰 `app/tailwind.config.js`, 시안 13장 `docs/design/shots/`. 강조는 "조합 처방전" 하나. 사장 수정 반영: "이 향수" 막대 골드 → 회갈색 `compare`(라이트 3.1:1·다크 3.9:1, 골드는 "매우 비슷" 배지만), 막대마다 "내 조합/이 향수" 글자+숫자, 누르는 영역 48dp(칩 보이는 36), "2 / 3단계" → "계열: …", 고른 칩 테두리 뺌, 다크 보조 글씨 대비 확인(최저 4.98:1, 그대로), PC 요약 오른쪽 추천 미리보기 3개.
   - **글꼴 방식**(`design_rules.md` 2-1): 세리프 Noto Serif KR **500 하나**, 본문 Pretendard 400·500·600. 웹은 Pretendard 공식 동적 서브셋 + 세리프 2,350자 서브셋 woff2(355KB)를 직접 올림(외부 CDN 안 씀), 앱은 서브셋 ttf/otf 약 2.5MB.
   - **이름 변경**: `docs/DESIGN.md`(Windows에서 `design.md`와 충돌) → `docs/design/design_rules.md`.
2. **다음 세션 시작점 = 5-1 카탈로그.** `plan_stage5.md` 2절 묶음 표 순서(5-1 → 5-2 조합 … → 5-9 마무리). 각 묶음은 테스트 먼저 → 구현 → `bash tests/run.sh` → 리뷰 → 사장 보고.
3. **멈추는 곳(사장 확인):** 단계 5 **각 묶음 끝**(캡처·결과 보고 후 OK 받고 다음), 5-1 실제 카탈로그 받기(미리 보기 → OK), 5-5 실제 로그인 확인, 5-7 출처·약관 문구, 5-9 앱 써 보기. **테스트 계정 삭제 → 이메일 새 가입 막기**는 **단계 5 끝(Plan B) 또는 출시 직전(Plan A)** — 그 전에 멈추고 묻는다(다시 만들지 않음).
4. **사장 일정:** **11-14** Fragrantica 추가 메일 · **11-18** Parfumo 추가 메일(답이 없으면) · **11-25** Plan A/B 결정(둘 다 허가면 A). 11-11 Parfumo 첫 메일 보냄.
5. 남은 서버 일(단계 5 안에서): CORS(실제 웹 주소만, `*` 거부 → `.redteam-needed` → 5-9 레드팀), 응답 record required 표시(OpenAPI), 베이스 이미지 digest 고정, Render 로그에 토큰·이메일 없는지(사장 확인), Tomcat HTML 400 → JSON(낮음), 바구니 지도 나누기(남은 것 6). 레드팀 로컬 서버: `RedTeamRig`.

## (이전) 2026-11-11 세션 중간 기록 — 묶음 9 서버 끝, 데이터 정리
1. **오늘 한 것:** OpenAPI 정리(3bd0961, `docs/openapi.json`) → Render 3bd0961 배포·확인 → 전체 API 10분 재측정 **RSS 최대 208MB / 기준 420MB** → IP 확인(`TRUSTED_PROXY_HOPS=1` 맞음, 두 줄 보내도 실제 IP 맨 오른쪽) → 레드팀(26/26 주소, 뚫림 1: 틀린 토큰 IP 막기가 같은 IP 정상 사용자도 막음) → 사장 결정 "틀린 토큰만 막기"로 고침(8fb9e0a) → Render 배포·재공격 막힘 → 측정용 환경 변수 4개 삭제(사장)·`.env` DEBUG_TOKEN 비움 → `.redteam-needed` 지움 → 배포 전 체크리스트 12개. 자세히는 NOTES "묶음 9", `docs/redteam.md`, `docs/review.md`.
2. **멈춘 곳:** 공개 저장소 데이터 정리 끝(2026-11-11): Fragrantica 수치 2곳(①), Parfumo 향수별 데이터 P1~P12(`plan_b_portfolio.md` 1-4). 원본은 비공개 저장소 `pipeline-data-out/private_docs/`, 생성 스크립트도 그 폴더(로컬 `pipeline/data/out/private_docs/`)에 씀. Plan B 확정 시 1-5 걸러낸 사본(실행 전 사장 확인). **순서(사장 결정 2026-11-11):** 테스트 계정은 단계 5 로그인 확인에 쓰고 **단계 5 끝(Plan B) 또는 출시 직전(Plan A)**에 삭제(다시 만들지 않음) → 바로 뒤 이메일 새 가입 막기(Supabase에 이메일만 막는 설정은 없음 → `before_user_created` 훅 또는 Email 끄기) → `api_check.sh` 확인. keepalive main 반영은 11-25에 Plan A로 정해질 때만
3. **묶음 9 남은 것(plan "묶음 9 배포 전 체크리스트"):** 실제 약관·방침 문구 → 동의 버전 올리기, 베이스 이미지 digest 고정, CORS(웹 출처 목록)·403 모양, Redirect URLs 등록, Render 로그에 토큰·이메일 없는지(사장 확인), 바구니 지도 나누기(남은 것 6), keepalive main 반영(이 브랜치에만 고침 — 예약 실행은 main에서만 돈다), Tomcat HTML 400을 JSON으로(레드팀 4번, 낮음).
4. 레드팀을 다시 돌릴 때: `RedTeamRig`(`./gradlew test --tests "*RedTeamRig*" -Dredteam.rig=true -Dredteam.minutes=60`, 이 컴퓨터에서만 서버를 띄움).

## (이전) 2026-11-10 세션 끝 — 묶음 8 완료, 카카오 보류, 묶음 9 전 멈춤
1. **오늘(2026-11-10) 한 것:**
   - 신고 당시 조합(notes) 칸을 V3에 추가(27335e7) → **V3 실제 DB 적용**(dbMigrate·dbCheck 넣기 11칸·api_check 기대대로)
   - **Render 6694790 배포·확인**(health 200, 신고 토큰 없이 401·테스트 계정 204, 찜 204)
   - 0번: 이 컴퓨터 `.env` 11칸 ✓(TEST_USER 두 줄 앞 공백 → 사장이 지움 ✓), `new_computer_setup.md`에 줄 앞 공백 주의·칸 확인 명령(348b2f3)
   - **묶음 8 계정 삭제** `DELETE /me` 코드·리뷰(34cd792) → **실제 삭제 확인**(테스트 계정, 이 컴퓨터에서 서버를 띄워 실제 Supabase: 204·같은 토큰 401·남은 줄 0, 48f0942)
   - 테스트 계정 다시 만듦(사장) → 동의 draft-1 다시 기록·로그인·찜 확인
   - 카카오 로그인 서버 쪽 코드(이메일 없는 `/me` null, 구글·카카오 계정 분리 테스트, 7f63b85) → 실제 로그인이 `invalid_scope: account_email` → **⏸ 카카오 보류(사장 결정)**. 원인·재시작 순서·대안은 `docs/kakao_setup.md` "카카오 로그인 보류". 기한: Play 비공개 테스트 전
   - launch_checklist에 Render 콜드 스타트 대책(첫 요청 실측 43.5초)·단계 5 딥링크(Supabase Redirect URLs) 추가
2. **배포 상태:** **Render에는 묶음 6+7(6694790)까지만** 올라가 있다. **묶음 8(계정 삭제)·카카오 `/me` 코드는 미배포** → 묶음 9 재측정 전 배포. 실제 DB는 V3까지 적용(새 마이그레이션 없음).
3. **다음 세션 시작점 = 묶음 9** (`docs/plan_stage4.md` 묶음 9 행·"묶음 9 배포 전 체크리스트"):
   - OpenAPI 문서 → 최신 코드 Render 배포 → 전체 API로 512MB 재측정(10분 부하, 420MB 기준, DEBUG_TOKEN 새로) → **레드팀**(`.redteam-needed` 있음: 로그인·계정 파일 수정) → 뚫린 것 고침 → IP 확인 절차 → keepalive를 `/health`로 → 배포 전 체크리스트 12개 ✓/✗
4. **다음 세션 멈추는 곳(사장 확인):**
   - 재측정 전 **Render 배포**(사장이 Manual Deploy 버튼, 배포할 커밋을 알려 주고 멈춤)
   - **테스트 계정 삭제** 전(묶음 9 끝, 다시 만들지 않음)
   - **이메일 로그인 끄기** 전(테스트 계정 삭제 **뒤**, `api_check.sh`로 막힘 확인)
5. **다음 세션 사장 할 일:** 묶음 9 시작 지시. (언제든) 카카오 재시작은 비즈 앱 전환부터.

## (이전) 2026-11-09 세션 끝 — 묶음 7 코드 완료, V3 적용 전 멈춤 (2026-11-10에 모두 처리)
1. **멈춘 곳:** 묶음 7 코드·리뷰·보안 수정 완료(4afa8f1, 74276bb). V3 `app.error_reports`는 실제 DB에 **아직 적용 안 함**. 테스트 파이프라인 121 / Vitest 37 / 서버·DB 560 통과.
   - Render는 묶음 6 코드(892a79f)로 Live. 배포 뒤 확인(health·카탈로그 200, 토큰 없이 401, 테스트 계정 찜 204) 통과. 74276bb(요청 제한 수정)는 아직 Render에 없음 → 묶음 7 배포 때 같이.
   - 2026-11-09 한 일: launch_checklist 커밋(bfaa388), 프롬프트 A(03c6176, 승인 목록 56개), 실제 카탈로그 2026.11.05 배포·진짜 Storage 확인(0066b96), Render 배포(892a79f, Gradle 받기 시간 초과로 두 번 실패 → 래퍼 60초·재시도 3번), 묶음 7(4afa8f1), 인코딩 주소로 한도 피하기 수정(74276bb).
2. **다음 할 일(순서대로):**
   1. V3에 **신고 당시 조합(노트·강도)** 칸 추가 — 테스트 먼저. 없어도 되는 칸(검색으로 연 상세에서 신고할 때). 검사는 조합 저장(blends.notes)과 같은 규칙. → 테스트·리뷰(QA + Gemini)
   2. V3 실제 적용: `cd backend && ./gradlew dbMigrate` → `./gradlew dbCheck` → `bash scripts/api_check.sh` (사장 OK 받음: 조합 칸 반영 + 테스트·리뷰 통과 뒤)
   3. 사장에게 Render 배포 요청하고 멈춤(사장이 Manual Deploy → Deploy latest commit) → 배포 뒤 신고 API 확인(테스트 계정, `204`)
   4. 묶음 8(계정 삭제). 카카오 로그인(이메일 없는 `/me`, 구글·카카오 계정 따로)은 `plan_stage4.md` 남은 것 10 계획대로
3. **2026-11-09 사장 결정:**
   - 신고(error_reports)에 신고 당시 조합(노트·강도)도 담는다. 없어도 되는 칸, 검사는 조합 저장과 같은 규칙. V3 적용 전에 반영.
   - V3 실제 DB 적용 OK(조합 칸 반영 + 테스트·리뷰 통과 뒤).
   - 메모리: Render 무료는 Metrics 메모리 그래프가 유료 전용 → 묶음 9 재측정(DEBUG_TOKEN 측정 주소, 10분 부하, 420MB 기준) 때 확인.
   - Render 배포는 사장이 직접 버튼을 누른다(Manual Deploy → Deploy latest commit). 이 컴퓨터에는 Render API 키·배포 주소가 없다.
   - 승인 목록 J1~J8·K1~K7 승인 + G2 보강(계정 삭제 뒤 기기 설정도 지움), 로그아웃은 기기 데이터 남김(J7).
   - 사장 확인 완료: render-server 키(.env·Render), 옛 default 키 Roll, Render `SUPABASE_URL`, Render 환경 변수 목록(필수 5개), Windows `.env` 테스트 계정. 테스트 계정에 동의 draft-1 기록함(묶음 9 계정 삭제 때 같이 지워짐).
3. **2026-11-08 사장 결정** (2026-11-09 프롬프트 A로 spec·requirements·tests·design·plan에 반영, app.json 세로 고정):
   - 1차에 기능 7개 추가: 카카오 로그인, 로그아웃, 약관·개인정보·만 14세 동의 화면, 데이터 출처·라이선스 화면, "추천이 이상해요" 신고 버튼, 첫 실행 안내, 앱 정보. **카카오 로그인·동의 화면은 묶음 8 전에 계획에 넣기.**
   - Play는 바로 공개하지 않고 **비공개 테스트(12명 이상, 14일)**를 거쳐 공개. 이 기간을 주변인 테스트·수정 기간으로 씀. 예전 "초대제 없이 공개(2026-11-06)"를 대체.
   - 화면은 **세로 고정**(app.json `orientation` → `portrait`, PC 넓은 화면 배치는 그대로).
4. 다른 컴퓨터에서 시작할 때는 `docs/new_computer_setup.md` 먼저.

## 2026-11-07 오늘 한 일·결정
- **묶음 1 실측:** 최대 메모리 178MB(기준 420MB), 시작 22.7~24.2초, 쉰 뒤 첫 요청 약 33.6초(33,565 / 33,587 / 33,593ms) → **Render 무료 유지**. 측정 주소는 Render 환경 변수 4개(`MEM_DEBUG`·`IP_DEBUG`·`DEBUG_TOKEN`·`DEBUG_UNTIL`) 삭제 + 재배포로 꺼짐(토큰을 붙여도 404 확인).
- **묶음 2:** V2 실제 DB 적용 완료(app 표 7개). 사용 기록 중복 기준은 (install_id, client_event_id) 묶음.
- **묶음 3:** 로그인 증표 ES256 고정, 실제 DB에서 살아 있는 세션 true / 남의 짝 false 확인, 테스트 231개.
- **묶음 4:** /me·동의·취향·합치기, 테스트 325개(위 "지금 멈춘 곳").
- **테스트 계정:** Supabase 대시보드 Add user로 만듦(Auto Confirm). 비밀번호는 Google 비밀번호 관리자("perfumer-lab 테스트 계정"). `.env`의 `TEST_USER_EMAIL`·`TEST_USER_PASSWORD`(Windows·Mac 둘 다 넣음). 묶음 9 배포 전에 지운다.
- **사장 결정:** 동의 문서 버전은 `application.yml`, 처음 값 `draft-1`(출시 전 날짜 버전으로 올림). `/me`에서 가입일은 뺌(나중에 필요하면 처음 동의 시각으로 대신).
- **사장 지시:** IP는 IPv4·IPv6 글자 형식으로만 해석(✅ 처리), 묶음 8에 계정 삭제 때 세션 보관 바로 지우기(대기).
- 화면 단계 할 일: 서버가 깨어나는 약 34초 동안 "연결 중" 안내, 실패 시 다시 시도.

## (이전) 2026-11-08, 단계 4 묶음 6 코드 완료 — 실제 카탈로그 배포 OK 대기, 묶음 7 시작 전)
- **묶음 6 완료(코드):** `POST /events`(설치 id 헤더, 1~100개, 잘못된 것만 빼고 저장, 중복 무시, 설치 id+IP 분당 10·IP 분당 30), `GET /catalog/latest`, `GET /catalog/{version}`(서버 경유만, 해시·크기 확인, IP 시간당 10 + 설치 id 하루 3), key 목록을 Storage에서(10분마다 새 배포 확인), 배포 도구 `dbCatalogPublish`(기본 미리 보기). 결정·이유는 NOTES "묶음 6", 리뷰는 `review.md` "묶음 6".
- 실제 Storage·DB는 건드리지 않음(미리 보기만 실행). 테스트용 실제 향수 key는 가짜 key로 바꿈.

## (이전) 단계 4 묶음 5 완료 기록 (2026-11-08)
- **묶음 5 완료:** 조합 `GET·POST /blends`, `GET·PUT·DELETE /blends/{id}` · 찜 `GET /favorites`, `PUT·DELETE /favorites/{perfumeKey}` · 시향 기록 `GET·POST /feedback`, `DELETE /feedback/{id}`. 남의 id 404, 한도 100·300·1,000(사용자 단위 잠금, 넘으면 409 `LIMIT_REACHED`), 카탈로그에 없는 key 400, 찜 다시 하면 그대로 성공. 결정·이유는 NOTES "묶음 5", 리뷰는 `review.md` "묶음 5".
- 실제 DB 적용·배포 없음(새 표 없음, V1 표·권한 그대로 사용).

## (이전) 단계 4 묶음 4 완료 기록 (2026-11-07)
- **묶음 4 완료:** `GET /me`(userId·email·동의 상태), `POST /me/consents`, `GET·PUT /me/preferences`, `POST /me/preferences/merge`. 동의 문서 지금 버전은 `application.yml` `scent.consent.*`(draft-1), 가입일 없음(사장 결정). 카탈로그 key 목록은 `CATALOG_KEYS_FILE`(묶음 6에서 Storage). 결정·이유는 NOTES "묶음 4", 리뷰는 `review.md` "묶음 4".
- 사장 지시 반영: IP는 글자로만 해석(InetAddress 안 씀, 이름 모양 값 DNS 없음 테스트), 묶음 8 계획에 "삭제 때 세션 보관 바로 지우기" 추가.

## (이전) 단계 4 묶음 3 완료 기록
- **묶음 3 완료:** 로그인 토큰 확인(ES256 고정, iss·aud·exp·role·is_anonymous·session_id), 세션 확인 30초 보관, 오류 응답 모양 통일, 요청 제한(사용자별 표 + 로그인 없이 IP 분당 60 + 틀린 토큰 1분 20번이면 IP 5분 막음), Render IP 규칙. 코드 `backend/src/main/java/.../{auth,limit}/`, 테스트 `api/{AuthApiTest,RateLimitApiTest,ClientIpTest,Bundle3ReviewFixesTest}`. 자세한 결정은 NOTES "묶음 3", 리뷰는 `review.md` "묶음 3".
- 테스트 계정(사장, `.env` `TEST_USER_EMAIL`·`TEST_USER_PASSWORD`, 두 컴퓨터) 로그인으로 실제 토큰 ES256 확인, `dbServerCheck` 살아 있는 세션 true. **묶음 9 배포 전에 계정 지우기 → 이메일 로그인 끄기, Render에 `SUPABASE_URL` 넣기.**
- /events·카탈로그 제한은 묶음 6(설치 id와 함께).

## (이전) 단계 4 묶음 2 완료 기록
- **V2 실제 DB 적용 완료(사장 OK, 01:33 UTC).** dbCheck·dbServerCheck·api_check 모두 기대대로(NOTES "실제 DB 적용"). 중복 기준은 `(install_id, client_event_id)` 묶음.
- **묶음 1 완료(사장 OK):** RSS 최대 178MB(기준 420MB), 준비 22.7~24.2초(4번으로 충분), 15분 쉰 뒤 첫 요청 약 33.6초, `TRUSTED_PROXY_HOPS=1` 유지. 측정 주소 꺼짐 확인(토큰 붙여도 404). `DEBUG_TOKEN`은 이제 안 쓴다 → `.env`에서 비워도 됨, 묶음 9 재측정 때 새로 만듦.
- **묶음 2(V2):** `backend/src/main/resources/db/migration/V2__session_catalog_events.sql` + `DbV2Test`(50) + `DbV2ReviewFixesTest`(5). 내장 Postgres 통과, 변조 6/6 잡힘, QA 9(중간 2 반영) + Gemini 문제 없음(`docs/review.md` "묶음 2"). 실제 Supabase에 적용됨.
- 결정: 사용 기록 중복 무시는 칸 없는 `on conflict do nothing`(칸을 적으면 scent_server에 읽기 권한이 필요해짐). 승인 테스트 `migrationCreatesSixTables`는 사장 OK로 "V1 표 6개 이름"으로, 표 전체 목록은 `DbTestBase.APP_TABLES` 한 곳.
- 살아 있는 세션이 실제 DB에서 true인지는 로그인 세션이 있어야 확인된다 → 묶음 3 테스트 계정 뒤 `./gradlew dbServerCheck`.

## (이전) 2026-11-06, 단계 4 묶음 1
- **다른 컴퓨터에서 시작할 때는 `docs/new_computer_setup.md` 먼저.** 묶음 계획과 상태는 `docs/plan_stage4.md`(맨 아래 "묶음별 상태").
- **Render 배포 완료:** `https://perfumer-lab.onrender.com`.
  - 배포 설정: 브랜치 `wip/scent-studio-stage4`, 커밋 `687b295`, Singapore, Free(0.1 CPU / 512MB), Auto-Deploy Off, Health Check `/api/v1/health/live`.
  - 환경 변수 7개: `DB_URL`, `DB_SERVER_USER`, `DB_SERVER_PASSWORD`, `DEBUG_TOKEN`, `MEM_DEBUG`, `IP_DEBUG`, `DEBUG_UNTIL=2026-11-07T03:00:00Z`.
  - 첫 배포 시작 22.7초.
- Render GitHub 앱에는 `perfumer-lab` 저장소만 허용했다.
- 묶음 1 코드·리뷰 완료(QA팀 14 + Gemini 5, `docs/review.md` "묶음 1"), 서버·DB 테스트 105/105. **실측은 아직 시작하지 않았다.**
- **Windows·Mac 둘 다 세팅 완료 (2026-11-06):** `.env` 8칸, Java 21, 비공개 데이터. 두 컴퓨터 모두 `tests/run.sh`(119+건너뜀 2 / 37 / 105)·`dbCheck`·`api_check.sh`·health 통과(Mac은 `dbServerCheck`도). 차이는 `docs/new_computer_setup.md` "Mac에서 다른 점"·"Windows에서 확인한 것". 시작 전 `git pull`, 커밋 전 `git pull --rebase`.
- ⚠️ `DEBUG_UNTIL`(2026-11-07 03:00 UTC = 한국 11/7 낮 12시)이 지나면 측정 주소가 저절로 꺼진다(404). 그 뒤에 실측하려면 Render 환경 변수 `DEBUG_UNTIL`을 새 시각(몇 시간 뒤)으로 바꾸고 재배포(Manual Deploy)한다.
- **다음 할 일:** 묶음 1 실측 진행.
  - 잴 것: 10분 부하 중 최대 RSS, 시작 시간 5번, 15분 넘게 쉰 뒤 첫 요청 3번, X-Forwarded-For 모양.
  - 측정 끝나면 측정 주소 끄는 방법을 알려 주고, 결과를 표로 보고하고 멈춤.
  - 명령(`backend`에서): `bash scripts/render_measure.sh snap | load 10 | wake | ip`. `.env`의 `RENDER_URL`·`DEBUG_TOKEN`을 쓴다.

## ★ 지금 멈춘 곳 (2026-11-05)
- **10단계 완료: 등급 0.763 / 0.647 확정, 엔진 v8 동결.** 검증 통과(매우 비슷 4.68 > 비슷 3.93 > 참고 3.38), 두 시드 차이 0.001 / 0.009. 규칙 `docs/engine_grade_v8_rules.md`, 표 `docs/engine_grade_v8.md`, 다시 돌리기 `py pipeline/experiments/grade_v8_run.py`. 원칙과 단계 3~5 로드맵은 `docs/plan.md`. **단계 3은 시작하지 않았다.**
- **9단계 눈 평가 합격 (판정자 사장).** 기본 10개 4.38(기준 3.5), 새 구르망 5개 3.96, 10번 포함 구르망 6개 4.07, 전체 15개 4.24. 엔진은 고치지 않고, 추정치 보정안은 v8.1 후보로 유지한다. 예전 임시 점수 3.7은 사람 점수로 대체됐다. **10단계는 시작하지 않았다.**
  - 관찰(위험 아님): 9번 3.0으로 유일하게 3.5 미만, 아쿠아틱 계열이 약할 가능성. 1위 평균 4.87, 2~5위 3.9~4.2.
  - 파일: 규칙 `docs/engine_eye_v8_rules.md`, 조합 `docs/engine_eye_v8_combos.json`, 집계 `pipeline/experiments/eye_v8_score.py`. 점수 채운 시트 `pipeline/data/out/eye_v8_sheet.md`는 커밋하지 않고 비공개 저장소 `eye_v8/sheet_scored.md`에 백업했다.
- **큰 표본 진단 (측정만, 고치지 않음):** `docs/engine_v8_bucket_diag.md`. 추정 어코드는 모든 분류에서 평평하다(같은 향수 기준 1위 값 -7.1, 유효 어코드 수 5.5 → 9.5). 구르망만의 뚜렷한 손해는 쿼리 474개에서 보이지 않았다. 다만 이 쿼리의 구르망은 스위트 노트가 몰린 조합이 아니어서(분류 비중 약 20%) 블라인드 구르망 조합과 성격이 다르다.
- **8단계 블라인드 채점 완료: 유의차 없음(v8 13 : v7 13, 유효 26, 양측 부호 검정 p=1.000).** 규칙대로 **v8 유지**, "블라인드 비유의"를 위험에 기록했다(`docs/plan_v8.md` 위험). v8이 더 낫다는 근거가 나온 것은 아니다. **9단계는 시작하지 않았다. 사장 지시를 기다린다.**
  - 판정 32개: A 12, B 14, 비슷함 6. 자동 비슷함 8개는 따로다.
  - 분류별 v8 : v7 — 프레시 3:4, 플로럴 5:2, 우디 오리엔탈 4:2, 구르망 1:5. 분류별 표본이 작아 참고용이다.
  - **판정자 변경:** 사장 결정으로 사람 → 채팅 AI(`docs/engine_blind_v8_rules.md` "판정자 변경 (채점 전)", 정답 키를 열기 전 커밋 `c9453c4`). 한계는 위험 목록에 있다.
  - **확신 낮은 판정 4개(13, 16, 19, 26), 모두 빼도 11:11 p=1.000.**
- **블라인드 파일:** 규칙 `docs/engine_blind_v8_rules.md`, 조합 `docs/engine_blind_v8_combos.json`, 채점 도구 `pipeline/experiments/blind_v8_score.py`. 판정 채운 시트 `pipeline/data/out/blind_v8_sheet.md`는 커밋하지 않고 비공개 저장소 `blind_v8/sheet_judged.md`에 백업했다.
- **정답 키는 비공개 저장소 `blind_v8/answer_key.json`에만 있다.** 이 컴퓨터에 파일로 받지 않고 대화에 출력하지 않는다. 채점은 표준 입력으로만 읽었다. `blind_v8_make.py sheet`는 다시 돌리지 않는다.
- **7단계 완료: 엔진 v8 적용.** P1은 `pipeline/estimate.py`, 강도 없는 향수의 추정값에는 `estimatedAccords`·`estimatedNotes` 표시. 앱 계산은 그대로다. v8 카탈로그 파일로 잰 값이 메모리 측정과 같음(W 42.8, 사람 유사 -2.5, `docs/engine_v8_check.md`).
- **v7 비교본:** `pipeline/data/out/catalog_v7_compare.json.gz`(정리 데이터 + 옛 연결, `save_v7_compare.py`). 8단계 블라인드의 v7 쪽으로 쓴다. 커밋 안 함, 비공개 백업에 있음.
- 정리 확정값: **후보 12,469, 제외 키 1,081, 표시 40.** 기준 목록은 `pipeline/manual/{filtered,review}_keys.csv` (`pipeline/experiments/freeze_exclusions.py`). `merge_keys.csv`는 비어 있고(예외 없음), `separate_keys.csv`는 겹침 규칙으로 대체됐지만 남겨 둔다.
- **측정 기준: 쿼리 893 / 남겨 둔 절반 474가 새 기준이다.** ★6 표(882/466)는 쿼리 집합이 달라 참고용이고 직접 비교하지 않는다. 지금 표는 `docs/engine_v8_final.md`, `docs/engine_v8_check.md`. 쿼리 목록은 `pipeline/data/out/queries_v8.json`.
- ★6 결정(2026-11-04): P1 채택, M1 제외, 맹점 공식 4/5 통과. P1-half는 블라인드 미달 시 예비안.

### 미뤄 둔 항목
- **P1 전체 재학습 (10단계 이후 필요하면 검토):** 지금은 개발 절반으로만 학습한다(사장 결정). 남겨 둔 절반을 평가용으로 깨끗하게 두기 위해서다.

### 미뤄 둔 항목 (화면 단계)
- **카탈로그 크기 줄이기:** gzip 압축 후 2,287KB, 풀면 10.7MB (v7 비교본 압축 1,574KB, 이전 기록 1,660KB). 추정치를 넣어 늘었다. 앱 용량과 함께 본다.
- **같은 해 분리 행:** 대표와 출시 연도가 같은 분리 행은 농도 차이일 수 있어 추천에 두 번 나올 수 있다. 같은 브랜드 + 기본 이름을 묶어 보여 준다.
- **승인 목록 H1·H2 글자 잘림:** 하단 탭 글자가 잘리는 기존 문제다(629b522에서도 같은 위치·같은 글자로 실패). 화면 단계에서 고친다.
- **서버 깨어나는 동안 안내 (사장 지시, 2026-11-06):** Render 무료 서버는 자다 깨는 데 30~90초(추정). 그동안 저장·찜을 누르면 "연결 중" 안내, 실패하면 다시 시도. 추천·상세 화면은 서버 없이 바로 뜬다(`plan.md` 단계 5, 승인 E4와 같은 방향).
- **등급 문구 보여 주는 방식 (사장 지시, 2026-11-06):** 자연스러운 조합에서는 Top 10의 절반 가까이가 "매우 비슷"이다(비교 24조합 Top 10 중 47.5%, `docs/engine_grade_v8.md`). 기준값 0.763/0.647은 동결이라 그대로 두고, 화면에서 문구를 어떻게 보여 줄지 정한다(`plan.md` 단계 5).
- **데이터 표기 문제 (사장 지적, 2026-11-05):** ① 향수 이름에 브랜드·연도·농도가 붙어 중복 표기된다(이름+브랜드+연도+농도 형태). ② 번역 안 된 노트 이름이 남아 있다(영어 그대로 나오는 노트들). ③ 같은 노트가 여러 층에 반복되는 행이 있다.
- 저장소는 공개 유지(사장 결정). 정리 목록은 비밀 키를 섞은 HMAC이고, 예전 커밋은 그대로 둔다. **비밀 키 `SCENT_KEY_SECRET`은 작업한 컴퓨터의 사용자 환경 변수에 있다.** 비밀번호 관리자에 옮겼는지는 아직 확인받지 못했다. 다른 컴퓨터에서는 같은 키를 설정해야 실제 카탈로그를 만들 수 있다.

### 이어서 시작할 때
- 위 남은 질문에 답을 받은 뒤 단계 3을 진행한다. 지시 없이 시작하지 않는다.
- 커밋하지 않는 `pipeline/data/out/`은 비공개 저장소 `junyeop3407/perfumer-lab-private`에서 받는다. 채운 시트는 경로가 따로다: `blind_v8/sheet_judged.md` → `blind_v8_sheet.md`, `eye_v8/sheet_scored.md` → `eye_v8_sheet.md`. 다른 컴퓨터에서는 `SCENT_KEY_SECRET`도 같은 값으로 설정해야 한다.
- `freeze_exclusions.py`는 정리 전 카탈로그 `pipeline/data/out/catalog_precleanup.json.gz`를 읽는다. `catalog.json.gz`는 이제 v8이다.
- 보류 목록의 합치기 분리 후 제외 4개는 `pipeline/manual/hold_keys.csv`에서 읽으므로 `freeze_exclusions.py`를 다시 돌려도 남는다. (손으로 다시 넣으라던 예전 메모는 이 수정으로 대체됐다.)
- 옛 실험 스크립트는 v8 카탈로그를 읽으면 경고하고 멈춘다(`catalog_rules.load_old_catalog`). v7 기준 비교는 `catalog_v7_compare.json.gz`를 쓴다.
- 다시 돌리는 법: `py pipeline/experiments/engine_v8_final.py`(메모리 측정), `py pipeline/experiments/engine_v8_check.py`(v8 파일 확인). 이름이 든 표는 `pipeline/data/out/`(커밋 안 함): `final_traces.txt`, `exclusion_changes.csv`, `merged_rep_excluded.csv`, `hold_list_web_check_10.csv`, `hold_list_ambiguous_pass.csv`, `number_not_in_slug.csv`. 다른 컴퓨터에서는 비공개 저장소 `junyeop3407/perfumer-lab-private`에서 받아 이 폴더에 복사한다. 자동으로 맞춰지지 않으니 표가 바뀌면 다시 올린다(마지막 백업 2026-11-05).
- `decant_audit.py`는 기록용이다. 다시 돌리지 않는다.
- **v8 뒤 할 일:** 이름 오류 복원(유명 향수가 카탈로그에서 빠진 상태일 수 있음), 보류 목록 14개(웹 재출시작 확인 후보 10개 + 합치기 분리 후 새로 제외된 4개) 확인, 복구 후 오염 규칙을 통과한 2개 처리.

## 새 세션 시작 (이 순서대로)
1. 브랜치: **`wip/scent-studio-stage4`** (main 아님. 승인 41/41 + 5단계 리뷰 뒤 main에 합침) — `git fetch && git switch wip/scent-studio-stage4 && git pull`
2. 읽기: 이 파일 → `docs/spec.md`(앱 전체) → `docs/plan.md`(4단계 계획·진행 표) → 필요하면 `docs/NOTES.md` 맨 아래(최근 결정과 이유)
3. 새 컴퓨터 준비(한 번): **`docs/new_computer_setup.md`를 따른다** (아래는 요약):
   - `py -m pip install -r pipeline/requirements.txt` (numpy, pytest)
   - `cd app && npm install && npx playwright install chromium`
   - 비밀 키: 환경 변수 `SCENT_KEY_SECRET` (저장소 README "데이터 정리 목록의 비밀 키"). 없으면 실제 카탈로그를 만들 수 없다. 테스트 1개는 키와 원본이 있을 때만 돈다
   - 원본 데이터(커밋 안 함): `py pipeline/fetch.py` → `py pipeline/fetch_decant.py` → `py pipeline/build_catalog.py`
4. 확인: `bash tests/run.sh` → 파이프라인 121 + Vitest 37 + 서버·DB 105 (키와 원본이 없으면 파이프라인 일부 건너뜀) 통과. DB 테스트는 Java 21 필요, 첫 실행 때 Gradle·내장 Postgres를 내려받아 1~2분

## 지금 상태 (2026-11-02)
- **엔진 v7** (사장 승인, 계산은 `docs/engine_how_it_works.md`): 어코드·노트 강도(Decant, 후보의 51.7%), 노트 일치도 × (0.3 + 0.7·√강도 몫 합), 등급은 선호와 무관. 사람 유사 시험 Top 50 38.4% → 44.3% (`docs/engine_similar_votes.md`). 재현 20/20(100개 100·97·99) · 맹점 4/5 · 다양성 92.
- **블라인드 문서** (아래 v6 설명보다 이것이 우선. 조합 12개, 답은 조합 단위): 주 비교 `docs/engine_blind_vs_v6.md` → 답 `docs/engine_blind_vs_v6_answers.txt` / 기준선 `docs/engine_blind.md` → 답 `docs/engine_blind_answers.txt`. 기준선의 5·6·7·9·11·12번이 주 비교와 겹치니 **주 비교 답을 받은 뒤에** 기준선을 보여 준다. 재생성은 `py pipeline/evaluate.py --compare-v6 --rebuild-blind` (답 받기 전에는 하지 않는다).
- **비상업 운영** → 2026-11-06부터 **Fragrantica 허가 전제, 비상업 무료 공개** (D4). Decant는 CC BY-NC-SA 4.0. Fragrantica는 허가 요청 메일 회신 대기.
- **사장에게 알려 둔 한계**: 강도 있는 향수로 쏠림(Top 10의 81%, v6 58%), 등급이 대부분 참고(82%, 기준값 임시 0.87/0.81). 눈으로 평가 때 함께 본다.

### 아래는 v6 때의 기록
- **단계 1·2 끝**: Expo 뼈대(`app/`) + 승인 테스트 41개(`app/tests/acceptance/`, 지금 1/41), TS 엔진(`app/src/engine/`, Python 골든과 일치). Java 엔진·Kotlin 앱은 지웠다.
- **엔진 v6** (사장 결정 순서대로, 이유·수치는 `docs/NOTES.md`):
  - 노트 프로필 = **함께 든 노트의 몫 빼기**(향수 어코드 ≈ 노트 프로필 평균, 음수 없는 최소제곱, numpy). 향수 20개 미만 노트는 계열 평균.
  - 점수 = **어코드 0.35 : 노트 0.55** : 선호 계열 0.1. **강 3.0**(어코드 쪽에만), 약 0.5, 중 1.0.
  - 원본 데이터셋 **복사 오류 제외**: 브랜드가 다른데 노트·평가 수·어코드·조향사가 같은 묶음(+평가 수만 1~5 다른 거의 복사)은 원본만 남기거나 묶음째 뺌 → 333개 제외, `pipeline/data/out/private_docs/copy_suspects.csv`(비공개, 2026-11-11부터).
  - 같은 향수 묶음(group): 이름이 같아도 노트가 절반 넘게 다르면 다른 향수.
  - 등급 기준값은 **아직 0.87 / 0.79 그대로**(사장 결정 대기, 같은 규칙으로 재면 0.92 / 0.87).
- 기준표 `docs/engine_report.md`: 재현 20/20(어려운 버전 20개 20·20, 100개 99·98·97) · 맹점 4/5(시트러스 1위 2) · 다양성 97.
- 블라인드 문서 두 개 (둘 다 조합 12개, 답은 **조합 단위** `블라인드: 1A 2B … 12A`, 일부 조합만 답해도 집계):
  - **주 비교** `docs/engine_blind_vs_v3.md` (지금 엔진 v6 vs 예전 엔진 v3) → 답 파일 `docs/engine_blind_vs_v3_answers.txt`
  - **기준선** `docs/engine_blind.md` (지금 엔진 vs 노트만 보는 방식) → 답 파일 `docs/engine_blind_answers.txt` (+ 눈으로 평가 `점수: 1=4 11=3 …`)
  - ⚠️ 두 문서는 1~6번 조합(기준선 문서의 1·2·3·4·8·10번)이 겹친다. 나란히 보면 A/B가 드러나므로 한 문서씩 따로 답하게 안내한다.
  - 두 문서는 사장 답을 받기 전에 **다시 만들지 않는다**. `py pipeline/evaluate.py`는 블라인드 문서가 이미 있으면 덮어쓰지 않으니 그냥 돌려도 된다(다시 만들 때만 `--rebuild-blind`). 줄별 A/B 고정본: `engine_blind_v6_pairs.json`, `engine_blind_vs_v3_pairs.json`.
  - 예전 v3 문서와 Claude 임시 답안은 `engine_blind_v3.md`, `engine_blind_answers_v3.txt`로 보관(지우지 않음).

## 2026-11-01 세션 기록 (사장이 "이어서"라고 하면 여기부터)
- 사장이 깃허브에서 한 작업(이 브랜치 22커밋)을 받아 확인했다. 브랜치는 그대로 `wip/scent-studio-stage4`.
- ai-workflow 견본의 최신 규칙(d88021a까지)을 반영했다: 웹·앱 동시 제작(React+Capacitor), **스킬 사용 규칙** 표, 보안 규칙, 6단계 레드팀 공격. 이 저장소에만 있던 "안드로이드 앱 전용" 규칙은 뺐고, scent-studio는 Expo 유지라고 CLAUDE.md에 적었다.
- 클라우드 세션에서 확인한 스킬: 있음 — awesome-design, hallmark, emil-design-eng, ponytail, web-design-guidelines, break-ui, junyeop-clean-ppt, code-review. **없음** — superpowers, agent-reach, graphify, frontend-design, webapp-testing, security-guidance, security-audit, **Gemini CLI(5단계 교차 리뷰 필수)**.
  - 사장에게 보기 3개를 드렸고 아직 답 없음: ① 클라우드 환경 Setup script에서 `junyeop3407/claude-setup`의 `install.sh` 실행(추천) ② agent-reach·webapp-testing을 claude.ai 스킬로 올리기 ③ 디자인·화면 단계 전까지 보류. 블라인드 평가에는 필요 없다.
- 이어서 할 일: 아래 1번(주 비교 블라인드 답 받기)부터. 스킬 설치 답은 디자인·화면 단계 전까지 받는다.

## 2026-11-02 세션 기록
- 사장 요청으로 엔진 계산 설명서 `docs/engine_how_it_works.md`를 썼다(예시 숫자: `pipeline/experiments/explain_example.py`). 엔진 동작은 그대로. 블라인드 답은 아직 없다.
- 설명서를 쓰다 찾은 확인 거리: ① 선호 계열을 켜면 점수가 낮아져 등급이 내려갈 수 있음 → v7에서 해결 ② 어코드 "목록이 진한 순서" 가정의 사이트 대조 기록을 찾지 못함 → 맨 위 "남은 확인 사항" 참고.
- 사장이 쓴 Claude 문서 "조향 추천 엔진 설계서 v2"(https://claude.ai/artifact/3FPLdg3ZG1ETHVMoYScVKM)의 방안을 시험판으로 돌렸다. 결과 `docs/engine_v2_trial.md`, 스크립트 `pipeline/experiments/engine_v2_trial.py`. **엔진·카탈로그·승인 테스트·골든·블라인드 문서는 그대로.**
  - 어코드·노트 강도 + "지금 일치도 × 강도 몫" 시험판이 사람 유사 시험 Top 50 38.4% → 44.3% (남겨 둔 절반, 구간 +2.8~+9.1). 기존 기준 통과.
  - 사장이 v2 진행을 결정해 계획 모드 승인 후 엔진 v7로 옮겼다. 위 "지금 상태" 참고. 받은 데이터: `pipeline/data/raw/decant/` (커밋 안 함, `fetch_decant.py`).

## 다음 세션에서 할 일
0. (끝남, 2026-11-04: 이름 든 전체는 `pipeline/data/out/`, docs에는 요약) **보류 목록 50개와 검증 표 20개를 어디에 둘까?** 저장소가 공개라 향수 이름·평가 수를 공개 목록에 싣지 않는 규칙(67a9e6d)과 "docs에 남기기" 요청이 부딪힌다. 추천: 이름이 든 전체는 커밋 안 하는 `pipeline/data/out/`에 두고, docs에는 이름 없는 요약만. 검증 표 20개는 대화로 보여 준다.
1. (끝남) **오염 단어 2단계 분류표**를 만들어 사장에게 먼저 보여 준다(지금 78개는 `pipeline/experiments/engine_v8_trial.py`의 `FOUL`).
   - 확실한 가짜(노트로 있을 수 없는 단어: sewer, nausea, carcass, rotten egg, zarquon 등): 1개라도 있으면 행 제외.
   - 애매한 단어(실제 니치 노트로 쓰이는 것: tomato leaf, dust, chlorine, melting plastic 등): 단독으로는 제외하지 않고, 그 행의 어코드와 노트가 어긋나는지(예: Oud Wood인데 우드 없음) 함께 보고 판단. **판단 규칙은 표와 함께 제안한다.**
   - 주의: zarquon은 이미 `junk_notes.csv`로 빠지는 단어이고, tomato leaf는 지금 78개에 없다. 분류표에서 이 둘의 처리를 밝힌다.
2. (끝남) **보류 목록**: 제외된 행 중 평가 수 상위 50개를 남긴다(나중에 다른 출처로 복원할 후보). 위치는 0번 답대로.
3. (끝남) **검증**: 제외 대상에서 무작위 20개를 뽑아 이름·브랜드·노트 표로 사장에게 보여 준다(사장이 따로 확인). 원본에 **의도적으로 넣은 가짜 행일 가능성**을 `docs/NOTES.md`에 기록한다.
4. (끝남) **정리된 데이터로 재측정**: M1, P1, M1+P1, **P1-half(보정 강도 절반)**. 강도 보유 목표치도 정리된 데이터 기준으로 다시 계산한다(지금 추정 약 55.5%).
   - P1에서 맹점이 3/5로 떨어지는 케이스가 무엇인지 찾는다.
   - M1 적용 후에도 Kaleidoscope가 민트·라벤더·시더우드 1위인 이유를 점수 구성(어코드/노트 기여)으로 분해한다.
5. (끝남, 2026-11-04) 재측정 보고와 v8 계획 승인. 이어서는 `docs/plan_v8.md`.
6. 승인 뒤: v8 구현 → 측정 → 새 조합 20개 이상 블라인드(A/B/비슷함, 비슷함은 집계 제외, 승률 + 이항검정 p값, 프레시·플로럴·우디 오리엔탈·구르망 고르게) → 눈으로 평가 → 등급 확정.
7. 그 뒤: Tversky 계수, TGSC 비중, 노트 상호작용 → `docs/plan.md` 단계 3(DB 구축) → 4(서버) → ★디자인 → 5(화면) → 6. 스킬 설치 답은 디자인 단계 전까지 받는다.

- **사장 몫(재촉하지 않음):** `docs/engine_accord_order_check.md` 20건 사이트 대조는 **보류**, 나중에 사장이 직접 한다. Parfumo는 자동 접속을 403으로 막으므로 우회하지 않는다. Fragrantica 회신도 대기 중이다. 배포 기준은 Fragrantica 허가 전제, 비상업 무료 공개이고, 스토어 공개는 회신을 받은 뒤(`plan.md` 출시 직전 확인 항목).
- **아직 안 본 것:** Eau de Nuit의 '스위트' 태그와 노트가 어긋나는지(사장 근거 글에서 나온 건).
- 기준선 블라인드 `engine_blind.md`는 받지 않았다. v8 뒤 새 블라인드로 대신할지 그때 정한다.

## 사장 결정 기록 (짧게, 자세한 이유는 NOTES.md)
- 2026-11-05 사장 결정: 합치기에 노트 겹침 검사 추가(대표와 0.5 미만이면 합치지 않음). 예외 없음(한 행을 합친 채 두려던 결정은 잘못 보고된 겹침 값 때문이라 철회). 쿼리 893/474를 새 기준으로, ★6 표는 참고용으로. `separate_keys.csv`는 지우지 않는다.
- 2026-11-04 사장 결정 (2차): 오염 1,015개 제외 확정. 복원은 v8 뒤로. 의심 행은 표시만. 검증의 기본은 Decant 대조, 단어 목록은 보조. 애매한 단어 행은 어코드·노트 어긋남 대신 Decant 대조로 판정.
- 2026-11-04 사장 결정:
  - v7을 기준 엔진으로 채택. 다음 블라인드는 새 조합 20개 이상, A/B/비슷함, 승률 + 이항검정 p값, 계열 고르게.
  - 오염 의심 행은 **행 제외가 기본**, 단어는 2단계(확실한 가짜 / 애매한 단어)로 나눠 애매한 단어는 어코드·노트 어긋남과 함께 판단. 평가 수 상위 50개는 보류 목록, 무작위 20개는 사장 검증.
  - 등급은 **(b′) 무작위 분포 기준**으로 간다. 엔진이 바뀌면 다시 계산하고, 확정은 v8 눈으로 평가 뒤.
  - 브랜드 상한은 새로 넣지 않는다(통계상 쏠림과 무관). 수익 기능을 붙이면 비상업 운영 결정을 다시 검토한다.
  - Parfumo 20건 대조는 우회하지 않고 보류, 사장이 직접 확인.
- 2026-10-31 웹 + 안드로이드 + 아이폰, 설계 E안(Expo). 앱 이름 Perfumer-LAB(향수 공방소), 앱 ID com.perfumerlab.scentstudio. 4단계 계획 승인(DB 구축 단계 포함).
- 2026-11-02 엔진 v7로 진행. 어코드 연결표 OK. 비상업 운영. 등급은 선호와 무관.
- 2026-10-31 엔진: 레몬 '강' 문제 → 특징 강조 + 강 2.0(v3) → 어코드 0.35 : 노트 0.55(v4) → 함께 든 노트 몫 빼기 + 복사 오류 제외(v5) → 강 3.0, 어코드 쪽에만(v6). 거의 복사도 제외. 블라인드는 조합 단위 답, 주 비교는 v6 vs v3.
- 블라인드·눈으로 평가에 Claude가 매긴 임시 답(claude-provisional)은 v3 문서용으로 보관만 하고, v6 판정에는 쓰지 않는다.

## 주요 파일
- 앱: `app/` (Expo Router 화면 `app/app/`, 엔진 `app/src/engine/`, 이름표 `app/src/ui/testIds.ts`, 승인 테스트 `app/tests/acceptance/`, 부품 테스트 `app/tests/unit/`)
- 데이터·검증: `pipeline/` (build_catalog, engine_ref, evaluate, make_golden, experiments/, tests/), 보고서 `pipeline/report.md`, 복사 오류 목록 `pipeline/data/out/private_docs/copy_suspects.csv`(비공개, 2026-11-11부터)
- 문서: `docs/requirements.md`(기획) · `docs/design.md`(설계, 엔진 8절) · `docs/spec.md`(명세) · `docs/tests.md`(승인 목록 41) · `docs/plan.md`(4단계 계획) · `docs/NOTES.md`(고친 이유·결정) · `docs/engine_how_it_works.md`(엔진 계산 설명) · `docs/engine_*.md`(엔진 실험·기준표·블라인드)
- 가짜 데이터·골든: `tests/fixtures/`, `tests/golden/recommend_cases.json`
- 포트폴리오용 문제 해결 기록: 저장소 루트 `portfolio/problem-solving.md`
- 복사 오류 목록: 공개 `pipeline/data/out/private_docs/copy_suspects.csv`(비공개, 2026-11-11부터)(향수 이름·평가 수 없음) / 전체는 `pipeline/data/out/copy_suspects_full.csv`(커밋 안 함, `build_catalog.py`가 만듦)
