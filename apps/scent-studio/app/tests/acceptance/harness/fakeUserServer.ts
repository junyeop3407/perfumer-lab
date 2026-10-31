import type { BrowserContext, Route } from '@playwright/test';
import { randomUUID } from 'node:crypto';

/**
 * 가짜 사용자 데이터 서버. 브라우저의 요청을 page.route로 가로채 실제 네트워크 없이 대답한다.
 * 경로와 JSON 모양은 docs/design.md 11절 API를 따른다 (4단계 Spring 서버도 같은 모양).
 * (예전 Kotlin 테스트의 FakeUserServer.kt를 옮김)
 *
 * - offline = true  : 인터넷이 끊긴 것처럼 모든 요청이 실패
 * - slowMillis > 0  : 서버가 잠들어 있다 깨는 것처럼 대답이 늦음
 */
export const FAKE_API_BASE = 'https://fake.scent.test/api/v1';
export const TEST_USER_ID = 'user-1';
export const TEST_TOKEN = 'test-token';

type Json = Record<string, unknown>;

const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'access-control-allow-headers': 'Authorization,Content-Type',
};

export class FakeUserServer {
  offline = false;
  slowMillis = 0;

  // 서버에 저장된 것 (테스트가 미리 넣거나 확인한다)
  dislikedNoteKeys: string[] = [];
  preferredFamilies: string[] = [];
  blends: Json[] = [];
  favorites: string[] = [];
  feedback: Json[] = [];
  /** 받은 사용 기록 이름 (도착 순서) */
  receivedEvents: string[] = [];
  accountDeleted = false;
  /** 받은 요청 "METHOD /경로" (도착 순서) */
  requests: string[] = [];

  async install(context: BrowserContext) {
    await context.route(`${FAKE_API_BASE}/**`, (route) => this.handle(route));
  }

  private async handle(route: Route) {
    const request = route.request();
    if (this.offline) return route.abort('internetdisconnected');
    const method = request.method();
    if (method === 'OPTIONS') return route.fulfill({ status: 204, headers: CORS });
    if (this.slowMillis > 0) await new Promise((r) => setTimeout(r, this.slowMillis));
    if (this.offline) return route.abort('internetdisconnected');

    const path = new URL(request.url()).pathname.split('/api/v1')[1] ?? '';
    this.requests.push(`${method} ${path}`);
    const body = request.postData() ? (JSON.parse(request.postData()!) as Json) : undefined;
    const authorized = (await request.headerValue('authorization')) === `Bearer ${TEST_TOKEN}`;

    const respond = (status: number, payload?: unknown) =>
      route.fulfill({
        status,
        headers: CORS,
        contentType: 'application/json; charset=utf-8',
        body: payload === undefined ? '' : JSON.stringify(payload),
      });
    const error = (status: number, code: string) => respond(status, { error: { code, message: code } });

    // 로그인 없이 되는 것
    if (method === 'GET' && path === '/catalog/latest') return error(404, 'NO_NEWER_CATALOG'); // 앱에 넣은 카탈로그를 그대로 쓰게 함
    if (method === 'POST' && path === '/events') {
      for (const e of (body!.events as Json[]) ?? []) this.receivedEvents.push(String(e.name));
      return respond(202);
    }

    if (!authorized) return error(401, 'UNAUTHORIZED');

    if (method === 'GET' && path === '/me') return respond(200, { userId: TEST_USER_ID });
    if (method === 'DELETE' && path === '/me') {
      this.accountDeleted = true;
      this.blends = [];
      this.favorites = [];
      this.feedback = [];
      this.dislikedNoteKeys = [];
      this.preferredFamilies = [];
      return respond(204);
    }
    if (method === 'GET' && path === '/me/preferences') return respond(200, this.preferences());
    if (method === 'PUT' && path === '/me/preferences') {
      this.dislikedNoteKeys = (body!.dislikedNoteKeys as string[]) ?? [];
      this.preferredFamilies = (body!.preferredFamilies as string[]) ?? [];
      return respond(200, this.preferences());
    }
    if (method === 'GET' && path === '/blends') return respond(200, this.blends);
    if (method === 'POST' && path === '/blends') {
      const saved = { ...body, id: randomUUID() };
      this.blends.push(saved);
      return respond(201, saved);
    }
    if (method === 'DELETE' && path.startsWith('/blends/')) {
      const id = decodeURIComponent(path.slice('/blends/'.length));
      this.blends = this.blends.filter((b) => b.id !== id);
      return respond(204);
    }
    if (method === 'GET' && path === '/favorites') return respond(200, this.favorites.map((perfumeKey) => ({ perfumeKey })));
    if (method === 'PUT' && path.startsWith('/favorites/')) {
      const key = decodeURIComponent(path.slice('/favorites/'.length));
      if (!this.favorites.includes(key)) this.favorites.push(key);
      return respond(204);
    }
    if (method === 'DELETE' && path.startsWith('/favorites/')) {
      const key = decodeURIComponent(path.slice('/favorites/'.length));
      this.favorites = this.favorites.filter((k) => k !== key);
      return respond(204);
    }
    if (method === 'GET' && path === '/feedback') return respond(200, this.feedback);
    if (method === 'POST' && path === '/feedback') {
      const saved = { ...body, id: randomUUID() };
      this.feedback.push(saved);
      return respond(201, saved);
    }
    return error(404, 'NOT_FOUND');
  }

  private preferences() {
    return { dislikedNoteKeys: this.dislikedNoteKeys, preferredFamilies: this.preferredFamilies };
  }
}
