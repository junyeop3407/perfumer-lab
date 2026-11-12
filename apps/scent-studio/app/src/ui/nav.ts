import { router, type Href } from 'expo-router';

import { navTap } from './tapLock';

/** 뒤로: 쌓인 화면이 없으면(웹에서 주소로 바로 연 경우) 홈으로 */
export function goBack() {
  navTap('back', () => {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  });
}

/** 다음 화면 열기(연속 클릭 잠금) */
export function go(href: Href) {
  navTap(String(href), () => router.push(href));
}
