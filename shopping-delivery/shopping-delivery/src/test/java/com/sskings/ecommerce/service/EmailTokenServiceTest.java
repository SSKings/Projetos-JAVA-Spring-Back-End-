package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.EmailToken;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.EmailTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/** ADR-025: links de uso único. */
class EmailTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final LocalDateTime LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private final EmailTokenRepository repository = mock(EmailTokenRepository.class);
    private final EmailTokenService service = new EmailTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    private final User user = User.builder().id(7L).email("ana@teste.com").active(true).build();

    private EmailToken stored(String raw, EmailToken.Purpose purpose, LocalDateTime expires, LocalDateTime used) {
        EmailToken t = EmailToken.builder().id(1L).user(user).purpose(purpose).tokenHash(EmailTokenService.hash(raw))
                .createdAt(LOCAL.minusMinutes(1)).expiresAt(expires).usedAt(used).build();
        given(repository.findByTokenHash(EmailTokenService.hash(raw))).willReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("Emitir: 256 bits em base64url, guarda só o SHA-256, aposenta os pendentes do mesmo tipo")
    void issue() {
        String token = service.issue(user, EmailToken.Purpose.VERIFY_EMAIL, Duration.ofHours(48));
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        var saved = ArgumentCaptor.forClass(EmailToken.class);
        then(repository).should().save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(EmailTokenService.hash(token)).isNotEqualTo(token).hasSize(64);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(LOCAL.plusHours(48));
        then(repository).should().invalidatePending(7L, EmailToken.Purpose.VERIFY_EMAIL, LOCAL);
        assertThat(service.issue(user, EmailToken.Purpose.VERIFY_EMAIL, Duration.ofHours(1))).isNotEqualTo(token);
    }

    @Test
    @DisplayName("Consumir: válido gasta uma vez; vencido, usado, outro tipo, conta bloqueada/anonimizada → mesma mensagem")
    void consume() {
        String ok = "a".repeat(43);
        stored(ok, EmailToken.Purpose.RESET_PASSWORD, LOCAL.plusMinutes(5), null);
        given(repository.markUsed(1L, LOCAL)).willReturn(1);
        assertThat(service.consume(ok, EmailToken.Purpose.RESET_PASSWORD)).isSameAs(user);

        given(repository.markUsed(1L, LOCAL)).willReturn(0);                         // outro clique gastou antes
        assertThatThrownBy(() -> service.consume(ok, EmailToken.Purpose.RESET_PASSWORD)).hasMessage(EmailTokenService.INVALID);
        assertThatThrownBy(() -> service.consume(ok, EmailToken.Purpose.VERIFY_EMAIL)).hasMessage(EmailTokenService.INVALID);

        String expired = "b".repeat(43);
        stored(expired, EmailToken.Purpose.RESET_PASSWORD, LOCAL, null);             // vence no instante exato
        String used = "c".repeat(43);
        stored(used, EmailToken.Purpose.RESET_PASSWORD, LOCAL.plusMinutes(5), LOCAL.minusSeconds(1));
        for (String t : new String[]{expired, used, "d".repeat(43), "curto", null}) {
            assertThatThrownBy(() -> service.consume(t, EmailToken.Purpose.RESET_PASSWORD))
                    .isInstanceOf(BusinessException.class).hasMessage(EmailTokenService.INVALID);
        }

        user.setActive(false);
        assertThatThrownBy(() -> service.peek(ok, EmailToken.Purpose.RESET_PASSWORD)).hasMessage(EmailTokenService.INVALID);
        user.setActive(true);
        user.setAnonymizedAt(LOCAL);
        assertThatThrownBy(() -> service.peek(ok, EmailToken.Purpose.RESET_PASSWORD)).hasMessage(EmailTokenService.INVALID);
    }

    @Test
    @DisplayName("Intervalo mínimo entre links do mesmo tipo")
    void issuedRecently() {
        given(repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(7L, EmailToken.Purpose.RESET_PASSWORD))
                .willReturn(Optional.of(EmailToken.builder().createdAt(LOCAL.minusMinutes(4)).build()));
        assertThat(service.issuedRecently(user, EmailToken.Purpose.RESET_PASSWORD, Duration.ofMinutes(5))).isTrue();
        assertThat(service.issuedRecently(user, EmailToken.Purpose.RESET_PASSWORD, Duration.ofMinutes(3))).isFalse();
        assertThat(service.issuedRecently(user, EmailToken.Purpose.VERIFY_EMAIL, Duration.ofMinutes(5))).isFalse();
    }
}
