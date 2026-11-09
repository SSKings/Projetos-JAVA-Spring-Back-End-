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
    @Mock TrustedLoginSources trustedSources;
    @Mock org.springframework.context.ApplicationEventPublisher events;

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

    private AuthService newService(PasswordEncoder enc, boolean requireAdminMfa, Clock c) {
        SecurityProperties p = props(requireAdminMfa);
        return new AuthService(userRepository, enc, jwtService, totp, crypto, auditService, p, c, "2026-11-05",
                new LoginAttemptService(p, c), trustedSources, new RegistrationLimiter(1000, c), events);
    }

    /** Simula a requisição vindo de um IP (o bloqueio é por email + IP). */
    private static void fromIp(String ip) {
        org.springframework.mock.web.MockHttpServletRequest req = new org.springframework.mock.web.MockHttpServletRequest();
        req.setRemoteAddr(ip);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(req));
    }

    @org.junit.jupiter.api.AfterEach
    void clearRequest() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @BeforeEach
    void setUp() {
        fromIp("203.0.113.10");
        service = newService(encoder, true, clock);
        user = User.builder().id(1L).name("Ana").email("ana@a.com").password(encoder.encode("senha123")).role(Role.CUSTOMER).build();
        given(userRepository.findByEmail("ana@a.com")).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtService.generateToken(any())).willReturn("jwt");
        // UPDATE condicional do banco: aceita só passo TOTP mais novo que o último gravado
        given(userRepository.claimMfaStep(eq(1L), anyLong())).willAnswer(inv -> {
            long step = inv.getArgument(1);
            return user.getMfaLastStep() == null || user.getMfaLastStep() < step ? 1 : 0;
        });
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
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        AuthResponse resp = service.login(new LoginRequest(" ANA@a.com ", "senha123"));
        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.mfaSetupRequired()).isFalse();
        // contador zerado: mais 2 erros (limite de teste = 3) ainda não bloqueiam
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        assertThat(login("senha123").token()).isEqualTo("jwt");
        then(auditService).should(times(2)).record(user, AuditService.LOGIN_SUCCESS, "USER", 1L, null);
    }

    @Test
    @DisplayName("Email inexistente: mesma mensagem e BCrypt executado mesmo assim (anti-enumeração por tempo)")
    void login_unknownEmail() {
        PasswordEncoder spy = spy(encoder);
        AuthService s = newService(spy, true, clock);
        given(userRepository.findByEmail("x@x.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> s.login(new LoginRequest("x@x.com", "qualquer1")))
                .isInstanceOf(BusinessException.class).hasMessage("Invalid credentials");
        then(spy).should().matches(eq("qualquer1"), anyString());
        then(auditService).should().recordIndependent(isNull(), eq(AuditService.LOGIN_FAILED), any(), any(), contains("x***@x.com"));
    }

    @Test
    @DisplayName("Senha errada conta falha; na 3ª este IP fica bloqueado por 15 min, mesmo com a senha certa")
    void login_lockout() {
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        then(auditService).should(never()).recordIndependent(any(), eq(AuditService.ACCOUNT_LOCKED), any(), any(), any());
        assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");

        then(auditService).should().recordIndependent(eq(user), eq(AuditService.ACCOUNT_LOCKED), any(), any(), contains("locked for 15 min"));

        assertThatThrownBy(() -> login("senha123"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(901));
    }

    @Test
    @DisplayName("Bloqueio vencido libera o login")
    void login_lockExpired() {
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(NOW);
        service = newService(encoder, true, movingClock(now));
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> login("errada")).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> login("senha123")).isInstanceOf(TooManyRequestsException.class);
        now.set(NOW.plusSeconds(15 * 60 + 1));
        assertThat(login("senha123").token()).isEqualTo("jwt");
    }

    private static Clock movingClock(java.util.concurrent.atomic.AtomicReference<Instant> now) {
        return new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
    }

    // ---------- revisão 3, A2: ninguém consegue trancar o dono da conta ----------

    @Test
    @DisplayName("A2: falhas de um atacante bloqueiam só o IP dele — o dono, de outro IP, continua entrando")
    void lockout_isPerIp_ownerNotLockedOut() {
        fromIp("198.51.100.66");                                           // atacante
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> login("chute")).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> login("senha123")).isInstanceOf(TooManyRequestsException.class);

        fromIp("203.0.113.10");                                            // dono, em casa
        assertThat(login("senha123").token()).isEqualTo("jwt");
        fromIp("2001:db8::1");                                             // dono, no celular
        assertThat(login("senha123").token()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("A2: acerto do atacante em um IP não apaga o bloqueio de outro IP")
    void lockout_successClearsOnlyThatIp() {
        fromIp("198.51.100.1");
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> login("x")).hasMessage("Invalid credentials");
        fromIp("198.51.100.2");
        assertThat(login("senha123").token()).isEqualTo("jwt");
        fromIp("198.51.100.1");
        assertThatThrownBy(() -> login("senha123")).isInstanceOf(TooManyRequestsException.class);
    }

    // ---------- revisão 4, R4-1: trocar de IP não pode dar tentativas ilimitadas ----------

    @Test
    @DisplayName("R4-1: estourado o teto da conta, novas origens recebem 429 sem testar a senha; o dono, de onde já entrou, entra")
    void distributedAttack_budgetThenOnlyTrustedSources() {
        given(trustedSources.isTrusted(1L, "203.0.113.10")).willReturn(true);
        for (int i = 0; i < LoginAttemptService.ACCOUNT_BUDGET; i++) {
            fromIp("198.51.100." + i);
            assertThatThrownBy(() -> login("chute")).hasMessage("Invalid credentials");
        }
        then(auditService).should(times(1)).recordIndependent(eq(user), eq(AuditService.LOGIN_ATTACK_SUSPECTED),
                eq("USER"), eq(1L), contains("a***@a.com"));
        then(events).should().publishEvent(any(com.sskings.ecommerce.event.SecurityAlertEvent.class));

        fromIp("198.51.100.250");                                          // origem nova, mesmo com a senha CERTA
        assertThatThrownBy(() -> login("senha123")).isInstanceOf(TooManyRequestsException.class);
        fromIp("2001:db8:77::1");
        assertThatThrownBy(() -> login("senha123")).isInstanceOf(TooManyRequestsException.class);

        fromIp("203.0.113.10");                                            // casa do dono
        assertThat(login("senha123").token()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("R4-1: email inexistente recebe o mesmo 429 do teto (o ataque distribuído não revela quem tem conta)")
    void distributedAttack_symmetricForUnknownEmail() {
        given(userRepository.findByEmail("fantasma@a.com")).willReturn(Optional.empty());
        java.util.List<String> real = new java.util.ArrayList<>(), ghost = new java.util.ArrayList<>();
        for (int i = 0; i < LoginAttemptService.ACCOUNT_BUDGET + 2; i++) {
            fromIp("198.51.100." + i);
            real.addAll(attempts("ana@a.com", 1));
            fromIp("192.0.2." + i);
            ghost.addAll(attempts("fantasma@a.com", 1));
        }
        assertThat(ghost).isEqualTo(real);
        assertThat(real).endsWith("429", "429");
    }

    @Test
    @DisplayName("R4-1: login com sucesso grava a origem como confiável (com a linha do usuário travada)")
    void successRecordsTrustedSource() {
        fromIp("2001:db8:5:6::abcd");
        login("senha123");
        then(userRepository).should().findByIdForUpdate(1L);
        then(trustedSources).should().recordSuccess(1L, "2001:db8:5:6::/64");
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
        AuthService optional = newService(encoder, false, clock);
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
        then(auditService).should(never()).recordIndependent(any(), eq(AuditService.LOGIN_FAILED), any(), any(), any()); // pedir o código não é falha

        String code = totp.currentCode(secret);
        AuthResponse resp = service.login(new LoginRequest("ana@a.com", "senha123", code));
        assertThat(resp.mfaEnabled()).isTrue();
        assertThat(user.getMfaLastStep()).isNotNull();

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@a.com", "senha123", code)))
                .hasMessage("Invalid MFA code");
        then(auditService).should().recordIndependent(user, AuditService.LOGIN_FAILED, "USER", 1L, "invalid MFA code");
    }

    @Test
    @DisplayName("MFA: código errado conta como falha")
    void login_mfaWrongCode() {
        enableMfa();
        assertThatThrownBy(() -> service.login(new LoginRequest("ana@a.com", "senha123", "000000")))
                .hasMessage("Invalid MFA code");
        then(auditService).should().recordIndependent(user, AuditService.LOGIN_FAILED, "USER", 1L, "invalid MFA code");
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
        service = newService(encoder, true, movingClock(now));
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThat(attempts("x@a.com", 4)).last().isEqualTo("429");
        now.set(NOW.plusSeconds(15 * 60 + 1));                       // bloqueio venceu
        assertThat(attempts("x@a.com", 3)).containsExactly(
                "400:Invalid credentials", "400:Invalid credentials", "400:Invalid credentials");  // 3ª bloqueia de novo
        assertThat(attempts("x@a.com", 1)).containsExactly("429");
        assertThat(attempts("y@a.com", 1)).containsExactly("400:Invalid credentials");   // outro email não é afetado
    }

    @Test
    @DisplayName("R4-2: reautenticação conta por CONTA — trocar de IP não dá tentativas novas; não tranca o login")
    void reauthHelpers() {
        for (int i = 0; i < 3; i++) {
            fromIp("198.51.100." + i);
            service.registerReauthFailure(user, "reauthentication: wrong password");
        }
        fromIp("192.0.2.77");
        assertThatThrownBy(() -> service.ensureReauthAllowed(user)).isInstanceOf(TooManyRequestsException.class);
        then(auditService).should().recordIndependent(eq(user), eq(AuditService.ACCOUNT_LOCKED), any(), any(), contains("reauthentication locked"));
        assertThat(login("senha123").token()).isEqualTo("jwt");             // o login do dono continua
        service.clearReauthFailures(user);
        service.ensureReauthAllowed(user);   // não lança
        then(auditService).should(times(3)).recordIndependent(user, AuditService.LOGIN_FAILED, "USER", 1L, "reauthentication: wrong password");
    }

    @Test
    @DisplayName("R4-7: código MFA já aceito por outra requisição (UPDATE condicional devolve 0) é recusado")
    void mfaClaimIsAtomic() {
        String secret = totp.newSecret();
        user.setMfaEnabled(true);
        user.setMfaSecret(crypto.encrypt(secret));
        given(userRepository.claimMfaStep(eq(1L), anyLong())).willReturn(0);   // a outra requisição gravou antes
        assertThatThrownBy(() -> service.login(new LoginRequest("ana@a.com", "senha123", totp.currentCode(secret))))
                .hasMessage("Invalid MFA code");
        assertThat(user.getMfaLastStep()).isNull();
    }
}
