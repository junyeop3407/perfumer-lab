// 색·글꼴·간격 토큰. 기준: docs/design.md 3절(사장 결정 색) + docs/design/design_rules.md(글꼴·크기·컴포넌트).
// 화면 코드는 이 토큰 이름만 쓴다(색 값·임의 간격 금지). 다크는 시스템 설정을 따른다.
/** @type {import('tailwindcss').Config} */

module.exports = {
  content: ['./app/**/*.{ts,tsx}', './src/**/*.{ts,tsx}'],
  presets: [require('nativewind/preset')],
  darkMode: 'media',
  theme: {
    extend: {
      colors: require('./theme/colors'),
      // 굵기마다 다른 글꼴 이름(src/ui/fontSet.ts). fontWeight는 쓰지 않는다(폰에서 시스템 글꼴로 바뀔 수 있음)
      fontFamily: {
        serif: ['NotoSerifKR'],
        sans: ['Pretendard'],
        'sans-medium': ['Pretendard-Medium'],
        'sans-semibold': ['Pretendard-SemiBold'],
      },
      fontSize: {
        // 굵기는 글꼴 이름으로 고른다(src/ui/Txt.tsx): display·title 세리프 500, headline 600, label 500, layer 600
        display: ['28px', { lineHeight: '36px' }],
        title: ['22px', { lineHeight: '30px' }],
        headline: ['18px', { lineHeight: '26px' }],
        body: ['16px', { lineHeight: '24px' }],
        label: ['14px', { lineHeight: '20px' }],
        caption: ['12px', { lineHeight: '16px' }],
        layer: ['12px', { lineHeight: '16px' }],
      },
      letterSpacing: { layer: '1.44px' }, // 0.12em × 12px
      borderRadius: { DEFAULT: '4px', chip: '2px' },
      maxWidth: { page: '1120px', text: '640px' },
      width: { nav: '240px', list: '400px', panel: '360px' },
      minHeight: { touch: '48px' },
    },
  },
  plugins: [],
};
