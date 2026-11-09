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
    /** Bloqueio por email + IP; a mesma regra vale para emails inexistentes (revisão 2, R3; revisão 3, A2). */
    private final LoginAttemptService attempts;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       TotpService totpService, CryptoService cryptoService, AuditService auditService,
                       SecurityProperties props, Clock clock, @Value("${app.terms-version}") String termsVersion,
                       LoginAttemptService attempts) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
        this.props = props;
        this.clock = clock;
        this.termsVersion = termsVersion;
        this.attempts = attempts;
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
        String email = normalizeEmail(request.email());
        attempts.ensureNotLocked(email);
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);   // mesmo custo de uma conta real
            fail(null, email, "unknown email " + AuditService.maskEmail(email));
            throw new BusinessException("Invalid credentials");
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            registerFailure(user, "wrong password");
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
                registerFailure(user, "invalid MFA code");
                throw new BusinessException("Invalid MFA code");
            }
            user.setMfaLastStep(step.getAsLong());
        }
        attempts.clear(email);
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

    /** Este IP bloqueado para esta conta → 429 com Retry-After. Usado no login e na reautenticação. */
    public void ensureNotLocked(User user) {
        attempts.ensureNotLocked(user.getEmail());
    }

    /** Senha/código errado no login ou na reautenticação: conta para o bloqueio e fica na auditoria. */
    public void registerFailure(User user, String reason) {
        fail(user, user.getEmail(), reason);
    }

    /** Reautenticação bem-sucedida também zera o contador, como o login. */
    public void clearFailures(User user) {
        attempts.clear(user.getEmail());
    }

    private void fail(User user, String email, String reason) {
        LoginAttemptService.Failure failure = attempts.registerFailure(email);
        Long id = user == null ? null : user.getId();
        if (failure.lockedNow() && user != null) {
            auditService.recordIndependent(user, AuditService.ACCOUNT_LOCKED, "USER", id,
                    "IP locked for " + props.login().lockMinutes() + " min after "
                            + props.login().maxFailedAttempts() + " failures");
        }
        if (failure.attackSuspected()) {
            auditService.recordIndependent(user, AuditService.LOGIN_ATTACK_SUSPECTED, "USER", id,
                    LoginAttemptService.ATTACK_THRESHOLD + " failures from several IPs on " + AuditService.maskEmail(email));
        }
        auditService.recordIndependent(user, AuditService.LOGIN_FAILED, "USER", id, reason);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
