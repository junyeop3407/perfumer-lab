package com.perfumerlab.scentstudio.limit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Render 뒤에서 요청한 사람의 IP 읽기 (server_design.md 4-1).
 * X-Forwarded-For 여러 줄을 순서대로 이어 붙이고 쉼표로 나눠 "오른쪽에서 hops번째"(기본 1 = 맨 끝, Render가 덧붙인 진짜 IP)만 쓴다.
 * 그 값이 IP 모양이 아니거나 항목이 모자라면 연결 주소를 쓴다. 다른 IP 헤더(X-Real-IP·True-Client-IP·Forwarded 등)는 읽지 않는다.
 * 이름 풀이(DNS)는 절대 하지 않는다: IP 글자 모양을 먼저 확인한 뒤에만 InetAddress로 바꾼다.
 */
public final class ClientIp {

    private static final Pattern IPV4 = Pattern.compile("(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}");
    // 첫 글자가 16진수나 ':'이고 ':'가 있어야 JDK가 IP 글자로만 읽는다(아니면 이름 풀이로 넘어감, 리뷰 묶음 3 Gemini-1)
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:][0-9A-Fa-f:.]{1,44}");

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
        if (IPV4.matcher(v).matches()) return true;
        if (!v.contains(":") || !IPV6_CHARS.matcher(v).matches()) return false; // 이 두 조건이면 InetAddress가 이름 풀이를 하지 않는다
        try {
            InetAddress.getByName(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 요청 제한 바구니 열쇠: IPv4는 그대로, IPv6는 /64(한 가정·한 기기가 보통 받는 범위)로 묶는다. 모양이 아니면 그대로. */
    public static String limitKey(String ip) {
        if (!ip.contains(":") || !isIp(ip)) return ip;
        try {
            InetAddress a = InetAddress.getByName(ip);
            if (!(a instanceof Inet6Address)) return a.getHostAddress(); // ::ffff:1.2.3.4 같은 IPv4
            byte[] b = a.getAddress();
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 8; i += 2) s.append(String.format("%02x%02x:", b[i], b[i + 1]));
            return s.append(":/64").toString();
        } catch (Exception e) {
            return ip;
        }
    }
}
