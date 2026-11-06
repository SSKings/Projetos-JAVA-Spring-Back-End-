package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.PasswordConfirmationRequest;
import com.sskings.ecommerce.dto.request.UpdateProfileRequest;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Address;
import com.sskings.ecommerce.model.AuditLog;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.*;
import com.sskings.ecommerce.security.CryptoService;
import com.sskings.ecommerce.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.*;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    @Mock UserRepository userRepository;
    @Mock AddressRepository addressRepository;
    @Mock OrderRepository orderRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock AuditService auditService;
    @Mock AuthService authService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TotpService totp = new TotpService(clock);
    private final CryptoService crypto = new CryptoService(Base64.getEncoder().encodeToString(new byte[32]));
    private AccountService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, addressRepository, orderRepository, cartItemRepository,
                auditLogRepository, auditService, authService, encoder, totp, crypto, clock);
        user = User.builder().id(1L).name("Ana Souza").email("ana@a.com").phone("11999999999")
                .password(encoder.encode("senha123")).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Perfil: leitura e correção (nome/telefone), com auditoria; usuário inexistente → 404")
    void profile() {
        assertThat(service.profile(1L).email()).isEqualTo("ana@a.com");
        var updated = service.updateProfile(1L, new UpdateProfileRequest(" Ana Lima ", " "));
        assertThat(updated.name()).isEqualTo("Ana Lima");
        assertThat(updated.phone()).isNull();
        assertThat(service.updateProfile(1L, new UpdateProfileRequest("Ana", "11 9")).phone()).isEqualTo("11 9");
        then(auditService).should(times(2)).record(user, AuditService.PROFILE_UPDATED, "USER", 1L, null);
        given(userRepository.findById(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.profile(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Exportação traz perfil, endereços, pedidos e eventos de segurança do titular")
    void export() {
        given(addressRepository.findAllByUserId(1L)).willReturn(List.of(Address.builder().id(3L).street("Rua A").build()));
        given(orderRepository.findAllByUserIdWithItems(1L)).willReturn(List.of());
        given(auditLogRepository.findTop500ByActorIdOrderByCreatedAtDesc(1L)).willReturn(List.of(
                AuditLog.builder().action("LOGIN_SUCCESS").ip("1.2.3.4").createdAt(LocalDateTime.now(clock)).build()));

        var data = service.exportData(1L);

        assertThat(data.profile().email()).isEqualTo("ana@a.com");
        assertThat(data.addresses()).extracting("street").containsExactly("Rua A");
        assertThat(data.securityEvents()).singleElement().extracting("action").isEqualTo("LOGIN_SUCCESS");
        assertThat(data.generatedAt()).isEqualTo(LocalDateTime.now(clock));
        then(auditService).should().record(user, AuditService.DATA_EXPORTED, "USER", 1L, null);
    }

    @Test
    @DisplayName("Exclusão anonimiza a conta, remove carrinho e endereços sem pedido e revoga sessões")
    void delete() {
        Address used = Address.builder().id(3L).build();
        Address unused = Address.builder().id(4L).build();
        given(addressRepository.findAllByUserId(1L)).willReturn(List.of(used, unused));
        given(orderRepository.existsByAddressId(3L)).willReturn(true);

        service.deleteAccount(1L, new PasswordConfirmationRequest("senha123", null));

        assertThat(user.getEmail()).isEqualTo("removed-1@anonymized.invalid");
        assertThat(user.getName()).isEqualTo("Usuário removido");
        assertThat(user.getPhone()).isNull();
        assertThat(user.isActive()).isFalse();
        assertThat(encoder.matches("senha123", user.getPassword())).isFalse();
        assertThat(user.getAnonymizedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(user.getTokenVersion()).isEqualTo(1);
        then(cartItemRepository).should().deleteAllByUserId(1L);
        then(addressRepository).should().delete(unused);
        then(addressRepository).should(never()).delete(used);   // mantido: vinculado a pedido (obrigação legal)
        then(auditLogRepository).should().replaceActorEmail(1L, "removed-1@anonymized.invalid");
    }

    @Test
    @DisplayName("Exclusão exige senha correta e nenhum pedido em andamento")
    void delete_rules() {
        assertThatThrownBy(() -> service.deleteAccount(1L, new PasswordConfirmationRequest("errada", null)))
                .hasMessage("Invalid password");
        given(orderRepository.existsByUserIdAndStatusIn(eq(1L), anyCollection())).willReturn(true);
        assertThatThrownBy(() -> service.deleteAccount(1L, new PasswordConfirmationRequest("senha123", null)))
                .isInstanceOf(BusinessException.class).hasMessage("Account has orders in progress");
        assertThat(user.getEmail()).isEqualTo("ana@a.com");
    }

    @Test
    @DisplayName("MFA: setup cifra o segredo; enable confere o código, revoga sessões e devolve token novo")
    void mfaEnable() {
        var setup = service.setupMfa(1L);
        assertThat(setup.otpauthUri()).startsWith("otpauth://totp/ShopEasy").contains("secret=" + setup.secret());
        assertThat(user.getMfaSecret()).isNotEqualTo(setup.secret());
        assertThat(crypto.decrypt(user.getMfaSecret())).isEqualTo(setup.secret());

        assertThatThrownBy(() -> service.enableMfa(1L, "000000")).hasMessage("Invalid MFA code");
        service.enableMfa(1L, totp.currentCode(setup.secret()));
        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        then(authService).should().response(user);
        then(auditService).should().record(user, AuditService.MFA_ENABLED, "USER", 1L, null);

        assertThatThrownBy(() -> service.setupMfa(1L)).hasMessage("MFA already enabled");
        assertThatThrownBy(() -> service.enableMfa(1L, "123456")).hasMessage("MFA already enabled");
    }

    @Test
    @DisplayName("MFA: enable sem setup; disable exige senha e código; exclusão com MFA exige código")
    void mfaDisableAndReauth() {
        assertThatThrownBy(() -> service.enableMfa(1L, "123456")).hasMessage("Start the MFA setup first");
        assertThatThrownBy(() -> service.disableMfa(1L, new PasswordConfirmationRequest("senha123", null)))
                .hasMessage("MFA is not enabled");

        String secret = totp.newSecret();
        user.setMfaEnabled(true);
        user.setMfaSecret(crypto.encrypt(secret));
        assertThatThrownBy(() -> service.disableMfa(1L, new PasswordConfirmationRequest("senha123", null)))
                .hasMessage("Invalid MFA code");
        assertThatThrownBy(() -> service.deleteAccount(1L, new PasswordConfirmationRequest("senha123", "000000")))
                .hasMessage("Invalid MFA code");

        service.disableMfa(1L, new PasswordConfirmationRequest("senha123", totp.currentCode(secret)));
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getMfaSecret()).isNull();
        then(auditService).should().record(user, AuditService.MFA_DISABLED, "USER", 1L, null);
    }

    // ---------- revisão 2: reautenticação conta para o bloqueio (R4) ----------

    @Test
    @DisplayName("R4: senha errada na reautenticação registra falha; conta bloqueada recebe 429; sucesso zera")
    void reauthentication_countsFailures() {
        assertThatThrownBy(() -> service.deleteAccount(1L, new PasswordConfirmationRequest("errada", null)))
                .hasMessage("Invalid password");
        then(authService).should().ensureNotLocked(user);
        then(authService).should().registerFailure(user, "reauthentication: wrong password");

        willThrow(new com.sskings.ecommerce.exception.TooManyRequestsException("Too many failed attempts. Try again later", 60))
                .given(authService).ensureNotLocked(user);
        assertThatThrownBy(() -> service.deleteAccount(1L, new PasswordConfirmationRequest("senha123", null)))
                .isInstanceOf(com.sskings.ecommerce.exception.TooManyRequestsException.class);
        assertThat(user.getEmail()).isEqualTo("ana@a.com");   // nada foi apagado

        willDoNothing().given(authService).ensureNotLocked(user);
        service.deleteAccount(1L, new PasswordConfirmationRequest("senha123", null));
        then(authService).should().clearFailures(user);
    }

    @Test
    @DisplayName("R4: código MFA errado na reautenticação também conta como falha")
    void reauthentication_mfaFailureCounts() {
        user.setMfaEnabled(true);
        user.setMfaSecret(crypto.encrypt(totp.newSecret()));
        assertThatThrownBy(() -> service.disableMfa(1L, new PasswordConfirmationRequest("senha123", "000000")))
                .hasMessage("Invalid MFA code");
        then(authService).should().registerFailure(user, "reauthentication: invalid MFA code");
    }
}
