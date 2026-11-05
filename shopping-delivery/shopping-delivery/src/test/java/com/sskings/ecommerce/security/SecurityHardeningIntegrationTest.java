package com.sskings.ecommerce.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.dto.request.CheckoutRequest;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.*;
import com.sskings.ecommerce.repository.*;
import com.sskings.ecommerce.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduz, com Spring + Flyway + H2 + filtros reais, os ataques da revisão de segurança
 * (docs/security-review.md) e prova que foram corrigidos. MFA de admin obrigatório nesta classe.
 */
@SpringBootTest(properties = {"security.require-admin-mfa=true",
        // banco próprio: o MFA ativado no admin aqui não pode vazar para outras classes de teste
        "spring.datasource.url=jdbc:h2:mem:security-hardening;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@AutoConfigureMockMvc
class SecurityHardeningIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired TotpService totp;

    private JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String resp = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return resp.isEmpty() ? null : json.readTree(resp);
    }

    private String register(String email) throws Exception {
        return call(post("/api/auth/register"), null, Map.of("name", "Cliente Teste", "email", email,
                "password", "senha12345", "acceptTerms", true), 201).get("token").asText();
    }

    private static String email() {
        return "sec-" + UUID.randomUUID() + "@teste.com";
    }

    @Test
    @DisplayName("C2: 10 checkouts simultâneos para 3 unidades → exatamente 3 pedidos, estoque 0, nunca negativo")
    void concurrentCheckoutNeverOversells() throws Exception {
        Product product = productRepository.save(Product.builder().name("Edição limitada").price(BigDecimal.TEN)
                .stockQuantity(3).active(true).build());
        List<long[]> buyers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User u = userRepository.save(User.builder().name("Comprador " + i).email(email()).password("x").build());
            Address a = addressRepository.save(Address.builder().user(u).zipCode("01000-000").street("Rua A").number("1")
                    .neighborhood("Centro").city("SP").state("SP").build());
            cartItemRepository.save(CartItem.builder().user(u).product(product).quantity(1).build());
            buyers.add(new long[]{u.getId(), a.getId()});
        }

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), outOfStock = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (long[] b : buyers) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    orderService.checkout(new CheckoutRequest(b[1]), b[0]);
                    ok.incrementAndGet();
                } catch (BusinessException e) {
                    outOfStock.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(unexpected).isEmpty();
        assertThat(ok.get()).isEqualTo(3);
        assertThat(outOfStock.get()).isEqualTo(7);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("M1: logout revoga o token (401 depois); token de outro login também cai")
    void logoutRevokesTokens() throws Exception {
        String mail = email();
        String token = register(mail);
        String second = call(post("/api/auth/login"), null, Map.of("email", mail, "password", "senha12345"), 200).get("token").asText();
        call(get("/api/cart"), token, null, 200);

        call(post("/api/auth/logout"), token, null, 204);

        call(get("/api/cart"), token, null, 401);
        call(get("/api/cart"), second, null, 401);
        String fresh = call(post("/api/auth/login"), null, Map.of("email", mail, "password", "senha12345"), 200).get("token").asText();
        call(get("/api/cart"), fresh, null, 200);
    }

    @Test
    @DisplayName("A1: 5 senhas erradas bloqueiam a conta; a senha certa recebe 429 com Retry-After")
    void bruteForceLocksAccount() throws Exception {
        String mail = email();
        register(mail);
        for (int i = 0; i < 5; i++) {
            call(post("/api/auth/login"), null, Map.of("email", mail, "password", "errada" + i), 400);
        }
        String retryAfter = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", mail, "password", "senha12345"))))
                .andExpect(status().isTooManyRequests()).andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 15 * 60L + 1);
    }

    @Test
    @DisplayName("C1/M7: token forjado com o segredo que vazou não funciona; admin só entra no /admin com MFA")
    void forgedTokenAndAdminMfa() throws Exception {
        String forged = com.auth0.jwt.JWT.create().withIssuer("ecommerce-api").withSubject("admin@shopeasy.com").withClaim("ver", 0)
                .withExpiresAt(java.time.Instant.now().plusSeconds(600))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("ecommerce-super-secret-key-change-in-production-2024"));
        call(get("/api/admin/users"), forged, null, 401);

        JsonNode login = call(post("/api/auth/login"), null, Map.of("email", "admin@shopeasy.com", "password", "admin123"), 200);
        assertThat(login.get("mfaSetupRequired").asBoolean()).isTrue();
        String token = login.get("token").asText();
        call(get("/api/admin/dashboard"), token, null, 403);

        String secret = call(post("/api/account/mfa/setup"), token, null, 200).get("secret").asText();
        String withMfa = call(post("/api/account/mfa/enable"), token, Map.of("code", totp.currentCode(secret)), 200).get("token").asText();
        call(get("/api/admin/dashboard"), token, null, 401);          // sessão anterior ao MFA foi revogada
        call(get("/api/admin/dashboard"), withMfa, null, 200);

        // novo login exige o código; o mesmo código não serve duas vezes
        call(post("/api/auth/login"), null, Map.of("email", "admin@shopeasy.com", "password", "admin123"), 400);
        long nextStep = java.time.Instant.now().getEpochSecond() / 30 + 1;
        String next = TotpService.generate(TotpService.decodeBase32(secret), nextStep);
        call(post("/api/auth/login"), null, Map.of("email", "admin@shopeasy.com", "password", "admin123", "mfaCode", next), 200);
        call(post("/api/auth/login"), null, Map.of("email", "admin@shopeasy.com", "password", "admin123", "mfaCode", next), 400);

        // a trilha registrou o MFA e as tentativas
        JsonNode audit = call(get("/api/admin/audit?action=MFA_ENABLED"), withMfa, null, 200);
        assertThat(audit.get("content").get(0).get("actorEmail").asText()).isEqualTo("admin@shopeasy.com");
    }

    @Test
    @DisplayName("LGPD: exportação dos dados e exclusão (anonimização) da conta")
    void exportAndDelete() throws Exception {
        String mail = email();
        String token = register(mail);
        call(put("/api/account"), token, Map.of("name", "Nome Corrigido", "phone", "11999990000"), 200);
        JsonNode export = call(get("/api/account/data-export"), token, null, 200);
        assertThat(export.get("profile").get("name").asText()).isEqualTo("Nome Corrigido");
        assertThat(export.get("profile").get("termsVersion").asText()).isNotBlank();

        call(post("/api/account/delete"), token, Map.of("password", "errada"), 400);
        call(post("/api/account/delete"), token, Map.of("password", "senha12345"), 204);

        call(get("/api/account"), token, null, 401);
        call(post("/api/auth/login"), null, Map.of("email", mail, "password", "senha12345"), 400);
        assertThat(userRepository.findByEmail(mail)).isEmpty();
    }
}
