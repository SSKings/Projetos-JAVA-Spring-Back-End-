package com.sskings.ecommerce.service;

import com.sskings.ecommerce.event.AccountEvent;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.mail.MailOutbox;
import com.sskings.ecommerce.mail.MailProperties;
import com.sskings.ecommerce.model.EmailToken;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/** ADR-025: confirmação de e-mail e avisos de segurança. */
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final EmailTokenService tokens = mock(EmailTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MailOutbox mail = mock(MailOutbox.class);
    private final AuditService audit = mock(AuditService.class);
    private final MailProperties props = new MailProperties(true, "l@gmail.com", "https://loja.com.br", false, null);
    private final EmailVerificationService service = new EmailVerificationService(tokens, users, mail, props, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final User user = User.builder().id(1L).name("Ana").email("ana@teste.com").active(true).build();

    @Test
    @DisplayName("Cadastro manda o link; aviso de segurança só para e-mail confirmado; conta sumida ou anonimizada: nada")
    void events() {
        given(users.findById(1L)).willReturn(Optional.of(user));
        given(tokens.issue(eq(user), eq(EmailToken.Purpose.VERIFY_EMAIL), any())).willReturn("T".repeat(43));
        service.on(new AccountEvent.Registered(1L));
        then(mail).should().send(eq("ana@teste.com"), startsWith("Confirme seu e-mail"), contains("T".repeat(43)));

        service.on(new AccountEvent.SecurityChange(1L, "Sua senha foi alterada"));      // não confirmado
        then(mail).should(times(1)).send(any(), any(), any());
        user.setEmailVerifiedAt(LocalDateTime.now());
        service.on(new AccountEvent.SecurityChange(1L, "Sua senha foi alterada"));
        then(mail).should().send(eq("ana@teste.com"), startsWith("Alerta de segurança"), contains("12:00"));

        user.setAnonymizedAt(LocalDateTime.now());
        service.on(new AccountEvent.SecurityChange(1L, "x"));
        given(users.findById(9L)).willReturn(Optional.empty());
        service.on(new AccountEvent.Registered(9L));
        then(mail).should(times(2)).send(any(), any(), any());
    }

    @Test
    @DisplayName("Reenviar: recusa se já confirmado ou se o último link é recente; senão manda outro")
    void resend() {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user));   // trava a linha da conta (R6-2)
        given(tokens.issuedRecently(user, EmailToken.Purpose.VERIFY_EMAIL, EmailVerificationService.RESEND_COOLDOWN)).willReturn(true);
        assertThatThrownBy(() -> service.resend(1L)).hasMessage("Wait a few minutes before requesting another email");
        given(tokens.issuedRecently(any(), any(), any())).willReturn(false);
        given(tokens.issue(any(), any(), any())).willReturn("T".repeat(43));
        service.resend(1L);
        then(mail).should().send(eq("ana@teste.com"), any(), any());
        user.setEmailVerifiedAt(LocalDateTime.now());
        assertThatThrownBy(() -> service.resend(1L)).isInstanceOf(BusinessException.class).hasMessage("Email already verified");
        given(users.findByIdForUpdate(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.resend(9L)).hasMessage("User not found");
    }

    @Test
    @DisplayName("Revisão 6 (R6-1): teto de 5 e-mails de confirmação por dia, mesmo respeitando o intervalo")
    void resendDailyLimit() {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(tokens.issuedRecently(any(), any(), any())).willReturn(false);
        given(tokens.dailyLimitReached(user, EmailToken.Purpose.VERIFY_EMAIL, EmailVerificationService.DAILY_LIMIT)).willReturn(true);
        assertThatThrownBy(() -> service.resend(1L)).isInstanceOf(BusinessException.class)
                .hasMessage("Daily limit of confirmation emails reached. Try again tomorrow");
        then(tokens).should(never()).issue(any(), any(), any());
        then(mail).shouldHaveNoInteractions();
        org.assertj.core.api.Assertions.assertThat(EmailVerificationService.DAILY_LIMIT).isEqualTo(5);
    }

    @Test
    @DisplayName("Confirmar: grava a data e audita; link de quem já confirmou não regrava")
    void verify() {
        given(tokens.consume("tok", EmailToken.Purpose.VERIFY_EMAIL)).willReturn(user);
        service.verify("tok");
        assertThat(user.getEmailVerifiedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        then(audit).should().record(user, AuditService.EMAIL_VERIFIED, "USER", 1L, null);
        service.verify("tok");
        then(audit).should(times(1)).record(any(), eq(AuditService.EMAIL_VERIFIED), any(), any(), any());
    }
}
