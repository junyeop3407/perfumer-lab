/**
 * 폰(안드로이드·아이폰)에서 카탈로그 파일 다루기 (웹은 platform.ts).
 * - 앱 안 파일: 빌드할 때 넣은 assets/catalog/catalog.json.gz (prepare-catalog.mjs가 실제/샘플 중 고른 것).
 * - 받아 두기: 앱 문서 폴더 catalog/downloaded.json.gz + downloaded.meta.json. 기록(meta)을 마지막에 써서,
 *   쓰는 도중 멈추면 기록이 없거나 해시가 달라 다음 실행에 버리고 앱 안 파일을 쓴다.
 * - 네트워크: 와이파이에서만 새 버전을 받는다(design.md 10절).
 */
import { Asset } from 'expo-asset';
import { Directory, File, Paths } from 'expo-file-system';
import * as Network from 'expo-network';

import type { CatalogCache, CatalogMeta, FailureNote, NetworkKind } from './catalogUpdate';
import { readUriBytes } from './uriBytes';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const BUNDLED = require('../../assets/catalog/catalog.json.gz');

const dir = () => new Directory(Paths.document, 'catalog');
const dataFile = () => new File(dir(), 'downloaded.json.gz');
const metaFile = () => new File(dir(), 'downloaded.meta.json');
const failFile = () => new File(dir(), 'failed.json');

const cache: CatalogCache = {
  async read() {
    const data = dataFile();
    const meta = metaFile();
    if (!data.exists || !meta.exists) return null;
    return { meta: JSON.parse(await meta.text()) as CatalogMeta, bytes: await data.bytes() }; // 기록이 깨졌으면 던짐 → openCatalog가 지운다
  },
  async save(bytes: Uint8Array, meta: CatalogMeta) {
    dir().create({ intermediates: true, idempotent: true });
    const m = metaFile();
    if (m.exists) m.delete();
    const tmp = new File(dir(), 'downloaded.json.gz.tmp');
    if (tmp.exists) tmp.delete();
    tmp.write(bytes);
    tmp.move(dataFile(), { overwrite: true });
    m.write(JSON.stringify(meta));
  },
  async clear() {
    for (const f of [metaFile(), dataFile()]) if (f.exists) f.delete();
  },
  async readFailure() {
    const f = failFile();
    if (!f.exists) return null;
    try {
      return JSON.parse(await f.text()) as FailureNote;
    } catch {
      return null;
    }
  },
  async saveFailure(note: FailureNote | null) {
    const f = failFile();
    if (note === null) {
      if (f.exists) f.delete();
      return;
    }
    dir().create({ intermediates: true, idempotent: true });
    f.write(JSON.stringify(note));
  },
};

export const catalogPlatform: {
  bundled: () => Promise<Uint8Array>;
  cache: CatalogCache | null;
  network: () => Promise<NetworkKind>;
  wifiOnly: boolean;
} = {
  bundled: async () => {
    const asset = Asset.fromModule(BUNDLED);
    await asset.downloadAsync();
    // 릴리스 빌드는 file://, 개발 서버는 http:// (리뷰 5-1 QA 중간-4)
    return readUriBytes(asset.localUri ?? asset.uri, { fetch: (u, i) => fetch(u, i), fileBytes: (u) => new File(u).bytes() });
  },
  cache,
  network: async () => {
    const s = await Network.getNetworkStateAsync();
    if (s.isConnected === false || s.isInternetReachable === false) return 'offline';
    if (s.type === Network.NetworkStateType.WIFI) return 'wifi';
    if (s.type === Network.NetworkStateType.CELLULAR) return 'cellular';
    return 'unknown';
  },
  wifiOnly: true,
};
