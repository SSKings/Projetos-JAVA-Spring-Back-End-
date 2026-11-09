package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** Revisão 4, R4-4: contas novas por origem em 24 h. */
class RegistrationLimiterTest {

    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");
    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };
    private final RegistrationLimiter limiter = new RegistrationLimiter(3, clock);

    @Test
    @DisplayName("3 contas por origem em 24 h; a 4ª recebe 429 com Retry-After; outra origem não é afetada; vence em 24 h")
    void limitsPerSourcePerDay() {
        for (int i = 0; i < 3; i++) {
            limiter.check("198.51.100.1");
            limiter.record("198.51.100.1");
        }
        now.set(T0.plus(Duration.ofHours(23)));
        assertThatThrownBy(() -> limiter.check("198.51.100.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(3601));
        limiter.check("198.51.100.2");
        now.set(T0.plus(Duration.ofDays(1)));
        limiter.check("198.51.100.1");
        limiter.record("198.51.100.1");                              // janela nova
        limiter.check("198.51.100.1");
    }

    @Test
    @DisplayName("Sem origem ('-', fora de requisição HTTP) não limita; memória limitada descarta janelas vencidas")
    void unknownSourceAndEviction() {
        for (int i = 0; i < 5; i++) limiter.record("-");
        limiter.check("-");
        for (int i = 0; i < 3; i++) limiter.record("velho");
        now.set(T0.plus(Duration.ofDays(2)));
        for (int i = 0; i < RegistrationLimiter.MAX_TRACKED + 1; i++) limiter.record("s" + i);
        limiter.check("velho");
        limiter.record("velho");
        limiter.check("velho");
    }
}
