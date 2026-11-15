package com.sskings.ecommerce.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.mail.* (ADR-025). O servidor SMTP é o spring.mail.* padrão do Spring Boot (host, porta, usuário, senha, TLS).
 *
 * @param enabled   desligado = nada é enviado (só log); os links continuam sendo gerados
 * @param from      remetente; no Gmail precisa ser a própria conta (ou um alias verificado nela)
 * @param publicUrl endereço do front-end usado nos links (ex.: https://loja.com.br) — sem barra no fim
 * @param async     envia numa thread própria depois do commit; false só nos testes
 * @param storeName nome da loja na assinatura e no assunto
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String from, String publicUrl, Boolean async, String storeName) {

    public MailProperties {
        storeName = storeName == null || storeName.isBlank() ? "ShopEasy" : storeName.trim();
        from = from == null ? "" : from.trim();
        publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
        async = async == null || async;
    }

    public String link(String path) {
        return publicUrl + path;
    }
}
