// 색 토큰 (docs/design.md 3절 사장 결정 + 2026-11-11 compare). tailwind.config.js와 화면 코드(src/ui/tokens.ts)가 같이 읽는다.
// 화면 코드는 클래스 이름(bg-primary 등)을 쓰고, 클래스를 못 쓰는 곳(탭 막대·아이콘 색)만 tokens.ts로 값을 읽는다.
const pair = (light, dark) => ({ DEFAULT: light, dark });

module.exports = {
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
  compare: pair('#8C8275', '#8A8378'), // 어코드 비교의 "이 향수" 막대 (2026-11-11 사장: 골드 대신 중립색)
  noteTop: pair('#A8B5A0', '#A8B5A0'),
  noteMiddle: pair('#C9A9A0', '#C9A9A0'),
  noteBase: pair('#7A5C48', '#7A5C48'),
  onNoteTop: pair('#1C1C1A', '#141614'),
  onNoteMiddle: pair('#1C1C1A', '#141614'),
  onNoteBase: pair('#F5F1E8', '#EFEDE6'),
};
