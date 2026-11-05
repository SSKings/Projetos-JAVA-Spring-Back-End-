package com.sskings.ecommerce.exception;

/** Notificação de pagamento sem assinatura válida → 401. */
public class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException(String message) {
        super(message);
    }
}
