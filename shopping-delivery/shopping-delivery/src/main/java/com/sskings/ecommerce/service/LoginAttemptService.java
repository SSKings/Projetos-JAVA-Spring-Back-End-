package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.security.SecurityProperties;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limites de tentativas de senha/código. Três contadores, em memória, por instância:
 * <ol>
 *   <li><b>Login por email + origem</b> — 5 falhas → aquela origem fica 15 min sem tentar aquele email. Quem erra
 *       tranca a si mesmo, não o dono (revisão 3, A2).</li>
 *   <li><b>Login por conta, somando todas as origens</b> — {@link #ACCOUNT_BUDGET} falhas na janela de 15 min.
 *       Passado o teto, só origens <i>confiáveis</i> (onde a conta já entrou com sucesso) continuam tentando; as
 *       demais recebem 429 sem a senha ser testada. Sem isso, trocar de IP dava tentativas ilimitadas — com IPv6,
 *       um único provedor entrega 2^64 endereços (revisão 4, R4-1).</li>
 *   <li><b>Reautenticação por conta</b> (id do usuário, sem olhar a origem) — quem reautentica já tem o token da
 *       conta, então não há dono para proteger de bloqueio por terceiros; contar por IP deixava um token roubado
 *       testar senhas sem limite trocando de IP (revisão 4, R4-2).</li>
 * </ol>
 * A "origem" é o IP; no IPv6, o bloco /64 inteiro (ver {@link #sourceKey}). Emails inexistentes seguem as regras
 * 1 e 2 exatamente como uma conta real (revisão 2, R3: o 429 não pode revelar quem tem conta).
 */
@Component
public class LoginAttemptService {

    static final int MAX_TRACKED = 50_000;
    /** Falhas por conta, somando todas as origens, por janela; acima disso só origens confiáveis tentam. */
    static final int ACCOUNT_BUDGET = 20;

    private final SecurityProperties.Login props;
    private final Clock clock;
    private final Map<String, Attempts> byClient = new ConcurrentHashMap<>();
    private final Map<String, Attempts> byAccount = new ConcurrentHashMap<>();
    private final Map<Long, Attempts> byReauth = new ConcurrentHashMap<>();
    /** Última limpeza: com o mapa cheio de entradas ainda válidas, limpar a cada falha virava O(n) por requisição. */
    private volatile LocalDateTime lastEviction;

    private record Attempts(int failures, LocalDateTime lockedUntil, LocalDateTime windowStart) {}

    /** lockedNow: esta origem acabou de ser bloqueada para o email; attackStarted: a conta estourou o teto agora. */
    public record Failure(boolean lockedNow, boolean attackStarted) {}

    public LoginAttemptService(SecurityProperties props, Clock clock) {
        this.props = props.login();
        this.clock = clock;
    }

    // ---------- login ----------

    /**
     * 429 se esta origem estiver bloqueada para o email, ou se a conta estourou o teto e a origem não é confiável.
     * @param trustedSource a conta já entrou com sucesso desta origem (sempre false para email inexistente)
     */
    public void ensureLoginAllowed(String email, boolean trustedSource) {
        LocalDateTime now = now();
        Attempts client = byClient.get(clientKey(email));
        if (client != null && client.lockedUntil() != null && client.lockedUntil().isAfter(now)) {
            throw locked(now, client.lockedUntil());
        }
        Attempts account = byAccount.get(email);
        if (!trustedSource && account != null && account.failures() >= ACCOUNT_BUDGET) {
            LocalDateTime windowEnd = account.windowStart().plusMinutes(props.lockMinutes());
            if (windowEnd.isAfter(now)) throw locked(now, windowEnd);
        }
    }

    public Failure registerLoginFailure(String email) {
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
            boolean expired = a == null || !a.windowStart().plusMinutes(props.lockMinutes()).isAfter(now);
            int failures = (expired ? 0 : a.failures()) + 1;
            attack[0] = failures == ACCOUNT_BUDGET;   // avisa uma vez por janela
            return new Attempts(failures, null, expired ? now : a.windowStart());
        });
        return new Failure(locked[0], attack[0]);
    }

    /** Sucesso zera só o contador desta origem: um acerto não apaga o histórico de outras origens nem o teto da conta. */
    public void clearLogin(String email) {
        byClient.remove(clientKey(email));
    }

    // ---------- reautenticação (por conta) ----------

    public void ensureReauthAllowed(Long userId) {
        LocalDateTime now = now();
        Attempts a = byReauth.get(userId);
        if (a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(now)) {
            throw locked(now, a.lockedUntil());
        }
    }

    /** @return true se a reautenticação desta conta acabou de ser bloqueada */
    public boolean registerReauthFailure(Long userId) {
        LocalDateTime now = now();
        evictIfFull(now);
        boolean[] locked = {false};
        byReauth.compute(userId, (k, a) -> {
            int failures = (a == null || a.lockedUntil() != null ? 0 : a.failures()) + 1;
            if (failures >= props.maxFailedAttempts()) {
                locked[0] = true;
                return new Attempts(0, now.plusMinutes(props.lockMinutes()), now);
            }
            return new Attempts(failures, null, now);
        });
        return locked[0];
    }

    public void clearReauth(Long userId) {
        byReauth.remove(userId);
    }

    // ---------- origem ----------

    /** Origem da requisição atual (ver {@link #sourceKey}); "-" fora de requisição HTTP (bot do Telegram, jobs). */
    public static String currentSource() {
        return sourceKey(AuditService.currentIp());
    }

    /**
     * IPv4 → o próprio IP; IPv6 → o prefixo /64 ("2001:db8:0:0::/64"). Um cliente IPv6 recebe pelo menos um /64 do
     * provedor e troca de endereço dentro dele à vontade: contar por endereço não limitaria nada.
     * IPv4 mapeado em IPv6 (::ffff:a.b.c.d) vira o IPv4.
     */
    public static String sourceKey(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.]+(%[\\w.-]+)?")) return "-";
        try {
            InetAddress address = InetAddress.getByName(ip.contains("%") ? ip.substring(0, ip.indexOf('%')) : ip);
            if (address instanceof Inet6Address) {
                byte[] b = address.getAddress();
                return "%x:%x:%x:%x::/64".formatted(
                        ((b[0] & 0xff) << 8) | (b[1] & 0xff), ((b[2] & 0xff) << 8) | (b[3] & 0xff),
                        ((b[4] & 0xff) << 8) | (b[5] & 0xff), ((b[6] & 0xff) << 8) | (b[7] & 0xff));
            }
            return address.getHostAddress();
        } catch (java.net.UnknownHostException e) {
            return "-";
        }
    }

    private static String clientKey(String email) {
        return email + "|" + currentSource();
    }

    private static TooManyRequestsException locked(LocalDateTime now, LocalDateTime until) {
        return new TooManyRequestsException("Too many failed attempts. Try again later",
                Duration.between(now, until).toSeconds() + 1);
    }

    private void evictIfFull(LocalDateTime now) {
        if (byClient.size() + byAccount.size() + byReauth.size() < MAX_TRACKED) return;
        if (lastEviction != null && lastEviction.plusMinutes(1).isAfter(now)) return;   // no máximo 1 varredura/min
        lastEviction = now;
        LocalDateTime staleBefore = now.minusMinutes(props.lockMinutes());
        byClient.entrySet().removeIf(e -> e.getValue().windowStart().isBefore(staleBefore));
        byAccount.entrySet().removeIf(e -> e.getValue().windowStart().isBefore(staleBefore));
        byReauth.entrySet().removeIf(e -> e.getValue().windowStart().isBefore(staleBefore));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
