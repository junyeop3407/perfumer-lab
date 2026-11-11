/**
 * 앱 시작 때 카탈로그 준비 (화면 묶음 5-2부터 부른다).
 * 1) 받아 둔 파일(맞으면) 또는 앱 안 파일을 바로 연다 → 서버 없이 추천이 된다(승인 E2).
 * 2) 폰이면 뒤에서 새 버전을 받아 두고 다음 실행부터 쓴다(한 번 실행에 한 번만). 결과는 onUpdate로만 알린다(실패해도 앱은 그대로).
 */
import Constants from 'expo-constants';

import { AppConfig } from '../config';
import { catalogStarter } from './catalogUpdate';
import { catalogPlatform } from './platform';

export const startCatalog = catalogStarter({
  ...catalogPlatform,
  appVersion: Constants.expoConfig?.version ?? '0.0.0',
  apiBaseUrl: AppConfig.apiBaseUrl,
  fetch: (input, init) => fetch(input, init),
});
