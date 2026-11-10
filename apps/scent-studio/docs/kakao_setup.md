# 카카오 로그인 준비 (사장 할 일, plan_stage4.md 남은 것 10)

2026-11-10 정리. 키 값은 대화창·저장소·`.env`·Render에 넣지 않는다. **넣는 곳은 Supabase 대시보드 한 곳뿐**이다(서버는 Supabase가 준 로그인 토큰만 확인하므로 카카오 키가 필요 없다).

## 순서
1. **카카오 개발자 가입·앱 만들기** — [developers.kakao.com](https://developers.kakao.com) → 로그인 → 내 애플리케이션 → 애플리케이션 추가하기
   - 앱 이름: `Perfumer-LAB`(카카오 동의 화면에 보임), 회사명: 개인이면 본인 이름, 카테고리: 라이프스타일 등, 앱 아이콘(있으면)
2. **REST API 키 확인** — 앱 → 앱 키(플랫폼 키)의 **REST API 키**. → 7번 Supabase **Client ID** 칸에 넣는다.
3. **Client Secret 만들기** — 앱 → 플랫폼 키 → **REST API 키**(눌러서 편집 화면) → Client Secret: 코드 생성 → 활성화 상태 **사용함**. (2026-11 콘솔 개편 뒤 위치, 사장 확인) → 7번 Supabase **Client Secret** 칸에 넣는다.
4. **카카오 로그인 켜기** — 카카오 로그인 → 활성화 설정 **ON**.
5. **Redirect URI 등록** — 앱 → 플랫폼 키 → **REST API 키** 편집 화면의 **카카오 로그인 Redirect URI**에 정확히 한 줄 (2026-11 콘솔 개편 뒤 위치, 사장 확인):
   `https://<프로젝트 참조값>.supabase.co/auth/v1/callback`
   (참조값은 Supabase 주소의 앞부분. Supabase Kakao 설정 화면에 "Callback URL"로 그대로 보이니 복사해 붙인다. 끝의 `/` 하나라도 다르면 KOE006 오류)
6. **동의 항목** — 카카오 로그인 → 동의항목
   - **닉네임(profile_nickname)·프로필 사진(profile_image): "선택 동의"로 켠다.** 앱은 쓰지 않지만 Supabase가 기본으로 요청해서, 꺼 두면 KOE205 오류가 난다.
   - **카카오계정(이메일)(account_email)**: 일반 앱에서는 켤 수 없다(비즈 앱 전용). **추천: 이메일 없이 간다**(사장 결정 2026-11-08 "이메일 없는 사용자 허용", 서버 `/me`는 email null 처리 예정).
7. **Supabase 설정** — 대시보드 → Authentication → Sign In / Providers → **Kakao** → Enable
   - Client ID = 2번 REST API 키, Client Secret = 3번 코드
   - **Allow users without an email: 켬**
   - Save
8. **직원에게 "다 했어"라고만 알려 주기** — 키 값은 붙이지 않는다.

## 직원이 그다음 할 것(코드)
- 사장 카카오 계정으로 한 번 로그인해서 실제 토큰 1개로 iss·aud·ES256 확인(브라우저에서 로그인 → 토큰 확인 도구, 값 출력 안 함). 이때 로그인 주소의 `scope`에 `account_email`이 섞여 KOE205가 나면 → 아래 "막히면".
- `/me` email 없음·빈 값 → `email: null`, 구글·카카오 계정은 다른 user id(합치지 않음, 남의 데이터 안 보임) 테스트.

## 막히면
- **2026-11-10 실제 로그인 결과:** 첫 시도 `OAuth state has expired`(로그인 창을 열고 오래 둠 → 새 창으로 바로 하면 됨), 둘째 시도 **`invalid_scope: account_email`** → Supabase가 이메일 범위를 늘 요청한다(Allow users without an email을 켜도). **비즈 앱 전환 + 이메일 "선택 동의"가 필요**(아래 KOE205와 같은 원인).
- **KOE205(설정하지 않은 동의 항목: account_email)**: Supabase가 이메일을 요청하는 경우. 비즈 앱 전환(개인 개발자도 본인 인증으로 가능) → 이메일을 "선택 동의"로 켠다. 이메일을 받게 되면 개인정보처리방침에 적는다.
- **KOE006(Redirect URI 불일치)**: 5번 주소를 Supabase 화면의 Callback URL과 글자 그대로 맞춘다.
- **개인정보 참고:** Supabase는 카카오가 준 닉네임·프로필 사진 주소를 계정 정보(auth.users의 사용자 메타데이터)에 저장한다 → 개인정보처리방침에 "카카오 로그인 시 닉네임·프로필 사진(선택)"을 적을지 출시 전 확인(launch_checklist).
- 앱(안드로이드·아이폰)에서 로그인 뒤 앱으로 돌아오는 주소(Supabase Redirect URLs)는 화면 단계(단계 5)에서 정한다. 지금은 필요 없다.

출처: [Supabase Login with Kakao](https://supabase.com/docs/guides/auth/social-login/auth-kakao), [Kakao 로그인 REST API](https://developers.kakao.com/docs/en/kakaologin/rest-api), [Kakao DevTalk KOE205 account_email](https://devtalk.kakao.com/t/koe205-account-email/147207)
