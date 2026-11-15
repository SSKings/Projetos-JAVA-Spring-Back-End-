package com.sskings.ecommerce.exception;

/** → 403 (ex.: IP de origem fora da allowlist do webhook). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
