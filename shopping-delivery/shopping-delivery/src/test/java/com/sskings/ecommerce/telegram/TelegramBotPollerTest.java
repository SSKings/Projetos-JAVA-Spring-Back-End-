package com.sskings.ecommerce.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sskings.ecommerce.telegram.TelegramFixtures.ADMIN_CHAT;
import static com.sskings.ecommerce.telegram.TelegramFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

class TelegramBotPollerTest {

    private final TelegramClient client = mock(TelegramClient.class);
    private final TelegramAdminBot bot = mock(TelegramAdminBot.class);
    private final TelegramBotPoller poller = new TelegramBotPoller(client, bot);

    private static JsonNode update(long id) {
        var u = (com.fasterxml.jackson.databind.node.ObjectNode) message(ADMIN_CHAT, "/ajuda");
        return u.put("update_id", id);
    }

    @Test
    @DisplayName("Primeira rodada descarta o acumulado; o offset avança a cada update tratado")
    void pollOnce() {
        given(client.skipBacklog()).willReturn(50L);
        given(client.getUpdates(50L)).willReturn(List.of(update(50), update(51)));
        given(client.getUpdates(52L)).willReturn(List.of());

        poller.pollOnce();
        poller.pollOnce();

        verify(client, times(1)).skipBacklog();
        verify(bot, times(2)).handle(any());
        assertThat(poller.offset()).isEqualTo(52);
    }

    @Test
    @DisplayName("Erro num update não trava a fila: o offset já andou e o seguinte é tratado")
    void handlerErrorIsIsolated() {
        JsonNode bad = update(7);
        given(client.getUpdates(anyLong())).willReturn(List.of(bad, update(8)));
        willThrow(new IllegalStateException("boom")).given(bot).handle(bad);

        poller.pollOnce();

        verify(bot, times(2)).handle(any());
        assertThat(poller.offset()).isEqualTo(9);
    }

    @Test
    @DisplayName("Espera entre tentativas cresce até 60 s")
    void backoff() {
        assertThat(TelegramBotPoller.backoffSeconds(1)).isEqualTo(2);
        assertThat(TelegramBotPoller.backoffSeconds(3)).isEqualTo(8);
        assertThat(TelegramBotPoller.backoffSeconds(6)).isEqualTo(60);
        assertThat(TelegramBotPoller.backoffSeconds(40)).isEqualTo(60);
    }

    @Test
    @DisplayName("start/stop: a thread consulta o Telegram, sobrevive a falhas e para no stop")
    void lifecycle() throws InterruptedException {
        given(client.skipBacklog()).willThrow(new TelegramException("getUpdates failed: offline")).willReturn(0L);
        given(client.getUpdates(anyLong())).willAnswer(inv -> {
            Thread.sleep(20);   // simula o long polling
            return List.of();
        });

        poller.start();
        poller.start();   // idempotente
        assertThat(poller.isRunning()).isTrue();
        verify(client, timeout(5000).atLeast(2)).skipBacklog();   // falhou, esperou 2 s e tentou de novo
        verify(client, timeout(2000).atLeastOnce()).getUpdates(anyLong());

        poller.stop();
        assertThat(poller.isRunning()).isFalse();
    }

    @Test
    @DisplayName("stop durante a espera de nova tentativa encerra a thread")
    void stopWhileBackingOff() throws InterruptedException {
        given(client.skipBacklog()).willThrow(new TelegramException("offline"));
        poller.start();
        verify(client, timeout(2000)).skipBacklog();
        poller.stop();
        Thread.sleep(100);
        verify(client, times(1)).skipBacklog();
        new TelegramBotPoller(client, bot).stop();   // stop sem start não falha
    }
}
