// 시안 캡처(5-0): node docs/design/capture.mjs [화면-폭-모드 …]  (scent-studio 폴더에서, app/node_modules의 Playwright 사용)
// 결과: docs/design/shots/<화면>-<폭>-<light|dark>[-200].png
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(path.join(here, '../../app/package.json'));
const { chromium } = require('@playwright/test');

const page_url = pathToFileURL(path.join(here, 'mock.html')).href;
const shots = [
  ...['home', 'notes', 'summary', 'recs', 'detail'].flatMap((s) => [[s, 360, 'light'], [s, 360, 'dark']]),
  ['recs', 1280, 'light'], ['recs', 1280, 'dark'], ['summary', 1280, 'light'],
  // 글자 200% 확인은 실제 화면으로(5-8). 정적 시안의 확대 흉내는 레이아웃이 실제와 달라 뺐다
];

// 이름을 주면 그것만: node docs/design/capture.mjs detail-360-light summary-1280-light
const only = process.argv.slice(2);
const browser = await chromium.launch();
for (const [screen, width, scheme, zoom = 1] of shots) {
  if (only.length && !only.includes(`${screen}-${width}-${scheme}`)) continue;
  const ctx = await browser.newContext({ viewport: { width, height: width < 1024 ? 800 : 900 }, colorScheme: scheme, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  await page.goto(`${page_url}#${screen}`);
  if (zoom !== 1) await page.addStyleTag({ content: `html{font-size:${16 * zoom}px} body{zoom:${zoom}}` });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  // 하단 탭(고정)이 맨 아래에 오도록 창 높이를 내용 높이에 맞춘 뒤 찍는다
  const h = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width, height: Math.max(h, width < 1024 ? 800 : 900) });
  const file = path.join(here, 'shots', `${screen}-${width}-${scheme}${zoom !== 1 ? '-200' : ''}.png`);
  await page.screenshot({ path: file, fullPage: true });
  await ctx.close();
  console.log(path.basename(file));
}
await browser.close();
