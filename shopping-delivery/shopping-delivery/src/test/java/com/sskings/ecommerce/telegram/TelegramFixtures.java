package com.sskings.ecommerce.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.dto.response.AddressResponse;
import com.sskings.ecommerce.dto.response.AdminOrderResponse;
import com.sskings.ecommerce.dto.response.OrderItemResponse;
import com.sskings.ecommerce.dto.response.PaymentResponse;
import com.sskings.ecommerce.dto.response.UserResponse;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.OrderStatus;
import com.sskings.ecommerce.model.enums.PaymentMethod;
import com.sskings.ecommerce.model.enums.PaymentStatus;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.security.SecurityProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Dados de teste do bot: updates do Telegram, admin, pedido e usuário. */
final class TelegramFixtures {

    static final String TOKEN = "123456789:AAH-test_token_0123456789abcdefghijk";
    static final long ADMIN_CHAT = 111L;
    static final String ADMIN_EMAIL = "admin@shopeasy.com";
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 15, 30);
    private static final ObjectMapper JSON = new ObjectMapper();

    private TelegramFixtures() {}

    static TelegramBotProperties props() {
        return new TelegramBotProperties(true, TOKEN, ADMIN_CHAT + ":" + ADMIN_EMAIL, "https://telegram.test", 25);
    }

    static SecurityProperties security(boolean requireAdminMfa) {
        return new SecurityProperties(new SecurityProperties.Jwt("s", 120), "k", requireAdminMfa,
                new SecurityProperties.Login(3, 15), new SecurityProperties.RateLimit(true, 20, 120));
    }

    static User admin() {
        return User.builder().id(1L).name("Admin").email(ADMIN_EMAIL).role(Role.ADMIN).active(true).mfaEnabled(true).build();
    }

    static JsonNode message(long chatId, String text) {
        return JSON.valueToTree(Map.of("update_id", 10, "message", Map.of(
                "message_id", 5, "date", 0, "text", text,
                "chat", Map.of("id", chatId, "type", "private"), "from", Map.of("id", chatId))));
    }

    static JsonNode click(long chatId, String data) {
        return JSON.valueToTree(Map.of("update_id", 11, "callback_query", Map.of(
                "id", "cb-1", "data", data, "from", Map.of("id", chatId),
                "message", Map.of("message_id", 6, "date", 0, "chat", Map.of("id", chatId, "type", "private")))));
    }

    static PaymentResponse payment(PaymentStatus status) {
        return new PaymentResponse(PaymentMethod.PIX, status, new BigDecimal("59.90"), "TX1", null,
                NOW, NOW.plusMinutes(30), status == PaymentStatus.PAID ? NOW.plusMinutes(2) : null, 0);
    }

    static AdminOrderResponse order(OrderStatus status, PaymentResponse payment, LocalDateTime returnRequestedAt) {
        return new AdminOrderResponse(12L, status, new BigDecimal("59.90"), NOW, 5L, "Ana <b>Souza</b>", "ana@x.com",
                new AddressResponse(1L, "01000-000", "Rua A", "1", null, "Centro", "São Paulo", "SP"),
                List.of(new OrderItemResponse(1L, 10L, "Caneca & Cia", 2, new BigDecimal("29.95"), new BigDecimal("59.90"))),
                status.nextStatuses(), payment, status == OrderStatus.DELIVERED ? NOW : null, returnRequestedAt,
                returnRequestedAt != null ? "não <serviu>" : null);
    }

    static UserResponse user(long id, Role role, boolean active) {
        return new UserResponse(id, "Ana Souza", "ana@x.com", null, role, active, NOW, 3);
    }
}
