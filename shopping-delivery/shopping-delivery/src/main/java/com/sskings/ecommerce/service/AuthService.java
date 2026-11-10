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
    private final TrustedLoginSources trustedSources;
    private final RegistrationLimiter registrations;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       TotpService totpService, CryptoService cryptoService, AuditService auditService,
                       SecurityProperties props, Clock clock, @Value("${app.terms-version}") String termsVersion,
                       LoginAttemptService attempts, TrustedLoginSources trustedSources, RegistrationLimiter registrations,
                       org.springframework.context.ApplicationEventPublisher events) {
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
        this.trustedSources = trustedSources;
        this.registrations = registrations;
        this.events = events;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String source = LoginAttemptService.currentSource();
        registrations.acquire(source);
        // Senha fácil de adivinhar (revisão 5, R5-1); antes de olhar o email, para a resposta não depender dele
        com.sskings.ecommerce.security.PasswordStrength.problem(request.password(), request.name(), email)
                .ifPresent(p -> { throw new BusinessException(p); });
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
        events.publishEvent(new com.sskings.ecommerce.event.AccountEvent.Registered(user.getId()));   // link de confirmação
        return response(user);
    }

    /** Falhas não fazem rollback: o contador de tentativas precisa ser gravado. */
    @Transactional(noRollbackFor = {BusinessException.class, TooManyRequestsException.class})
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String source = LoginAttemptService.currentSource();
        Optional<User> found = userRepository.findByEmail(email);
        // Origem confiável = a conta já entrou daqui; durante um ataque distribuído só ela segue tentando (R4-1)
        attempts.ensureLoginAllowed(email, found.isPresent() && trustedSources.isTrusted(found.get().getId(), source));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);   // mesmo custo de uma conta real
            loginFailure(null, email, "unknown email " + AuditService.maskEmail(email));
            throw new BusinessException("Invalid credentials");
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginFailure(user, email, "wrong password");
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
            if (step.isEmpty() || !claimMfaStep(user, step.getAsLong())) {
                loginFailure(user, email, "invalid MFA code");
                throw new BusinessException("Invalid MFA code");
            }
        }
        attempts.clearLogin(email);
        userRepository.findByIdForUpdate(user.getId());   // serializa logins simultâneos ao gravar a origem
        trustedSources.recordSuccess(user.getId(), source);
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

    // ---------- reautenticação (senha/código numa sessão já aberta): contada por CONTA, não por IP (R4-2) ----------

    /** Reautenticação desta conta bloqueada → 429. Usado pelo AccountService e pela sessão do bot do Telegram. */
    public void ensureReauthAllowed(User user) {
        attempts.ensureReauthAllowed(user.getId());
    }

    /** Senha/código errado na reautenticação: conta para o bloqueio da reautenticação e fica na auditoria. */
    public void registerReauthFailure(User user, String reason) {
        if (attempts.registerReauthFailure(user.getId())) {
            auditService.recordIndependent(user, AuditService.ACCOUNT_LOCKED, "USER", user.getId(),
                    "reauthentication locked for " + props.login().lockMinutes() + " min after "
                            + props.login().maxFailedAttempts() + " failures");
        }
        auditService.recordIndependent(user, AuditService.LOGIN_FAILED, "USER", user.getId(), reason);
    }

    public void clearReauthFailures(User user) {
        attempts.clearReauth(user.getId());
    }

    /**
     * Aceita o passo TOTP de forma atômica (UPDATE condicional): o mesmo código não serve duas vezes nem em
     * requisições simultâneas (R4-7). Não regrava a entidade inteira (R4-6).
     */
    public boolean claimMfaStep(User user, long step) {
        if (userRepository.claimMfaStep(user.getId(), step) == 0) return false;
        user.setMfaLastStep(step);
        return true;
    }

    private void loginFailure(User user, String email, String reason) {
        LoginAttemptService.Failure failure = attempts.registerLoginFailure(email);
        Long id = user == null ? null : user.getId();
        if (failure.lockedNow() && user != null) {
            auditService.recordIndependent(user, AuditService.ACCOUNT_LOCKED, "USER", id,
                    "source " + LoginAttemptService.currentSource() + " locked for " + props.login().lockMinutes()
                            + " min after " + props.login().maxFailedAttempts() + " failures");
        }
        if (failure.attackStarted()) {
            String details = LoginAttemptService.ACCOUNT_BUDGET + " failures from several sources on "
                    + AuditService.maskEmail(email) + "; only trusted sources can try until the window ends";
            auditService.recordIndependent(user, AuditService.LOGIN_ATTACK_SUSPECTED, "USER", id, details);
            events.publishEvent(new com.sskings.ecommerce.event.SecurityAlertEvent(
                    "Ataque de senha em andamento contra " + AuditService.maskEmail(email) + ": "
                            + LoginAttemptService.ACCOUNT_BUDGET + " falhas de origens diferentes em "
                            + props.login().lockMinutes() + " min. Novas tentativas só de onde a conta já entrou."));
        }
        auditService.recordIndependent(user, AuditService.LOGIN_FAILED, "USER", id, reason);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
