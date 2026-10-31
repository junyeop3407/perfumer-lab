// 색·글꼴·간격 토큰은 디자인팀이 docs/DESIGN.md(design.md 3절)를 쓰면서 채운다 (plan.md ★디자인).
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./app/**/*.{ts,tsx}', './src/**/*.{ts,tsx}'],
  presets: [require('nativewind/preset')],
  darkMode: 'media',
  theme: {
    extend: {
      colors: {
        bg: { DEFAULT: '#F5F1E8', dark: '#141614' },
        primary: { DEFAULT: '#1F3D2E', dark: '#8FAE98' },
        ink: { DEFAULT: '#1C1C1A', dark: '#EFEDE6' },
      },
    },
  },
  plugins: [],
};
