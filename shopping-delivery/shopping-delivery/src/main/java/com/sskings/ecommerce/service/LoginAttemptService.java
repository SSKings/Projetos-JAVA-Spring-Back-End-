package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.security.SecurityProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueio por tentativas de login/reautenticação, por <b>email + IP de origem</b>.
 * <p>
 * Revisão 3, A2: o bloqueio era da conta inteira — 5 senhas erradas enviadas por QUALQUER pessoa trancavam o dono
 * (inclusive o admin) por 15 min, e 1 tentativa a cada 3 min o mantinha trancado para sempre. Agora quem erra só
 * tranca a si mesmo: o dono, de outro IP, continua entrando. Contra ataque distribuído (muitos IPs na mesma conta),
 * a soma de falhas por conta gera o evento {@code LOGIN_ATTACK_SUSPECTED} na auditoria, sem trancar o dono.
 * <p>
 * Emails que não existem passam pela mesmíssima regra (revisão 2, R3: o 429 não pode revelar quem tem conta).
 * Em memória, por instância, limitado a {@link #MAX_TRACKED} chaves (como o RateLimitFilter).
 */
@Component
public class LoginAttemptService {

    static final int MAX_TRACKED = 50_000;
    /** Falhas somadas de todos os IPs numa conta, dentro da janela de bloqueio, que caracterizam ataque. */
    static final int ATTACK_THRESHOLD = 20;

    private final SecurityProperties.Login props;
    private final Clock clock;
    private final Map<String, Attempts> byClient = new ConcurrentHashMap<>();
    private final Map<String, Attempts> byAccount = new ConcurrentHashMap<>();

    private record Attempts(int failures, LocalDateTime lockedUntil, LocalDateTime windowStart) {}

    /** lockedNow: este IP acabou de ser bloqueado nesta conta; attackSuspected: a conta passou do limite agora. */
    public record Failure(boolean lockedNow, boolean attackSuspected) {}

    public LoginAttemptService(SecurityProperties props, Clock clock) {
        this.props = props.login();
        this.clock = clock;
    }

    /** 429 com Retry-After se este IP estiver bloqueado para este email. */
    public void ensureNotLocked(String email) {
        LocalDateTime now = now();
        Attempts current = byClient.get(clientKey(email));
        if (current != null && current.lockedUntil() != null && current.lockedUntil().isAfter(now)) {
            throw new TooManyRequestsException("Too many failed attempts. Try again later",
                    Duration.between(now, current.lockedUntil()).toSeconds() + 1);
        }
    }

    public Failure registerFailure(String email) {
        LocalDateTime now = now();
        evictIfFull(now);
        boolean[] locked = {false};
        byClient.compute(clientKey(email), (k, a) -> {
            int failures = (a == null || a.lockedUntil() != null ? 0 : a.failures()) + 1;
            if (failures >= props.maxFailedAttempts()) {
                locked[0] = true;
                return new Attempts(0, now.plusMinutes(props.lockMinutes()), now);
            }
            return new Attempts(failures, null, now);
        });
        boolean[] attack = {false};
        byAccount.compute(email, (k, a) -> {
            boolean expired = a == null || a.windowStart().plusMinutes(props.lockMinutes()).isBefore(now);
            int failures = (expired ? 0 : a.failures()) + 1;
            attack[0] = failures == ATTACK_THRESHOLD;   // avisa uma vez por janela
            return new Attempts(failures, null, expired ? now : a.windowStart());
        });
        return new Failure(locked[0], attack[0]);
    }

    /** Sucesso zera só o contador deste IP: um acerto do atacante não apaga o histórico de outros IPs. */
    public void clear(String email) {
        byClient.remove(clientKey(email));
    }

    private void evictIfFull(LocalDateTime now) {
        if (byClient.size() + byAccount.size() < MAX_TRACKED) return;
        LocalDateTime staleBefore = now.minusMinutes(props.lockMinutes());
        byClient.entrySet().removeIf(e -> e.getValue().windowStart().isBefore(staleBefore));
        byAccount.entrySet().removeIf(e -> e.getValue().windowStart().isBefore(staleBefore));
    }

    private static String clientKey(String email) {
        String ip = AuditService.currentIp();
        return email + "|" + (ip == null ? "-" : ip);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
