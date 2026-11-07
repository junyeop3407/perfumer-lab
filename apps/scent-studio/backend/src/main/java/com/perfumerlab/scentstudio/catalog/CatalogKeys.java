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

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 지금 카탈로그의 key 목록(노트·계열·향수). 서버가 저장할 때 "카탈로그에 있는 key인지" 확인한다(server_design.md 2절).
 * 파일 모양: {"version", "notes":[...], "families":[...], "perfumes":[...]} (.json 또는 .json.gz).
 * 묶음 4에서는 설정의 파일 위치에서 읽고, 묶음 6에서 비공개 Storage에서 읽게 바꾼다.
 * 못 읽으면 서버는 뜨고, key 확인이 필요한 저장만 503(아무 key나 받아 버리지 않음).
 */
@Component
public class CatalogKeys {

    private static final Logger log = LoggerFactory.getLogger(CatalogKeys.class);

    public enum Kind { NOTE, FAMILY, PERFUME }

    private record Snapshot(String version, Set<String> notes, Set<String> families, Set<String> perfumes) {}

    private volatile Snapshot keys;

    public CatalogKeys(ResourceLoader loader, @Value("${scent.catalog.keys-file:}") String location) {
        if (location.isBlank()) {
            log.warn("카탈로그 key 목록 위치가 없어 key 확인이 필요한 저장은 503으로 답합니다");
            return;
        }
        try (InputStream raw = loader.getResource(location.strip()).getInputStream();
             InputStream in = location.strip().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            // 읽혀도 모양이 틀리거나 비었으면 "목록 없음"(503)으로 둔다. 0개 목록으로 기기 값을 모두 버리지 않게(리뷰 묶음 4 QA-1)
            if (!root.path("notes").isArray() || root.path("notes").isEmpty() || !root.path("families").isArray()
                    || root.path("families").isEmpty() || !root.path("perfumes").isArray()) {
                log.warn("카탈로그 key 목록 모양이 틀려 쓰지 않습니다. key 확인이 필요한 저장은 503");
                return;
            }
            keys = new Snapshot(root.path("version").asString(""), set(root, "notes"), set(root, "families"), set(root, "perfumes"));
            log.info("카탈로그 key 목록 {}: 노트 {}, 계열 {}, 향수 {}", keys.version(), keys.notes().size(), keys.families().size(), keys.perfumes().size());
        } catch (Exception e) {
            log.warn("카탈로그 key 목록을 읽지 못했습니다({}). key 확인이 필요한 저장은 503", e.getClass().getSimpleName());
        }
    }

    private static Set<String> set(JsonNode root, String field) {
        Set<String> out = new HashSet<>();
        for (JsonNode n : root.path(field)) if (n.isString()) out.add(n.asString());
        return Set.copyOf(out);
    }

    /** 목록에 있나. 목록이 없으면 503 */
    public boolean contains(Kind kind, String key) {
        Snapshot s = keys;
        if (s == null) throw new ApiException(ApiError.UNAVAILABLE);
        return switch (kind) {
            case NOTE -> s.notes().contains(key);
            case FAMILY -> s.families().contains(key);
            case PERFUME -> s.perfumes().contains(key);
        };
    }
}
