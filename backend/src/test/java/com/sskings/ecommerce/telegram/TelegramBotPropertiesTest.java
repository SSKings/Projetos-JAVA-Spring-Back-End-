package com.sskings.ecommerce.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.sskings.ecommerce.telegram.TelegramFixtures.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

class TelegramBotPropertiesTest {

    @Test
    @DisplayName("Padrões: API oficial e long polling de 25 s")
    void defaults() {
        var p = new TelegramBotProperties(false, null, null, null, null);
        assertThat(p.apiUrl()).isEqualTo("https://api.telegram.org");
        assertThat(p.pollTimeoutSeconds()).isEqualTo(25);
        assertThat(p.adminChats()).isEmpty();
    }

    @Test
    @DisplayName("Lista de admins: chatId:email, e-mail normalizado, entradas inválidas fora do mapa")
    void adminChats() {
        var p = new TelegramBotProperties(true, TOKEN, " 111:Admin@Loja.com , 222:outro@loja.com,,lixo ", null, null);
        assertThat(p.adminChats()).isEqualTo(Map.of(111L, "admin@loja.com", 222L, "outro@loja.com"));
        assertThat(p.problems()).singleElement().asString().contains("invalid entries").contains("lixo");
    }

    @Test
    @DisplayName("Configuração válida não tem problemas")
    void valid() {
        assertThat(TelegramFixtures.props().problems()).isEmpty();
    }

    @Test
    @DisplayName("Token, admins e timeout inválidos são apontados — sem expor o token")
    void problems() {
        var p = new TelegramBotProperties(true, "abc:segredo", "", null, 0);
        assertThat(p.problems()).hasSize(3)
                .anyMatch(s -> s.contains("TELEGRAM_ADMIN_BOT_TOKEN"))
                .anyMatch(s -> s.contains("at least one"))
                .anyMatch(s -> s.contains("poll-timeout"))
                .noneMatch(s -> s.contains("segredo"));
        assertThat(new TelegramBotProperties(true, TOKEN, "1:a@b.c", null, 51).problems()).hasSize(1);
    }

    @Test
    @DisplayName("toString não inclui o token")
    void toStringHidesToken() {
        assertThat(TelegramFixtures.props().toString()).doesNotContain(TOKEN).contains("admins=111:");
    }
}
