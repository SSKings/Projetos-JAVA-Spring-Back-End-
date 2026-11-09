package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.ChangePasswordRequest;
import com.sskings.ecommerce.dto.request.PasswordConfirmationRequest;
import com.sskings.ecommerce.dto.request.UpdateProfileRequest;
import com.sskings.ecommerce.dto.response.*;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Address;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.repository.*;
import com.sskings.ecommerce.security.CryptoService;
import com.sskings.ecommerce.security.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Direitos do titular (LGPD art. 18): acesso/portabilidade (exportação), correção (perfil) e eliminação
 * (anonimização), além do MFA. Pedidos e endereços usados em pedidos são mantidos para cumprimento de
 * obrigação legal/fiscal e defesa em processos (LGPD art. 16, I e art. 7º, VI), mas desvinculados da pessoa.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    static final String ISSUER = "ShopEasy";
    static final List<OrderStatus> IN_PROGRESS = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED);

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final CryptoService cryptoService;
    private final TrustedLoginSources trustedSources;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId) {
        return ProfileResponse.from(load(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = load(userId);
        user.setName(request.name().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        auditService.record(user, AuditService.PROFILE_UPDATED, "USER", user.getId(), null);
        return ProfileResponse.from(userRepository.save(user));
    }

    @Transactional
    public DataExportResponse exportData(Long userId) {
        User user = load(userId);
        auditService.record(user, AuditService.DATA_EXPORTED, "USER", user.getId(), null);
        List<DataExportResponse.SecurityEvent> events = auditLogRepository
                .findTop500ByActorIdOrderByCreatedAtDesc(userId).stream()
                .map(a -> new DataExportResponse.SecurityEvent(a.getCreatedAt(), a.getAction(), a.getIp()))
                .toList();
        return new DataExportResponse(LocalDateTime.now(clock), ProfileResponse.from(user),
                addressRepository.findAllByUserId(userId).stream().map(AddressResponse::from).toList(),
                orderRepository.findAllByUserIdWithItems(userId).stream().map(OrderResponse::from).toList(),
                events);
    }

    /**
     * Exclusão da conta = anonimização irreversível. Exige senha (e código MFA, se ativo).
     * noRollbackFor: a falha de reautenticação precisa ser gravada (contador de tentativas).
     */
    @Transactional(noRollbackFor = {BusinessException.class, com.sskings.ecommerce.exception.TooManyRequestsException.class})
    public void deleteAccount(Long userId, PasswordConfirmationRequest request) {
        User user = load(userId);
        reauthenticate(user, request);
        // Revisão 5, R5-6: sem isso a loja ficava sem admin e, no restart, o AdminSeeder recriava a conta com a
        // ADMIN_PASSWORD que ficou no .env.prod
        if (user.getRole() == com.sskings.ecommerce.model.enums.Role.ADMIN
                && userRepository.countByRoleAndActiveTrueAndAnonymizedAtIsNull(com.sskings.ecommerce.model.enums.Role.ADMIN) <= 1) {
            throw new BusinessException("The last active admin cannot delete the account. Promote another admin first");
        }
        if (orderRepository.existsByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw new BusinessException("Account has orders in progress");
        }
        cartItemRepository.deleteAllByUserId(userId);
        for (Address address : addressRepository.findAllByUserId(userId)) {
            if (!orderRepository.existsByAddressId(address.getId())) addressRepository.delete(address);
        }
        // Aleatório, não "removed-<id>": com o id previsível, alguém cadastrava o endereço antes e a exclusão da vítima
        // falhava para sempre (unique) — direito do titular bloqueado (revisão 4, R4-5). O cadastro também recusa .invalid.
        String anonymousEmail = "removed-" + java.util.UUID.randomUUID() + "@anonymized.invalid";
        user.setName("Usuário removido");
        user.setEmail(anonymousEmail);
        user.setPhone(null);
        user.setPassword(passwordEncoder.encode(randomToken()));
        user.setActive(false);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setAnonymizedAt(LocalDateTime.now(clock));
        user.revokeTokens();
        userRepository.save(user);
        trustedSources.forget(userId);   // IPs de onde a pessoa entrava também são dados pessoais
        auditLogRepository.replaceActorEmail(userId, anonymousEmail);
        auditService.record(user, AuditService.ACCOUNT_DELETED, "USER", userId, "anonymized");
    }

    /**
     * Troca de senha (revisão 3, M1): exige a senha atual (e o MFA, se ativo), encerra todas as sessões e devolve um
     * token novo. Sem isso, uma senha vazada — inclusive a ADMIN_PASSWORD inicial do .env.prod — não tinha como ser trocada.
     */
    @Transactional(noRollbackFor = {BusinessException.class, com.sskings.ecommerce.exception.TooManyRequestsException.class})
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = load(userId);
        reauthenticate(user, new PasswordConfirmationRequest(request.currentPassword(), request.mfaCode()));
        com.sskings.ecommerce.security.PasswordStrength.problem(request.newPassword(), user.getName(), user.getEmail())
                .ifPresent(p -> { throw new BusinessException(p); });
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("New password must be different from the current one");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.revokeTokens();
        userRepository.save(user);
        auditService.record(user, AuditService.PASSWORD_CHANGED, "USER", user.getId(), null);
        return authService.response(user);
    }

    // ---------- MFA ----------

    /**
     * Exige a senha (revisão 3, A1): só com o token, quem roubasse a sessão ativava o MFA com o app DELE e o dono,
     * sem o código, nunca mais entrava — o roubo de um token de 2 h virava sequestro permanente da conta.
     * Sem o segredo devolvido aqui, o /mfa/enable não tem como ser concluído por outra pessoa.
     */
    @Transactional(noRollbackFor = {BusinessException.class, com.sskings.ecommerce.exception.TooManyRequestsException.class})
    public MfaSetupResponse setupMfa(Long userId, PasswordConfirmationRequest request) {
        User user = load(userId);
        if (user.isMfaEnabled()) throw new BusinessException("MFA already enabled");
        reauthenticate(user, request);
        String secret = totpService.newSecret();
        user.setMfaSecret(cryptoService.encrypt(secret));
        userRepository.save(user);
        auditService.record(user, AuditService.MFA_SETUP_STARTED, "USER", user.getId(), null);
        return new MfaSetupResponse(secret, totpService.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    /** Confirma o código do app e ativa o MFA. Revoga sessões antigas e devolve um token novo. */
    @Transactional
    public AuthResponse enableMfa(Long userId, String code) {
        User user = load(userId);
        if (user.isMfaEnabled()) throw new BusinessException("MFA already enabled");
        if (user.getMfaSecret() == null) throw new BusinessException("Start the MFA setup first");
        var step = totpService.verify(cryptoService.decrypt(user.getMfaSecret()), code, null);
        if (step.isEmpty()) throw new BusinessException("Invalid MFA code");
        user.setMfaEnabled(true);
        user.setMfaLastStep(step.getAsLong());
        user.revokeTokens();
        userRepository.save(user);
        auditService.record(user, AuditService.MFA_ENABLED, "USER", user.getId(), null);
        return authService.response(user);
    }

    @Transactional(noRollbackFor = {BusinessException.class, com.sskings.ecommerce.exception.TooManyRequestsException.class})
    public AuthResponse disableMfa(Long userId, PasswordConfirmationRequest request) {
        User user = load(userId);
        if (!user.isMfaEnabled()) throw new BusinessException("MFA is not enabled");
        reauthenticate(user, request);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaLastStep(null);
        user.revokeTokens();
        userRepository.save(user);
        auditService.record(user, AuditService.MFA_DISABLED, "USER", user.getId(), null);
        return authService.response(user);
    }

    /**
     * Reautenticação (senha e, com MFA, código) para ações sensíveis: aqui e nas ações de admin que mudam papel ou
     * bloqueiam admins (AdminUserService, revisão 4, R4-3). Falhas contam por CONTA (não por IP) e bloqueiam só a
     * reautenticação: com o token roubado, trocar de IP não dá mais tentativas (R4-2). O código MFA é aceito de
     * forma atômica (R4-7).
     */
    public void reauthenticate(User user, PasswordConfirmationRequest request) {
        authService.ensureReauthAllowed(user);
        if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            authService.registerReauthFailure(user, "reauthentication: wrong password");
            throw new BusinessException("Invalid password");
        }
        if (user.isMfaEnabled()) {
            var step = request.mfaCode() == null ? java.util.OptionalLong.empty()
                    : totpService.verify(cryptoService.decrypt(user.getMfaSecret()), request.mfaCode(), user.getMfaLastStep());
            if (step.isEmpty() || !authService.claimMfaStep(user, step.getAsLong())) {
                authService.registerReauthFailure(user, "reauthentication: invalid MFA code");
                throw new BusinessException("Invalid MFA code");
            }
        }
        authService.clearReauthFailures(user);
    }

    /**
     * Step-up para ações de admin com efeito financeiro ou de privilégio (revisões 4 e 5). Sem a confirmação →
     * "Password confirmation required" (não conta como falha); confirmação errada conta para o bloqueio.
     */
    public void verifyStepUp(User user, StepUp stepUp) {
        if (stepUp == null) throw new BusinessException("Password confirmation required");
        if (!stepUp.mfaOnly()) {
            if (stepUp.password() == null || stepUp.password().isBlank()) {
                throw new BusinessException("Password confirmation required");
            }
            reauthenticate(user, new PasswordConfirmationRequest(stepUp.password(), stepUp.mfaCode()));
            return;
        }
        if (!user.isMfaEnabled()) throw new BusinessException("MFA is not enabled");
        authService.ensureReauthAllowed(user);
        var step = stepUp.mfaCode() == null || !stepUp.mfaCode().matches("\\d{6}") ? java.util.OptionalLong.empty()
                : totpService.verify(cryptoService.decrypt(user.getMfaSecret()), stepUp.mfaCode(), user.getMfaLastStep());
        if (step.isEmpty() || !authService.claimMfaStep(user, step.getAsLong())) {
            authService.registerReauthFailure(user, "step-up: invalid MFA code");
            throw new BusinessException("Invalid MFA code");
        }
        authService.clearReauthFailures(user);
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static String randomToken() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
}
