package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static com.perfumerlab.scentstudio.api.BlendsApiTest.note;
import static com.perfumerlab.scentstudio.api.BlendsApiTest.notesJson;
import static com.perfumerlab.scentstudio.api.ReportsApiTest.PATH;
import static com.perfumerlab.scentstudio.api.ReportsApiTest.rep;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 신고 당시 조합(노트·강도) 칸 (사장 결정 2026-11-09).
 * - 없어도 되는 칸(검색으로 연 상세에서 신고할 때). 빠지거나 null이면 DB에 null
 * - 있으면 조합 저장(POST /blends notes)과 같은 검사: 1~9개, 층마다 3개, 같은 노트 한 번, layer·strength 정해진 값, 카탈로그에 있는 노트
 * - DB에는 조합과 같은 모양 [{note_key, layer, strength}]으로
 */
class ReportsBlendNotesTest extends MeApiTestBase {

    static final String BASE = "\"kind\":\"not_similar\",\"perfumeKey\":\"Brand:perfume\"";

    static long withNotes(UUID user, String jsonb) throws Exception {
        return dbCount("select count(*) from app.error_reports where user_id = ? and notes = ?::jsonb", user, jsonb);
    }

    @Test
    void savesBlendNotesInBlendShape() throws Exception {
        TestAuth.User u = consentedUser();
        String notes = "[" + note("bergamot", "top", "strong") + "," + note("rose", "middle", "medium") + "," + note("musk", "base", "weak") + "]";
        assertEquals(204, postAs(u, PATH, rep(BASE + ",\"notes\":" + notes)).statusCode());
        assertEquals(1, withNotes(u.id(), "[{\"note_key\":\"bergamot\",\"layer\":\"top\",\"strength\":\"strong\"},"
                + "{\"note_key\":\"rose\",\"layer\":\"middle\",\"strength\":\"medium\"},"
                + "{\"note_key\":\"musk\",\"layer\":\"base\",\"strength\":\"weak\"}]"));
    }

    @Test
    void notesAreOptional() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(204, postAs(u, PATH, rep(BASE)).statusCode());
        assertEquals(204, postAs(u, PATH, rep(BASE + ",\"notes\":null")).statusCode());
        assertEquals(2, dbCount("select count(*) from app.error_reports where user_id = ? and notes is null", u.id()));
    }

    @Test
    void nineNotesAllowed() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(204, postAs(u, PATH, rep(BASE + ",\"notes\":" + notesJson(9))).statusCode());
        assertEquals(1, dbCount("select count(*) from app.error_reports where user_id = ? and jsonb_array_length(notes) = 9", u.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",                                                                   // 0개
            "\"rose\"",                                                             // 배열 아님
            "{}",
            "[null]",
            "TEN",                                                                  // 10개
            "FOUR_TOP",                                                             // 한 층 4개
            "DUP",                                                                  // 같은 노트 두 번
            "[{\"noteKey\":\"rose\",\"layer\":\"heart\",\"strength\":\"weak\"}]",
            "[{\"noteKey\":\"rose\",\"layer\":\"top\",\"strength\":\"huge\"}]",
            "[{\"noteKey\":\"rose\",\"layer\":\"top\"}]",
            "[{\"noteKey\":null,\"layer\":\"top\",\"strength\":\"weak\"}]",
            "[{\"noteKey\":\" rose\",\"layer\":\"top\",\"strength\":\"weak\"}]",
            "[{\"noteKey\":\"rose\",\"layer\":\"TOP\",\"strength\":\"weak\"}]",
            "[{\"noteKey\":1,\"layer\":\"top\",\"strength\":\"weak\"}]",
            "[\"rose\"]",
    })
    void badNotesAre400(String notes) throws Exception {
        String body = rep(BASE + ",\"notes\":" + notes
                .replace("TEN", notesJson(10))
                .replace("FOUR_TOP", "[" + note("rose", "top", "weak") + "," + note("musk", "top", "weak") + ","
                        + note("vanilla", "top", "weak") + "," + note("bergamot", "top", "weak") + "]")
                .replace("DUP", "[" + note("rose", "top", "weak") + "," + note("rose", "base", "weak") + "]"));
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, body), 400, "BAD_REQUEST");
        assertEquals(0, ReportsApiTest.count(u.id()));
    }

    @Test
    void noteKeyOver100CharactersIs400() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, rep(BASE + ",\"notes\":[" + note("n".repeat(101), "top", "weak") + "]")), 400, "BAD_REQUEST");
        assertEquals(0, ReportsApiTest.count(u.id()));
    }

    @Test
    void unknownNoteInBlendIs400UnknownKey() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, rep(BASE + ",\"notes\":[" + note("rose", "top", "weak") + "," + note("zz-not-a-note", "base", "weak") + "]")),
                400, "UNKNOWN_KEY");
        assertEquals(0, ReportsApiTest.count(u.id()));
    }

    @Test
    void noteOnlyReportCanCarryBlend() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(204, postAs(u, PATH, rep("\"kind\":\"note_name\",\"noteKey\":\"bergamot\",\"notes\":[" + note("bergamot", "top", "weak") + "]")).statusCode());
        assertEquals(1, withNotes(u.id(), "[{\"note_key\":\"bergamot\",\"layer\":\"top\",\"strength\":\"weak\"}]"));
    }
}
