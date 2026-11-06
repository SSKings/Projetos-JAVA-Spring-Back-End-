package com.sskings.ecommerce.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observabilidade (ADR-022): o Actuator não pode virar vazamento de informação. Health é público mas só diz
 * UP/DOWN; métricas (que revelam rotas, volume e versões) exigem ADMIN; o resto do Actuator nem é exposto.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:actuator-security;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
// @SpringBootTest desliga os exportadores de métricas por padrão; sem isto /actuator/prometheus nem existiria
@AutoConfigureObservability
class ActuatorSecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token(String path, Map<String, Object> body) throws Exception {
        String resp = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(resp).get("token").asText();
    }

    private String admin() throws Exception {
        // senha fixa só nos testes (src/test/resources/config/application-dev.yml)
        return token("/api/auth/login", Map.of("email", "admin@shopeasy.com", "password", "admin123"));
    }

    private String customer() throws Exception {
        return token("/api/auth/register", Map.of("name", "Cliente Teste", "email", "obs-" + UUID.randomUUID() + "@teste.com",
                "password", "senha12345", "acceptTerms", true));
    }

    @Test
    @DisplayName("health é público e anônimo vê só o status, sem detalhes de banco/disco")
    void health_isPublicWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN vê os detalhes do health")
    void health_adminSeesDetails() throws Exception {
        mvc.perform(get("/actuator/health").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @ParameterizedTest(name = "{0} exige login")
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/jvm.memory.used"})
    void metrics_anonymous_is401(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} é negado a cliente")
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/metrics"})
    void metrics_customer_is403(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", customer())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN lê as métricas no formato Prometheus")
    void prometheus_admin_isOk() throws Exception {
        mvc.perform(get("/actuator/prometheus").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @ParameterizedTest(name = "{0} não é exposto nem para ADMIN")
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/loggers"})
    void sensitiveEndpoints_notExposed(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", admin())).andExpect(status().isNotFound());
    }
}
