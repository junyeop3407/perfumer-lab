package com.perfumerlab.scentstudio.me;

import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 약관·방침·만 14세 확인 동의 (db_design.md ⑥). 지금 버전은 서버 설정(사장 결정 2026-11-07).
 * 쓰는 API는 지금 버전 세 개가 다 있어야 받는다 → 없으면 403 CONSENT_REQUIRED. 옛 버전 동의는 치지 않는다.
 */
@Component
public class Consents {

    public static final List<String> TYPES = List.of("terms", "privacy", "age_14");

    private final JdbcClient jdbc;
    private final Map<String, String> current = new LinkedHashMap<>();

    public Consents(JdbcClient jdbc, @Value("${scent.consent.terms}") String terms, @Value("${scent.consent.privacy}") String privacy,
                    @Value("${scent.consent.age-14}") String age14) {
        this.jdbc = jdbc;
        current.put("terms", check(terms));
        current.put("privacy", check(privacy));
        current.put("age_14", check(age14));
    }

    private static String check(String v) {
        if (v == null || !v.matches("[0-9A-Za-z.-]{1,20}")) throw new IllegalStateException("동의 문서 버전은 영문·숫자·.-로 1~20자");
        return v;
    }

    public Map<String, String> current() {
        return current;
    }

    /** 지금 버전에 동의한 문서 종류 */
    public Set<String> agreed(UUID user) {
        return new HashSet<>(jdbc.sql("select doc_type from app.consents where user_id = ?"
                        + " and ((doc_type = 'terms' and doc_version = ?) or (doc_type = 'privacy' and doc_version = ?) or (doc_type = 'age_14' and doc_version = ?))")
                .params(user, current.get("terms"), current.get("privacy"), current.get("age_14"))
                .query(String.class).list());
    }

    public void requireComplete(UUID user) {
        if (agreed(user).size() < TYPES.size()) throw new ApiException(ApiError.CONSENT_REQUIRED);
    }

    /** 이미 같은 버전에 동의했으면 그대로(한 번만 기록) */
    public void record(UUID user, String type, String appVersion) {
        jdbc.sql("insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, ?, ?, ?)"
                        + " on conflict (user_id, doc_type, doc_version) do nothing")
                .params(user, type, current.get(type), appVersion).update();
    }
}
