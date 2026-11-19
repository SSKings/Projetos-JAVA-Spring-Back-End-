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

    // ---- cada regra sozinha (achadas pelo teste de mutação: os testes acima juntavam vários problemas num caso só) ----

    private static List<String> only(String jwt, String key, CorsProperties cors, String admin) {
        return ProductionConfigValidator.problems(security(jwt, key, true, true), pix("12345678000199", ""), cors, admin, DB);
    }

    @Test
    @DisplayName("Chave de criptografia: exatamente 32 bytes (31 ou 33 são recusados)")
    void encryptionKeyLength() {
        for (int n : new int[]{16, 31, 33, 64}) {
            assertThat(only(STRONG, Base64.getEncoder().encodeToString(new byte[n]), CORS, "SenhaForte2026!"))
                    .as(n + " bytes").singleElement().asString().startsWith("APP_ENCRYPTION_KEY");
        }
        assertThat(ProductionConfigValidator.isAes256Key(KEY)).isTrue();
        assertThat(ProductionConfigValidator.isAes256Key("%%%")).isFalse();
    }

    @Test
    @DisplayName("Segredo: 32 caracteres passam, 31 não")
    void secretLengthBoundary() {
        assertThat(only("y".repeat(32), KEY, CORS, "SenhaForte2026!")).isEmpty();
        assertThat(only("y".repeat(31), KEY, CORS, "SenhaForte2026!")).singleElement().asString().startsWith("JWT_SECRET");
    }

    @Test
    @DisplayName("CORS: uma origem http no meio das https já é recusada; curinga também")
    void corsEachOrigin() {
        assertThat(only(STRONG, KEY, new CorsProperties(List.of("https://loja.com.br", "http://loja.com.br")), "SenhaForte2026!"))
                .singleElement().asString().startsWith("CORS_ALLOWED_ORIGINS");
        assertThat(only(STRONG, KEY, new CorsProperties(List.of("*")), "SenhaForte2026!"))
                .singleElement().asString().startsWith("CORS_ALLOWED_ORIGINS");
    }

    @Test
    @DisplayName("Senha do admin: 12 caracteres com letra e número; sem letra, sem número ou com 11 é recusada")
    void adminPasswordRules() {
        assertThat(only(STRONG, KEY, CORS, "Xq7mLp2vRt9k")).isEmpty();
        for (String bad : new String[]{"Xq7mLp2vRt9", "583920174652", "XqmLpvRtkWzN", null}) {
            assertThat(only(STRONG, KEY, CORS, bad)).as(String.valueOf(bad)).singleElement().asString().startsWith("ADMIN_PASSWORD");
        }
    }

    @Test
    @DisplayName("Usuário do banco da API: nem vazio, nem postgres (em qualquer caixa)")
    void databaseUserEachCase() {
        for (String bad : new String[]{null, " ", "POSTGRES", "postgres"}) {
            assertThat(ProductionConfigValidator.databaseUserProblems(bad, "shop_owner")).as(String.valueOf(bad))
                    .singleElement().asString().startsWith("DATABASE_USERNAME");
        }
        assertThat(ProductionConfigValidator.databaseUserProblems("shop_app", "shop_owner")).isEmpty();
    }

    @Test
    @DisplayName("E-mail: desligado não exige nada; ligado exige host, usuário, senha e remetente, cada um")
    void mailEachField() {
        assertThat(ProductionConfigValidator.mailProblems(new ProductionConfigValidator.MailSettings(false, "", "", "", "", false, "")))
                .isEmpty();
        String[][] cases = {{"", "u", "p", "f"}, {"h", "", "p", "f"}, {"h", "u", "", "f"}, {"h", "u", "p", ""}};
        for (String[] c : cases) {
            assertThat(ProductionConfigValidator.mailProblems(new ProductionConfigValidator.MailSettings(true, c[0], c[1], c[2], c[3],
                    true, "https://l.com"))).as(String.join(",", c)).anyMatch(p -> p.startsWith("MAIL_HOST"));
        }
    }
}
