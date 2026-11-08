package com.sskings.ecommerce.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sskings.ecommerce.repository.AuditLogRepository;
import com.sskings.ecommerce.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static com.sskings.ecommerce.telegram.TelegramFixtures.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo real: checkout pela API → evento após o commit → aviso no Telegram; depois o admin cancela pelo bot e a
 * auditoria registra o admin como autor. Só a rede do Telegram (TelegramClient) e o long polling são simulados.
 */
@SpringBootTest(properties = {
        "app.telegram-bot.enabled=true",
        "app.telegram-bot.token=" + TOKEN,
        "app.telegram-bot.admins=4242:admin@shopeasy.com"})
@AutoConfigureMockMvc
class TelegramBotIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TelegramAdminBot bot;
    @Autowired AuditLogRepository auditLogs;
    @Value("${app.admin.email}") String adminEmail;
    @MockitoBean TelegramClient client;
    @MockitoBean TelegramBotPoller poller;

    private JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Checkout avisa o admin no Telegram; cancelamento pelo bot devolve o pedido e fica auditado com o admin")
    void checkoutNotifiesAndBotCancels() throws Exception {
        String token = call(post("/api/auth/register"), null, Map.of("name", "Cliente Bot", "email",
                "bot-" + UUID.randomUUID() + "@teste.com", "password", "abc12345", "acceptTerms", true), 201).get("token").asText();
        call(post("/api/cart/items"), token, Map.of("productId", 1, "quantity", 1), 200);
        long addressId = call(post("/api/addresses"), token, Map.of("zipCode", "01000-000", "street", "Rua A",
                "number", "1", "neighborhood", "Centro", "city", "Sao Paulo", "state", "SP"), 201).get("id").asLong();
        long orderId = call(post("/api/orders/checkout"), token, Map.of("addressId", addressId, "paymentMethod", "PIX"), 201)
                .get("id").asLong();

        verify(client, timeout(5000)).sendMessage(eq(4242L), contains("Novo pedido #" + orderId), anyList());

        bot.handle(TelegramFixtures.click(4242, "!st:" + orderId + ":CANCELLED"));

        verify(client).sendMessage(eq(4242L), contains("✔ Pedido #" + orderId + " agora está ❌ Cancelado"), anyList());
        var audit = auditLogs.findAllByAction(AuditService.ORDER_STATUS_CHANGED, PageRequest.of(0, 50)).getContent().stream()
                .filter(a -> String.valueOf(orderId).equals(a.getTargetId())).findFirst().orElseThrow();
        assertThat(audit.getActorEmail()).isEqualTo(adminEmail);
        assertThat(audit.getDetails()).isEqualTo("PENDING -> CANCELLED");
    }

    @Test
    @DisplayName("Bot ligado com token ou admins inválidos impede o startup (sem expor o token)")
    void invalidConfigurationFailsFast() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TelegramBotConfig.class)
                .withPropertyValues("app.telegram-bot.enabled=true", "app.telegram-bot.token=nao-e-token",
                        "app.telegram-bot.admins=")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("TELEGRAM_ADMIN_BOT_TOKEN").hasMessageNotContaining("nao-e-token"));

        new ApplicationContextRunner()
                .withUserConfiguration(TelegramBotConfig.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(TelegramBotPoller.class));   // desligado por padrão
    }

    @Test
    @DisplayName("Configuração válida cria cliente, bot, poller (iniciado) e notificador; Telegram fora do ar não derruba nada")
    void validConfigurationStartsBot() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TelegramBotConfig.class)
                .withBean(org.springframework.web.client.RestClient.Builder.class, org.springframework.web.client.RestClient::builder)
                .withBean(com.sskings.ecommerce.security.SecurityProperties.class, () -> TelegramFixtures.security(true))
                .withBean(com.sskings.ecommerce.repository.UserRepository.class, () -> mock(com.sskings.ecommerce.repository.UserRepository.class))
                .withBean(com.sskings.ecommerce.service.admin.AdminOrderService.class, () -> mock(com.sskings.ecommerce.service.admin.AdminOrderService.class))
                .withBean(com.sskings.ecommerce.service.admin.AdminUserService.class, () -> mock(com.sskings.ecommerce.service.admin.AdminUserService.class))
                .withBean(com.sskings.ecommerce.service.admin.AdminDashboardService.class, () -> mock(com.sskings.ecommerce.service.admin.AdminDashboardService.class))
                .withPropertyValues("app.telegram-bot.enabled=true", "app.telegram-bot.token=" + TOKEN,
                        "app.telegram-bot.admins=1:a@b.com", "app.telegram-bot.api-url=http://127.0.0.1:1")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(TelegramClient.class).hasSingleBean(TelegramOrderNotifier.class);
                    assertThat(ctx.getBean(TelegramBotPoller.class).isRunning()).isTrue();
                });
    }
}
