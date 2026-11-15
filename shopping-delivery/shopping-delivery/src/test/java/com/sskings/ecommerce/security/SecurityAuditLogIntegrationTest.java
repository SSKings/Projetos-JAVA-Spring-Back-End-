package com.sskings.ecommerce.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Análise de logs (2026-11-15): eventos de segurança iam só para o banco — nada no Loki nem no `docker logs`.
 * Aqui, pela API de verdade: login errado e senha trocada viram linhas do logger "security.audit".
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security-audit-log;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityAuditLogIntegrationTest {

    private static final String PASSWORD = "Vagalume4821";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String send(String path, String ip, String token, Object body) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
                .with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Login com senha errada: WARN security.audit com IP; o e-mail nunca aparece inteiro")
    void failedLoginIsLogged(CapturedOutput output) throws Exception {
        String email = "logcheck-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        send("/api/auth/register", "10.40.0.1", null, Map.of("name", "Ana", "email", email, "password", PASSWORD, "acceptTerms", true));
        send("/api/auth/login", "10.40.0.2", null, Map.of("email", email, "password", "errada-123x"));
        send("/api/auth/login", "10.40.0.3", null, Map.of("email", "ninguem-" + email, "password", "errada-123x"));

        assertThat(output.getOut()).containsPattern("WARN .*security\\.audit\\s*: LOGIN_FAILED ator=#\\d+ l\\*\\*\\*@teste\\.com alvo=USER#\\d+ ip=10\\.40\\.0\\.2 — wrong password");
        assertThat(output.getOut()).containsPattern("WARN .*security\\.audit\\s*: LOGIN_FAILED ator=- alvo=USER ip=10\\.40\\.0\\.3 — unknown email n\\*\\*\\*@teste\\.com");
        assertThat(output.getOut()).doesNotContain(email);
    }

    @Test
    @DisplayName("Senha trocada: INFO security.audit depois do commit")
    void passwordChangeIsLogged(CapturedOutput output) throws Exception {
        String email = "logcheck-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        String token = json.readTree(send("/api/auth/register", "10.40.1.1", null,
                Map.of("name", "Ana", "email", email, "password", PASSWORD, "acceptTerms", true))).get("token").asText();
        send("/api/account/password", "10.40.1.2", token, Map.of("currentPassword", PASSWORD, "newPassword", "Pirilampo7390"));

        assertThat(output.getOut()).containsPattern("INFO .*security\\.audit\\s*: PASSWORD_CHANGED ator=#\\d+ l\\*\\*\\*@teste\\.com alvo=USER#\\d+ ip=10\\.40\\.1\\.2");
        assertThat(output.getOut()).doesNotContain(email);
    }
}
