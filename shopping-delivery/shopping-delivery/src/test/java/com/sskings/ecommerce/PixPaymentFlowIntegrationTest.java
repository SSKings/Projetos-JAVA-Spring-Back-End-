package com.sskings.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.controller.PaymentController;
import com.sskings.ecommerce.payment.WebhookSignatureVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo real (Spring + Flyway + H2 + segurança): também garante que a migração V4 bate com a entidade Payment
 * (ddl-auto: validate) e que o webhook é público mas exige assinatura.
 */
@SpringBootTest(properties = "app.pix.webhook-secret=integration-secret")
@AutoConfigureMockMvc
class PixPaymentFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.admin.email}") String adminEmail;
    @Value("${app.admin.password}") String adminPassword;

    private JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String resp = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return resp.isEmpty() ? null : json.readTree(resp);
    }

    private String register() throws Exception {
        String email = "pix-" + UUID.randomUUID() + "@teste.com";
        return call(post("/api/auth/register"), null,
                java.util.Map.of("name", "Cliente Pix", "email", email, "password", "abc12345", "acceptTerms", true), 201).get("token").asText();
    }

    private JsonNode checkout(String token) throws Exception {
        call(post("/api/cart/items"), token, java.util.Map.of("productId", 1, "quantity", 2), 200);
        long addressId = call(post("/api/addresses"), token, java.util.Map.of("zipCode", "01000-000", "street", "Rua A",
                "number", "1", "neighborhood", "Centro", "city", "Sao Paulo", "state", "SP"), 201).get("id").asLong();
        return call(post("/api/orders/checkout"), token, java.util.Map.of("addressId", addressId, "paymentMethod", "PIX"), 201);
    }

    private int stockOfProduct1() throws Exception {
        return call(get("/api/products/1"), null, null, 200).get("stockQuantity").asInt();
    }

    private String admin() throws Exception {
        return call(post("/api/auth/login"), null, java.util.Map.of("email", adminEmail, "password", adminPassword), 200)
                .get("token").asText();
    }

    @Test
    @DisplayName("Checkout gera Pix → admin não avança pedido não pago → webhook assinado confirma → pedido CONFIRMED")
    void pixPaidViaWebhook() throws Exception {
        String customer = register();
        int stockBefore = stockOfProduct1();
        JsonNode order = checkout(customer);
        long orderId = order.get("id").asLong();
        JsonNode payment = order.get("payment");

        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(payment.get("status").asText()).isEqualTo("PENDING");
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo(order.get("total").decimalValue());
        String copyPaste = payment.get("pixCopyPaste").asText();
        assertThat(copyPaste).startsWith("000201").contains("br.gov.bcb.pix", payment.get("txid").asText());
        assertThat(stockOfProduct1()).isEqualTo(stockBefore - 2);
        assertThat(payment.get("expiresInSeconds").asLong()).isBetween(1700L, 1800L);

        // admin: pedido aguardando Pix só pode ser cancelado
        String admin = admin();
        JsonNode adminOrder = call(get("/api/admin/orders/" + orderId), admin, null, 200);
        assertThat(adminOrder.get("nextStatuses").toString()).isEqualTo("[\"CANCELLED\"]");
        assertThat(call(patch("/api/admin/orders/" + orderId + "/status"), admin, java.util.Map.of("status", "SHIPPED"), 400)
                .get("message").asText()).isEqualTo("Order payment is pending");

        // webhook sem assinatura → 401; com assinatura → 200
        String body = json.writeValueAsString(java.util.Map.of("txid", payment.get("txid").asText(),
                "endToEndId", "E00000000202611051200abcdefghijk", "amount", payment.get("amount").decimalValue()));
        mvc.perform(post("/api/payments/pix/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String ts = String.valueOf(Instant.now().getEpochSecond());
        mvc.perform(post("/api/payments/pix/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(PaymentController.TIMESTAMP_HEADER, ts)
                        .header(PaymentController.SIGNATURE_HEADER, WebhookSignatureVerifier.sign("integration-secret", ts, body)))
                .andExpect(status().isOk());

        JsonNode paid = call(get("/api/orders/" + orderId), customer, null, 200);
        assertThat(paid.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(paid.get("payment").get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("payment").get("pixCopyPaste").isNull()).isTrue();

        // outro cliente não enxerga o pagamento
        call(get("/api/orders/" + orderId + "/payment"), register(), null, 404);
    }

    @Test
    @DisplayName("Admin confirma Pix manualmente; cancelar outro pedido não pago devolve o estoque")
    void manualConfirmationAndCancel() throws Exception {
        String customer = register();
        String admin = admin();
        long first = checkout(customer).get("id").asLong();

        JsonNode confirmed = call(post("/api/admin/orders/" + first + "/payment/confirm"), admin, null, 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("payment").get("status").asText()).isEqualTo("PAID");
        assertThat(confirmed.get("nextStatuses").toString()).isEqualTo("[\"SHIPPED\",\"DELIVERED\",\"CANCELLED\"]");
        call(post("/api/admin/orders/" + first + "/payment/confirm"), admin, null, 400); // já pago

        int stock = stockOfProduct1();
        long second = checkout(customer).get("id").asLong();
        assertThat(stockOfProduct1()).isEqualTo(stock - 2);
        JsonNode cancelled = call(patch("/api/admin/orders/" + second + "/status"), admin, java.util.Map.of("status", "CANCELLED"), 200);
        assertThat(cancelled.get("payment").get("status").asText()).isEqualTo("CANCELLED");
        assertThat(stockOfProduct1()).isEqualTo(stock);

        // cliente não usa a rota de admin
        call(post("/api/admin/orders/" + second + "/payment/confirm"), customer, null, 403);
    }
}
