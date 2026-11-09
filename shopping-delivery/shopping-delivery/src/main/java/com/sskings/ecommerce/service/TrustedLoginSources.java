package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.TrustedLoginSource;
import com.sskings.ecommerce.repository.TrustedLoginSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Origens (IP ou /64 IPv6) de onde cada conta já entrou com sucesso — durante um ataque distribuído só elas seguem
 * tentando o login (LoginAttemptService, revisão 4, R4-1). Dado pessoal: no máximo {@link #MAX_PER_USER} por conta,
 * válidas por {@link #TTL_DAYS} dias e apagadas com a conta.
 */
@Component
@RequiredArgsConstructor
public class TrustedLoginSources {

    static final int MAX_PER_USER = 10;
    static final int TTL_DAYS = 90;

    private final TrustedLoginSourceRepository repository;
    private final Clock clock;

    public boolean isTrusted(Long userId, String source) {
        return !"-".equals(source)
                && repository.existsByUserIdAndSourceKeyAndLastSuccessAtAfter(userId, source, now().minusDays(TTL_DAYS));
    }

    /** Chamar com a linha do usuário travada (login): dois logins simultâneos não inserem a mesma origem duas vezes. */
    public void recordSuccess(Long userId, String source) {
        if ("-".equals(source)) return;
        LocalDateTime now = now();
        TrustedLoginSource entry = repository.findByUserIdAndSourceKey(userId, source)
                .orElseGet(() -> TrustedLoginSource.builder().userId(userId).sourceKey(source).build());
        entry.setLastSuccessAt(now);
        repository.save(entry);
        List<TrustedLoginSource> all = repository.findAllByUserIdOrderByLastSuccessAtDesc(userId);
        LocalDateTime expired = now.minusDays(TTL_DAYS);
        for (int i = 0; i < all.size(); i++) {
            if (i >= MAX_PER_USER || !all.get(i).getLastSuccessAt().isAfter(expired)) repository.delete(all.get(i));
        }
    }

    public void forget(Long userId) {
        repository.deleteAllByUserId(userId);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
