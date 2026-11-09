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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Revisão de segurança 4 (docs/security-review.md, seção 10): cada ataque confirmado no pentest de 2026-11-09,
 * repetido contra a aplicação inteira. Duas das falhas (R4-1, R4-2) foram regressões da revisão 3 que passaram porque
 * os testes atacavam de um IP só — aqui todo ataque varia a origem.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security-review-4;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
class SecurityReview4IntegrationTest {

    private static final String PASSWORD = "Vagalume4821";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TotpService totp;
    @Autowired com.sskings.ecommerce.repository.UserRepository users;

    private MockHttpServletResponse call(MockHttpServletRequestBuilder req, String ip, String token, Object body) throws Exception {
        req.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private static int counter = 0;

    /** IP novo a cada chamada (o rate limit de 20/min por IP não interfere). */
    private static synchronized String ip() {
        counter++;
        return "10.4." + (counter / 250) + "." + (counter % 250 + 1);
    }

    private String register(String email) throws Exception {
        var r = call(post("/api/auth/register"), ip(), null,
                Map.of("name", "Cliente R4", "email", email, "password", PASSWORD, "acceptTerms", true));
        assertThat(r.getStatus()).isEqualTo(201);
        return body(r).get("token").asText();
    }

    private static String email() {
        return "r4-" + UUID.randomUUID() + "@teste.com";
    }

    private int login(String email, String password, String ip) throws Exception {
        return call(post("/api/auth/login"), ip, null, Map.of("email", email, "password", password)).getStatus();
    }

    @Test
    @DisplayName("R4-1: trocar de IP não dá tentativas ilimitadas; o dono entra de onde já entrou; IPv6 conta por /64")
    void distributedBruteForceIsBounded() throws Exception {
        String victim = email();
        register(victim);
        String home = ip();
        assertThat(login(victim, PASSWORD, home)).isEqualTo(200);           // o dono já entrou de casa

        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 30; i++) statuses.add(login(victim, "chute" + i + "x", ip()));
        assertThat(statuses.subList(0, 20)).containsOnly(400);
        assertThat(statuses.subList(20, 30)).containsOnly(429);            // teto da conta: 20 por 15 min

        assertThat(login(victim, PASSWORD, ip())).isEqualTo(429);           // origem nova, mesmo com a senha certa
        assertThat(login(victim, PASSWORD, home)).isEqualTo(200);           // casa do dono

        String other = email();
        register(other);
        for (int i = 1; i <= 5; i++) assertThat(login(other, "chute" + i + "x", "2001:db8:44:1::" + i)).isEqualTo(400);
        assertThat(login(other, PASSWORD, "2001:db8:44:1::abcd")).isEqualTo(429);   // mesmo /64
        assertThat(login(other, PASSWORD, "2001:db8:44:2::1")).isEqualTo(200);      // outro /64
    }

    @Test
    @DisplayName("R4-2: token roubado não testa a senha trocando de IP (reautenticação conta por conta)")
    void stolenTokenCannotBruteForceReauth() throws Exception {
        String victim = email();
        String stolen = register(victim);
        for (int i = 0; i < 5; i++) {
            assertThat(call(post("/api/account/mfa/setup"), ip(), stolen, Map.of("password", "chute" + i + "x")).getStatus())
                    .isEqualTo(400);
        }
        var locked = call(post("/api/account/password"), ip(), stolen,
                Map.of("currentPassword", PASSWORD, "newPassword", "Outra12345"));
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(locked.getHeader("Retry-After")).isNotNull();
        assertThat(login(victim, PASSWORD, ip())).isEqualTo(200);           // o login do dono não é afetado
    }

    @Test
    @DisplayName("R4-3: token de admin roubado não promove ninguém sem a senha do admin; com a senha, promove")
    void stolenAdminTokenCannotCreateAdmins() throws Exception {
        String attacker = email();
        register(attacker);
        long attackerId = users.findByEmail(attacker).orElseThrow().getId();
        String admin = body(call(post("/api/auth/login"), ip(), null,
                Map.of("email", "admin@shopeasy.com", "password", "admin123"))).get("token").asText();

        var noPassword = call(patch("/api/admin/users/" + attackerId), ip(), admin, Map.of("role", "ADMIN"));
        assertThat(noPassword.getStatus()).isEqualTo(400);
        assertThat(body(noPassword).get("message").asText()).isEqualTo("Password confirmation required");
        assertThat(call(patch("/api/admin/users/" + attackerId), ip(), admin,
                Map.of("role", "ADMIN", "password", "chute123")).getStatus()).isEqualTo(400);
        assertThat(users.findByEmail(attacker).orElseThrow().getRole().name()).isEqualTo("CUSTOMER");

        // bloquear cliente continua direto (rotina de atendimento)
        assertThat(call(patch("/api/admin/users/" + attackerId), ip(), admin, Map.of("active", false)).getStatus()).isEqualTo(200);
        assertThat(call(patch("/api/admin/users/" + attackerId), ip(), admin,
                Map.of("active", true, "role", "ADMIN", "password", "admin123")).getStatus()).isEqualTo(200);
        assertThat(users.findByEmail(attacker).orElseThrow().getRole().name()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("R4-4: uma conta não leva todo o estoque de um produto de estoque baixo")
    void oneAccountCannotTakeLowStockProduct() throws Exception {
        String ip = ip();
        String token = register(email());
        long addressId = body(call(post("/api/addresses"), ip, token, Map.of("zipCode", "01000-000", "street", "Rua R4",
                "number", "1", "neighborhood", "Centro", "city", "São Paulo", "state", "SP"))).get("id").asLong();
        JsonNode target = null;
        for (JsonNode p : body(call(get("/api/products?size=50"), ip, null, null)).get("content")) {
            if (p.get("stockQuantity").asInt() == 10) target = p;
        }
        assertThat(target).as("produto com estoque 10 no seed").isNotNull();
        long id = target.get("id").asLong();

        call(post("/api/cart/items"), ip, token, Map.of("productId", id, "quantity", 10));
        var all = call(post("/api/orders/checkout"), ip, token, Map.of("addressId", addressId, "paymentMethod", "PIX"));
        assertThat(all.getStatus()).isEqualTo(400);
        assertThat(body(all).get("message").asText()).startsWith("Maximum quantity for this product right now: 5");

        call(put("/api/cart/items/" + body(call(get("/api/cart"), ip, token, null)).at("/items/0/id").asLong()), ip, token,
                Map.of("quantity", 5));
        assertThat(call(post("/api/orders/checkout"), ip, token, Map.of("addressId", addressId, "paymentMethod", "PIX"))
                .getStatus()).isEqualTo(201);
        assertThat(body(call(get("/api/products/" + id), ip, null, null)).get("stockQuantity").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("R4-5: ninguém ocupa o e-mail anonimizado; a exclusão funciona e usa endereço aleatório")
    void accountDeletionCannotBeBlocked() throws Exception {
        String victim = email();
        String token = register(victim);
        long id = users.findByEmail(victim).orElseThrow().getId();
        for (String squat : new String[]{"removed-" + id + "@anonymized.invalid", "x@ANONYMIZED.INVALID", "a@b.invalid "}) {
            assertThat(call(post("/api/auth/register"), ip(), null,
                    Map.of("name", "Squat Ter", "email", squat, "password", PASSWORD, "acceptTerms", true)).getStatus())
                    .as(squat).isEqualTo(400);
        }
        assertThat(call(post("/api/account/delete"), ip(), token, Map.of("password", PASSWORD)).getStatus()).isEqualTo(204);
        assertThat(users.findById(id).orElseThrow().getEmail()).matches("removed-[0-9a-f-]{36}@anonymized\\.invalid");
    }

    @Test
    @DisplayName("R4-7: o mesmo código MFA em dois logins simultâneos só vale para um")
    void mfaCodeRaceAcceptsOnlyOne() throws Exception {
        String user = email();
        String token = register(user);
        String secret = body(call(post("/api/account/mfa/setup"), ip(), token, Map.of("password", PASSWORD))).get("secret").asText();
        String code = totp.currentCode(secret);
        assertThat(call(post("/api/account/mfa/enable"), ip(), token, Map.of("code", code)).getStatus()).isEqualTo(200);
        // libera o código de novo (como se ainda não tivesse sido usado) para os dois logins disputarem
        users.findByEmail(user).ifPresent(u -> { u.setMfaLastStep(null); users.save(u); });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String ip = ip();
            results.add(pool.submit(() -> {
                start.await();
                return call(post("/api/auth/login"), ip, null, Map.of("email", user, "password", PASSWORD, "mfaCode", code)).getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
    }
}
