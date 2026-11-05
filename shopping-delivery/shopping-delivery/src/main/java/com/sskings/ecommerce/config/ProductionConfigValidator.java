package com.sskings.ecommerce.config;

import com.sskings.ecommerce.payment.PixProperties;
import com.sskings.ecommerce.security.SecurityProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Perfil prod: impede o startup com segredos ausentes, curtos ou conhecidos (os valores de dev e o segredo
 * antigo que vazou no repositório público), CORS aberto, H2 ou MFA de admin desligado.
 * A mensagem lista o que corrigir, nunca os valores.
 */
@Component
@Profile("prod")
public class ProductionConfigValidator implements InitializingBean {

    /** Valores públicos (repositório/documentação): nunca aceitos em produção. */
    static final List<String> KNOWN_PUBLIC_VALUES = List.of(
            "ecommerce-super-secret-key-change-in-production-2024",
            "dev-only-jwt-secret-not-for-production-0123456789",
            "dev-pix-webhook-secret-change-me",
            "ZGV2LW9ubHktZW5jcnlwdGlvbi1rZXktMzItYnl0ZXM=",
            "admin123");

    static final int MIN_SECRET_LENGTH = 32;
    static final int MIN_ADMIN_PASSWORD = 12;

    private final SecurityProperties security;
    private final PixProperties pix;
    private final CorsProperties cors;
    private final String adminPassword;
    private final String datasourceUrl;

    public ProductionConfigValidator(SecurityProperties security, PixProperties pix, CorsProperties cors,
                                     @Value("${app.admin.password}") String adminPassword,
                                     @Value("${spring.datasource.url}") String datasourceUrl) {
        this.security = security;
        this.pix = pix;
        this.cors = cors;
        this.adminPassword = adminPassword;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = problems(security, pix, cors, adminPassword, datasourceUrl);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Insecure production configuration:\n - " + String.join("\n - ", problems));
        }
    }

    static List<String> problems(SecurityProperties security, PixProperties pix, CorsProperties cors,
                                 String adminPassword, String datasourceUrl) {
        List<String> p = new ArrayList<>();
        secret(p, "JWT_SECRET", security.jwt().secret());
        if (KNOWN_PUBLIC_VALUES.contains(security.encryptionKey()) || !isAes256Key(security.encryptionKey())) {
            p.add("APP_ENCRYPTION_KEY must be a new base64-encoded 32-byte key (openssl rand -base64 32)");
        }
        if (pix.webhookSecret() != null && !pix.webhookSecret().isBlank()) {
            secret(p, "PIX_WEBHOOK_SECRET", pix.webhookSecret());
        }
        if (pix.key().toLowerCase().endsWith(".example")) {
            p.add("PIX_KEY must be the store's real Pix key");
        }
        if (cors.allowAny() || cors.allowedOrigins().stream().anyMatch(o -> !o.startsWith("https://"))) {
            p.add("CORS_ALLOWED_ORIGINS must list the exact https:// origins of the front-end");
        }
        if (adminPassword == null || adminPassword.length() < MIN_ADMIN_PASSWORD || KNOWN_PUBLIC_VALUES.contains(adminPassword)
                || !adminPassword.matches(".*\\p{L}.*") || !adminPassword.matches(".*\\d.*")) {
            p.add("ADMIN_PASSWORD must have at least " + MIN_ADMIN_PASSWORD + " characters with letters and numbers");
        }
        if (!security.requireAdminMfa()) p.add("REQUIRE_ADMIN_MFA must be true");
        if (!security.rateLimit().enabled()) p.add("security.rate-limit.enabled must be true");
        if (datasourceUrl == null || datasourceUrl.startsWith("jdbc:h2:")) p.add("DATABASE_URL must point to a persistent database (not H2)");
        return p;
    }

    private static void secret(List<String> p, String name, String value) {
        if (value == null || value.length() < MIN_SECRET_LENGTH || KNOWN_PUBLIC_VALUES.contains(value)) {
            p.add(name + " must be a new random value with at least " + MIN_SECRET_LENGTH + " characters (openssl rand -base64 48)");
        }
    }

    static boolean isAes256Key(String base64) {
        try {
            return Base64.getDecoder().decode(base64).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
