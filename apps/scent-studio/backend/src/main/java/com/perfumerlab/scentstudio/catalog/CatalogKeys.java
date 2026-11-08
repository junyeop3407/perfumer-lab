package com.perfumerlab.scentstudio.catalog;

import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 지금 카탈로그의 key 목록(노트·계열·향수). 서버가 저장할 때 "카탈로그에 있는 key인지" 확인한다(server_design.md 2·5절).
 * 파일 모양: {"version", "notes":[...], "families":[...], "perfumes":[...]} (.json 또는 .json.gz).
 * - 설정 scent.catalog.keys-file이 있으면 그 파일(테스트·내 컴퓨터).
 * - 없으면 처음 쓸 때 비공개 Storage에서 지금 배포(catalog_releases is_current)의 key 목록 파일을 받아 keys_sha256을 확인한 뒤 쓴다.
 *   못 받거나 해시가 다르면 key 확인이 필요한 저장만 503(아무 key나 받아 버리지 않음), 30초 뒤 다시 받아 본다.
 *   켜져 있는 동안에도 10분마다 지금 배포 버전을 확인해 바뀌었으면 새 목록을 받는다.
 */
@Component
public class CatalogKeys {

    private static final Logger log = LoggerFactory.getLogger(CatalogKeys.class);
    static final Duration RETRY = Duration.ofSeconds(30), REFRESH = Duration.ofMinutes(10);

    public enum Kind { NOTE, FAMILY, PERFUME }

    private record Snapshot(String version, Set<String> notes, Set<String> families, Set<String> perfumes) {}

    private final CatalogStore store;
    private final Clock clock;
    private final boolean fromStorage;
    private volatile Snapshot keys;
    private volatile Instant nextCheck = Instant.MIN;
    private final java.util.concurrent.locks.ReentrantLock loading = new java.util.concurrent.locks.ReentrantLock();

    public CatalogKeys(ResourceLoader loader, @Value("${scent.catalog.keys-file:}") String location, CatalogStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
        this.fromStorage = location.isBlank();
        if (fromStorage) {
            log.info("카탈로그 key 목록은 처음 쓸 때 Storage에서 받습니다");
            return;
        }
        try (InputStream raw = loader.getResource(location.strip()).getInputStream()) {
            keys = parse(raw, location.strip().endsWith(".gz"));
            if (keys == null) log.warn("카탈로그 key 목록 모양이 틀려 쓰지 않습니다. key 확인이 필요한 저장은 503");
            else log.info("카탈로그 key 목록 {}: 노트 {}, 계열 {}, 향수 {}", keys.version(), keys.notes().size(), keys.families().size(), keys.perfumes().size());
        } catch (Exception e) {
            log.warn("카탈로그 key 목록을 읽지 못했습니다({}). key 확인이 필요한 저장은 503", e.getClass().getSimpleName());
        }
    }

    /** 읽혀도 모양이 틀리거나 비었으면 null(503). 0개 목록으로 기기 값을 모두 버리지 않게(리뷰 묶음 4 QA-1) */
    private static Snapshot parse(InputStream raw, boolean gz) throws Exception {
        try (InputStream in = gz ? new GZIPInputStream(raw) : raw) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            if (!root.path("notes").isArray() || root.path("notes").isEmpty() || !root.path("families").isArray()
                    || root.path("families").isEmpty() || !root.path("perfumes").isArray()) {
                return null;
            }
            return new Snapshot(root.path("version").asString(""), set(root, "notes"), set(root, "families"), set(root, "perfumes"));
        }
    }

    private static Set<String> set(JsonNode root, String field) {
        Set<String> out = new HashSet<>();
        for (JsonNode n : root.path(field)) if (n.isString()) out.add(n.asString());
        return Set.copyOf(out);
    }

    /**
     * Storage 목록: 10분마다 지금 배포 버전만 DB에 물어 바뀌었으면 새로 받는다(서버가 켜져 있는 동안 새 배포를 따라감, 리뷰 묶음 6 QA-3).
     * 받는 중이면 다른 요청은 기다리지 않고 있던 목록을 쓴다(없으면 503). 실패하면 있던 목록을 계속 쓰고 30초 뒤 다시.
     */
    private Snapshot refresh() {
        Snapshot old = keys;
        if (clock.instant().isBefore(nextCheck) || !loading.tryLock()) return old;
        try {
            if (clock.instant().isBefore(nextCheck)) return keys;
            CatalogStore.Release r = store.current().orElse(null);
            if (r == null) {
                log.warn("지금 카탈로그 배포(catalog_releases)가 없어 key 목록을 받을 수 없습니다");
            } else if (old != null && r.version().equals(old.version())) {
                nextCheck = clock.instant().plus(REFRESH);
                return old;
            } else {
                byte[] file = store.fetch(r.keysStoragePath(), r.keysSha256(), 0);
                Snapshot s = parse(new ByteArrayInputStream(file), true);
                if (s == null) log.warn("Storage의 key 목록 모양이 틀려 쓰지 않습니다");
                else if (!r.version().equals(s.version())) log.warn("key 목록의 버전이 배포 기록과 달라 쓰지 않습니다");
                else {
                    keys = s;
                    nextCheck = clock.instant().plus(REFRESH);
                    log.info("카탈로그 key 목록 {}(Storage): 노트 {}, 계열 {}, 향수 {}", s.version(), s.notes().size(), s.families().size(), s.perfumes().size());
                    return s;
                }
            }
        } catch (ApiException e) {
            // CatalogStore가 이미 이유(상태 번호 등)를 남김
        } catch (Exception e) {
            log.warn("key 목록을 쓰지 못했습니다({})", e.getClass().getSimpleName());
        } finally {
            loading.unlock();
        }
        nextCheck = clock.instant().plus(RETRY);
        return keys;
    }

    /** 목록에 있나. 목록이 없으면 503 */
    public boolean contains(Kind kind, String key) {
        Snapshot s = keys;
        if (fromStorage) s = refresh();
        if (s == null) throw new ApiException(ApiError.UNAVAILABLE);
        return switch (kind) {
            case NOTE -> s.notes().contains(key);
            case FAMILY -> s.families().contains(key);
            case PERFUME -> s.perfumes().contains(key);
        };
    }
}
