/**
 * 앱을 조립할 때 바깥에서 정해 주는 값. 빌드할 때 EXPO_PUBLIC_* 환경 변수로 넣는다.
 * 실제 앱은 배포 설정값으로, 승인 테스트는 tests/acceptance/build-web.sh가 가짜 서버·가짜 로그인 값으로 빌드한다.
 * (예전 Kotlin 앱의 AppConfig를 옮김)
 */
export type AuthMode = 'supabase' | 'fake';

export const AppConfig = {
  /** 사용자 데이터 서버 주소. 예: https://api.example.com/api/v1 */
  apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  /** 앱에 넣은 카탈로그 파일 이름 (웹에서는 같은 주소 아래 정적 파일, docs/design.md 7절) */
  catalogFile: 'catalog.json.gz',
  /** fake: 승인 테스트용 로그인 흉내("Google로 로그인"을 누르면 바로 로그인) */
  authMode: (process.env.EXPO_PUBLIC_AUTH_MODE === 'fake' ? 'fake' : 'supabase') as AuthMode,
  /** 이 시간이 지나도 서버가 대답하지 않으면 "서버를 깨우는 중이에요"를 보여 준다 */
  slowServerHintMillis: Number(process.env.EXPO_PUBLIC_SLOW_SERVER_HINT_MS ?? 3000),
  /** 사용 기록을 서버로 보내는 간격 */
  eventFlushIntervalMillis: Number(process.env.EXPO_PUBLIC_EVENT_FLUSH_MS ?? 30000),
} as const;
