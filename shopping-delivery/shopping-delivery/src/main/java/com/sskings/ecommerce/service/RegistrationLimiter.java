package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contas novas por origem (IP ou /64 IPv6) em 24 h (revisão 4, R4-4). Os tetos de estoque são por conta; sem
 * verificação de e-mail, criar contas era de graça e algumas bastavam para esgotar a loja. O rate limit por minuto
 * (20/min na API, 10/min no nginx) ainda permitia ~14 mil contas por dia de um único IP.
 * Em memória, por instância (como o RateLimitFilter). A correção completa é verificar o e-mail no cadastro.
 */
@Component
public class RegistrationLimiter {

    static final int MAX_TRACKED = 50_000;

    private final int perDay;
    private final Clock clock;
    private final Map<String, Window> bySource = new ConcurrentHashMap<>();
    private volatile Instant lastEviction;

    private record Window(Instant start, int count) {}

    public RegistrationLimiter(@Value("${security.rate-limit.registrations-per-day:10}") int perDay, Clock clock) {
        this.perDay = perDay;
        this.clock = clock;
    }

    /**
     * Reserva uma vaga de cadastro para a origem numa operação só (revisão 5, R5-7): "verificar e depois registrar"
     * deixava cadastros simultâneos da mesma origem passarem juntos do limite. A vaga é gasta mesmo se o cadastro
     * falhar depois (email em uso, senha fraca): tentativas também contam.
     * @throws TooManyRequestsException se a origem já usou as vagas das últimas 24 h
     */
    public void acquire(String source) {
        if ("-".equals(source)) return;
        Instant now = clock.instant();
        if (bySource.size() >= MAX_TRACKED && (lastEviction == null || !lastEviction.plusSeconds(60).isAfter(now))) {
            lastEviction = now;   // no máximo 1 varredura por minuto
            bySource.entrySet().removeIf(e -> !e.getValue().start().plus(Duration.ofDays(1)).isAfter(now));
        }
        bySource.compute(source, (k, w) -> {
            if (w == null || !w.start().plus(Duration.ofDays(1)).isAfter(now)) return new Window(now, 1);
            if (w.count() >= perDay) {
                throw new TooManyRequestsException("Too many accounts created from this network today. Try again later",
                        Duration.between(now, w.start().plus(Duration.ofDays(1))).toSeconds() + 1);
            }
            return new Window(w.start(), w.count() + 1);
        });
    }
}
