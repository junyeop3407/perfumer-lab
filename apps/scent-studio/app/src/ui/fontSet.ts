/**
 * 글꼴 이름과 파일 (design_rules.md 2절). 굵기마다 다른 글꼴 이름을 쓴다:
 * 폰에서는 fontWeight로 굵기를 고르면 시스템 글꼴로 바뀌거나 가짜 굵게가 될 수 있어서다.
 * 파일은 app/scripts/build-fonts.py가 만든다.
 */
export const FONT = {
  sans: 'Pretendard',
  sansMedium: 'Pretendard-Medium',
  sansSemiBold: 'Pretendard-SemiBold',
  serif: 'NotoSerifKR',
} as const;

export const FONT_FILES_APP = {
  sans: 'Pretendard-Regular.otf',
  sansMedium: 'Pretendard-Medium.otf',
  sansSemiBold: 'Pretendard-SemiBold.otf',
  serif: 'NotoSerifKR-Medium.ttf',
} as const;

export const FONT_FILES_WEB = {
  sans: 'Pretendard-Regular.woff2',
  sansMedium: 'Pretendard-Medium.woff2',
  sansSemiBold: 'Pretendard-SemiBold.woff2',
  serif: 'NotoSerifKR-Medium.woff2',
} as const;
