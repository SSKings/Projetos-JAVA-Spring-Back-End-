package com.sskings.ecommerce.service;

import com.sskings.ecommerce.model.TrustedLoginSource;
import com.sskings.ecommerce.repository.TrustedLoginSourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/** Revisão 4, R4-1: origens de onde a conta já entrou. */
class TrustedLoginSourcesTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final LocalDateTime LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private final TrustedLoginSourceRepository repository = mock(TrustedLoginSourceRepository.class);
    private final TrustedLoginSources sources = new TrustedLoginSources(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Confiável só se entrou dali nos últimos 90 dias; origem desconhecida ('-') nunca é")
    void isTrusted() {
        given(repository.existsByUserIdAndSourceKeyAndLastSuccessAtAfter(1L, "203.0.113.1", LOCAL.minusDays(90))).willReturn(true);
        assertThat(sources.isTrusted(1L, "203.0.113.1")).isTrue();
        assertThat(sources.isTrusted(1L, "198.51.100.1")).isFalse();
        assertThat(sources.isTrusted(1L, "-")).isFalse();
        then(repository).should(never()).existsByUserIdAndSourceKeyAndLastSuccessAtAfter(any(), org.mockito.ArgumentMatchers.eq("-"), any());
    }

    @Test
    @DisplayName("Sucesso cria ou renova a origem; mantém só as 10 mais recentes e apaga as vencidas")
    void recordSuccess() {
        TrustedLoginSource existing = TrustedLoginSource.builder().id(5L).userId(1L).sourceKey("203.0.113.1")
                .lastSuccessAt(LOCAL.minusDays(3)).build();
        given(repository.findByUserIdAndSourceKey(1L, "203.0.113.1")).willReturn(Optional.of(existing));
        List<TrustedLoginSource> all = new ArrayList<>();
        all.add(existing);
        for (int i = 0; i < 10; i++) {
            all.add(TrustedLoginSource.builder().id(10L + i).userId(1L).sourceKey("x" + i).lastSuccessAt(LOCAL.minusDays(i + 1)).build());
        }
        TrustedLoginSource expired = TrustedLoginSource.builder().id(99L).userId(1L).sourceKey("velha").lastSuccessAt(LOCAL.minusDays(91)).build();
        all.set(1, expired);
        given(repository.findAllByUserIdOrderByLastSuccessAtDesc(1L)).willReturn(all);

        sources.recordSuccess(1L, "203.0.113.1");

        assertThat(existing.getLastSuccessAt()).isEqualTo(LOCAL);
        then(repository).should().save(existing);
        then(repository).should().delete(expired);                  // vencida
        then(repository).should().delete(all.get(10));             // 11ª
        then(repository).should(times(2)).delete(any());

        sources.recordSuccess(1L, "2001:db8:1:2::/64");             // nova
        then(repository).should().save(argThat(t -> t.getSourceKey().equals("2001:db8:1:2::/64") && t.getUserId() == 1L));
        sources.recordSuccess(1L, "-");                             // sem origem: nada gravado
        then(repository).should(times(2)).save(any());
    }

    @Test
    @DisplayName("Exclusão da conta apaga as origens (dado pessoal)")
    void forget() {
        sources.forget(7L);
        then(repository).should().deleteAllByUserId(7L);
    }
}
