package com.sskings.ecommerce.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.model.EmailToken;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.EmailTokenRepository;
import com.sskings.ecommerce.repository.UserRepository;
import com.sskings.ecommerce.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Revisão de segurança 6 (docs/security-review.md, seção 13): cada ataque confirmado no pentest de 2026-11-10,
 * repetido contra a aplicação inteira, cada chamada de uma origem diferente (regra 12 do checklist).
 */
// limite de produção (20/min por IP) — os testes em geral usam 100000 (src/test/resources/config/application.yml)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:security-review-6;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "security.rate-limit.auth-per-minute=20"})
@AutoConfigureMockMvc
class SecurityReview6IntegrationTest {

    private static final String PASSWORD = "Vagalume4821";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired EmailTokenRepository emailTokens;
    @Autowired PasswordResetService passwordReset;

    private static int counter = 0;

    private static synchronized String ip() {
        counter++;
        return "10.6." + (counter / 250) + "." + (counter % 250 + 1);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder req, String ip, String token, Object body) throws Exception {
        req.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn().getResponse();
    }

    /** Cadastra e devolve [e-mail, JWT]. */
    private String[] register() throws Exception {
        String email = "r6-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
        MockHttpServletResponse r = call(post("/api/auth/register"), ip(), null,
                Map.of("name", "Ana Teste", "email", email, "password", PASSWORD, "acceptTerms", true));
        assertThat(r.getStatus()).isEqualTo(201);
        return new String[]{email, json.readTree(r.getContentAsString()).get("token").asText()};
    }

    private static String randomHash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    @Test
    @DisplayName("R6-3: /api/auth/%66orgot-password chega ao mesmo endpoint e conta no MESMO limite por IP")
    void encodedPathIsRateLimited() throws Exception {
        String attacker = ip();
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            codes.add(call(post(URI.create("/api/auth/%66orgot-password")), attacker, null, Map.of("email", "ninguem@teste.com")).getStatus());
        }
        assertThat(codes.get(0)).as("o caminho codificado chega mesmo ao endpoint").isEqualTo(202);
        assertThat(codes).as("antes: 25 × 202").contains(429);
    }

    @Test
    @DisplayName("R6-4: console H2 por caminho codificado continua \"só localhost\" (no MockMvc caía na regra geral; no Tomcat real o pedido já falhava antes)")
    void encodedDevToolPathStaysLocalOnly() throws Exception {
        MockHttpServletResponse r = call(get(URI.create("/h2-%63onsole/")), "192.168.1.50", null, null);
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentAsString()).contains("only available from localhost");
    }

    @Test
    @DisplayName("R6-2: 20 pedidos de nova senha simultâneos para a mesma conta geram UM link (antes: até 8)")
    void concurrentResetRequestsIssueOneLink() throws Exception {
        String email = register()[0];
        User user = users.findByEmail(email).orElseThrow();
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                done.add(pool.submit(() -> {
                    start.await();
                    passwordReset.request(email);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : done) f.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        long links = emailTokens.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), EmailToken.Purpose.RESET_PASSWORD,
                LocalDateTime.now().minusHours(1));
        assertThat(links).isEqualTo(1);
    }

    @Test
    @DisplayName("R6-1: reenviar a confirmação para no 5º e-mail do dia, mesmo respeitando o intervalo de 2 min")
    void resendHasDailyLimit() throws Exception {
        String[] account = register();                       // 1º e-mail: o do cadastro
        User user = users.findByEmail(account[0]).orElseThrow();
        LocalDateTime anHourAgo = LocalDateTime.now().minusHours(1);
        // simula envios anteriores ao longo do dia, todos fora do intervalo mínimo
        emailTokens.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId())).forEach(t -> {
            t.setCreatedAt(anHourAgo);
            emailTokens.save(t);
        });
        for (int i = 0; i < 3; i++) {
            emailTokens.save(EmailToken.builder().user(user).purpose(EmailToken.Purpose.VERIFY_EMAIL).tokenHash(randomHash())
                    .createdAt(anHourAgo).expiresAt(anHourAgo.plusHours(48)).usedAt(anHourAgo).build());
        }
        // 4 no dia: o 5º ainda sai (controle)
        assertThat(call(post("/api/account/verification-email"), ip(), account[1], null).getStatus()).isEqualTo(202);

        emailTokens.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId())).forEach(t -> {
            t.setCreatedAt(anHourAgo);
            emailTokens.save(t);
        });
        MockHttpServletResponse sixth = call(post("/api/account/verification-email"), ip(), account[1], null);
        assertThat(sixth.getStatus()).as("antes: 202, sem teto").isEqualTo(400);
        assertThat(sixth.getContentAsString()).contains("Daily limit of confirmation emails reached");
    }
}
