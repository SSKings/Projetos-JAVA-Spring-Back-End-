package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.security.SecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** Revisão 3, A2: regras do rastreador (as de ponta a ponta estão no AuthServiceTest e no SecurityReview3IntegrationTest). */
class LoginAttemptServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");
    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };
    private final LoginAttemptService attempts = new LoginAttemptService(new SecurityProperties(
            new SecurityProperties.Jwt("s", 60), "k", true, new SecurityProperties.Login(3, 15),
            new SecurityProperties.RateLimit(true, 20, 120)), clock);

    @AfterEach
    void reset() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Sem requisição (job, bot do Telegram) o canal conta como um 'IP' próprio")
    void withoutRequestContext() {
        for (int i = 0; i < 2; i++) assertThat(attempts.registerFailure("a@a.com").lockedNow()).isFalse();
        assertThat(attempts.registerFailure("a@a.com").lockedNow()).isTrue();
        assertThatThrownBy(() -> attempts.ensureNotLocked("a@a.com")).isInstanceOf(TooManyRequestsException.class);
        attempts.ensureNotLocked("b@a.com");   // outro email não é afetado
    }

    @Test
    @DisplayName("Janela do alerta de ataque recomeça depois do tempo de bloqueio")
    void attackWindowResets() {
        for (int i = 1; i < LoginAttemptService.ATTACK_THRESHOLD; i++) {
            assertThat(attempts.registerFailure("v@a.com").attackSuspected()).isFalse();
        }
        now.set(T0.plus(Duration.ofMinutes(16)));
        for (int i = 1; i < LoginAttemptService.ATTACK_THRESHOLD; i++) {
            assertThat(attempts.registerFailure("v@a.com").attackSuspected()).isFalse();
        }
        assertThat(attempts.registerFailure("v@a.com").attackSuspected()).isTrue();
        assertThat(attempts.registerFailure("v@a.com").attackSuspected()).isFalse();   // avisa uma vez só
    }

    @Test
    @DisplayName("Memória limitada: cheio, descarta entradas vencidas e mantém bloqueios ainda válidos")
    void evictsStaleEntriesWhenFull() {
        for (int i = 0; i < 3; i++) attempts.registerFailure("travado@a.com");       // bloqueado agora
        now.set(T0.plus(Duration.ofMinutes(10)));
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED; i++) attempts.registerFailure("u" + i + "@a.com");
        assertThatThrownBy(() -> attempts.ensureNotLocked("travado@a.com")).isInstanceOf(TooManyRequestsException.class);

        now.set(T0.plus(Duration.ofMinutes(26)));                                      // tudo acima venceu
        attempts.registerFailure("novo@a.com");                                         // dispara a limpeza
        attempts.ensureNotLocked("travado@a.com");
        assertThat(attempts.registerFailure("u1@a.com").lockedNow()).isFalse();        // contagem recomeçou
    }
}
