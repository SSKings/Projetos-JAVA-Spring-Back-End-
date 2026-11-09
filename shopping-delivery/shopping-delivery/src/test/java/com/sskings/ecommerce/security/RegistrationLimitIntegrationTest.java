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
                                "password", "senha12345", "acceptTerms", true))))
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
}
