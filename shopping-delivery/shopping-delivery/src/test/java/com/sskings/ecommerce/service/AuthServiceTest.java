package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.LoginRequest;
import com.sskings.ecommerce.dto.request.RegisterRequest;
import com.sskings.ecommerce.dto.response.AuthResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.TooManyRequestsException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.CryptoService;
import com.sskings.ecommerce.security.JwtService;
import com.sskings.ecommerce.security.SecurityProperties;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final String KEY = java.util.Base64.getEncoder().encodeToString(new byte[32]);

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock AuditService auditService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TotpService totp = new TotpService(clock);
    private final CryptoService crypto = new CryptoService(KEY);
    private AuthService service;
    private User user;

    private static SecurityProperties props(boolean requireAdminMfa) {
        return new SecurityProperties(new SecurityProperties.Jwt("s", 120), KEY, requireAdminMfa,
                new SecurityProperties.Login(3, 15), new SecurityProperties.RateLimit(true, 20, 120));
    }

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, encoder, jwtService, totp, crypto, auditService, props(true), clock, "2026-11-05");
        user = User.builder().id(1L).name("Ana").email("ana@a.com").password(encoder.encode("senha123")).role(Role.CUSTOMER).build();
        given(userRepository.findByEmail("ana@a.com")).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtService.generateToken(any())).willReturn("jwt");
    }

    private AuthResponse login(String password) {
        return service.login(new LoginRequest("ana@a.com", password));
    }

    // ---------- cadastro ----------

    @Test
    @DisplayName("Cadastro normaliza o email, grava hash, versão e data do aceite dos termos")
    void register_success() {
        given(userRepository.existsByEmail("novo@a.com")).willReturn(false);

        AuthResponse resp = service.register(new RegisterRequest(" Bia Souza ", " Novo@A.com ", "senha123", null, true));

        assertThat(resp.token()).isEqualTo("jwt");
        then(userRepository).should().save(argThat(u -> u.getEmail().equals("novo@a.com")
                && u.getName().equals("Bia Souza")
                && encoder.matches("senha123", u.getPassword())
                && "2026-11-05".equals(u.getTermsVersion())
                && LOCAL_NOW.equals(u.getTermsAcceptedAt())));
        then(auditService).should().record(any(), eq(AuditService.REGISTER), eq("USER"), any(), contains("2026-11-05"));
    }

    @Test
    @DisplayName("Cadastro com email existente é recusado")
    void register_duplicate() {
        given(userRepository.existsByEmail("ana@a.com")).willReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana S", "ANA@a.com", "senha123", null, true)))
                .hasMessage("Email already in use");
    }

    // ---------- login ----------

    @Test
    @DisplayName("Login correto zera o contador de falhas e audita")
    void login_success() {
        user.setFailedLoginAttempts(2);
        AuthResponse resp = service.login(new LoginRequest(" ANA@a.com ", "senha123"));
        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.mfaSetupRequired()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        then(auditService).should().record(user, AuditService.LOGIN_SUCCESS, "USER", 1L, null);
    }

    @Test
    @DisplayName("Email inexistente: mesma mensagem e BCrypt executado mesmo assim (anti-enumeração por tempo)")
    void login_unknownEmail() {
        PasswordEncoder spy = spy(encoder);
        AuthService s = new AuthService(userRepository, spy, jwtService, totp, crypto, auditService, props(true), clock, "v");
        given(userRepository.findByEmail("x@x.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> s.login(new LoginRequest("x@x.com", "qualquer1")))
                .isInstanceOf(BusinessException.class).hasMessage("Invalid credentials");
        then(spy).should().matches(eq("qualquer1"), anyString());
        then(auditService).should().recordIndependent(isNull(), eq(AuditService.LOGIN_FAILED), any(), any(), contains("x***@x.com"));
    }

    @Test
    @DisplayName("Senha errada conta falha; na 3ª a conta fica bloqueada por 15 min, mesmo com a senha certa")
    void login_lockout() {
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");

        assertThat(user.getLockedUntil()).isEqualTo(LOCAL_NOW.plusMinutes(15));
        then(auditService).should().recordIndependent(eq(user), eq(AuditService.ACCOUNT_LOCKED), any(), any(), any());

        assertThatThrownBy(() -> login("senha123"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(901));
    }

    @Test
    @DisplayName("Bloqueio vencido libera o login")
    void login_lockExpired() {
        user.setLockedUntil(LOCAL_NOW.minusSeconds(1));
        assertThat(login("senha123").token()).isEqualTo("jwt");
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Conta bloqueada pelo admin (depois de validar a senha)")
    void login_blocked() {
        user.setActive(false);
        assertThatThrownBy(() -> login("senha123")).hasMessage("Account is blocked");
    }

    @Test
    @DisplayName("Admin sem MFA recebe mfaSetupRequired quando o MFA é obrigatório")
    void login_adminWithoutMfa() {
        user.setRole(Role.ADMIN);
        assertThat(login("senha123").mfaSetupRequired()).isTrue();
        AuthService optional = new AuthService(userRepository, encoder, jwtService, totp, crypto, auditService, props(false), clock, "v");
        assertThat(optional.login(new LoginRequest("ana@a.com", "senha123")).mfaSetupRequired()).isFalse();
    }

    // ---------- MFA ----------

    private String enableMfa() {
        String secret = totp.newSecret();
        user.setMfaEnabled(true);
        user.setMfaSecret(crypto.encrypt(secret));
        return secret;
    }

    @Test
    @DisplayName("MFA: sem código pede o código; código certo entra e não pode ser reutilizado")
    void login_mfa() {
        String secret = enableMfa();
        assertThatThrownBy(() -> login("senha123")).hasMessage("MFA code required");
        assertThat(user.getFailedLoginAttempts()).isZero(); // pedir o código não é falha

        String code = totp.currentCode(secret);
        AuthResponse resp = service.login(new LoginRequest("ana@a.com", "senha123", code));
        assertThat(resp.mfaEnabled()).isTrue();
        assertThat(user.getMfaLastStep()).isNotNull();

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@a.com", "senha123", code)))
                .hasMessage("Invalid MFA code");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("MFA: código errado conta como falha")
    void login_mfaWrongCode() {
        enableMfa();
        assertThatThrownBy(() -> service.login(new LoginRequest("ana@a.com", "senha123", "000000")))
                .hasMessage("Invalid MFA code");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    // ---------- logout ----------

    @Test
    @DisplayName("Logout incrementa a versão do token (revoga todos os tokens)")
    void logout() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        service.logout(1L);
        assertThat(user.getTokenVersion()).isEqualTo(1);
        then(auditService).should().record(user, AuditService.LOGOUT, "USER", 1L, null);

        given(userRepository.findById(9L)).willReturn(Optional.empty());
        service.logout(9L); // usuário inexistente: nada acontece
    }

    // ---------- revisão 2: bloqueio não pode revelar quem tem conta (R3) ----------

    private java.util.List<String> attempts(String email, int n) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            try {
                service.login(new LoginRequest(email, "errada" + i));
                out.add("200");
            } catch (TooManyRequestsException e) {
                out.add("429");
            } catch (BusinessException e) {
                out.add("400:" + e.getMessage());
            }
        }
        return out;
    }

    @Test
    @DisplayName("R3: email inexistente recebe exatamente as mesmas respostas (inclusive o 429) que uma conta real")
    void lockout_isSymmetricForUnknownEmails() {
        given(userRepository.findByEmail("fantasma@a.com")).willReturn(Optional.empty());

        java.util.List<String> existing = attempts("ana@a.com", 4);      // limite de teste = 3 falhas
        java.util.List<String> unknown = attempts("fantasma@a.com", 4);

        assertThat(unknown).isEqualTo(existing)
                .containsExactly("400:Invalid credentials", "400:Invalid credentials", "400:Invalid credentials", "429");
        assertThatThrownBy(() -> service.login(new LoginRequest(" FANTASMA@a.com ", "x")))   // normalizado
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(901));
    }

    @Test
    @DisplayName("R3: bloqueio de email inexistente vence em 15 min e a contagem recomeça do zero")
    void lockout_unknownEmailExpiresAndResets() {
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock moving = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        service = new AuthService(userRepository, encoder, jwtService, totp, crypto, auditService, props(true), moving, "v");
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThat(attempts("x@a.com", 4)).last().isEqualTo("429");
        now.set(NOW.plusSeconds(15 * 60 + 1));                       // bloqueio venceu
        assertThat(attempts("x@a.com", 3)).containsExactly(
                "400:Invalid credentials", "400:Invalid credentials", "400:Invalid credentials");  // 3ª bloqueia de novo
        assertThat(attempts("x@a.com", 1)).containsExactly("429");
        assertThat(attempts("y@a.com", 1)).containsExactly("400:Invalid credentials");   // outro email não é afetado
    }

    @Test
    @DisplayName("R4: API pública de bloqueio usada pela reautenticação")
    void reauthHelpers() {
        user.setLockedUntil(LOCAL_NOW.plusMinutes(1));
        assertThatThrownBy(() -> service.ensureNotLocked(user)).isInstanceOf(TooManyRequestsException.class);
        service.clearFailures(user);
        service.ensureNotLocked(user);   // não lança
        service.registerFailure(user, "reauthentication: wrong password");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        then(auditService).should().recordIndependent(user, AuditService.LOGIN_FAILED, "USER", 1L, "reauthentication: wrong password");
    }
}
