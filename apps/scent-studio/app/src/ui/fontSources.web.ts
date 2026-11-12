/** 웹: 서브셋 woff2 (우리 서버에 같이 올림, 외부 CDN 안 씀 — design_rules.md 2-1) */
import { FONT } from './fontSet';

/* eslint-disable @typescript-eslint/no-require-imports */
export const FONT_SOURCES = {
  [FONT.sans]: require('../../assets/fonts/Pretendard-Regular.woff2'),
  [FONT.sansMedium]: require('../../assets/fonts/Pretendard-Medium.woff2'),
  [FONT.sansSemiBold]: require('../../assets/fonts/Pretendard-SemiBold.woff2'),
  [FONT.serif]: require('../../assets/fonts/NotoSerifKR-Medium.woff2'),
};
