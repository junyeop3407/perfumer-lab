package com.perfumerlab.scentstudio.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 앞으로 돌릴 수 있는 시계. 세션 보관 30초·요청 제한 창·5분 막기를 기다리지 않고 확인한다. */
final class TestClock extends Clock {
    private volatile Instant now = Instant.now();

    void advance(Duration d) {
        now = now.plus(d);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }
}
