package com.perfumerlab.scentstudio.me;

import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * GET /me: 내 계정(사용자 id, 이메일)과 지금 동의해야 할 문서 버전·내 동의 상태. 가입일은 주지 않는다(사장 결정 2026-11-07).
 * POST /me/consents: 동의 기록. 앱이 보여 준 문서 버전이 지금 버전과 다르면 409(옛 문서에 동의한 것으로 남지 않게).
 * 사용자 id·이메일은 토큰에서만 꺼낸다. 이메일은 없을 수 있다(카카오 로그인) → null. 구글·카카오 계정은 서로 다른 user id라 합치지 않는다.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    static final Pattern APP_VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,31}");

    public record ConsentItem(String type, String version) {}

    public record ConsentRequest(String appVersion, List<ConsentItem> consents) {}

    private final Consents consents;
    private final TransactionTemplate tx;

    public MeController(Consents consents, TransactionTemplate tx) {
        this.consents = consents;
        this.tx = tx;
    }

    static UUID userId(JwtAuthenticationToken auth) {
        return UUID.fromString(auth.getName());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> me(JwtAuthenticationToken auth) {
        UUID user = userId(auth);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", user.toString());
        out.put("email", email(auth.getToken().getClaims().get("email")));
        out.put("consents", status(user));
        return out;
    }

    /** 카카오 로그인은 이메일이 없을 수 있다(이메일 동의를 받지 않음, 사장 결정 2026-11-08). 없음·빈 값·공백·문자열이 아님 → null */
    static String email(Object claim) {
        return claim instanceof String s && !s.isBlank() ? s : null;
    }

    private Map<String, Object> status(UUID user) {
        Set<String> agreed = consents.agreed(user);
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String t : Consents.TYPES) flags.put(t, agreed.contains(t));
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("required", consents.current());
        s.put("agreed", flags);
        s.put("complete", agreed.size() == Consents.TYPES.size());
        return s;
    }

    @PostMapping(path = "/consents", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> consent(JwtAuthenticationToken auth, @RequestBody ConsentRequest req) {
        if (req.appVersion() == null || !APP_VERSION.matcher(req.appVersion()).matches()) throw new ApiException(ApiError.BAD_REQUEST);
        List<ConsentItem> items = req.consents();
        if (items == null || items.isEmpty() || items.size() > Consents.TYPES.size()) throw new ApiException(ApiError.BAD_REQUEST);
        Set<String> seen = new HashSet<>();
        for (ConsentItem i : items) {
            if (i == null || i.type() == null || i.version() == null || !Consents.TYPES.contains(i.type()) || !seen.add(i.type())) {
                throw new ApiException(ApiError.BAD_REQUEST);
            }
        }
        for (ConsentItem i : items) {
            if (!consents.current().get(i.type()).equals(i.version())) throw new ApiException(ApiError.STALE_CONSENT_VERSION);
        }
        UUID user = userId(auth);
        tx.executeWithoutResult(s -> items.forEach(i -> consents.record(user, i.type(), req.appVersion())));
        return me(auth);
    }
}
