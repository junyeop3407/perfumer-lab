package com.perfumerlab.scentstudio.api;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 가짜 Supabase 관리 API(묶음 8 계정 삭제). DELETE /auth/v1/admin/users/{id}를 받으면 실제 Supabase처럼
 * 테스트 DB의 auth.users 줄을 지운다(→ 외래 키 규칙으로 app 표 정리, 세션도 사라짐). 응답은 테스트가 바꿀 수 있다.
 */
final class FakeAuthAdmin {

    record Seen(String method, String path, String apikey, String authorization) {}

    /** 다음 응답: 2(지우고 연결 끊기) / 1(200이라고 답하지만 안 지움) / 200(지움) / 404(없음) / 500·401 등(안 지움) / 0(연결 끊기, 안 지움) / -1(늦게 답하기, 안 지움) */
    static volatile int status = 200;
    static final List<Seen> seen = new CopyOnWriteArrayList<>();
    private static final Pattern USER = Pattern.compile("/auth/v1/admin/users/([0-9a-f-]{36})");
    private static HttpServer server;

    private FakeAuthAdmin() {}

    static synchronized String url() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", ex -> {
                    String path = ex.getRequestURI().getRawPath();
                    seen.add(new Seen(ex.getRequestMethod(), path, ex.getRequestHeaders().getFirst("apikey"),
                            ex.getRequestHeaders().getFirst("Authorization")));
                    int s = status;
                    Matcher m = USER.matcher(path);
                    if (s == 0) {
                        ex.close();
                        return;
                    }
                    if (s == -1) {
                        try { Thread.sleep(30_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        ex.close();
                        return;
                    }
                    boolean authorized = FakeStorage.SECRET.equals(ex.getRequestHeaders().getFirst("apikey"));
                    if (s == 200 && (!authorized || !"DELETE".equals(ex.getRequestMethod()) || !m.matches())) s = 401;
                    if (s == 200) TestAuth.admin("delete from auth.users where id = ?", UUID.fromString(m.group(1)));
                    if (s == 2 && m.matches()) { // 지운 뒤 답하기 전에 연결이 끊김(서버는 실패로 봄)
                        TestAuth.admin("delete from auth.users where id = ?", UUID.fromString(m.group(1)));
                        ex.close();
                        return;
                    }
                    if (s == 1) s = 200;
                    // 실패 응답 본문에 키를 되돌려 적어도 서버가 내보내면 안 된다
                    byte[] body = (s == 200 ? "{}" : "{\"msg\":\"failed " + FakeStorage.SECRET + "\"}").getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(s, body.length);
                    ex.getResponseBody().write(body);
                    ex.close();
                });
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    static void reset() {
        status = 200;
        seen.clear();
    }
}
