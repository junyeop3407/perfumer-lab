package com.perfumerlab.scentstudio.limit;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Render 뒤에서 요청한 사람의 IP 읽기 (server_design.md 4-1).
 * X-Forwarded-For 여러 줄을 순서대로 이어 붙이고 쉼표로 나눠 "오른쪽에서 hops번째"(기본 1 = 맨 끝, Render가 덧붙인 진짜 IP)만 쓴다.
 * 그 값이 IPv4·IPv6 주소 글자가 아니거나 항목이 모자라면 버리고 연결 주소를 쓴다. 다른 IP 헤더(X-Real-IP·True-Client-IP·Forwarded 등)는 읽지 않는다.
 * 주소는 글자로만 해석한다: InetAddress 같은 이름 풀이(DNS)를 할 수 있는 도구는 쓰지 않는다(사장 지시 2026-11-07, 리뷰 묶음 3 Gemini-1).
 */
public final class ClientIp {

    private static final Pattern IPV4 = Pattern.compile("(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}");
    private static final Pattern HEX_GROUP = Pattern.compile("[0-9A-Fa-f]{1,4}");

    private ClientIp() {}

    public static String resolve(List<String> xffLines, String remoteAddr, int hops) {
        List<String> entries = new ArrayList<>();
        for (String line : xffLines) {
            for (String part : line.split(",", -1)) entries.add(part.strip());
        }
        if (hops < 1 || entries.size() < hops) return remoteAddr;
        String v = entries.get(entries.size() - hops);
        return isIp(v) ? v : remoteAddr;
    }

    static boolean isIp(String v) {
        return IPV4.matcher(v).matches() || ipv6(v) != null;
    }

    /** 요청 제한 바구니 열쇠: IPv4는 그대로, IPv6는 /64(한 가정·한 기기가 보통 받는 범위)로 묶는다. IPv4를 담은 IPv6(::ffff:a.b.c.d)는 IPv4로. 모양이 아니면 그대로. */
    public static String limitKey(String ip) {
        if (IPV4.matcher(ip).matches()) return ip;
        int[] g = ipv6(ip);
        if (g == null) return ip;
        if (g[0] == 0 && g[1] == 0 && g[2] == 0 && g[3] == 0 && g[4] == 0 && g[5] == 0xffff) {
            return (g[6] >> 8) + "." + (g[6] & 0xff) + "." + (g[7] >> 8) + "." + (g[7] & 0xff);
        }
        return String.format("%04x:%04x:%04x:%04x::/64", g[0], g[1], g[2], g[3]);
    }

    /** IPv6 글자 → 16비트 묶음 8개. "::" 줄임 한 번, 끝에 IPv4 글자(예: ::ffff:1.2.3.4) 허용. 그 밖(구역 %·대괄호·포트 등)은 null. */
    static int[] ipv6(String s) {
        if (s.length() < 2 || s.length() > 45 || s.indexOf(':') < 0) return null;
        int lastColon = s.lastIndexOf(':');
        String last = s.substring(lastColon + 1);
        if (last.indexOf('.') >= 0) { // 끝의 IPv4 글자를 16진수 두 묶음 글자로 바꾼 뒤 똑같이 읽는다
            if (!IPV4.matcher(last).matches()) return null;
            String[] o = last.split("[.]");
            s = s.substring(0, lastColon + 1) + Integer.toHexString(Integer.parseInt(o[0]) << 8 | Integer.parseInt(o[1]))
                    + ":" + Integer.toHexString(Integer.parseInt(o[2]) << 8 | Integer.parseInt(o[3]));
        }
        int dbl = s.indexOf("::");
        if (dbl >= 0 && dbl != s.lastIndexOf("::")) return null;
        List<Integer> left = new ArrayList<>(), right = new ArrayList<>();
        if (dbl >= 0) {
            if (!groups(s.substring(0, dbl), left) || !groups(s.substring(dbl + 2), right)) return null;
        } else if (!groups(s, left)) {
            return null;
        }
        int total = left.size() + right.size();
        if (dbl < 0 ? total != 8 : total > 7) return null;
        int[] out = new int[8];
        for (int i = 0; i < left.size(); i++) out[i] = left.get(i);
        for (int i = 0; i < right.size(); i++) out[8 - right.size() + i] = right.get(i);
        return out;
    }

    /** "1:2:abcd" → 묶음 목록. 빈 글자는 묶음 0개. 빈 묶음(":1", "1:")이나 16진수 1~4자가 아니면 false */
    private static boolean groups(String part, List<Integer> out) {
        if (part.isEmpty()) return true;
        for (String g : part.split(":", -1)) {
            if (!HEX_GROUP.matcher(g).matches()) return false;
            out.add(Integer.parseInt(g, 16));
        }
        return true;
    }
}
