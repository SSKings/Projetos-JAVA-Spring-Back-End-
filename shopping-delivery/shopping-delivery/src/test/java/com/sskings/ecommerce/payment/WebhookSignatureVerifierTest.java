package com.sskings.ecommerce.payment;

import com.sskings.ecommerce.exception.InvalidSignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final String TS = String.valueOf(NOW.getEpochSecond());
    private static final String BODY = "{\"txid\":\"T1\",\"endToEndId\":\"E1\",\"amount\":10.00}";
    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("s3cret", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Assinatura correta é aceita (inclusive em maiúsculas)")
    void valid() {
        String sig = WebhookSignatureVerifier.sign("s3cret", TS, BODY);
        assertThat(sig).hasSize(64).matches("[0-9a-f]+");
        assertThatCode(() -> verifier.verify(TS, sig, BODY)).doesNotThrowAnyException();
        assertThatCode(() -> verifier.verify(TS, sig.toUpperCase(), BODY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Corpo, timestamp ou segredo diferentes invalidam a assinatura")
    void tampered() {
        String sig = WebhookSignatureVerifier.sign("s3cret", TS, BODY);
        assertThatThrownBy(() -> verifier.verify(TS, sig, BODY.replace("10.00", "1.00")))
                .isInstanceOf(InvalidSignatureException.class).hasMessage("Invalid webhook signature");
        String other = String.valueOf(NOW.getEpochSecond() - 10);
        assertThatThrownBy(() -> verifier.verify(other, sig, BODY)).hasMessage("Invalid webhook signature");
        assertThatThrownBy(() -> verifier.verify(TS, WebhookSignatureVerifier.sign("outro", TS, BODY), BODY))
                .hasMessage("Invalid webhook signature");
    }

    @Test
    @DisplayName("Timestamp fora da janela de 5 minutos (replay) é recusado")
    void replay() {
        String old = String.valueOf(NOW.getEpochSecond() - 301);
        String future = String.valueOf(NOW.getEpochSecond() + 301);
        String edge = String.valueOf(NOW.getEpochSecond() - 300);
        assertThatThrownBy(() -> verifier.verify(old, WebhookSignatureVerifier.sign("s3cret", old, BODY), BODY))
                .hasMessage("Webhook timestamp outside the allowed window");
        assertThatThrownBy(() -> verifier.verify(future, WebhookSignatureVerifier.sign("s3cret", future, BODY), BODY))
                .hasMessage("Webhook timestamp outside the allowed window");
        assertThatCode(() -> verifier.verify(edge, WebhookSignatureVerifier.sign("s3cret", edge, BODY), BODY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Cabeçalhos ausentes, timestamp inválido ou segredo não configurado")
    void missingOrDisabled() {
        assertThatThrownBy(() -> verifier.verify(null, "x", BODY)).hasMessage("Missing webhook signature");
        assertThatThrownBy(() -> verifier.verify(TS, null, BODY)).hasMessage("Missing webhook signature");
        assertThatThrownBy(() -> verifier.verify(TS, "x", null)).hasMessage("Missing webhook signature");
        assertThatThrownBy(() -> verifier.verify("abc", "x", BODY)).hasMessage("Invalid webhook timestamp");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        assertThatThrownBy(() -> new WebhookSignatureVerifier("", clock).verify(TS, "x", BODY))
                .hasMessage("Pix webhook is not configured");
        assertThatThrownBy(() -> new WebhookSignatureVerifier(null, clock).verify(TS, "x", BODY))
                .hasMessage("Pix webhook is not configured");
    }
}
