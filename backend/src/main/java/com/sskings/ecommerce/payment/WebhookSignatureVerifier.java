package com.sskings.ecommerce.payment;

import com.sskings.ecommerce.exception.InvalidSignatureException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Autentica notificações do PSP: assinatura = hex(HMAC-SHA256(secret, timestamp + "." + corpo bruto)).
 * O timestamp (epoch em segundos) limita replay a uma janela de 5 minutos; a comparação é em tempo constante.
 */
public class WebhookSignatureVerifier {

    static final long MAX_SKEW_SECONDS = 300;

    private final String secret;
    private final Clock clock;

    public WebhookSignatureVerifier(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    public void verify(String timestamp, String signature, String rawBody) {
        if (secret == null || secret.isBlank()) {
            throw new InvalidSignatureException("Pix webhook is not configured");
        }
        if (timestamp == null || signature == null || rawBody == null) {
            throw new InvalidSignatureException("Missing webhook signature");
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new InvalidSignatureException("Invalid webhook timestamp");
        }
        if (Math.abs(clock.instant().getEpochSecond() - sentAt) > MAX_SKEW_SECONDS) {
            throw new InvalidSignatureException("Webhook timestamp outside the allowed window");
        }
        byte[] expected = sign(secret, timestamp, rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] received = signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new InvalidSignatureException("Invalid webhook signature");
        }
    }

    public static String sign(String secret, String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
