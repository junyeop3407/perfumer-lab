/** 폰: 서브셋 otf/ttf (웹은 fontSources.web.ts의 woff2). 이름은 fontSet.ts */
import { FONT } from './fontSet';

/* eslint-disable @typescript-eslint/no-require-imports */
export const FONT_SOURCES = {
  [FONT.sans]: require('../../assets/fonts/Pretendard-Regular.otf'),
  [FONT.sansMedium]: require('../../assets/fonts/Pretendard-Medium.otf'),
  [FONT.sansSemiBold]: require('../../assets/fonts/Pretendard-SemiBold.otf'),
  [FONT.serif]: require('../../assets/fonts/NotoSerifKR-Medium.ttf'),
};
