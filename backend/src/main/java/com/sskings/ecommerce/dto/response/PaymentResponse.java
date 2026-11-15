package com.sskings.ecommerce.dto.response;

import com.sskings.ecommerce.model.Payment;
import com.sskings.ecommerce.model.enums.PaymentMethod;
import com.sskings.ecommerce.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * O "copia e cola" só é exposto enquanto o pagamento está pendente.
 * expiresInSeconds é calculado no servidor: o contador do front-end não depende do relógio/fuso do cliente.
 */
public record PaymentResponse(
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        String txid,
        String pixCopyPaste,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        long expiresInSeconds
) {
    public static PaymentResponse from(Payment p) {
        return from(p, LocalDateTime.now());
    }

    public static PaymentResponse from(Payment p, LocalDateTime now) {
        if (p == null) return null;
        long remaining = p.isPending() ? Math.max(0, Duration.between(now, p.getExpiresAt()).getSeconds()) : 0;
        return new PaymentResponse(p.getMethod(), p.getStatus(), p.getAmount(), p.getTxid(),
                p.isPending() ? p.getPixPayload() : null,
                p.getCreatedAt(), p.getExpiresAt(), p.getPaidAt(), remaining);
    }
}
