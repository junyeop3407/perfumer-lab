package com.perfumerlab.scentstudio.api;

import com.perfumerlab.scentstudio.limit.ClientIp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 묶음 3: Render 뒤에서 실제 IP 읽기 규칙 (server_design.md 4-1). 오른쪽에서 hops번째, IP 모양이 아니면 연결 주소. */
class ClientIpTest {

    static final String PROXY = "10.0.0.9";

    @Test
    void takesRightmostByDefault() {
        assertEquals("203.0.113.7", ClientIp.resolve(List.of("1.2.3.4, 203.0.113.7"), PROXY, 1));
        assertEquals("203.0.113.7", ClientIp.resolve(List.of("203.0.113.7"), PROXY, 1));
    }

    @Test
    void hopsCountsFromTheRight() {
        assertEquals("1.2.3.4", ClientIp.resolve(List.of("9.9.9.9, 1.2.3.4, 203.0.113.7"), PROXY, 2));
        assertEquals(PROXY, ClientIp.resolve(List.of("203.0.113.7"), PROXY, 2), "항목이 모자라면 연결 주소");
    }

    @Test
    void multipleHeaderLinesAreJoinedInOrder() {
        assertEquals("203.0.113.7", ClientIp.resolve(List.of("1.2.3.4", "5.6.7.8, 203.0.113.7"), PROXY, 1));
        assertEquals("5.6.7.8", ClientIp.resolve(List.of("1.2.3.4", "5.6.7.8, 203.0.113.7"), PROXY, 2));
    }

    @Test
    void noHeaderUsesRemoteAddress() {
        assertEquals(PROXY, ClientIp.resolve(List.of(), PROXY, 1));
    }

    @Test
    void manyCommasAndEmptyValues() {
        assertEquals("203.0.113.7", ClientIp.resolve(List.of(",".repeat(50) + "203.0.113.7"), PROXY, 1));
        assertEquals(PROXY, ClientIp.resolve(List.of("203.0.113.7,"), PROXY, 1), "맨 끝이 빈 값이면 연결 주소(앞 값으로 건너뛰지 않음)");
        assertEquals(PROXY, ClientIp.resolve(List.of(""), PROXY, 1));
        assertEquals(PROXY, ClientIp.resolve(List.of(" , , "), PROXY, 1));
    }

    @ParameterizedTest(name = "IP 모양 아님: {0}")
    @ValueSource(strings = {"abc", "999.1.1.1", "1.2.3", "1.2.3.4.5", "01.2.3.4", "1.2.3.4:80", "[::1]:80", "fe80::1%eth0",
            "localhost", "example.com", "1.2.3.-4", "unknown", "::g", "1:2:3:4:5:6:7:8:9"})
    void invalidValuesFallBackToRemoteAddress(String v) {
        assertEquals(PROXY, ClientIp.resolve(List.of("203.0.113.7, " + v), PROXY, 1), v);
    }

    @ParameterizedTest(name = "IP 모양: {0}")
    @ValueSource(strings = {"0.0.0.0", "255.255.255.255", "2001:db8::1", "::1", "2001:0db8:0000:0000:0000:ff00:0042:8329", "::ffff:192.0.2.1"})
    void validValuesAreUsed(String v) {
        assertNotEquals(PROXY, ClientIp.resolve(List.of(v), PROXY, 1), v);
    }

    @Test
    void ipv6IsGroupedBySlash64ForLimits() {
        assertEquals(ClientIp.limitKey("2001:db8:1:2:aaaa::1"), ClientIp.limitKey("2001:db8:1:2:bbbb::9"), "같은 /64는 같은 바구니");
        assertNotEquals(ClientIp.limitKey("2001:db8:1:2::1"), ClientIp.limitKey("2001:db8:1:3::1"));
        assertEquals("203.0.113.7", ClientIp.limitKey("203.0.113.7"));
    }
}
