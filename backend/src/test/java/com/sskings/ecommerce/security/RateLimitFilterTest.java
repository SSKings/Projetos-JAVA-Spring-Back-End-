package com.sskings.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-05T12:00:10Z"));
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };

    private RateLimitFilter filter(boolean enabled) {
        return new RateLimitFilter(new SecurityProperties.RateLimit(enabled, 3, 2), clock);
    }

    private int call(RateLimitFilter f, String method, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        if (res.getStatus() == 429) {
            assertThat(res.getHeader("Retry-After")).isEqualTo("50");
            assertThat(res.getContentAsString()).contains("Too many requests");
        }
        return res.getStatus();
    }

    @Test
    @DisplayName("Login: 3 por minuto por IP; a 4ª recebe 429 com Retry-After; outro IP não é afetado")
    void auth() throws Exception {
        RateLimitFilter f = filter(true);
        for (int i = 0; i < 3; i++) assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/register", "1.1.1.1")).isEqualTo(429);   // mesmo balde
        assertThat(call(f, "POST", "/api/auth/login", "2.2.2.2")).isEqualTo(200);

        now.set(Instant.parse("2026-11-05T12:01:00Z"));                             // próxima janela
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("Webhook tem limite próprio; GET e outras rotas não são limitados; desligado não limita")
    void webhookAndOthers() throws Exception {
        RateLimitFilter f = filter(true);
        assertThat(call(f, "POST", "/api/payments/pix/webhook", "9.9.9.9")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/payments/pix/webhook", "9.9.9.9")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/payments/pix/webhook", "9.9.9.9")).isEqualTo(429);
        for (int i = 0; i < 10; i++) {
            assertThat(call(f, "GET", "/api/auth/login", "9.9.9.9")).isEqualTo(200);
            assertThat(call(f, "POST", "/api/cart/items", "9.9.9.9")).isEqualTo(200);
        }
        RateLimitFilter off = filter(false);
        for (int i = 0; i < 10; i++) assertThat(call(off, "POST", "/api/auth/login", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("Muitos IPs distintos: janelas antigas são descartadas (memória limitada)")
    void cleanup() throws Exception {
        RateLimitFilter f = filter(true);
        for (int i = 0; i < 10_001; i++) call(f, "POST", "/api/auth/login", "10.0." + (i / 256) + "." + (i % 256));
        now.set(Instant.parse("2026-11-05T12:05:00Z"));
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("Revisão 4: IPv6 conta pelo bloco /64 — trocar de endereço no mesmo bloco não renova o limite")
    void ipv6CountsBySlash64() throws Exception {
        RateLimitFilter f = filter(true);
        for (int i = 1; i <= 3; i++) assertThat(call(f, "POST", "/api/auth/login", "2001:db8:1:2::" + i)).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/login", "2001:db8:1:2:ffff:ffff:ffff:ffff")).isEqualTo(429);
        assertThat(call(f, "POST", "/api/auth/login", "2001:db8:1:3::1")).isEqualTo(200);   // outro /64
    }

    @Test
    @DisplayName("Revisão 6 (R6-3): caminho codificado (%66, ;param) cai no mesmo balde — não contorna o limite")
    void encodedPathCountsInSameBucket() throws Exception {
        RateLimitFilter f = filter(true);
        assertThat(call(f, "POST", "/api/auth/%66orgot-password", "3.3.3.3")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/forgot-password;x=1", "3.3.3.3")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/%6Cogin", "3.3.3.3")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/forgot-password", "3.3.3.3")).isEqualTo(429);
        assertThat(call(f, "POST", "/api/payments/pix/%77ebhook", "4.4.4.4")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/payments/pix/%77ebhook", "4.4.4.4")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/payments/pix/webhook", "4.4.4.4")).isEqualTo(429);
    }
}
