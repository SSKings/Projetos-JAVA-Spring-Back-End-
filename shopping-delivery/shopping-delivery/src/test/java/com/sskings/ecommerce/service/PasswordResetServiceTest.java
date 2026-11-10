package com.sskings.ecommerce.service;

import com.sskings.ecommerce.mail.MailOutbox;
import com.sskings.ecommerce.mail.MailProperties;
import com.sskings.ecommerce.model.EmailToken;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.*;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/** ADR-025: pedido de nova senha não manda nada para conta bloqueada, anonimizada ou com link recente. */
class PasswordResetServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final EmailTokenService tokens = mock(EmailTokenService.class);
    private final MailOutbox mail = mock(MailOutbox.class);
    private final PasswordResetService service = new PasswordResetService(users, tokens, mail,
            new MailProperties(true, "l@gmail.com", "https://loja.com.br", false, null), new BCryptPasswordEncoder(4),
            mock(AuditService.class), mock(ApplicationEventPublisher.class), Clock.systemUTC());
    private final User user = User.builder().id(1L).name("Ana").email("ana@teste.com").active(true).build();

    @Test
    @DisplayName("Conta bloqueada, anonimizada, inexistente ou com link recente: nenhum e-mail")
    void noMailWhenNotAllowed() {
        given(users.findByEmail("ana@teste.com")).willReturn(Optional.of(user));
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user));   // trava a linha da conta (R6-2)
        user.setActive(false);
        service.request("ana@teste.com");
        user.setActive(true);
        user.setAnonymizedAt(LocalDateTime.now());
        service.request("ana@teste.com");
        user.setAnonymizedAt(null);
        given(tokens.issuedRecently(user, EmailToken.Purpose.RESET_PASSWORD, PasswordResetService.COOLDOWN)).willReturn(true);
        service.request(" ANA@teste.com ");
        service.request("outra@teste.com");
        then(mail).shouldHaveNoInteractions();

        given(tokens.issuedRecently(any(), any(), any())).willReturn(false);
        given(tokens.issue(any(), any(), any())).willReturn("T".repeat(43));
        service.request("Ana@Teste.com");
        then(mail).should().send(eq("ana@teste.com"), startsWith("Redefinição de senha"), contains("/redefinir-senha#token="));
        then(users).should(atLeastOnce()).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("Revisão 6 (R6-1): teto de 5 links de nova senha por dia por conta — sem e-mail, mesma resposta")
    void dailyLimit() {
        given(users.findByEmail("ana@teste.com")).willReturn(Optional.of(user));
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(tokens.issuedRecently(any(), any(), any())).willReturn(false);
        given(tokens.dailyLimitReached(user, EmailToken.Purpose.RESET_PASSWORD, PasswordResetService.DAILY_LIMIT)).willReturn(true);
        service.request("ana@teste.com");                 // não lança: a resposta não pode revelar nada
        then(tokens).should(never()).issue(any(), any(), any());
        then(mail).shouldHaveNoInteractions();
        org.assertj.core.api.Assertions.assertThat(PasswordResetService.DAILY_LIMIT).isEqualTo(5);
    }
}
