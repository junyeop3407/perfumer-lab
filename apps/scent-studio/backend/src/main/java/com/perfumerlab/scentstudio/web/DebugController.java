package com.perfumerlab.scentstudio.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 묶음 1 실측용 측정 주소 (plan_stage4.md 승인 때 추가 1).
 * MEM_DEBUG / IP_DEBUG가 켜져 있고, DEBUG_TOKEN(32자 이상)과 X-Debug-Token 헤더가 같을 때만 응답한다.
 * 그 밖에는 모두 404(없는 주소와 같게). 실측이 끝나면 Render 환경 변수에서 지워 끈다.
 */
@RestController
@RequestMapping("/api/v1/_debug")
public class DebugController {

    private final boolean memEnabled;
    private final boolean ipEnabled;
    private final byte[] token;
    private volatile long jvmStartToReadyMs = -1;

    public DebugController(@Value("${scent.debug.mem:false}") boolean memEnabled,
                           @Value("${scent.debug.ip:false}") boolean ipEnabled,
                           @Value("${scent.debug.token:}") String token) {
        this.memEnabled = memEnabled;
        this.ipEnabled = ipEnabled;
        this.token = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @EventListener(ApplicationReadyEvent.class)
    void ready() {
        jvmStartToReadyMs = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    boolean allowed(boolean enabled, HttpServletRequest req) {
        if (!enabled || token.length < 32) return false;
        String given = req.getHeader("X-Debug-Token");
        if (given == null) return false;
        return MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8)); // 시간 차로 토큰을 맞혀 보지 못하게
    }

    @GetMapping("/mem")
    public ResponseEntity<Map<String, Object>> mem(HttpServletRequest req) {
        if (!allowed(memEnabled, req)) return ResponseEntity.notFound().build();
        Map<String, Object> m = new LinkedHashMap<>();
        var mx = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mx.getHeapMemoryUsage(), nonHeap = mx.getNonHeapMemoryUsage();
        m.put("jvmStartToReadyMs", jvmStartToReadyMs);
        m.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        m.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        m.put("heapUsedMB", mb(heap.getUsed()));
        m.put("heapCommittedMB", mb(heap.getCommitted()));
        m.put("heapMaxMB", mb(heap.getMax()));
        m.put("nonHeapUsedMB", mb(nonHeap.getUsed()));
        m.put("nonHeapCommittedMB", mb(nonHeap.getCommitted()));
        m.put("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        // 프로세스 전체 메모리 (리눅스만): 지금(VmRSS)과 시작 뒤 최대(VmHWM)
        m.put("rssMB", procStatusMB("VmRSS"));
        m.put("rssPeakMB", procStatusMB("VmHWM"));
        // 컨테이너 전체 (cgroup v2): 한도·지금·최대
        m.put("cgroupLimitMB", cgroupMB("memory.max"));
        m.put("cgroupCurrentMB", cgroupMB("memory.current"));
        m.put("cgroupPeakMB", cgroupMB("memory.peak"));
        m.put("jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith("-XX:") || a.startsWith("-Xss")).toList());
        return ResponseEntity.ok(m);
    }

    @GetMapping("/ip")
    public ResponseEntity<Map<String, Object>> ip(HttpServletRequest req) {
        if (!allowed(ipEnabled, req)) return ResponseEntity.notFound().build();
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        for (String n : Collections.list(req.getHeaderNames())) names.add(n.toLowerCase(Locale.ROOT));
        Collections.sort(names);
        List<String> xff = new ArrayList<>();
        for (String h : Collections.list(req.getHeaders("X-Forwarded-For")))
            for (String part : h.split(",")) if (!part.isBlank()) xff.add(part.strip());
        m.put("headerNames", names);                    // 헤더 이름만 (값은 안 돌려줌)
        m.put("xffHeaderLines", Collections.list(req.getHeaders("X-Forwarded-For")).size());
        m.put("xffEntries", xff.size());
        m.put("xffFromRight", reversed(xff));            // 요청한 사람 자신의 경로만 보인다
        m.put("remoteAddr", req.getRemoteAddr());
        return ResponseEntity.ok(m);
    }

    private static List<String> reversed(List<String> l) {
        List<String> r = new ArrayList<>(l);
        Collections.reverse(r);
        return r;
    }

    private static Long mb(long bytes) {
        return bytes < 0 ? null : bytes / (1024 * 1024);
    }

    private static Long procStatusMB(String key) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith(key + ":")) {
                    String kb = line.substring(key.length() + 1).replace("kB", "").strip();
                    return Long.parseLong(kb) / 1024;
                }
            }
        } catch (Exception ignored) {
            // 리눅스가 아니면 없음
        }
        return null;
    }

    private static Long cgroupMB(String file) {
        try {
            String v = Files.readString(Path.of("/sys/fs/cgroup", file)).strip();
            return "max".equals(v) ? -1L : Long.parseLong(v) / (1024 * 1024);
        } catch (Exception ignored) {
            return null;
        }
    }
}
