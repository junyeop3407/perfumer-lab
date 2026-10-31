/**
 * 카탈로그 파일(catalog.json.gz) 읽기. 단계 1에서는 테스트 모드의 가짜 카탈로그를 웹에서 읽는 데만 쓴다.
 * 모양 검사·해시 확인·폰 앱에 넣은 파일 읽기는 plan.md 단계 5에서 더한다.
 */

import type { Catalog } from '../engine/types';

export type { Catalog };

function isGzip(bytes: Uint8Array): boolean {
  return bytes.length >= 2 && bytes[0] === 0x1f && bytes[1] === 0x8b;
}

/** gzip이면 풀고(브라우저·Node의 DecompressionStream), 아니면 그대로 JSON으로 읽는다 */
export async function parseCatalogBytes(bytes: Uint8Array): Promise<Catalog> {
  let text: string;
  if (isGzip(bytes)) {
    const stream = new Blob([bytes as BlobPart]).stream().pipeThrough(new DecompressionStream('gzip'));
    text = await new Response(stream).text();
  } else {
    text = new TextDecoder().decode(bytes);
  }
  return JSON.parse(text) as Catalog;
}

export async function fetchCatalog(url: string): Promise<Catalog> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`카탈로그를 읽지 못했어요 (${res.status})`);
  return parseCatalogBytes(new Uint8Array(await res.arrayBuffer()));
}
