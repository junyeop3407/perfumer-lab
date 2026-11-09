package com.perfumerlab.scentstudio.library;

import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 보관함(조합·찜·시향 기록) 공통: 1인당 한도 잠금, 글자 검사, id 해석.
 * 한도는 "사용자 단위 잠금을 건 트랜잭션 안에서 세고 넣기"로 지킨다(db_design.md 8절, 보안 규칙 "한 번만 돼야 하는 처리").
 * 먼저 세고 나중에 넣는 사이에 다른 요청이 끼어들 수 없게, 같은 사용자·같은 종류의 저장은 줄을 서서 하나씩 처리된다.
 */
public final class Library {

    /** 잠금 종류(pg_advisory_xact_lock 첫 번호). 종류마다 다른 줄 → 찜하는 동안 조합 저장은 기다리지 않음 */
    enum Kind {
        BLENDS(5101), FAVORITES(5102), FEEDBACK(5103);

        final int lockClass;

        Kind(int lockClass) {
            this.lockClass = lockClass;
        }
    }

    /** DB app.valid_key와 같은 글자 규칙: 눈에 보이는 ASCII, 가운데 공백만 허용 */
    static final Pattern KEY = Pattern.compile("[!-~]([ -~]*[!-~])?");

    private Library() {}

    /**
     * 이 사용자·종류의 잠금을 트랜잭션이 끝날 때까지 잡는다. 반드시 트랜잭션 안에서 부른다.
     * 다른 사용자와 해시가 겹치면 잠깐 같이 기다릴 뿐 틀린 결과는 없다.
     * 기다리는 동안 DB 연결(풀 4개)을 붙잡으므로 2초만 기다리고, 못 잡으면 503으로 끝내 연결을 돌려준다
     * (한 사용자가 동시에 많이 보내 다른 사용자가 연결을 못 얻는 일을 막음, 리뷰 묶음 5 QA-1).
     */
    static void lock(JdbcClient jdbc, Kind kind, UUID user) {
        jdbc.sql("set local lock_timeout = '2s'").update();
        try {
            jdbc.sql("select pg_advisory_xact_lock(?, hashtext(?))").params(kind.lockClass, user.toString()).query((rs, n) -> 0).list();
        } catch (DataAccessException e) {
            if (sqlState(e, "55P03")) throw new ApiException(ApiError.UNAVAILABLE); // lock_not_available
            throw e;
        }
    }

    static boolean sqlState(Throwable e, String state) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && state.equals(s.getSQLState())) return true;
        }
        return false;
    }

    /** 잠금을 잡은 뒤 개수를 세서 한도 이상이면 409 LIMIT_REACHED */
    static void requireRoom(JdbcClient jdbc, String table, UUID user, int limit) {
        long n = jdbc.sql("select count(*) from app." + table + " where user_id = ?").param(user).query(Long.class).single();
        if (n >= limit) throw new ApiException(ApiError.LIMIT_REACHED);
    }

    /** 경로의 id. uuid 모양이 아니면 "없음"(404) — 남의 id·없는 id와 같은 답 */
    static UUID id(String raw) {
        if (raw == null || raw.length() != 36) throw new ApiException(ApiError.NOT_FOUND);
        try {
            UUID u = UUID.fromString(raw);
            if (!u.toString().equals(raw.toLowerCase())) throw new ApiException(ApiError.NOT_FOUND);
            return u;
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiError.NOT_FOUND);
        }
    }

    public static boolean key(String k, int max) {
        return k != null && k.length() <= max && KEY.matcher(k).matches();
    }

    /** 목록 key: null·중복 없음, 개수 max 이하, 각 key 모양 */
    static boolean keys(List<String> l, int max) {
        if (l == null || l.size() > max) return false;
        if (l.stream().distinct().count() != l.size()) return false;
        for (String k : l) if (!key(k, 100)) return false;
        return true;
    }

    /** 글자 수(코드 포인트, DB char_length와 같게). 짝 없는 서로게이트·NUL이 있으면 -1 */
    public static int length(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            if (c == 0 || (c >= 0xD800 && c <= 0xDFFF)) return -1;
            i += Character.charCount(c);
            n++;
        }
        return n;
    }

    /** 조합 이름: 1~40자, 제어 문자 없음, 공백·보이지 않는 글자만으로 된 이름 불가 (DB 검사와 같은 규칙) */
    static boolean name(String s) {
        if (s == null) return false;
        int len = length(s);
        if (len < 1 || len > 40) return false;
        boolean visible = false;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            if (c <= 0x1f || (c >= 0x7f && c <= 0x9f)) return false;
            if (!blank(c)) visible = true;
            i += Character.charCount(c);
        }
        return visible;
    }

    private static boolean blank(int c) {
        // 0x180e: Java는 공백으로 안 보지만 DB 정규식 \s는 공백으로 봄 → 맞춤(리뷰 묶음 5 QA-2, 재현)
        return Character.isWhitespace(c) || c == 0x00a0 || c == 0x1680 || c == 0x180e || (c >= 0x2000 && c <= 0x200f) || (c >= 0x2028 && c <= 0x202f)
                || (c >= 0x205f && c <= 0x2064) || c == 0x3000 || c == 0xfeff;
    }

    static String time(ResultSet rs, String col) throws SQLException {
        OffsetDateTime t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant().toString();
    }
}
