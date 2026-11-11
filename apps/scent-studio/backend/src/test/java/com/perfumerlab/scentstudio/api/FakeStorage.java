package com.perfumerlab.scentstudio.api;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

/**
 * 테스트용 가짜 Supabase Storage (이 컴퓨터 안의 작은 HTTP 서버). 실제 Storage에는 아무것도 만들거나 올리지 않는다.
 * GET /storage/v1/object/{bucket}/{path} 에 미리 넣어 둔 응답(상태·본문)을 주고, 받은 요청(주소·apikey·Authorization)을 기록한다.
 */
public final class FakeStorage {
    /** 테스트용 서버 전용 키(실제 키 아님). 응답·로그에 이 글자가 나오면 안 된다 */
    public static final String SECRET = "sb_secret_TESTONLY_kq3Zr8VwXy2Lp9NcT4mB";

    record Reply(int status, byte[] body) {}

    record Seen(String path, String apikey, String authorization) {}

    static final Map<String, Reply> replies = new ConcurrentHashMap<>();
    static final Map<String, Long> delays = new ConcurrentHashMap<>(); // 주소별로 늦게 답하기(ms)
    static final Map<String, String> redirects = new ConcurrentHashMap<>(); // 주소 → 302 Location
    static final java.util.Set<String> stalls = ConcurrentHashMap.newKeySet(); // 헤더와 본문 일부만 보내고 멈춤
    static final List<Seen> seen = new CopyOnWriteArrayList<>();
    private static HttpServer server;

    private FakeStorage() {}

    public static synchronized String url() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", ex -> {
                    String path = ex.getRequestURI().getRawPath();
                    seen.add(new Seen(path, ex.getRequestHeaders().getFirst("apikey"), ex.getRequestHeaders().getFirst("Authorization")));
                    Long delay = delays.get(path);
                    if (delay != null) {
                        try { Thread.sleep(delay); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                    if (redirects.containsKey(path)) {
                        ex.getResponseHeaders().add("Location", redirects.get(path));
                        ex.sendResponseHeaders(302, -1);
                        ex.close();
                        return;
                    }
                    if (stalls.contains(path)) {
                        ex.sendResponseHeaders(200, 1000);
                        ex.getResponseBody().write(new byte[10]);
                        ex.getResponseBody().flush();
                        try { Thread.sleep(120_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        ex.close();
                        return;
                    }
                    Reply r = replies.getOrDefault(path, new Reply(404, "{\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8)));
                    if (r.status() == 0) { // 0 = 연결을 끊음
                        ex.close();
                        return;
                    }
                    ex.sendResponseHeaders(r.status(), r.body().length == 0 ? -1 : r.body().length);
                    ex.getResponseBody().write(r.body());
                    ex.close();
                });
                server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // 늦은 응답 하나가 다른 응답을 막지 않게
                server.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public static void reset() {
        replies.clear();
        seen.clear();
        delays.clear();
        redirects.clear();
        stalls.clear();
    }

    static void put(String bucketPath, int status, byte[] body) {
        replies.put("/storage/v1/object/" + bucketPath, new Reply(status, body));
    }

    /** 아무 주소(예: /storage/v1/bucket/catalog)의 응답 */
    public static void putPath(String path, int status, String body) {
        replies.put(path, new Reply(status, body.getBytes(StandardCharsets.UTF_8)));
    }

    /** 아무 주소의 응답(바이트 그대로, 예: gzip 카탈로그) */
    public static void putPath(String path, int status, byte[] body) {
        replies.put(path, new Reply(status, body));
    }

    /** 이 주소는 헤더와 본문 일부만 보내고 멈춘다(받는 쪽 시간 제한 확인용) */
    public static void stallPath(String path) {
        stalls.add(path);
    }

    /** 받은 요청 중 apikey 헤더 값들(주소, 키). 테스트에서 키를 공개 주소에 보내지 않았는지 확인용 */
    public static List<String[]> seenRequests() {
        return seen.stream().map(s -> new String[]{s.path(), s.apikey()}).toList();
    }

    public static byte[] gzip(String text) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(); GZIPOutputStream g = new GZIPOutputStream(b)) {
            g.write(text.getBytes(StandardCharsets.UTF_8));
            g.finish();
            return b.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
