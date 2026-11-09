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

    /** 429 se esta origem já criou o máximo de contas nas últimas 24 h. */
    public void check(String source) {
        if ("-".equals(source)) return;
        Instant now = clock.instant();
        Window w = bySource.get(source);
        if (w != null && w.start().plus(Duration.ofDays(1)).isAfter(now) && w.count() >= perDay) {
            throw new TooManyRequestsException("Too many accounts created from this network today. Try again later",
                    Duration.between(now, w.start().plus(Duration.ofDays(1))).toSeconds() + 1);
        }
    }

    public void record(String source) {
        if ("-".equals(source)) return;
        Instant now = clock.instant();
        if (bySource.size() >= MAX_TRACKED && (lastEviction == null || !lastEviction.plusSeconds(60).isAfter(now))) {
            lastEviction = now;   // no máximo 1 varredura por minuto (senão, cheio, cada cadastro varria o mapa inteiro)
            bySource.entrySet().removeIf(e -> !e.getValue().start().plus(Duration.ofDays(1)).isAfter(now));
        }
        bySource.compute(source, (k, w) -> w == null || !w.start().plus(Duration.ofDays(1)).isAfter(now)
                ? new Window(now, 1) : new Window(w.start(), w.count() + 1));
    }
}
