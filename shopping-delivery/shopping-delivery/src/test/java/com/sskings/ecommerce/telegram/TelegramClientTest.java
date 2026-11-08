package com.sskings.ecommerce.telegram;

import com.sskings.ecommerce.telegram.TelegramClient.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static com.sskings.ecommerce.telegram.TelegramFixtures.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramClientTest {

    private static final String BASE = "https://telegram.test/bot" + TOKEN;
    private MockRestServiceServer server;
    private TelegramClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TelegramClient(builder, TelegramFixtures.props());
    }

    @Test
    @DisplayName("getUpdates: long polling com offset, só mensagens e cliques")
    void getUpdates() {
        server.expect(requestTo(BASE + "/getUpdates")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.offset").value(7))
                .andExpect(jsonPath("$.timeout").value(25))
                .andExpect(jsonPath("$.allowed_updates[1]").value("callback_query"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":[{\"update_id\":7},{\"update_id\":8}]}", MediaType.APPLICATION_JSON));

        assertThat(client.getUpdates(7)).extracting(u -> u.get("update_id").asLong()).containsExactly(7L, 8L);
        server.verify();
    }

    @Test
    @DisplayName("skipBacklog: pede só o último update (offset -1, sem espera) e devolve o próximo offset")
    void skipBacklog() {
        server.expect(requestTo(BASE + "/getUpdates")).andExpect(jsonPath("$.offset").value(-1)).andExpect(jsonPath("$.timeout").value(0))
                .andRespond(withSuccess("{\"ok\":true,\"result\":[{\"update_id\":41}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/getUpdates"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.skipBacklog()).isEqualTo(42);
        assertThat(client.skipBacklog()).isZero();
    }

    @Test
    @DisplayName("sendMessage: HTML, sem prévia de link e teclado inline")
    void sendMessage() {
        server.expect(requestTo(BASE + "/sendMessage"))
                .andExpect(jsonPath("$.chat_id").value(111))
                .andExpect(jsonPath("$.parse_mode").value("HTML"))
                .andExpect(jsonPath("$.link_preview_options.is_disabled").value(true))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].callback_data").value("x"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/sendMessage")).andExpect(jsonPath("$.reply_markup").doesNotExist())
                .andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));

        client.sendMessage(111, "<b>oi</b>", List.of(List.of(new Button("Sim", "!a"), new Button("Não", "x"))));
        client.sendMessage(111, "sem botões", List.of());
        server.verify();
    }

    @Test
    @DisplayName("answerCallback com e sem texto")
    void answerCallback() {
        server.expect(requestTo(BASE + "/answerCallbackQuery")).andExpect(jsonPath("$.text").value("ok"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/answerCallbackQuery")).andExpect(jsonPath("$.text").doesNotExist())
                .andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));
        client.answerCallback("cb", "ok");
        client.answerCallback("cb", null);
        server.verify();
    }

    @Test
    @DisplayName("ok=false, HTTP de erro e resposta vazia viram TelegramException")
    void errors() {
        server.expect(requestTo(BASE + "/sendMessage"))
                .andRespond(withSuccess("{\"ok\":false,\"description\":\"chat not found\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/sendMessage")).andRespond(withUnauthorizedRequest());
        server.expect(requestTo(BASE + "/sendMessage")).andRespond(withSuccess());

        assertThatThrownBy(() -> client.sendMessage(1, "a", null)).isInstanceOf(TelegramException.class).hasMessageContaining("chat not found");
        assertThatThrownBy(() -> client.sendMessage(1, "a", null)).isInstanceOf(TelegramException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> client.sendMessage(1, "a", null)).isInstanceOf(TelegramException.class).hasMessageContaining("empty response");
    }

    @Test
    @DisplayName("Erro de rede traz a URL com o token: a mensagem sai com <token> no lugar")
    void networkErrorRedactsToken() {
        server.expect(requestTo(BASE + "/getUpdates")).andRespond(withException(new IOException("timeout")));

        assertThatThrownBy(() -> client.getUpdates(1))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("<token>")
                .hasMessageNotContaining(TOKEN);
        assertThat(client.redact(null)).isNull();
    }
}
