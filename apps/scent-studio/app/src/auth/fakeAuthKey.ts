/**
 * 승인 테스트용 로그인 흉내(AppConfig.authMode === 'fake')가 로그인 상태를 담는 저장소 열쇠.
 * 테스트(tests/acceptance/harness)가 같은 열쇠로 "로그인된 상태로 시작"을 만든다.
 * 값 모양: {"userId": "...", "accessToken": "..."}
 */
export const FAKE_AUTH_STORAGE_KEY = 'perfumerlab.fakeAuthSession';
