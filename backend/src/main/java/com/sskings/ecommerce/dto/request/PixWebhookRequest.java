package com.sskings.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Notificação de Pix recebido, enviada pelo PSP (assinada — ver WebhookSignatureVerifier). */
public record PixWebhookRequest(
        @NotBlank String txid,
        @NotBlank String endToEndId,
        @NotNull @Positive BigDecimal amount
) {}
