const { getDefaultConfig } = require('expo/metro-config');
const { withNativeWind } = require('nativewind/metro');

const config = getDefaultConfig(__dirname);
// 폰 앱에 카탈로그(assets/catalog/catalog.json.gz)를 파일 그대로 넣는다(src/catalog/platform.native.ts)
config.resolver.assetExts.push('gz');

module.exports = withNativeWind(config, { input: './global.css' });
