package com.perfumerlab.scentstudio.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.UUID;

/** 설치 id: 앱이 설치 때 만든 무작위 uuid를 X-Install-Id 헤더로 보낸다(광고 ID 아님). 없거나 uuid 모양이 아니면 400. */
public final class InstallId {

    public static final String HEADER = "X-Install-Id";

    private InstallId() {}

    public static UUID from(HttpServletRequest req) {
        String raw = req.getHeader(HEADER);
        if (raw == null || raw.length() != 36) throw new ApiException(ApiError.BAD_REQUEST);
        try {
            UUID u = UUID.fromString(raw);
            if (!u.toString().equals(raw.toLowerCase(Locale.ROOT))) throw new ApiException(ApiError.BAD_REQUEST);
            return u;
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiError.BAD_REQUEST);
        }
    }
}
