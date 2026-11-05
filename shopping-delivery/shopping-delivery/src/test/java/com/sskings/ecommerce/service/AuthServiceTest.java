package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.LoginRequest;
import com.sskings.ecommerce.dto.request.RegisterRequest;
import com.sskings.ecommerce.dto.response.AuthResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    @DisplayName("register — happy path returns token")
    void register_success() {
        given(userRepository.existsByEmail("a@a.com")).willReturn(false);
        given(passwordEncoder.encode("secret")).willReturn("hashed");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtService.generateToken("a@a.com")).willReturn("jwt-token");

        AuthResponse resp = authService.register(
                new RegisterRequest("Alice", "a@a.com", "secret", null));

        assertThat(resp.token()).isEqualTo("jwt-token");
        assertThat(resp.email()).isEqualTo("a@a.com");
    }

    @Test
    @DisplayName("register — duplicate email throws BusinessException")
    void register_duplicateEmail_throws() {
        given(userRepository.existsByEmail("a@a.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Alice", "a@a.com", "secret", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email already in use");
    }

    @Test
    @DisplayName("login — happy path returns token")
    void login_success() {
        User user = User.builder().email("a@a.com").password("hashed").name("Alice").build();
        given(userRepository.findByEmail("a@a.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("secret", "hashed")).willReturn(true);
        given(jwtService.generateToken("a@a.com")).willReturn("jwt-token");

        AuthResponse resp = authService.login(new LoginRequest("a@a.com", "secret"));

        assertThat(resp.token()).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("login — wrong password throws BusinessException")
    void login_wrongPassword_throws() {
        User user = User.builder().email("a@a.com").password("hashed").build();
        given(userRepository.findByEmail("a@a.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@a.com", "wrong")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    @DisplayName("login — blocked account throws BusinessException and issues no token")
    void login_blockedUser_throws() {
        User user = User.builder().email("a@a.com").password("hashed").active(false).build();
        given(userRepository.findByEmail("a@a.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("secret", "hashed")).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@a.com", "secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account is blocked");
        org.mockito.Mockito.verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("login — user not found throws BusinessException")
    void login_userNotFound_throws() {
        given(userRepository.findByEmail("x@x.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@x.com", "pass")))
                .isInstanceOf(BusinessException.class);
    }
}
