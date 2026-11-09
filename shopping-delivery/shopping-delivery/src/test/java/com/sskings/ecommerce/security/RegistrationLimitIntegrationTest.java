package com.sskings.ecommerce.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Revisão 4, R4-4: contas novas por origem em 24 h (produção: 10; aqui 2 para o teste). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration-limit;DB_CLOSE_DELAY=-1",
        "security.rate-limit.registrations-per-day=2"})
@AutoConfigureMockMvc
class RegistrationLimitIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private int register(String ip) throws Exception {
        return mvc.perform(post("/api/auth/register").with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Conta Teste", "email", UUID.randomUUID() + "@teste.com",
                                "password", "Vagalume4821", "acceptTerms", true))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("3ª conta da mesma origem no dia → 429; IPv6 do mesmo /64 é a mesma origem; outra origem cadastra")
    void limitsAccountsPerSource() throws Exception {
        assertThat(register("2001:db8:aa:1::1")).isEqualTo(201);
        assertThat(register("2001:db8:aa:1::2")).isEqualTo(201);
        assertThat(register("2001:db8:aa:1::3")).isEqualTo(429);
        assertThat(register("198.51.100.9")).isEqualTo(201);
    }

    @Test
    @DisplayName("R5-7: 20 cadastros simultâneos da mesma origem — só 2 passam (reserva atômica)")
    void concurrentRegistrationsRespectTheLimit() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(10);
        var start = new java.util.concurrent.CountDownLatch(1);
        var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> { start.await(); return register("203.0.113.77"); }));
        }
        start.countDown();
        java.util.List<Integer> statuses = new java.util.ArrayList<>();
        for (var f : results) statuses.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(2);
        assertThat(statuses.stream().filter(s -> s == 429).count()).isEqualTo(18);
    }
}
