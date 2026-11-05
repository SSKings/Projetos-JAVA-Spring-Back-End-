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
        userRepository.save(User.builder()
                .name(name)
                .email(normalized)
                .password(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .build());
        log.info("Admin user created: {}", email);
    }
}
