package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.security.SecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** Regras do rastreador de tentativas (revisões 3 e 4). As de ponta a ponta ficam no AuthServiceTest e nos *IntegrationTest. */
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

    private static void fromIp(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void reset() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Origem: IPv4 é o IP; IPv6 é o bloco /64 (qualquer forma de escrita); IPv4 mapeado vira IPv4")
    void sourceKey() {
        assertThat(LoginAttemptService.sourceKey("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(LoginAttemptService.sourceKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo("2001:db8:1:2::/64");
        assertThat(LoginAttemptService.sourceKey("2001:0db8:0001:0002::1")).isEqualTo("2001:db8:1:2::/64");
        assertThat(LoginAttemptService.sourceKey("2001:db8:1:3::1")).isEqualTo("2001:db8:1:3::/64");
        assertThat(LoginAttemptService.sourceKey("fe80::1%eth0")).isEqualTo("fe80:0:0:0::/64");
        assertThat(LoginAttemptService.sourceKey("::1")).isEqualTo("0:0:0:0::/64");
        assertThat(LoginAttemptService.sourceKey("::ffff:198.51.100.4")).isEqualTo("198.51.100.4");
        for (String bad : new String[]{null, "", "localhost", "evil.example"}) {
            assertThat(LoginAttemptService.sourceKey(bad)).as(String.valueOf(bad)).isEqualTo("-");
        }
    }

    @Test
    @DisplayName("R4-1: trocar de endereço dentro do mesmo /64 IPv6 não dá tentativas novas")
    void ipv6RotationInsideSlash64IsOneSource() {
        for (int i = 1; i <= 3; i++) {
            fromIp("2001:db8:1:2::" + Integer.toHexString(i));
            attempts.registerLoginFailure("v@a.com");
        }
        fromIp("2001:db8:1:2::ffff");
        assertThatThrownBy(() -> attempts.ensureLoginAllowed("v@a.com", false)).isInstanceOf(TooManyRequestsException.class);
        fromIp("2001:db8:9:9::1");                                                // outro /64: outra origem
        attempts.ensureLoginAllowed("v@a.com", false);
    }

    @Test
    @DisplayName("R4-1: estourado o teto da conta, origens novas param; a confiável continua; a janela vence")
    void accountBudget() {
        for (int i = 0; i < LoginAttemptService.ACCOUNT_BUDGET; i++) {
            fromIp("198.51.100." + i);
            LoginAttemptService.Failure f = attempts.registerLoginFailure("v@a.com");
            assertThat(f.attackStarted()).isEqualTo(i == LoginAttemptService.ACCOUNT_BUDGET - 1);
        }
        fromIp("198.51.100.200");                                                 // origem nunca usada
        assertThatThrownBy(() -> attempts.ensureLoginAllowed("v@a.com", false))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(15 * 60 + 1));
        attempts.ensureLoginAllowed("v@a.com", true);                              // casa do dono
        attempts.ensureLoginAllowed("outro@a.com", false);                         // outra conta não é afetada
        assertThat(attempts.registerLoginFailure("v@a.com").attackStarted()).isFalse();   // avisa uma vez só

        now.set(T0.plus(Duration.ofMinutes(15)));
        attempts.ensureLoginAllowed("v@a.com", false);                             // janela venceu
    }

    @Test
    @DisplayName("R4-2: reautenticação conta por conta — trocar de IP não ajuda; não afeta o login nem outra conta")
    void reauthPerAccount() {
        for (int i = 0; i < 2; i++) {
            fromIp("198.51.100." + i);
            assertThat(attempts.registerReauthFailure(7L)).isFalse();
        }
        fromIp("203.0.113.99");
        assertThat(attempts.registerReauthFailure(7L)).isTrue();
        fromIp("192.0.2.1");
        assertThatThrownBy(() -> attempts.ensureReauthAllowed(7L)).isInstanceOf(TooManyRequestsException.class);
        attempts.ensureReauthAllowed(8L);
        attempts.ensureLoginAllowed("v@a.com", false);

        attempts.clearReauth(7L);
        attempts.ensureReauthAllowed(7L);
        for (int i = 0; i < 3; i++) attempts.registerReauthFailure(7L);
        now.set(T0.plus(Duration.ofMinutes(15)).plusSeconds(1));
        attempts.ensureReauthAllowed(7L);                                          // bloqueio venceu
    }

    @Test
    @DisplayName("Sem requisição (bot do Telegram, jobs) a origem é '-', uma só para todos")
    void withoutRequestContext() {
        for (int i = 0; i < 2; i++) assertThat(attempts.registerLoginFailure("a@a.com").lockedNow()).isFalse();
        assertThat(attempts.registerLoginFailure("a@a.com").lockedNow()).isTrue();
        assertThatThrownBy(() -> attempts.ensureLoginAllowed("a@a.com", false)).isInstanceOf(TooManyRequestsException.class);
        assertThat(LoginAttemptService.currentSource()).isEqualTo("-");
    }

    @Test
    @DisplayName("Memória limitada: cheio, descarta entradas vencidas e mantém bloqueios ainda válidos")
    void evictsStaleEntriesWhenFull() {
        for (int i = 0; i < 3; i++) attempts.registerLoginFailure("travado@a.com");
        for (int i = 0; i < 3; i++) attempts.registerReauthFailure(1L);
        now.set(T0.plus(Duration.ofMinutes(10)));
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED; i++) attempts.registerLoginFailure("u" + i + "@a.com");
        assertThatThrownBy(() -> attempts.ensureLoginAllowed("travado@a.com", true)).isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> attempts.ensureReauthAllowed(1L)).isInstanceOf(TooManyRequestsException.class);

        now.set(T0.plus(Duration.ofMinutes(26)));
        attempts.registerReauthFailure(2L);                                        // dispara a limpeza
        attempts.ensureLoginAllowed("travado@a.com", true);
        assertThat(attempts.registerLoginFailure("u1@a.com").lockedNow()).isFalse();
    }
}
