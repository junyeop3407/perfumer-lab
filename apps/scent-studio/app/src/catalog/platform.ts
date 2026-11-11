/**
 * 웹에서 카탈로그 파일 다루기 (폰은 platform.native.ts — Metro가 플랫폼별로 고른다).
 * - 앱 안 파일: 빌드 준비 스크립트가 app/public/catalog.json.gz로 복사한 정적 파일을 같은 주소 아래에서 읽는다.
 * - 받아 두기: 없음(웹은 배포할 때 파일이 바뀌고, 오프라인은 서비스 워커가 맡는다 — design.md 10절).
 */
import { AppConfig } from '../config';
import type { CatalogCache, NetworkKind } from './catalogUpdate';
import { CatalogError } from './loadCatalog';

export const catalogPlatform: {
  bundled: () => Promise<Uint8Array>;
  cache: CatalogCache | null;
  network: () => Promise<NetworkKind>;
  wifiOnly: boolean;
} = {
  bundled: async () => {
    const res = await fetch(AppConfig.catalogFile);
    if (!res.ok) throw new CatalogError(`앱 카탈로그 파일을 읽지 못했습니다(${res.status})`);
    return new Uint8Array(await res.arrayBuffer());
  },
  cache: null,
  network: async () => (typeof navigator !== 'undefined' && navigator.onLine === false ? 'offline' : 'unknown'),
  wifiOnly: false,
};
