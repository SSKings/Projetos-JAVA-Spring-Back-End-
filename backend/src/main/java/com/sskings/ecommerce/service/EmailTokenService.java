package com.sskings.ecommerce.service;

import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.EmailToken;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.EmailTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Links de uso único enviados por e-mail (ADR-025).
 * <ul>
 *   <li>Token de 256 bits aleatórios (SecureRandom), em base64url no link; no banco, só o SHA-256 — um vazamento do
 *       banco não entrega links válidos.</li>
 *   <li>Vale uma vez ({@code UPDATE ... WHERE used_at IS NULL}: dois cliques simultâneos não valem os dois), para um
 *       propósito só, até expirar, e só para conta ativa e não anonimizada.</li>
 *   <li>Emitir um link novo aposenta os pendentes do mesmo tipo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmailTokenService {

    public static final String INVALID = "Invalid or expired link";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailTokenRepository repository;
    private final Clock clock;

    /** Cria o link e devolve o token em claro (só vai no e-mail). */
    @Transactional
    public String issue(User user, EmailToken.Purpose purpose, Duration ttl) {
        LocalDateTime now = now();
        repository.invalidatePending(user.getId(), purpose, now);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        repository.save(EmailToken.builder().user(user).purpose(purpose).tokenHash(hash(token))
                .createdAt(now).expiresAt(now.plus(ttl)).build());
        return token;
    }

    /** O último link deste tipo foi emitido há menos de {@code cooldown}? (Evita usar a loja para lotar a caixa de alguém.) */
    @Transactional(readOnly = true)
    public boolean issuedRecently(User user, EmailToken.Purpose purpose, Duration cooldown) {
        Optional<EmailToken> last = repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose);
        return last.isPresent() && last.get().getCreatedAt().plus(cooldown).isAfter(now());
    }

    /**
     * Já saíram {@code dailyLimit} links deste tipo para a conta nas últimas 24 h? Só o intervalo mínimo não basta:
     * uma conta reenviando a cada 2 min manda ~720 e-mails/dia — acima da cota do Gmail (~500/dia), o que pararia
     * todos os e-mails da loja, e uma forma de lotar a caixa de outra pessoa (revisão 6, R6-1).
     */
    @Transactional(readOnly = true)
    public boolean dailyLimitReached(User user, EmailToken.Purpose purpose, int dailyLimit) {
        return repository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, now().minusHours(24)) >= dailyLimit;
    }

    /** Valida e gasta o link. Qualquer problema → a mesma mensagem (não diz se existe, expirou ou já foi usado). */
    @Transactional(noRollbackFor = BusinessException.class)
    public User consume(String token, EmailToken.Purpose purpose) {
        EmailToken t = valid(token, purpose);
        if (repository.markUsed(t.getId(), now()) == 0) throw new BusinessException(INVALID);   // outro clique gastou antes
        return t.getUser();
    }

    /** Confere o link sem gastar (ex.: validar a senha nova antes — senha fraca não pode queimar o link). */
    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)   // participa da transação de quem chama
    public User peek(String token, EmailToken.Purpose purpose) {
        return valid(token, purpose).getUser();
    }

    private EmailToken valid(String token, EmailToken.Purpose purpose) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new BusinessException(INVALID);
        EmailToken t = repository.findByTokenHash(hash(token)).orElseThrow(() -> new BusinessException(INVALID));
        User user = t.getUser();
        if (t.getPurpose() != purpose || t.getUsedAt() != null || !t.getExpiresAt().isAfter(now())
                || !user.isActive() || user.getAnonymizedAt() != null) {
            throw new BusinessException(INVALID);
        }
        return t;
    }

    @Transactional
    public void invalidatePending(User user, EmailToken.Purpose purpose) {
        repository.invalidatePending(user.getId(), purpose, now());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
