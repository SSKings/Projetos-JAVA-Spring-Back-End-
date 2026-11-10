package com.sskings.ecommerce.service;

import com.sskings.ecommerce.repository.AuditLogRepository;
import com.sskings.ecommerce.repository.TrustedLoginSourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/** Revisão 5, R5-5: dados pessoais fora do prazo são apagados. */
class DataRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final LocalDateTime LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final TrustedLoginSourceRepository sources = mock(TrustedLoginSourceRepository.class);
    private final com.sskings.ecommerce.repository.EmailTokenRepository tokens = mock(com.sskings.ecommerce.repository.EmailTokenRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("Apaga auditoria além do prazo configurado e origens confiáveis sem login há 90 dias")
    void purge() {
        given(audit.deleteOlderThan(LOCAL.minusDays(400))).willReturn(12);
        new DataRetentionJob(audit, sources, tokens, clock, 400).purge();
        then(audit).should().deleteOlderThan(LOCAL.minusDays(400));
        then(sources).should().deleteOlderThan(LOCAL.minusDays(90));
        then(tokens).should().deleteExpiredBefore(LOCAL.minusDays(1));   // links de e-mail vencidos (ADR-025)

        new DataRetentionJob(audit, sources, tokens, clock, 365).purge();   // nada a apagar: só não loga
        then(audit).should().deleteOlderThan(LOCAL.minusDays(365));
    }

    @Test
    @DisplayName("Prazo menor que 6 meses impede o startup (Marco Civil, art. 15)")
    void minimumRetention() {
        assertThatThrownBy(() -> new DataRetentionJob(audit, sources, tokens, clock, 179))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("180");
    }
}
