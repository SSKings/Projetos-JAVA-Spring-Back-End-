package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.LoginRequest;
import com.sskings.ecommerce.dto.request.RegisterRequest;
import com.sskings.ecommerce.dto.response.AuthResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.CryptoService;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.security.SecurityProperties;
import com.sskings.ecommerce.security.TotpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final CryptoService cryptoService;
    private final AuditService auditService;
    private final SecurityProperties props;
    private final Clock clock;
    private final String termsVersion;
    /** Hash usado quando o email não existe: o login leva o mesmo tempo nos dois casos (anti-enumeração). */
    private final String dummyHash;
    /**
     * Tentativas para emails que NÃO existem, com a mesma regra de bloqueio das contas reais. Sem isso, a 6ª
     * tentativa respondia 429 só para emails cadastrados e revelava quem tem conta (revisão 2, R3).
     * Em memória (por instância), limitada a MAX_TRACKED_UNKNOWN entradas.
     */
    private final java.util.Map<String, UnknownEmailAttempts> unknownEmails = new java.util.concurrent.ConcurrentHashMap<>();
    static final int MAX_TRACKED_UNKNOWN = 10_000;

    private record UnknownEmailAttempts(int failures, LocalDateTime lockedUntil, LocalDateTime lastAttempt) {}

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       TotpService totpService, CryptoService cryptoService, AuditService auditService,
                       SecurityProperties props, Clock clock, @Value("${app.terms-version}") String termsVersion) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
        this.props = props;
        this.clock = clock;
        this.termsVersion = termsVersion;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            // Mitigado por rate limit; a correção completa exige verificação de email (ver security-review.md, A2)
            throw new BusinessException("Email already in use");
        }
        User user = userRepository.save(User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .termsVersion(termsVersion)
                .termsAcceptedAt(now())
                .build());
        auditService.record(user, AuditService.REGISTER, "USER", user.getId(), "terms " + termsVersion);
        return response(user);
    }

    /** Falhas não fazem rollback: o contador de tentativas precisa ser gravado. */
    @Transactional(noRollbackFor = {BusinessException.class, TooManyRequestsException.class})
    public AuthResponse login(LoginRequest request) {
        LocalDateTime now = now();
        String email = normalizeEmail(request.email());
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            failUnknownEmail(email, request.password(), now);
        }
        User user = found.get();
        ensureNotLocked(user);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailure(user, now, "wrong password");
            throw new BusinessException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new BusinessException("Account is blocked");
        }
        if (user.isMfaEnabled()) {
            if (request.mfaCode() == null || request.mfaCode().isBlank()) {
                throw new BusinessException("MFA code required");
            }
            OptionalLong step = totpService.verify(cryptoService.decrypt(user.getMfaSecret()), request.mfaCode(), user.getMfaLastStep());
            if (step.isEmpty()) {
                registerFailure(user, now, "invalid MFA code");
                throw new BusinessException("Invalid MFA code");
            }
            user.setMfaLastStep(step.getAsLong());
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.record(user, AuditService.LOGIN_SUCCESS, "USER", user.getId(), null);
        return response(user);
    }

    /** Revoga todos os tokens do usuário (este e os de outros dispositivos). */
    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.revokeTokens();
            userRepository.save(user);
            auditService.record(user, AuditService.LOGOUT, "USER", user.getId(), null);
        });
    }

    public AuthResponse response(User user) {
        return AuthResponse.of(jwtService.generateToken(user), user, props.requireAdminMfa());
    }

    /** Conta bloqueada por tentativas → 429 com Retry-After. Usado no login e na reautenticação. */
    public void ensureNotLocked(User user) {
        LocalDateTime now = now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw lockedException(now, user.getLockedUntil());
        }
    }

    /** Senha/código errado no login ou na reautenticação: conta para o bloqueio e fica na auditoria. */
    public void registerFailure(User user, String reason) {
        registerFailure(user, now(), reason);
    }

    /** Reautenticação bem-sucedida também zera o contador, como o login. */
    public void clearFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    private static TooManyRequestsException lockedException(LocalDateTime now, LocalDateTime until) {
        return new TooManyRequestsException("Too many failed attempts. Try again later",
                Duration.between(now, until).toSeconds() + 1);
    }

    /** Mesmo custo (BCrypt), mesmas respostas e mesmo bloqueio de uma conta real. Sempre lança exceção. */
    private void failUnknownEmail(String email, String password, LocalDateTime now) {
        UnknownEmailAttempts current = unknownEmails.get(email);
        if (current != null && current.lockedUntil() != null && current.lockedUntil().isAfter(now)) {
            throw lockedException(now, current.lockedUntil());
        }
        passwordEncoder.matches(password, dummyHash);
        int failures = (current == null || current.lockedUntil() != null ? 0 : current.failures()) + 1;
        UnknownEmailAttempts next = failures >= props.login().maxFailedAttempts()
                ? new UnknownEmailAttempts(0, now.plusMinutes(props.login().lockMinutes()), now)
                : new UnknownEmailAttempts(failures, null, now);
        if (unknownEmails.size() >= MAX_TRACKED_UNKNOWN) {
            LocalDateTime staleBefore = now.minusMinutes(props.login().lockMinutes());
            unknownEmails.entrySet().removeIf(e -> e.getValue().lastAttempt().isBefore(staleBefore));
        }
        unknownEmails.put(email, next);
        auditService.recordIndependent(null, AuditService.LOGIN_FAILED, "USER", null,
                "unknown email " + AuditService.maskEmail(email));
        throw new BusinessException("Invalid credentials");
    }

    private void registerFailure(User user, LocalDateTime now, String reason) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= props.login().maxFailedAttempts()) {
            user.setLockedUntil(now.plusMinutes(props.login().lockMinutes()));
            user.setFailedLoginAttempts(0);
            auditService.recordIndependent(user, AuditService.ACCOUNT_LOCKED, "USER", user.getId(),
                    "locked for " + props.login().lockMinutes() + " min after " + attempts + " failures");
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);
        auditService.recordIndependent(user, AuditService.LOGIN_FAILED, "USER", user.getId(), reason);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
