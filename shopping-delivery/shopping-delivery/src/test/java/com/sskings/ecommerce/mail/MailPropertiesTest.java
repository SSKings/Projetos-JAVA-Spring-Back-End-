package com.sskings.ecommerce.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MailPropertiesTest {

    @Test
    @DisplayName("Padrões: sem remetente/URL vira texto vazio, assíncrono ligado, nome da loja ShopEasy; URL sem barra no fim")
    void defaults() {
        var p = new MailProperties(true, null, null, null, " ");
        assertThat(p.from()).isEmpty();
        assertThat(p.publicUrl()).isEmpty();
        assertThat(p.async()).isTrue();
        assertThat(p.storeName()).isEqualTo("ShopEasy");
        assertThat(new MailProperties(true, " a@b.com ", "https://loja.com.br//", false, " Loja ").link("/x"))
                .isEqualTo("https://loja.com.br/x");
    }

    @Test
    @DisplayName("Thread de e-mail: uma só, nomeada e daemon (não segura o desligamento da API)")
    void executor() throws Exception {
        var executor = new MailConfig().mailExecutor();
        try {
            var thread = CompletableFuture.supplyAsync(Thread::currentThread, executor).get(5, TimeUnit.SECONDS);
            assertThat(thread.getName()).isEqualTo("mail-outbox");
            assertThat(thread.isDaemon()).isTrue();
        } finally {
            executor.shutdown();
        }
    }
}
