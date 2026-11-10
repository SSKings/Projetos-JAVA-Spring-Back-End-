package com.sskings.ecommerce.service;

import com.sskings.ecommerce.event.AccountEvent;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.mail.MailMessages;
import com.sskings.ecommerce.mail.MailOutbox;
import com.sskings.ecommerce.mail.MailProperties;
import com.sskings.ecommerce.model.EmailToken;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Confirmação do e-mail e avisos de segurança da conta (ADR-025). Sem o e-mail confirmado, o cliente navega e
 * monta o carrinho, mas não fecha pedido (app.require-verified-email) nem recebe avisos: o endereço pode ser de
 * outra pessoa.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration TTL = Duration.ofHours(48);
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);

    private final EmailTokenService tokens;
    private final UserRepository users;
    private final MailOutbox mail;
    private final MailProperties props;
    private final AuditService audit;
    private final Clock clock;

    /** Na mesma transação do evento: rollback do cadastro desfaz o link e não manda e-mail. */
    @EventListener
    public void on(AccountEvent event) {
        Long userId = event instanceof AccountEvent.Registered r ? r.userId() : ((AccountEvent.SecurityChange) event).userId();
        User user = users.findById(userId).orElse(null);
        if (user == null || user.getAnonymizedAt() != null) return;
        if (event instanceof AccountEvent.Registered) {
            send(user);
        } else if (event instanceof AccountEvent.SecurityChange s && user.isEmailVerified()) {
            var m = MailMessages.security(props, user.getName(), s.what(), LocalDateTime.now(clock));
            mail.send(user.getEmail(), m.subject(), m.body());
        }
    }

    /** "Reenviar e-mail de confirmação" (logado). Intervalo mínimo entre envios: não serve para lotar caixa de alguém. */
    @Transactional
    public void resend(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isEmailVerified()) throw new BusinessException("Email already verified");
        if (tokens.issuedRecently(user, EmailToken.Purpose.VERIFY_EMAIL, RESEND_COOLDOWN)) {
            throw new BusinessException("Wait a few minutes before requesting another email");
        }
        send(user);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(String token) {
        User user = tokens.consume(token, EmailToken.Purpose.VERIFY_EMAIL);
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(LocalDateTime.now(clock));
            users.save(user);
            audit.record(user, AuditService.EMAIL_VERIFIED, "USER", user.getId(), null);
        }
    }

    private void send(User user) {
        String token = tokens.issue(user, EmailToken.Purpose.VERIFY_EMAIL, TTL);
        var m = MailMessages.verifyEmail(props, user.getName(), token, (int) TTL.toHours());
        mail.send(user.getEmail(), m.subject(), m.body());
    }
}
