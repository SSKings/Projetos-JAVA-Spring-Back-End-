package com.sskings.ecommerce.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revisão de segurança 2 (docs/security-review.md): reproduz cada ataque confirmado contra o ambiente rodando
 * e prova que foi corrigido. Perfil dev (o padrão), com banco próprio.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security-review-2;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityReview2IntegrationTest {

    /** Segredo JWT que ficava fixo e público no application-dev.yml. */
    static final String OLD_PUBLIC_DEV_SECRET = "dev-only-jwt-secret-not-for-production-0123456789";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${security.jwt.secret}") String jwtSecret;

    private int httpStatus(MockHttpServletRequestBuilder req, Object body) throws Exception {
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    private String register(String email) throws Exception {
        String resp = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("name", "Cliente Teste", "email", email, "password", "Vagalume4821", "acceptTerms", true))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("token").asText();
    }

    private static String email() {
        return "r2-" + UUID.randomUUID() + "@teste.com";
    }

    @Test
    @DisplayName("R2: em dev o segredo JWT é aleatório; o antigo segredo público não forja mais token de admin")
    void devJwtSecretIsNotPublic() throws Exception {
        assertThat(jwtSecret).isNotEqualTo(OLD_PUBLIC_DEV_SECRET).hasSizeGreaterThanOrEqualTo(32);
        for (int ver = 0; ver <= 5; ver++) {
            String forged = com.auth0.jwt.JWT.create().withIssuer("ecommerce-api").withSubject("admin@shopeasy.com")
                    .withClaim("ver", ver).withExpiresAt(java.time.Instant.now().plusSeconds(600))
                    .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(OLD_PUBLIC_DEV_SECRET));
            assertThat(httpStatus(get("/api/admin/users").header("Authorization", "Bearer " + forged), null)).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("R3: 6 senhas erradas dão a mesma sequência de respostas para email cadastrado e inexistente")
    void lockoutDoesNotRevealAccounts() throws Exception {
        String existing = email();
        register(existing);
        List<Integer> forExisting = new ArrayList<>(), forUnknown = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            forExisting.add(httpStatus(post("/api/auth/login"), Map.of("email", existing, "password", "errada" + i)));
            forUnknown.add(httpStatus(post("/api/auth/login"), Map.of("email", email(), "password", "errada" + i)));
        }
        // emails inexistentes diferentes não acumulam; o mesmo email inexistente acumula igual a uma conta
        String ghost = email();
        List<Integer> forSameUnknown = new ArrayList<>();
        for (int i = 0; i < 6; i++) forSameUnknown.add(httpStatus(post("/api/auth/login"), Map.of("email", ghost, "password", "errada" + i)));

        assertThat(forExisting).containsExactly(400, 400, 400, 400, 400, 429);
        assertThat(forSameUnknown).isEqualTo(forExisting);
        assertThat(forUnknown).containsOnly(400);
    }

    @Test
    @DisplayName("R4: senhas erradas na reautenticação bloqueiam a conta (429), inclusive para a senha certa")
    void reauthenticationIsRateLimited() throws Exception {
        String mail = email();
        String token = register(mail);
        for (int i = 0; i < 5; i++) {
            assertThat(httpStatus(post("/api/account/delete").header("Authorization", "Bearer " + token),
                    Map.of("password", "chute" + i))).isEqualTo(400);
        }
        mvc.perform(post("/api/account/delete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Vagalume4821\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // a conta não foi excluída. Revisão 4 (R4-2): o bloqueio é só da reautenticação, por conta — o login do dono
        // continua (quem errava aqui tem o token, não é o dono tentando entrar)
        assertThat(httpStatus(get("/api/account").header("Authorization", "Bearer " + token), null)).isEqualTo(200);
        assertThat(httpStatus(post("/api/auth/login"), Map.of("email", mail, "password", "Vagalume4821"))).isEqualTo(200);
    }

    @Test
    @DisplayName("R5: corpo acima de 64 KB → 413 antes de ser lido (webhook público e login)")
    void requestBodyIsLimited() throws Exception {
        byte[] big = new byte[65 * 1024];
        java.util.Arrays.fill(big, (byte) 'a');
        mvc.perform(post("/api/payments/pix/webhook").contentType(MediaType.APPLICATION_JSON).content(big)
                        .header("X-Pix-Timestamp", "1").header("X-Pix-Signature", "x"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Request body too large"));
        assertThat(httpStatus(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(big), null)).isEqualTo(413);
        // corpo normal continua chegando ao controller (assinatura inválida → 401)
        assertThat(httpStatus(post("/api/payments/pix/webhook").contentType(MediaType.APPLICATION_JSON)
                .content("{\"txid\":\"T\",\"endToEndId\":\"E\",\"amount\":1}"), null)).isEqualTo(401);
    }

    @Test
    @DisplayName("R6: erros comuns têm o código HTTP certo e não geram stack trace no log")
    void commonErrorsAreNotServerErrors(CapturedOutput output) throws Exception {
        String token = register(email());
        mvc.perform(get("/api/nao-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Resource not found"));
        mvc.perform(delete("/api/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/products").param("sort", "naoExiste"))     // anônimo e sem rate limit
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid sort property"));
        mvc.perform(get("/api/products").param("sort", "price,desc")).andExpect(status().isOk());

        assertThat(output.getAll()).doesNotContain("Unhandled exception");
    }
}
