package com.sskings.ecommerce.config;

import com.sskings.ecommerce.payment.PixProperties;
import com.sskings.ecommerce.security.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {

    private static final String STRONG = "x".repeat(40);
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static SecurityProperties security(String jwt, String key, boolean mfa, boolean rateLimit) {
        return new SecurityProperties(new SecurityProperties.Jwt(jwt, 60), key, mfa,
                new SecurityProperties.Login(5, 15), new SecurityProperties.RateLimit(rateLimit, 20, 120));
    }

    private static PixProperties pix(String key, String webhookSecret) {
        return new PixProperties(key, "Loja", "SP", 30, webhookSecret, List.of("200.0.0.0/24"));
    }

    private static final CorsProperties CORS = new CorsProperties(List.of("https://loja.com.br"));
    private static final String DB = "jdbc:postgresql://db:5432/shop";

    @Test
    @DisplayName("Configuração forte passa (webhook pode ficar desligado)")
    void valid() {
        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true), pix("12345678000199", STRONG),
                CORS, "SenhaForte2026!", DB)).isEmpty();
        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true), pix("12345678000199", ""),
                CORS, "SenhaForte2026!", DB)).isEmpty();
        assertThatCode(() -> new ProductionConfigValidator(security(STRONG, KEY, true, true), pix("12345678000199", null),
                CORS, "SenhaForte2026!", DB).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valores de dev e o segredo que vazou no repositório são recusados, sem expor os valores")
    void devValues() {
        List<String> problems = ProductionConfigValidator.problems(
                security("ecommerce-super-secret-key-change-in-production-2024", "ZGV2LW9ubHktZW5jcnlwdGlvbi1rZXktMzItYnl0ZXM=", false, false),
                pix("pix@shopeasy.example", "dev-pix-webhook-secret-change-me"),
                new CorsProperties(List.of("*")), "admin123", "jdbc:h2:mem:x");

        assertThat(problems).hasSize(9).noneMatch(p -> p.contains("ecommerce-super-secret") || p.contains("admin123"));
        assertThatThrownBy(() -> new ProductionConfigValidator(
                security("curto", "não-base64", true, true), pix("12345678000199", "curto"),
                new CorsProperties(List.of("http://loja.com.br")), "semNumeros!!!", null).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET").hasMessageContaining("APP_ENCRYPTION_KEY")
                .hasMessageContaining("PIX_WEBHOOK_SECRET").hasMessageContaining("CORS_ALLOWED_ORIGINS")
                .hasMessageContaining("ADMIN_PASSWORD").hasMessageContaining("DATABASE_URL");
    }

    @Test
    @DisplayName("Revisão 3, B4: webhook ligado exige a allowlist de IPs do PSP; desligado, não")
    void webhookAllowlist() {
        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true),
                new PixProperties("12345678000199", "Loja", "SP", 30, STRONG, List.of()), CORS, "SenhaForte2026!", DB))
                .singleElement().asString().contains("PIX_WEBHOOK_ALLOWED_IPS");
        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true),
                new PixProperties("12345678000199", "Loja", "SP", 30, "", List.of()), CORS, "SenhaForte2026!", DB))
                .isEmpty();
    }

    @Test
    @DisplayName("Senha de admin curta ou sem letras é recusada")
    void adminPassword() {
        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true), pix("k@loja.com.br", null),
                CORS, "Curta1", DB)).singleElement().asString().contains("ADMIN_PASSWORD");
        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true), pix("k@loja.com.br", null),
                CORS, "123456789012345", DB)).hasSize(1);
        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true), pix("k@loja.com.br", null),
                CORS, null, DB)).hasSize(1);
    }

    @Test
    @DisplayName("Revisão 5: API com o dono das tabelas ou o superusuário do banco, ou ADMIN_PASSWORD previsível, é recusada")
    void databaseUsersAndPredictableAdminPassword() {
        assertThat(ProductionConfigValidator.databaseUserProblems("shopeasy_app", "shopeasy_owner")).isEmpty();
        assertThat(ProductionConfigValidator.databaseUserProblems("shopeasy", "shopeasy")).singleElement()
                .asString().contains("DATABASE_MIGRATION_USERNAME");
        assertThat(ProductionConfigValidator.databaseUserProblems("shopeasy", "")).hasSize(1);
        assertThat(ProductionConfigValidator.databaseUserProblems("postgres", "owner")).singleElement()
                .asString().contains("not postgres");
        assertThat(ProductionConfigValidator.databaseUserProblems(null, null)).hasSize(2);
        assertThatThrownBy(() -> new ProductionConfigValidator(security(STRONG, KEY, true, true), pix("12345678000199", null),
                CORS, "SenhaForte2026!", DB, "app", "app", true, "smtp.gmail.com", "l@gmail.com", "pw", "l@gmail.com", true,
                "https://loja.com.br").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DATABASE_MIGRATION_USERNAME");

        assertThat(ProductionConfigValidator.problems(security(STRONG, KEY, true, true), pix("k@loja.com.br", null),
                CORS, "Administrador2026", DB)).singleElement().asString().contains("too predictable");
    }

    @Test
    @DisplayName("ADR-025: e-mail em produção exige servidor, credenciais, remetente, STARTTLS e link https")
    void mail() {
        var ok = new ProductionConfigValidator.MailSettings(true, "smtp.gmail.com", "loja@gmail.com", "app-pass", "loja@gmail.com",
                true, "https://loja.com.br");
        assertThat(ProductionConfigValidator.mailProblems(ok)).isEmpty();
        assertThat(ProductionConfigValidator.mailProblems(new ProductionConfigValidator.MailSettings(true, "", "", "", "", false,
                "http://loja.com.br"))).hasSize(3).anySatisfy(p -> assertThat(p).contains("MAIL_HOST"))
                .anySatisfy(p -> assertThat(p).contains("STARTTLS")).anySatisfy(p -> assertThat(p).contains("https"));
        assertThat(ProductionConfigValidator.mailProblems(new ProductionConfigValidator.MailSettings(false, null, null, null, null,
                false, null))).isEmpty();                                                   // e-mail desligado de propósito
        for (var partial : new ProductionConfigValidator.MailSettings[]{
                new ProductionConfigValidator.MailSettings(true, "smtp.gmail.com", "", "pw", "l@g.com", true, "https://l.com"),
                new ProductionConfigValidator.MailSettings(true, "smtp.gmail.com", "u", null, "l@g.com", true, "https://l.com"),
                new ProductionConfigValidator.MailSettings(true, "smtp.gmail.com", "u", "pw", " ", true, "https://l.com")}) {
            assertThat(ProductionConfigValidator.mailProblems(partial)).singleElement().asString().contains("MAIL_PASSWORD");
        }
        assertThat(ProductionConfigValidator.mailProblems(new ProductionConfigValidator.MailSettings(true, "smtp.gmail.com", "u",
                "pw", "l@g.com", true, null))).singleElement().asString().contains("APP_PUBLIC_URL");
    }
}
