package com.sskings.ecommerce.controller;

import com.sskings.ecommerce.dto.request.LoginRequest;
import com.sskings.ecommerce.dto.request.RegisterRequest;
import com.sskings.ecommerce.dto.response.AuthResponse;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.sskings.ecommerce.service.EmailVerificationService emailVerification;
    private final com.sskings.ecommerce.service.PasswordResetService passwordReset;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Confirma o e-mail pelo link (ADR-025). Link inválido, vencido ou já usado: 400 com a mesma mensagem. */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody com.sskings.ecommerce.dto.request.EmailTokenRequest request) {
        emailVerification.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    /** "Esqueci minha senha": sempre 202, exista a conta ou não (não revela quem tem conta). */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody com.sskings.ecommerce.dto.request.ForgotPasswordRequest request) {
        passwordReset.request(request.email());
        return ResponseEntity.accepted().build();
    }

    /** Nova senha pelo link do e-mail: derruba todas as sessões. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody com.sskings.ecommerce.dto.request.ResetPasswordRequest request) {
        passwordReset.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Invalida o token atual e todos os outros do usuário (tokenVersion++). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        authService.logout(user.getId());
        return ResponseEntity.noContent().build();
    }
}
