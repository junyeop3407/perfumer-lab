package com.perfumerlab.scentstudio.api;

import java.net.InetAddress;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 테스트 JVM 전체의 이름 풀이(DNS)를 기록만 하고 원래 풀이에 넘긴다(java.net.spi, META-INF/services로 등록).
 * IP 읽기 규칙이 사용자가 보낸 값으로 DNS를 부르지 않는지 확인하는 데 쓴다(리뷰 묶음 3 Gemini-1).
 */
public class RecordingDnsResolver extends InetAddressResolverProvider {
    static final Set<String> LOOKUPS = ConcurrentHashMap.newKeySet();

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws java.net.UnknownHostException {
                LOOKUPS.add(host);
                return builtin.lookupByName(host, policy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws java.net.UnknownHostException {
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "recording";
    }
}
