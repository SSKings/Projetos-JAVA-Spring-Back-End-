package com.sskings.ecommerce.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bot de administração no Telegram (ADR-024). Desligado por padrão.
 *
 * @param enabled            liga o bot (TELEGRAM_ADMIN_BOT_ENABLED)
 * @param token              token do @BotFather (TELEGRAM_ADMIN_BOT_TOKEN) — um bot diferente do bot dos alertas do Grafana
 * @param admins             quem pode usar: "chatId:email-do-admin" separados por vírgula (TELEGRAM_ADMIN_BOT_ADMINS).
 *                           O chat só age como aquele admin, e só enquanto a conta for ADMIN ativa (com MFA, se exigido)
 * @param apiUrl             API do Telegram (trocada só nos testes)
 * @param pollTimeoutSeconds espera do long polling (getUpdates)
 */
@ConfigurationProperties("app.telegram-bot")
public record TelegramBotProperties(boolean enabled, String token, String admins, String apiUrl, Integer pollTimeoutSeconds) {

    static final Pattern TOKEN = Pattern.compile("^\\d+:[A-Za-z0-9_-]{30,}$");
    static final Pattern ADMIN = Pattern.compile("^(\\d+):([^\\s@:,]+@[^\\s@:,]+)$");

    public TelegramBotProperties {
        apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.telegram.org" : apiUrl;
        pollTimeoutSeconds = pollTimeoutSeconds == null ? 25 : pollTimeoutSeconds;
    }

    /** chat ID → email do admin, na ordem da configuração. Entradas inválidas são ignoradas aqui e apontadas por {@link #problems()}. */
    public Map<Long, String> adminChats() {
        Map<Long, String> chats = new LinkedHashMap<>();
        for (String entry : entries()) {
            var m = ADMIN.matcher(entry);
            if (m.matches()) chats.put(Long.parseLong(m.group(1)), m.group(2).toLowerCase());
        }
        return Collections.unmodifiableMap(chats);
    }

    /** O que impede o bot de subir. A mensagem nunca inclui o token. */
    public List<String> problems() {
        List<String> p = new ArrayList<>();
        if (token == null || !TOKEN.matcher(token).matches()) {
            p.add("TELEGRAM_ADMIN_BOT_TOKEN must be the token from @BotFather (123456789:AA...)");
        }
        List<String> invalid = entries().stream().filter(e -> !ADMIN.matcher(e).matches()).toList();
        if (!invalid.isEmpty()) {
            p.add("TELEGRAM_ADMIN_BOT_ADMINS has invalid entries (expected chatId:admin-email): " + invalid);
        }
        if (entries().isEmpty()) {
            p.add("TELEGRAM_ADMIN_BOT_ADMINS must list at least one chatId:admin-email");
        }
        if (pollTimeoutSeconds < 1 || pollTimeoutSeconds > 50) {
            p.add("app.telegram-bot.poll-timeout-seconds must be between 1 and 50");
        }
        return p;
    }

    private List<String> entries() {
        if (admins == null || admins.isBlank()) return List.of();
        return java.util.Arrays.stream(admins.split(",")).map(String::trim).filter(e -> !e.isEmpty()).toList();
    }

    /** Sem o token: o record é logado por engano em algum debug e o segredo não pode ir junto. */
    @Override
    public String toString() {
        return "TelegramBotProperties[enabled=" + enabled + ", admins=" + admins + ", apiUrl=" + apiUrl
                + ", pollTimeoutSeconds=" + pollTimeoutSeconds + "]";
    }
}
