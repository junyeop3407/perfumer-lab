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

## ⏸ 카카오 로그인 보류 (2026-11-10 사장 결정)
- **원인:** Supabase 카카오 공급자는 "Allow users without an email"을 켜도 카카오에 `account_email` 범위를 **항상** 요청한다. 우리 카카오 앱은 일반 앱이라 이메일 동의 항목을 켤 수 없어 → 실제 로그인이 `invalid_scope: account_email`(콘솔 설정에 따라 KOE205)로 실패. (1번째 시도의 `OAuth state has expired`는 창을 오래 둔 탓으로 별개)
- **다시 시작할 때 할 일(순서):** ① 사장: 카카오 앱 비즈 앱 전환(개인 개발자 본인 인증) ② 동의 항목 카카오계정(이메일) **"선택 동의"** ③ 로그인 다시 시도(창을 열면 바로) → 실제 토큰 형식 확인(alg·iss·aud·role·session_id·provider, 값 출력 없이, Render `/me` 200·email null 또는 값) ④ 테스트 먼저 → ⑤ 이메일 없는 `/me`·구글·카카오 계정 분리 — **④⑤는 이미 됨(7f63b85, `KakaoLoginApiTest` 10개)**, 실제 토큰 모양이 테스트와 다르면 그때 테스트부터 고침.
- **출시 전:** 개인정보처리방침에 "카카오 로그인 시 이메일(선택)·닉네임·프로필 사진" 추가.
- **기한:** Play 비공개 테스트 시작 전까지 해결(안 되면 1차는 구글 로그인만 — 사장 결정 필요).
- **비즈 앱 말고 다른 방법(조사만, 코드 없음):**
  - *Supabase에서 요청 범위 바꾸기:* 앱이 `scopes`로 범위를 **더할** 수는 있지만 기본값의 `account_email`을 **뺄** 방법은 문서에 없다. 다른 개발자도 범위를 줄여 보냈는데 같은 오류 → 비즈 앱 전환으로 해결한 사례. → 안 됨으로 봄.
  - *카카오 OIDC + `signInWithIdToken({provider: 'kakao'})`:* 카카오 콘솔에서 OpenID Connect 켜기 → 앱이 카카오 로그인을 직접 해서(이메일 없이 `openid profile_nickname`) id_token을 받고 Supabase에 넘겨 세션을 받는 방식. Supabase 문서에 있는 길. 서버는 그대로(여전히 Supabase 토큰). 대신 앱 쪽 카카오 로그인 코드·nonce 처리가 생기고(단계 5), 비즈 앱 없이 되는지는 실제 확인 필요. **비즈 앱 전환이 막히면 1순위 대안.**
  - *서버가 카카오 OAuth를 직접 처리:* Spring이 카카오 로그인을 받고 자체 토큰 발급 → 로그인 체계가 두 개가 되고 세션·삭제·레드팀 범위가 커짐. **추천 안 함.**
  - 출처: [Supabase Login with Kakao](https://supabase.com/docs/guides/auth/social-login/auth-kakao), [Kakao DevTalk KOE205 account_email](https://devtalk.kakao.com/t/koe205-account-email/147207), [Supabase signInWithIdToken](https://supabase.com/docs/reference/kotlin/auth-signinwithidtoken)
- **Supabase Kakao 공급자는 켜 둬도 됨:** 앱에 카카오 버튼이 없고, 누가 주소로 직접 불러도 카카오 단계에서 실패해 계정이 생기지 않는다.
