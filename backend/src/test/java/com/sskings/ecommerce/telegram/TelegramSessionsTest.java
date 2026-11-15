package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.security.CryptoService;
import com.sskings.ecommerce.security.TotpService;
import com.sskings.ecommerce.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static com.sskings.ecommerce.telegram.TelegramFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/** Revisão 3, M2: sessão do bot aberta com o TOTP do admin. */
class TelegramSessionsTest {

    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };
    private final TotpService totp = new TotpService(clock);
    private final CryptoService crypto = new CryptoService(Base64.getEncoder().encodeToString(new byte[32]));
    private final AuthService auth = mock(AuthService.class);
    private final TelegramSessions sessions = new TelegramSessions(totp, crypto, auth, clock);
    private User admin;
    private String secret;

    @BeforeEach
    void setUp() {
        secret = totp.newSecret();
        admin = admin();
        admin.setMfaSecret(crypto.encrypt(secret));
        // UPDATE condicional do banco (AuthService.claimMfaStep): só passo mais novo que o último
        given(auth.claimMfaStep(any(), anyLong())).willAnswer(inv -> {
            com.sskings.ecommerce.model.User u = inv.getArgument(0);
            long step = inv.getArgument(1);
            if (u.getMfaLastStep() != null && u.getMfaLastStep() >= step) return false;
            u.setMfaLastStep(step);
            return true;
        });
    }

    @Test
    @DisplayName("Sem /entrar não há sessão; código certo abre por 12 h, grava o passo TOTP e zera as falhas")
    void openWithValidCode() {
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isFalse();

        assertThat(sessions.open(ADMIN_CHAT, admin, totp.currentCode(secret))).isTrue();
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isTrue();
        assertThat(admin.getMfaLastStep()).isNotNull();
        then(auth).should().claimMfaStep(eq(admin), anyLong());   // R4-6: só o passo, nunca a entidade inteira
        then(auth).should().ensureReauthAllowed(admin);
        then(auth).should().clearReauthFailures(admin);
        assertThat(sessions.isOpen(222L, admin)).isFalse();                   // vale só para este chat

        now.set(T0.plus(TelegramSessions.TTL).minusSeconds(1));
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isTrue();
        now.set(T0.plus(TelegramSessions.TTL));
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isFalse();             // expirou
    }

    @Test
    @DisplayName("Código errado, reutilizado ou malformado conta como falha e não abre sessão")
    void invalidCodes() {
        String code = totp.currentCode(secret);
        assertThat(sessions.open(ADMIN_CHAT, admin, code)).isTrue();
        sessions.close(ADMIN_CHAT);
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isFalse();

        assertThat(sessions.open(ADMIN_CHAT, admin, code)).isFalse();        // replay
        assertThat(sessions.open(ADMIN_CHAT, admin, "000000")).isFalse();
        assertThat(sessions.open(ADMIN_CHAT, admin, "")).isFalse();
        assertThat(sessions.open(ADMIN_CHAT, admin, "12ab56")).isFalse();
        then(auth).should(times(4)).registerReauthFailure(admin, "telegram: invalid MFA code");
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isFalse();
    }

    @Test
    @DisplayName("Bloqueado por tentativas: nem valida o código")
    void locked() {
        willThrow(new TooManyRequestsException("locked", 60)).given(auth).ensureReauthAllowed(admin);
        assertThatThrownBy(() -> sessions.open(ADMIN_CHAT, admin, totp.currentCode(secret)))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isFalse();
        then(auth).should(never()).claimMfaStep(any(), anyLong());
    }

    @Test
    @DisplayName("Logout/troca de senha/bloqueio no painel (tokenVersion muda) derrubam a sessão do bot")
    void revokedWithPanelSessions() {
        sessions.open(ADMIN_CHAT, admin, totp.currentCode(secret));
        admin.revokeTokens();
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isFalse();
    }

    @Test
    @DisplayName("Outro admin ligado ao mesmo chat não herda a sessão; admin sem MFA (dev) não precisa de sessão")
    void boundToUserAndDevWithoutMfa() {
        sessions.open(ADMIN_CHAT, admin, totp.currentCode(secret));
        User other = admin();
        other.setId(2L);
        assertThat(sessions.isOpen(ADMIN_CHAT, other)).isFalse();

        User devAdmin = admin();
        devAdmin.setMfaEnabled(false);
        assertThat(sessions.isOpen(ADMIN_CHAT, devAdmin)).isTrue();
        assertThat(sessions.open(ADMIN_CHAT, devAdmin, "123456")).isFalse();   // sem segredo, sem sessão
    }

    @Test
    @DisplayName("R4-6/R4-7: código aceito por outra requisição no mesmo instante (UPDATE devolve 0) não abre sessão")
    void concurrentClaimLoses() {
        willReturn(false).given(auth).claimMfaStep(any(), anyLong());
        assertThat(sessions.open(ADMIN_CHAT, admin, totp.currentCode(secret))).isFalse();
        assertThat(sessions.isOpen(ADMIN_CHAT, admin)).isFalse();
        then(auth).should().registerReauthFailure(admin, "telegram: invalid MFA code");
    }
}
