package com.sskings.ecommerce.service;

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
        if (orderRepository.existsByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw new BusinessException("Account has orders in progress");
        }
        cartItemRepository.deleteAllByUserId(userId);
        for (Address address : addressRepository.findAllByUserId(userId)) {
            if (!orderRepository.existsByAddressId(address.getId())) addressRepository.delete(address);
        }
        String anonymousEmail = "removed-" + user.getId() + "@anonymized.invalid";
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
        auditLogRepository.replaceActorEmail(userId, anonymousEmail);
        auditService.record(user, AuditService.ACCOUNT_DELETED, "USER", userId, "anonymized");
    }

    // ---------- MFA ----------

    @Transactional
    public MfaSetupResponse setupMfa(Long userId) {
        User user = load(userId);
        if (user.isMfaEnabled()) throw new BusinessException("MFA already enabled");
        String secret = totpService.newSecret();
        user.setMfaSecret(cryptoService.encrypt(secret));
        userRepository.save(user);
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
     * Reautenticação com as mesmas regras do login: conta bloqueada → 429; senha ou código errado conta para o
     * bloqueio. Sem isso, um token roubado permitia testar senhas sem limite (revisão 2, R4).
     */
    private void reauthenticate(User user, PasswordConfirmationRequest request) {
        authService.ensureNotLocked(user);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            authService.registerFailure(user, "reauthentication: wrong password");
            throw new BusinessException("Invalid password");
        }
        if (user.isMfaEnabled()) {
            var step = request.mfaCode() == null ? java.util.OptionalLong.empty()
                    : totpService.verify(cryptoService.decrypt(user.getMfaSecret()), request.mfaCode(), user.getMfaLastStep());
            if (step.isEmpty()) {
                authService.registerFailure(user, "reauthentication: invalid MFA code");
                throw new BusinessException("Invalid MFA code");
            }
            user.setMfaLastStep(step.getAsLong());
        }
        authService.clearFailures(user);
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
