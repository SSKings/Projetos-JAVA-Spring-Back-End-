package com.sskings.ecommerce.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Revisão de segurança 3 (docs/security-review.md): cada ataque confirmado no pentest de 2026-11-08, repetido
 * contra a aplicação inteira (perfil dev, banco próprio) para provar a correção e impedir que volte.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security-review-3;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
class SecurityReview3IntegrationTest {

    private static final String PASSWORD = "senha12345";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpServletResponse call(MockHttpServletRequestBuilder req, String ip, String token, Object body) throws Exception {
        req.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    /** Cada teste com seus IPs: o rate limit (20/min por IP) não interfere entre testes. */
    private static String ip() {
        return "10.3." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250 + 1);
    }

    private String register(String email, String ip) throws Exception {
        var r = call(post("/api/auth/register"), ip, null,
                Map.of("name", "Cliente R3", "email", email, "password", PASSWORD, "acceptTerms", true));
        assertThat(r.getStatus()).isEqualTo(201);
        return body(r).get("token").asText();
    }

    private static String email() {
        return "r3-" + UUID.randomUUID() + "@teste.com";
    }

    @Test
    @DisplayName("A1: token roubado não ativa o MFA (exige senha) e o dono continua entrando sem código")
    void stolenTokenCannotEnrollMfa() throws Exception {
        String victim = email(), ip = ip();
        String stolen = register(victim, ip);

        assertThat(call(post("/api/account/mfa/setup"), ip, stolen, null).getStatus()).isEqualTo(400);
        assertThat(call(post("/api/account/mfa/setup"), ip, stolen, Map.of()).getStatus()).isEqualTo(400);
        var wrong = call(post("/api/account/mfa/setup"), ip, stolen, Map.of("password", "chute1234"));
        assertThat(wrong.getStatus()).isEqualTo(400);
        assertThat(body(wrong).get("message").asText()).isEqualTo("Invalid password");

        var owner = call(post("/api/auth/login"), ip(), null, Map.of("email", victim, "password", PASSWORD));
        assertThat(owner.getStatus()).isEqualTo(200);
        assertThat(body(owner).get("mfaEnabled").asBoolean()).isFalse();

        // o dono, com a senha, ativa normalmente
        assertThat(call(post("/api/account/mfa/setup"), ip, stolen, Map.of("password", PASSWORD)).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("A2: 5 senhas erradas de um atacante bloqueiam só o IP dele; o dono entra de outro IP (inclusive o admin)")
    void attackerCannotLockOwnerOut() throws Exception {
        String victim = email(), attacker = ip(), owner = ip();
        register(victim, owner);

        for (String target : new String[]{victim, "admin@shopeasy.com"}) {
            for (int i = 0; i < 5; i++) {
                assertThat(call(post("/api/auth/login"), attacker, null, Map.of("email", target, "password", "chute" + i)).getStatus())
                        .isEqualTo(400);
            }
            var locked = call(post("/api/auth/login"), attacker, null, Map.of("email", target, "password", "qualquer1"));
            assertThat(locked.getStatus()).isEqualTo(429);
            assertThat(locked.getHeader("Retry-After")).isNotNull();
        }
        assertThat(call(post("/api/auth/login"), owner, null, Map.of("email", victim, "password", PASSWORD)).getStatus())
                .isEqualTo(200);
        // o admin, do IP dele, recebe a resposta normal (400 = senha errada aqui; NÃO 429)
        assertThat(call(post("/api/auth/login"), owner, null, Map.of("email", "admin@shopeasy.com", "password", "outra123")).getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("A3: no máximo 10 unidades por produto e 2 pedidos aguardando Pix por conta")
    void oneAccountCannotHoardStock() throws Exception {
        String ip = ip();
        String token = register(email(), ip);
        long addressId = body(call(post("/api/addresses"), ip, token, Map.of("zipCode", "01000-000", "street", "Rua R3",
                "number", "1", "neighborhood", "Centro", "city", "São Paulo", "state", "SP"))).get("id").asLong();
        JsonNode products = body(call(get("/api/products?size=50"), ip, null, null)).get("content");
        long productId = -1;
        for (JsonNode p : products) {
            if (p.get("stockQuantity").asInt() >= 20) { productId = p.get("id").asLong(); break; }
        }
        assertThat(productId).as("produto com estoque no seed").isPositive();

        assertThat(call(post("/api/cart/items"), ip, token, Map.of("productId", productId, "quantity", 11)).getStatus()).isEqualTo(400);
        assertThat(call(post("/api/cart/items"), ip, token, Map.of("productId", productId, "quantity", 10)).getStatus()).isEqualTo(200);
        assertThat(call(post("/api/cart/items"), ip, token, Map.of("productId", productId, "quantity", 1)).getStatus()).isEqualTo(400);

        for (int i = 0; i < 2; i++) {
            if (i > 0) call(post("/api/cart/items"), ip, token, Map.of("productId", productId, "quantity", 1));
            assertThat(call(post("/api/orders/checkout"), ip, token, Map.of("addressId", addressId, "paymentMethod", "PIX")).getStatus())
                    .isEqualTo(201);
        }
        call(post("/api/cart/items"), ip, token, Map.of("productId", productId, "quantity", 1));
        var third = call(post("/api/orders/checkout"), ip, token, Map.of("addressId", addressId, "paymentMethod", "PIX"));
        assertThat(third.getStatus()).isEqualTo(400);
        assertThat(body(third).get("message").asText()).contains("awaiting payment");
    }

    @Test
    @DisplayName("M1: troca de senha exige a atual, derruba o token antigo e a senha nova passa a valer")
    void passwordChange() throws Exception {
        String user = email(), ip = ip();
        String token = register(user, ip);

        assertThat(call(post("/api/account/password"), ip, token,
                Map.of("currentPassword", "errada123", "newPassword", "novaSenha99")).getStatus()).isEqualTo(400);
        var ok = call(post("/api/account/password"), ip, token, Map.of("currentPassword", PASSWORD, "newPassword", "novaSenha99"));
        assertThat(ok.getStatus()).isEqualTo(200);
        String fresh = body(ok).get("token").asText();

        assertThat(call(get("/api/account"), ip, token, null).getStatus()).isEqualTo(401);
        assertThat(call(get("/api/account"), ip, fresh, null).getStatus()).isEqualTo(200);
        assertThat(call(post("/api/auth/login"), ip, null, Map.of("email", user, "password", PASSWORD)).getStatus()).isEqualTo(400);
        assertThat(call(post("/api/auth/login"), ip, null, Map.of("email", user, "password", "novaSenha99")).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Dev: console H2 e Swagger só respondem para a própria máquina, não para a rede local")
    void devToolsOnlyFromLocalhost() throws Exception {
        for (String path : new String[]{"/h2-console/", "/v3/api-docs", "/swagger-ui/index.html"}) {
            var lan = call(get(path), "192.168.1.50", null, null);
            assertThat(lan.getStatus()).as(path + " pela rede").isEqualTo(403);
            assertThat(lan.getContentAsString()).contains("only available from localhost");
            // local: passa pela segurança — nem 403 desta regra, nem 401 da cadeia principal (que pegaria a rota se o
            // matcher não a reconhecesse). O MockMvc não tem o servlet do H2, então ali o "sucesso" é 404.
            assertThat(call(get(path), "127.0.0.1", null, null).getStatus()).as(path + " local").isNotIn(401, 403);
            assertThat(call(get(path), "::1", null, null).getStatus()).as(path + " local IPv6").isNotIn(401, 403);
        }
    }

    @Test
    @DisplayName("B5: página pública de produtos limitada a 100 itens")
    void pageSizeCapped() throws Exception {
        assertThat(body(call(get("/api/products?size=2000"), ip(), null, null)).get("size").asInt()).isEqualTo(100);
    }
}
