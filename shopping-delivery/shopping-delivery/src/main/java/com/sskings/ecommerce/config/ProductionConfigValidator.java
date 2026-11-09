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
    private final String databaseUser;
    private final String migrationUser;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductionConfigValidator(SecurityProperties security, PixProperties pix, CorsProperties cors,
                                     @Value("${app.admin.password}") String adminPassword,
                                     @Value("${spring.datasource.url}") String datasourceUrl,
                                     @Value("${spring.datasource.username:}") String databaseUser,
                                     @Value("${spring.flyway.user:}") String migrationUser) {
        this.security = security;
        this.pix = pix;
        this.cors = cors;
        this.adminPassword = adminPassword;
        this.datasourceUrl = datasourceUrl;
        this.databaseUser = databaseUser;
        this.migrationUser = migrationUser;
    }

    ProductionConfigValidator(SecurityProperties security, PixProperties pix, CorsProperties cors,
                              String adminPassword, String datasourceUrl) {
        this(security, pix, cors, adminPassword, datasourceUrl, "shopeasy_app", "shopeasy_owner");
    }

    /**
     * Revisão 5, R5-4: a API não pode usar o superusuário nem o dono das tabelas. Com um usuário só de DML, uma falha
     * de SQL não altera o schema nem executa comandos no servidor do banco.
     */
    static List<String> databaseUserProblems(String databaseUser, String migrationUser) {
        List<String> p = new ArrayList<>();
        if (migrationUser == null || migrationUser.isBlank() || migrationUser.equals(databaseUser)) {
            p.add("DATABASE_MIGRATION_USERNAME must be set and differ from DATABASE_USERNAME (the API must not own the tables)");
        }
        if (databaseUser == null || databaseUser.isBlank() || databaseUser.equalsIgnoreCase("postgres")) {
            p.add("DATABASE_USERNAME must be the unprivileged application user (not postgres)");
        }
        return p;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>(problems(security, pix, cors, adminPassword, datasourceUrl));
        problems.addAll(databaseUserProblems(databaseUser, migrationUser));
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
            // Revisão 3, B4: com o webhook ligado, a allowlist é a 2ª camada se o segredo do HMAC vazar
            if (pix.webhookAllowedIps().isEmpty()) {
                p.add("PIX_WEBHOOK_ALLOWED_IPS must list the PSP's IPs/CIDRs when the webhook is enabled");
            }
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
        } else if (com.sskings.ecommerce.security.PasswordStrength.problem(adminPassword, null, null).isPresent()) {
            p.add("ADMIN_PASSWORD is too predictable (common password or common word with numbers)");
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
