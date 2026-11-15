package com.sskings.ecommerce.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.event.SecurityAlertEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Revisão de segurança 5 (docs/security-review.md, seção 11): cada ataque confirmado no pentest de 2026-11-09 (tarde),
 * repetido contra a aplicação inteira, cada chamada de uma origem diferente (regra 12 do checklist).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security-review-5;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@RecordApplicationEvents
class SecurityReview5IntegrationTest {

    private static final String PASSWORD = "Vagalume4821";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.sskings.ecommerce.repository.UserRepository users;
    @Autowired ApplicationEvents events;

    private static int counter = 0;

    private static synchronized String ip() {
        counter++;
        return "10.5." + (counter / 250) + "." + (counter % 250 + 1);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        String ip = ip();
        req.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private MockHttpServletResponse register(String email, String password) throws Exception {
        return call(post("/api/auth/register"), null,
                Map.of("name", "Cliente Cinco", "email", email, "password", password, "acceptTerms", true));
    }

    private static String email() {
        return "r5-" + UUID.randomUUID() + "@teste.com";
    }

    @Test
    @DisplayName("R5-1: as senhas que o pentest cadastrou são recusadas; senha forte passa")
    void predictablePasswordsAreRefused() throws Exception {
        for (String weak : new String[]{"password1", "senha123", "12345678a", "qwerty123", "admin123", "shopeasy1", "Mudar123"}) {
            var r = register(email(), weak);
            assertThat(r.getStatus()).as(weak).isEqualTo(400);
            assertThat(body(r).get("message").asText()).as(weak).startsWith("Password");
        }
        assertThat(register(email(), PASSWORD).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("R5-2: token de admin roubado não confirma o Pix do pedido do atacante sem a senha do admin")
    void stolenAdminTokenCannotConfirmPix() throws Exception {
        String attacker = email();
        String token = body(register(attacker, PASSWORD)).get("token").asText();
        long address = body(call(post("/api/addresses"), token, Map.of("zipCode", "01000-000", "street", "Rua R5",
                "number", "1", "neighborhood", "Centro", "city", "São Paulo", "state", "SP"))).get("id").asLong();
        call(post("/api/cart/items"), token, Map.of("productId", 1, "quantity", 1));
        long order = body(call(post("/api/orders/checkout"), token, Map.of("addressId", address))).get("id").asLong();
        String admin = body(call(post("/api/auth/login"), null, Map.of("email", "admin@shopeasy.com", "password", "admin123")))
                .get("token").asText();

        assertThat(call(post("/api/admin/orders/" + order + "/payment/confirm"), admin, null).getStatus()).isEqualTo(400);
        assertThat(call(post("/api/admin/orders/" + order + "/payment/confirm"), admin, Map.of("password", "chute1234"))
                .getStatus()).isEqualTo(400);
        assertThat(call(patch("/api/admin/orders/" + order + "/status"), admin, Map.of("status", "SHIPPED")).getStatus())
                .isEqualTo(400);                                                         // Pix pendente não envia
        JsonNode stillPending = body(call(get("/api/orders/" + order), token, null));
        assertThat(stillPending.get("status").asText()).isEqualTo("PENDING");
        assertThat(stillPending.at("/payment/status").asText()).isEqualTo("PENDING");

        long before = events.stream(SecurityAlertEvent.class).count();
        assertThat(call(post("/api/admin/orders/" + order + "/payment/confirm"), admin, Map.of("password", "admin123"))
                .getStatus()).isEqualTo(200);
        assertThat(events.stream(SecurityAlertEvent.class).skip(before))
                .anySatisfy(e -> assertThat(e.text()).contains("Pix do pedido #" + order, "manualmente"));
    }

    @Test
    @DisplayName("R5-3: e-mail com letra de outro alfabeto (parecido com o do admin) é recusado")
    void lookalikeEmailIsRefused() throws Exception {
        var r = register("аdmin@shopeasy.com", PASSWORD);
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(r.getContentAsString()).contains("only unaccented letters");
        assertThat(users.findByEmail("аdmin@shopeasy.com")).isEmpty();
    }

    @Test
    @DisplayName("R5-6: o último admin ativo não exclui a própria conta")
    void lastAdminCannotDeleteAccount() throws Exception {
        String admin = body(call(post("/api/auth/login"), null, Map.of("email", "admin@shopeasy.com", "password", "admin123")))
                .get("token").asText();
        var r = call(post("/api/account/delete"), admin, Map.of("password", "admin123"));
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(body(r).get("message").asText()).startsWith("The last active admin");
        assertThat(users.findByEmail("admin@shopeasy.com")).get().extracting(u -> u.getAnonymizedAt()).isNull();
    }
}
