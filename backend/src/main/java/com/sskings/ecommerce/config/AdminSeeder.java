package com.sskings.ecommerce.config;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garante que exista um administrador inicial. As credenciais vêm de app.admin.* — em produção,
 * ADMIN_EMAIL / ADMIN_PASSWORD obrigatórios e validados (ProductionConfigValidator). O admin precisa
 * ativar o MFA no primeiro acesso antes de usar /api/admin/**.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.name}")     private String name;
    @Value("${app.admin.email}")    private String email;
    @Value("${app.admin.password}") private String password;

    @Override
    public void run(String... args) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(normalized)) {
            return;
        }
        String effectivePassword = password;
        if (effectivePassword == null || effectivePassword.isBlank()) {
            // Só acontece fora de produção: em prod o ProductionConfigValidator exige ADMIN_PASSWORD forte.
            effectivePassword = generatePassword();
            log.warn("\n==========================================================\n"
                    + " Senha do admin de desenvolvimento ({}): {}\n"
                    + " Gerada neste start; defina ADMIN_PASSWORD para fixar.\n"
                    + "==========================================================", normalized, effectivePassword);
        }
        userRepository.save(User.builder()
                .name(name)
                .email(normalized)
                .password(passwordEncoder.encode(effectivePassword))
                .role(Role.ADMIN)
                .emailVerifiedAt(java.time.LocalDateTime.now())   // e-mail definido por quem opera a loja (ADMIN_EMAIL)
                .build());
        log.info("Admin user created: {}", email);
    }

    /** 16 caracteres de letras e números (SecureRandom), sempre com os dois tipos. */
    static String generatePassword() {
        String letters = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
        String digits = "23456789";
        String all = letters + digits;
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(letters.charAt(random.nextInt(letters.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        for (int i = 2; i < 16; i++) sb.append(all.charAt(random.nextInt(all.length())));
        return sb.toString();
    }
}
