package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.security.CryptoService;
import com.sskings.ecommerce.security.TotpService;
import com.sskings.ecommerce.service.AuthService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessão do bot aberta com o código do app autenticador (revisão 3, M2).
 * <p>
 * Antes, quem estivesse com o Telegram do admin aberto (celular desbloqueado, sessão do Telegram Desktop/Web
 * esquecida) agia como admin sem o segundo fator que o painel exige. Agora o admin com MFA precisa mandar
 * {@code /entrar 123456}; a sessão vale {@link #TTL} e cai junto com os tokens do painel (logout, troca de senha,
 * mudança de MFA, bloqueio — tudo que incrementa o tokenVersion). Códigos errados contam para o bloqueio de
 * reautenticação da conta (5 falhas → 15 min), o mesmo do painel.
 */
public class TelegramSessions {

    static final Duration TTL = Duration.ofHours(12);

    private final TotpService totp;
    private final CryptoService crypto;
    private final AuthService auth;
    private final Clock clock;
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    private record Session(Long userId, int tokenVersion, Instant expiresAt) {}

    public TelegramSessions(TotpService totp, CryptoService crypto, AuthService auth, Clock clock) {
        this.totp = totp;
        this.crypto = crypto;
        this.auth = auth;
        this.clock = clock;
    }

    /** Admin sem MFA só chega aqui com REQUIRE_ADMIN_MFA=false (dev): não há segundo fator para pedir. */
    public boolean isOpen(long chatId, User admin) {
        if (!admin.isMfaEnabled()) return true;
        Session s = sessions.get(chatId);
        boolean valid = s != null && s.userId().equals(admin.getId()) && s.tokenVersion() == admin.getTokenVersion()
                && s.expiresAt().isAfter(clock.instant());
        if (!valid && s != null) sessions.remove(chatId);
        return valid;
    }

    /**
     * Valida o TOTP (sem reutilizar código) e abre a sessão. Bloqueado → TooManyRequestsException.
     * Não grava a entidade do admin (lida no início da mensagem): só o passo TOTP, por UPDATE condicional —
     * regravar o User inteiro podia desfazer um bloqueio feito no mesmo instante (revisão 4, R4-6).
     */
    public boolean open(long chatId, User admin, String code) {
        auth.ensureReauthAllowed(admin);
        var step = admin.isMfaEnabled() && code != null && code.matches("\\d{6}")
                ? totp.verify(crypto.decrypt(admin.getMfaSecret()), code, admin.getMfaLastStep())
                : java.util.OptionalLong.empty();
        if (step.isEmpty() || !auth.claimMfaStep(admin, step.getAsLong())) {
            auth.registerReauthFailure(admin, "telegram: invalid MFA code");
            return false;
        }
        auth.clearReauthFailures(admin);
        sessions.put(chatId, new Session(admin.getId(), admin.getTokenVersion(), clock.instant().plus(TTL)));
        return true;
    }

    public void close(long chatId) {
        sessions.remove(chatId);
    }
}
