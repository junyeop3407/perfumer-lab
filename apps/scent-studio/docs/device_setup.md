# 기기 준비 안내 (사장용)

작성 2026-11-12 · 기준: `plan_stage5.md` 8절(기기 테스트), `tests.md` "기기 승인 목록" L1~L9
명령은 모두 저장소의 `apps/scent-studio/app` 폴더에서 친다. 이 문서의 명령에는 비밀값이 없다.

| 무엇 | 언제 필요 | 절 |
|---|---|---|
| 폰(Expo Go)으로 보기 | **5-2부터 묶음 끝마다** | 1 |
| Windows 안드로이드 에뮬레이터 2대 | 5-2부터(작은 폰 ①), 5-8에 둘 다 | 2 |
| 아이폰 실기기 개발용 빌드(무료 Apple ID) | 5-8 끝~5-9 | 3 |
| L9 카탈로그 첫 로딩 시간 재기 | 5-2 첫 확인(참고값), 5-9 판정 | 4 |

---

## 1. 폰에서 보기 (Expo Go)

### 1-1. 처음 한 번
1. 폰에 **Expo Go** 설치: 안드로이드는 Play 스토어, 아이폰은 App Store에서 "Expo Go".
2. Expo Go를 열어 **SDK 버전**을 본다(설정·프로필 화면 아래쪽). 우리 앱은 **SDK 58**이다. 다르면 앱이 열리지 않는다 → Expo Go를 최신으로 업데이트한다. 그래도 다르면 직원에게 알린다(8-7 "SDK 버전").
3. 컴퓨터에서 처음 `npx expo start`를 치면 Windows 방화벽 창이 뜰 수 있다 → **개인 네트워크 허용**을 누른다(공용 네트워크는 체크하지 않는다).

### 1-2. 매번 보는 순서
1. 폰과 컴퓨터를 **같은 와이파이**에 연결한다(폰 데이터·다른 와이파이면 안 됨).
2. 컴퓨터 터미널:
   ```
   cd apps/scent-studio/app
   CATALOG_SOURCE=real npm run catalog:prepare   # 카탈로그 준비. 처음이나 실제/샘플을 바꿀 때만
   npx expo start
   ```
   - 위는 Git Bash 기준. PowerShell이면 첫 줄 대신 `$env:CATALOG_SOURCE="real"; npm run catalog:prepare`.
   - 샘플(가짜 이름) 카탈로그로 보려면 `real` 대신 `sample`.
3. 터미널에 **QR 코드**가 나온다.
   - **안드로이드**: Expo Go 앱 → "Scan QR code" → QR을 찍는다.
   - **아이폰**: 기본 **카메라 앱**으로 QR을 찍고 → 위에 뜨는 "Expo Go에서 열기"를 누른다.
4. 처음 열 때 1~2분 걸린다(앱 코드를 폰으로 보냄). 다음부터는 빠르다.
5. 코드를 고치면 폰 화면이 바로 바뀐다. 이상하면 터미널에서 **`r`**(다시 읽기).
6. 끝낼 때 터미널에서 **Ctrl + C**.

### 1-3. 학교·카페 와이파이에서 연결이 안 될 때 (`--tunnel`)
공용 와이파이는 같은 와이파이 안의 기기끼리 서로 못 보게 막아 두는 경우가 많다. 증상: QR을 찍으면 오래 돌다가 "Could not connect", "timed out".
1. 터미널을 Ctrl + C로 끄고 다시:
   ```
   npx expo start --tunnel
   ```
2. 처음 한 번은 "`@expo/ngrok` 설치할까요?"가 나온다 → **Y**. (무료, 계정 필요 없음)
3. 새 QR을 찍는다. 이때는 폰이 **폰 데이터나 다른 와이파이여도** 열린다(인터넷만 되면 됨).
4. 단점: 처음 열기·코드 반영이 조금 느리다. 집 와이파이에서는 `--tunnel` 없이 쓰는 게 빠르다.
5. 그래도 안 되면: 폰 핫스팟을 켜고 컴퓨터를 그 핫스팟에 연결한 뒤 `npx expo start`(터널 없이).

### 1-4. Expo Go로는 확인이 안 되는 것
세로 고정, 구글 로그인 뒤 앱으로 돌아오기, 앱 아이콘·스플래시, 성능(개발 모드라 느림)은 Expo Go에서 실제 앱과 다르다 → 5-9에 개발용 빌드로 본다(`plan_stage5.md` 8-7).

---

## 2. Windows 안드로이드 에뮬레이터 2대

이 컴퓨터 상태(2026-11-12 확인): Android Studio **아직 없음**, Windows 가상화(하이퍼바이저) **켜져 있음**, 메모리 16GB(에뮬레이터 2대는 하나씩 켜면 충분).

### 2-1. 가상화 확인 (처음 한 번, 5분)
에뮬레이터는 CPU 가상화가 켜져 있어야 빠르다.
1. **작업 관리자**(Ctrl + Shift + Esc) → 성능 → CPU → 오른쪽 아래 **"가상화: 사용"**인지 본다.
   - "사용 안 함"이면 BIOS에서 켜야 한다: 컴퓨터를 다시 켜면서 Del(또는 F2) → **Intel Virtualization Technology(VT-x)** = Enabled → 저장 후 재시작. (메인보드마다 메뉴 이름이 조금 다르다. 모르면 직원에게 메인보드 이름을 알려 준다.)
2. 시작 메뉴에서 **"Windows 기능 켜기/끄기"** 실행 → **"Windows 하이퍼바이저 플랫폼"**에 체크 → 확인 → 재시작.
   - 이 컴퓨터는 Windows 자체 하이퍼바이저가 이미 돌고 있어서, 안드로이드 에뮬레이터가 이 기능(WHPX)을 써야 한다. 체크가 이미 되어 있으면 그대로 둔다.

### 2-2. Android Studio 설치 (처음 한 번, 20~40분)
1. PowerShell에서:
   ```
   winget install --id Google.AndroidStudio -e
   ```
   (또는 developer.android.com/studio 에서 받아 설치)
2. Android Studio를 처음 열면 설치 마법사가 나온다 → **Standard** → 라이선스 모두 Accept → Finish(SDK·에뮬레이터를 받는다, 몇 GB).
3. **환경 변수**(Expo가 에뮬레이터를 찾게): PowerShell에서 아래 두 줄을 친 뒤 **터미널·VS Code를 모두 닫고 다시 연다**.
   ```
   [Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
   [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:LOCALAPPDATA\Android\Sdk\emulator", "User")
   ```
4. 확인: 새 터미널에서 `adb version` → 버전이 나오면 ✓.

### 2-3. 에뮬레이터 ① 작은 폰 (360×800dp, 저사양 흉내)
1. Android Studio 첫 화면 → **More Actions → Virtual Device Manager**(프로젝트가 열려 있으면 오른쪽 "Device Manager").
2. **+ (Create Virtual Device)** → 왼쪽 Phone → 오른쪽 아래 **New Hardware Profile**:
   - Device Name: `Small 360x800`
   - Screen size: `6.0` · Resolution: `720` × `1600`
   - RAM: `2048` MB
   - 나머지 그대로 → Finish → 방금 만든 `Small 360x800` 선택 → Next
3. 시스템 이미지: **API 36(또는 목록 맨 위 최신) "Google Play"** 줄의 다운로드 화살표 → 받은 뒤 선택 → Next
4. AVD Name: `small360` · **Show Advanced Settings**:
   - Graphics: Automatic
   - **Multi-Core CPU: 2** (저사양 흉내, L9 측정용)
   - Boot option: Cold boot
   → Finish
5. ▶(Play)로 켠다. 처음 켜기는 1~3분.
6. **dp 확인**(꼭 한 번): 터미널에서
   ```
   adb shell wm size        # Physical size: 720x1600
   adb shell wm density     # Physical density: 320 이어야 360dp
   ```
   - density가 320이 아니면: `adb shell wm density 320` (에뮬레이터를 껐다 켜도 남는다. 되돌리기는 `adb shell wm density reset`).
   - 계산: 화면 폭 dp = 720 × 160 ÷ 320 = **360**.

### 2-4. 에뮬레이터 ② 큰 폰 (412dp급, 내비 두 방식)
1. Create Virtual Device → Phone → **Medium Phone**(1080×2400, 420dpi = 약 411×914dp) 선택 → Next
2. 같은 최신 Google Play 이미지 → AVD Name: `large412` → Finish (CPU·RAM은 기본값)
3. 켠 뒤 확인: `adb shell wm size` = 1080x2400, `adb shell wm density` = 420.
4. **내비게이션 두 방식**(D2): 에뮬레이터 안 설정 → 시스템 → 제스처(또는 "탐색 모드") → **제스처 탐색** / **3버튼 탐색**을 바꿔 가며 본다.
   - 명령으로: 3버튼 `adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton` · 제스처 `adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural`

### 2-5. 에뮬레이터에서 앱 보기
1. 에뮬레이터를 **먼저 켜 둔다**(Device Manager에서 ▶).
2. 터미널: `npx expo start` → **`a`** 를 누른다. Expo Go가 에뮬레이터에 자동 설치되고 앱이 열린다.
3. 둘 다 켜져 있으면 `shift + a`로 고른다. (메모리 아끼려면 한 번에 하나만 켠다.)

### 2-6. 자주 쓰는 설정 바꾸기 (명령, 에뮬레이터 한 대만 켜져 있을 때)
| 무엇 | 켜기 | 되돌리기 |
|---|---|---|
| 다크 모드(D7) | `adb shell cmd uimode night yes` | `adb shell cmd uimode night no` |
| 글자 크기 200%(D8) | `adb shell settings put system font_scale 2.0` | `adb shell settings put system font_scale 1.0` |
| 비행기 모드(D10) | 에뮬레이터 위에서 아래로 쓸어 내려 비행기 아이콘 | 같은 곳 |
| 느린 네트워크(D10) | 에뮬레이터 옆 막대 **⋯ → Cellular** → Network type: EDGE | Full |
| 앱 데이터 지우기(첫 실행처럼) | `adb shell pm clear host.exp.exponent`(Expo Go) | — |
| 화면 캡처 | 옆 막대 카메라 아이콘(바탕화면에 저장) | — |

---

## 3. 아이폰 실기기에 개발용 빌드 깔기 (무료 Apple ID + Xcode)

**언제**: 5-8 끝~5-9(Expo Go로 안 되는 것 확인, `plan_stage5.md` 8-5·8-7). 그 전에는 아이폰도 Expo Go로 충분하다.
**필요**: **Mac**(Windows로는 아이폰 빌드를 만들 수 없다), Xcode, USB 케이블, 일반 Apple ID(무료).

### 3-1. 처음 한 번 (Mac)
1. App Store에서 **Xcode** 설치(10GB 넘음) → 한 번 열어서 추가 구성요소 설치 → 터미널에서 `xcode-select --install`.
2. Xcode → Settings(⌘,) → **Accounts** → 왼쪽 아래 + → Apple ID → 로그인. 목록에 **"(이름) (Personal Team)"**이 생기면 ✓.
3. 아이폰을 USB로 Mac에 연결 → 아이폰에 "이 컴퓨터를 신뢰하겠습니까?" → **신뢰**.
4. 아이폰 **개발자 모드** 켜기(iOS 16 이상): 설정 → 개인정보 보호 및 보안 → 맨 아래 **개발자 모드** → 켬 → 재시동 → 다시 켜졌을 때 "켜기" 확인.
   - 메뉴가 안 보이면 3-2의 빌드를 한 번 시도한 뒤 다시 보면 나타난다.

### 3-2. 빌드해서 깔기 (Mac 터미널)
```
cd apps/scent-studio/app
CATALOG_SOURCE=real npm run catalog:prepare
npx expo run:ios --device --configuration Release
```
- 연결된 아이폰을 고르라고 나오면 고른다.
- **서명 오류**("Signing requires a development team", "No profiles for …")가 나면:
  1. `open ios/*.xcworkspace` 로 Xcode를 연다.
  2. 왼쪽 프로젝트 → TARGETS 앱 → **Signing & Capabilities** → Team: **(이름) (Personal Team)**.
  3. Bundle Identifier가 이미 쓰이고 있다는 오류면 끝에 `.dev` 등을 붙인다(예 `com.perfumerlab.scentstudio.dev`). **이 변경은 커밋하지 않는다**(`ios/` 폴더는 원래 커밋 안 함).
  4. 터미널에서 같은 명령을 다시.
- `--configuration Release`: 앱 안에 코드가 들어가 **컴퓨터 없이도 열린다**(L9 성능도 이걸로 잰다). 코드를 고치면 다시 빌드해야 한다.
- 처음 열 때 "신뢰하지 않는 개발자" → 아이폰 설정 → 일반 → **VPN 및 기기 관리** → 내 Apple ID → **신뢰**.

### 3-3. 무료 Apple ID의 제한
| 제한 | 우리 앱에 영향 |
|---|---|
| 깐 앱이 **7일 뒤 안 열린다** → 3-2를 다시(재설치) | 테스트 기간에만 쓰므로 괜찮다. 7일마다 다시 깐다 |
| 한 아이폰에 이렇게 깐 앱 **최대 3개**, 새 앱 ID 7일에 10개 | 영향 없음 |
| **USB로 연결한 내 기기에만** 깔 수 있다(다른 사람에게 보내기·TestFlight 불가) | 사장 아이폰만 확인 → 괜찮다 |
| 푸시 알림, Sign in with Apple, 유니버설 링크(Associated Domains), iCloud, 앱 그룹, 인앱 결제 **못 씀** | 지금 계획에 없음. 구글 로그인 돌아오기는 `perfumerlab://` 주소라 **무료로 된다** |
| EAS Build(클라우드)로 아이폰 기기용 빌드 **못 함** | Mac에서 직접 빌드(3-2)로 대신 |

### 3-4. 연 99달러 Apple Developer Program이 **꼭 필요한** 때
- **App Store 출시**(Plan A 출시 때) — 반드시 필요.
- **TestFlight**로 다른 사람(지인·테스터) 아이폰에 앱을 보낼 때.
- **EAS Build**로 아이폰 설치 파일을 클라우드에서 만들 때(Mac 없이 빌드하려면).
- 7일마다 다시 까는 게 번거로울 때(유료는 1년).
- 나중에 푸시 알림·Apple 로그인·유니버설 링크를 넣을 때.

**정리:** 단계 5 기기 확인(5-9까지)은 **무료로 충분**하다. 99달러는 **App Store 출시를 정한 뒤(11-25 Plan A)** 출시 준비 때 결제하면 된다. 결정은 5-9 전(안드로이드 빌드 방식과 같이).

---

## 4. L9 카탈로그 첫 로딩 시간 재기

L9: 작은 안드로이드(360dp급)에서 앱을 처음 실행(받아 둔 카탈로그 없음)했을 때 카탈로그 첫 로딩이 **2초 이내**.

### 4-1. 무엇을 재나
- **시작**: 앱이 카탈로그를 열기 시작한 순간(앱 코드가 뜬 직후, `startCatalog` 부름).
- **끝**: 카탈로그 파일 읽기 → 압축 풀기 → 모양 확인 → 추천 엔진 준비까지 끝나서 **"새 조합 만들기"를 누를 수 있게 된 순간**.
- 앱이 이 시간을 로그 한 줄로 남긴다(5-2에서 넣음):
  `[L9] catalog ready 1234ms (open 1070 · engine 164) origin=bundled` (open = 파일 읽기·풀기·모양 확인, engine = 추천 엔진 준비)
  - `origin=bundled`(앱 안 파일)이어야 "처음 실행" 측정이다. `cache`면 받아 둔 파일이라 다시 잰다.
- 아이콘을 누른 뒤 앱 코드가 뜨기까지(스플래시) 시간은 L9에 넣지 않는다(빌드 방식에 따라 다르고 우리 코드가 아님). 참고로 같이 적는다.

### 4-2. 어디서 재나
| 구분 | 기기·빌드 | 쓰임 |
|---|---|---|
| **판정** | 작은 안드로이드 **릴리스(미리 보기) 빌드**. 실기기(저가 폰)가 있으면 실기기, 없으면 에뮬레이터 ①(CPU 2코어·RAM 2GB) | 5-9 L9 ✓/✗ |
| 참고 | 에뮬레이터 ①의 **Expo Go**(개발 모드라 더 느림) | 5-2부터 매 묶음. 여기서 2초 안이면 릴리스는 더 빠르다. 넘으면 바로 판정하지 않고 릴리스로 다시 잰다 |
| 참고 | 사장 폰(Expo Go) | 5-2 최소 확인 때 같이 |

- 주의: 이 컴퓨터(i5-14600K)의 에뮬레이터는 실제 저가 폰보다 **빠르게** 나올 수 있다. 에뮬레이터에서 1.5초를 넘으면 실기기에서 2초를 넘을 수 있다고 보고 대책을 미리 검토한다.
- 참고값(2026-11-12, 이 컴퓨터 Node, 실제 카탈로그 2.3MB): 풀기·읽기 약 90~140ms · 확인 30~45ms · 엔진 20~30ms = **약 150~210ms**. 폰의 Hermes는 이보다 몇 배~십수 배 느릴 수 있다.

### 4-3. 순서 (에뮬레이터 ①, 5번 재서 가운데 값)
1. 에뮬레이터 ①을 켜고 다른 앱은 끈다.
2. 앱 데이터를 지운다(받아 둔 카탈로그를 없앰):
   - Expo Go: `adb shell pm clear host.exp.exponent`
   - 릴리스 빌드: `adb shell pm clear com.perfumerlab.scentstudio`
3. 로그 보기 창을 하나 연다(PowerShell):
   ```
   adb logcat -c
   adb logcat -s ReactNativeJS | Select-String "L9"
   ```
   (Expo Go일 때는 `npx expo start` 터미널에도 같은 줄이 나온다.)
4. 앱을 연다(Expo Go면 `npx expo start` → `a`, 릴리스면 앱 아이콘).
5. `[L9] catalog ready ____ms origin=bundled` 숫자를 적는다.
6. 앱을 완전히 끄고(최근 앱에서 밀기) 2번부터 다시. **5번** 재서 **가운데 값(중앙값)**을 기록한다.
7. 기록: `docs/NOTES.md` "L9 측정"에 날짜·기기·빌드·5개 숫자·중앙값.

### 4-4. 2초를 넘으면
- 멈추고 사장에게 보고한다(HANDOFF "멈추는 곳"). 그때 낼 대책 후보:
  1. 숨김 노트(약 2,000개)·쓰지 않는 칸을 앱 카탈로그에서 빼 크기 줄이기
  2. 향수 목록을 나눠 읽기(첫 화면은 노트만, 향수는 뒤에서)
  3. 무거운 작업을 화면을 막지 않게 뒤에서(첫 화면은 먼저 보이고 버튼만 잠깐 "준비 중")
  4. 미리 만든 인덱스(빌드 때 계산해 넣기)
