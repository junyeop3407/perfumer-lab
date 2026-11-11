/**
 * 주소의 바이트 읽기. 폰에서 앱 안 자산 주소는 릴리스 빌드면 file://, 개발 서버(npx expo start)면 http://라
 * 둘 다 읽을 수 있어야 한다(리뷰 5-1 QA 중간-4). 파일 읽기는 밖에서 넣어 준다(expo-file-system).
 */
export async function readUriBytes(
  uri: string,
  deps: { fetch: typeof fetch; fileBytes: (uri: string) => Promise<Uint8Array> },
): Promise<Uint8Array> {
  if (/^https?:\/\//i.test(uri)) {
    const res = await deps.fetch(uri);
    if (!res.ok) throw new Error(`앱 카탈로그 파일을 읽지 못했습니다(${res.status})`);
    return new Uint8Array(await res.arrayBuffer());
  }
  return deps.fileBytes(uri);
}
