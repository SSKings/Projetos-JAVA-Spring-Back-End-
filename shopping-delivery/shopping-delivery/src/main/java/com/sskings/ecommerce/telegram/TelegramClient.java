package com.sskings.ecommerce.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chamadas à Bot API do Telegram (só saída HTTPS: long polling, sem webhook — a API não precisa ficar exposta).
 * O token faz parte da URL; toda mensagem de erro passa por {@link #redact} antes de virar exceção ou log.
 */
public class TelegramClient {

    /** Botão inline: o Telegram devolve {@code data} (até 64 bytes) no callback_query quando o admin clica. */
    public record Button(String text, String data) {}

    private final RestClient rest;
    private final String token;
    private final int pollTimeoutSeconds;

    public TelegramClient(RestClient.Builder builder, TelegramBotProperties props) {
        this.token = props.token();
        this.pollTimeoutSeconds = props.pollTimeoutSeconds();
        this.rest = builder.baseUrl(props.apiUrl() + "/bot" + token).build();
    }

    /** Long polling: bloqueia até chegar algo ou o timeout vencer. {@code offset} confirma o que já foi lido. */
    public List<JsonNode> getUpdates(long offset) {
        return getUpdates(offset, pollTimeoutSeconds);
    }

    /** Descarta o que se acumulou com a API fora do ar: comandos antigos não são executados de surpresa no restart. */
    public long skipBacklog() {
        List<JsonNode> last = getUpdates(-1, 0);
        return last.isEmpty() ? 0 : last.get(last.size() - 1).path("update_id").asLong() + 1;
    }

    private List<JsonNode> getUpdates(long offset, int timeout) {
        JsonNode result = call("getUpdates", Map.of(
                "offset", offset,
                "timeout", timeout,
                "allowed_updates", List.of("message", "callback_query")));
        List<JsonNode> updates = new ArrayList<>();
        result.forEach(updates::add);
        return updates;
    }

    public void sendMessage(long chatId, String html, List<List<Button>> keyboard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("link_preview_options", Map.of("is_disabled", true));
        if (keyboard != null && !keyboard.isEmpty()) {
            body.put("reply_markup", Map.of("inline_keyboard", keyboard.stream()
                    .map(row -> row.stream().map(b -> Map.of("text", b.text(), "callback_data", b.data())).toList())
                    .toList()));
        }
        call("sendMessage", body);
    }

    /** Fecha o "carregando" do botão; {@code text} aparece como aviso rápido (pode ser null). */
    public void answerCallback(String callbackId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackId);
        if (text != null) body.put("text", text);
        call("answerCallbackQuery", body);
    }

    private JsonNode call(String method, Map<String, Object> body) {
        JsonNode response;
        try {
            response = rest.post().uri("/" + method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new TelegramException(method + " failed: " + redact(e.getMessage()));
        }
        if (response == null || !response.path("ok").asBoolean(false)) {
            String description = response == null ? "empty response" : response.path("description").asText("unknown error");
            throw new TelegramException(method + " failed: " + redact(description));
        }
        return response.path("result");
    }

    String redact(String message) {
        return message == null ? null : message.replace(token, "<token>");
    }
}
