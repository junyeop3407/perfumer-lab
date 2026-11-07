package com.perfumerlab.scentstudio.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 본문은 256KB까지 (server_design.md 4-2: 사용 기록 묶음 한 요청 256KB가 가장 큼).
 * 길이가 적힌 요청은 바로 413, 길이 없이(chunked) 오면 읽다가 넘는 순간 멈춘다 → 413. 큰 본문을 다 읽어 메모리에 올리지 않게.
 * 가장 먼저 돈다(토큰 확인보다 앞).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BodySizeFilter extends OncePerRequestFilter {

    public static final int MAX_BYTES = 256 * 1024;

    /** 본문이 한도를 넘음 (읽던 곳까지 전해져 413이 된다) */
    public static class TooLarge extends IOException {
        TooLarge() {
            super("request body too large", null);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (req.getContentLengthLong() > MAX_BYTES) {
            ApiError.PAYLOAD_TOO_LARGE.write(res);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(req) {
            private ServletInputStream limited;

            @Override
            public ServletInputStream getInputStream() throws IOException {
                if (limited == null) limited = new Limited(super.getInputStream());
                return limited;
            }

            @Override
            public java.io.BufferedReader getReader() throws IOException { // 글자로 읽는 길도 같은 한도(리뷰 묶음 4 QA-4)
                String cs = getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding();
                return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), cs));
            }
        }, res);
    }

    private static final class Limited extends ServletInputStream {
        private final ServletInputStream in;
        private long read;

        Limited(ServletInputStream in) {
            this.in = in;
        }

        private int count(int n) throws IOException {
            if (n > 0 && (read += n) > MAX_BYTES) throw new TooLarge();
            return n;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return count(in.read(b, off, len));
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener l) {
            in.setReadListener(l);
        }
    }
}
