// 색·글꼴·간격 토큰. 기준: docs/design.md 3절(사장 결정 색) + docs/design/DESIGN.md(글꼴·크기·컴포넌트).
// 화면 코드는 이 토큰 이름만 쓴다(색 값·임의 간격 금지). 다크는 시스템 설정을 따른다.
/** @type {import('tailwindcss').Config} */
const pair = (light, dark) => ({ DEFAULT: light, dark });

module.exports = {
  content: ['./app/**/*.{ts,tsx}', './src/**/*.{ts,tsx}'],
  presets: [require('nativewind/preset')],
  darkMode: 'media',
  theme: {
    extend: {
      colors: {
        bg: pair('#F5F1E8', '#141614'),
        surface: pair('#FBF9F4', '#1D201D'),
        surfaceAlt: pair('#EDE7DA', '#262A26'),
        primary: pair('#1F3D2E', '#8FAE98'),
        primaryHover: pair('#2B5240', '#A3C0AB'),
        accent: pair('#B08D57', '#C9A66B'),
        accentStrong: pair('#866634', '#C9A66B'),
        accentSoft: pair('#E8DCC4', '#3A3226'),
        ink: pair('#1C1C1A', '#EFEDE6'), // text
        inkSub: pair('#62625E', '#A3A39E'), // textSub
        onPrimary: pair('#F5F1E8', '#141614'),
        onAccent: pair('#1C1C1A', '#141614'),
        line: pair('#D9D2C3', '#333833'), // border
        noteTop: pair('#A8B5A0', '#A8B5A0'),
        noteMiddle: pair('#C9A9A0', '#C9A9A0'),
        noteBase: pair('#7A5C48', '#7A5C48'),
        onNoteTop: pair('#1C1C1A', '#141614'),
        onNoteMiddle: pair('#1C1C1A', '#141614'),
        onNoteBase: pair('#F5F1E8', '#EFEDE6'),
      },
      fontFamily: {
        serif: ['NotoSerifKR'],
        sans: ['Pretendard'],
      },
      fontSize: {
        display: ['28px', { lineHeight: '36px', fontWeight: '600' }],
        title: ['22px', { lineHeight: '30px', fontWeight: '600' }],
        headline: ['18px', { lineHeight: '26px', fontWeight: '600' }],
        body: ['16px', { lineHeight: '24px' }],
        label: ['14px', { lineHeight: '20px', fontWeight: '500' }],
        caption: ['12px', { lineHeight: '16px' }],
        layer: ['12px', { lineHeight: '16px', fontWeight: '600', letterSpacing: '0.12em' }],
      },
      borderRadius: { DEFAULT: '4px', chip: '2px' },
      maxWidth: { page: '1120px', text: '640px' },
      width: { nav: '240px', list: '400px' },
      minHeight: { touch: '48px' },
    },
  },
  plugins: [],
};
