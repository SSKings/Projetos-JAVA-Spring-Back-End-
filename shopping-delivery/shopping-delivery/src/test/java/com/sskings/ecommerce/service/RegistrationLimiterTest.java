package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** Revisões 4 (R4-4) e 5 (R5-7): contas novas por origem em 24 h, com reserva atômica. */
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
        for (int i = 0; i < 3; i++) limiter.acquire("198.51.100.1");
        now.set(T0.plus(Duration.ofHours(23)));
        assertThatThrownBy(() -> limiter.acquire("198.51.100.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(3601));
        assertThatThrownBy(() -> limiter.acquire("198.51.100.1")).isInstanceOf(TooManyRequestsException.class);   // recusa não consome
        limiter.acquire("198.51.100.2");
        now.set(T0.plus(Duration.ofDays(1)));
        for (int i = 0; i < 3; i++) limiter.acquire("198.51.100.1");                 // janela nova
    }

    @Test
    @DisplayName("R5-7: 50 cadastros simultâneos da mesma origem — só 3 passam (reserva atômica)")
    void concurrentAcquireRespectsTheLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), refused = new AtomicInteger();
        for (int i = 0; i < 50; i++) {
            pool.submit(() -> {
                start.await();
                try { limiter.acquire("203.0.113.5"); ok.incrementAndGet(); }
                catch (TooManyRequestsException e) { refused.incrementAndGet(); }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(3);
        assertThat(refused.get()).isEqualTo(47);
    }

    @Test
    @DisplayName("Sem origem ('-', fora de requisição HTTP) não limita; memória limitada descarta janelas vencidas")
    void unknownSourceAndEviction() {
        for (int i = 0; i < 5; i++) limiter.acquire("-");
        for (int i = 0; i < 3; i++) limiter.acquire("velho");
        now.set(T0.plus(Duration.ofDays(2)));
        for (int i = 0; i < RegistrationLimiter.MAX_TRACKED + 1; i++) limiter.acquire("s" + i);
        for (int i = 0; i < 3; i++) limiter.acquire("velho");
    }
}
