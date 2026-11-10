package com.sskings.ecommerce.service;

import com.sskings.ecommerce.event.AccountEvent;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.mail.MailMessages;
import com.sskings.ecommerce.mail.MailOutbox;
import com.sskings.ecommerce.mail.MailProperties;
import com.sskings.ecommerce.model.EmailToken;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.PasswordStrength;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Recuperação de senha por e-mail (ADR-025).
 * <ul>
 *   <li>O pedido responde sempre igual, exista a conta ou não (não revela quem tem conta).</li>
 *   <li>Link de 30 min, uso único; no máximo um a cada 5 min por conta (não serve para lotar a caixa de ninguém).</li>
 *   <li>Redefinir: mesma política de senha do cadastro, derruba todas as sessões, aposenta outros links e avisa o
 *       titular. O MFA continua valendo: quem tem só o e-mail da vítima ainda precisa do código para entrar.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(30);
    static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final UserRepository users;
    private final EmailTokenService tokens;
    private final MailOutbox mail;
    private final MailProperties props;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void request(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(normalized)
                .filter(u -> u.isActive() && u.getAnonymizedAt() == null)
                .filter(u -> !tokens.issuedRecently(u, EmailToken.Purpose.RESET_PASSWORD, COOLDOWN))
                .ifPresent(user -> {
                    String token = tokens.issue(user, EmailToken.Purpose.RESET_PASSWORD, TTL);
                    var m = MailMessages.resetPassword(props, user.getName(), token, (int) TTL.toMinutes());
                    mail.send(user.getEmail(), m.subject(), m.body());
                    audit.record(user, AuditService.PASSWORD_RESET_REQUESTED, "USER", user.getId(), null);
                });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void reset(String token, String newPassword) {
        // senha fraca não pode gastar o link: confere a senha antes de consumir
        User owner = tokens.peek(token, EmailToken.Purpose.RESET_PASSWORD);
        PasswordStrength.problem(newPassword, owner.getName(), owner.getEmail())
                .ifPresent(p -> { throw new BusinessException(p); });
        User user = tokens.consume(token, EmailToken.Purpose.RESET_PASSWORD);
        user.setPassword(encoder.encode(newPassword));
        user.revokeTokens();
        if (!user.isEmailVerified()) user.setEmailVerifiedAt(LocalDateTime.now(clock));   // o link provou o e-mail
        users.save(user);
        tokens.invalidatePending(user, EmailToken.Purpose.RESET_PASSWORD);
        audit.record(user, AuditService.PASSWORD_RESET, "USER", user.getId(), null);
        events.publishEvent(new AccountEvent.SecurityChange(user.getId(), "Sua senha foi redefinida pelo link enviado por e-mail"));
    }
}
