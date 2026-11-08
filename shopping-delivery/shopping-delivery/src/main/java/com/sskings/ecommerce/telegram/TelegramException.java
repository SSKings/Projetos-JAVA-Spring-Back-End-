package com.sskings.ecommerce.telegram;

/** Falha ao falar com a API do Telegram. A mensagem já vem sem o token. */
public class TelegramException extends RuntimeException {
    public TelegramException(String message) {
        super(message);
    }
}
