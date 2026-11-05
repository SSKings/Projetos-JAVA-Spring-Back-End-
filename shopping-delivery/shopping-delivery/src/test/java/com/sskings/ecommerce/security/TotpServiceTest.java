package com.sskings.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpServiceTest {

    /** Segredo dos vetores de teste da RFC 6238 (apêndice B), SHA1. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static TotpService at(long epochSecond) {
        return new TotpService(Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Vetores oficiais da RFC 6238 (6 últimos dígitos)")
    void rfcVectors() {
        assertThat(TotpService.generate(RFC_KEY, 59 / 30)).isEqualTo("287082");            // 94287082
        assertThat(TotpService.generate(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");   // 07081804
        assertThat(TotpService.generate(RFC_KEY, 1234567890L / 30)).isEqualTo("005924");   // 89005924
        assertThat(TotpService.generate(RFC_KEY, 2000000000L / 30)).isEqualTo("279037");   // 69279037
    }

    @Test
    @DisplayName("Base32 ida e volta; caractere inválido é recusado")
    void base32() {
        String encoded = TotpService.base32(RFC_KEY);
        assertThat(encoded).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(TotpService.decodeBase32(encoded.toLowerCase() + "==")).isEqualTo(RFC_KEY);
        assertThatThrownBy(() -> TotpService.decodeBase32("ABC1")).hasMessage("Invalid Base32");
    }

    @Test
    @DisplayName("Aceita ±1 passo de 30 s, recusa fora da janela, formato inválido e reuso do mesmo passo")
    void verify() {
        String secret = TotpService.base32(RFC_KEY);
        TotpService now = at(1111111109L);
        String code = now.currentCode(secret);

        long step = now.verify(secret, code, null).orElseThrow();
        assertThat(step).isEqualTo(1111111109L / 30);
        assertThat(at(1111111109L + 30).verify(secret, code, null)).isPresent();   // relógio do celular adiantado
        assertThat(at(1111111109L - 30).verify(secret, code, null)).isPresent();
        assertThat(at(1111111109L + 90).verify(secret, code, null)).isEmpty();
        assertThat(now.verify(secret, code, step)).isEmpty();                      // reuso
        assertThat(now.verify(secret, "12345", null)).isEmpty();
        assertThat(now.verify(secret, null, null)).isEmpty();
        assertThat(now.verify(secret, "abcdef", null)).isEmpty();
    }

    @Test
    @DisplayName("Segredo novo tem 160 bits e o URI otpauth segue o formato do Google Authenticator")
    void secretAndUri() {
        TotpService totp = at(0);
        String secret = totp.newSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(TotpService.decodeBase32(secret)).hasSize(20);
        assertThat(totp.newSecret()).isNotEqualTo(secret);
        assertThat(totp.otpauthUri("ShopEasy", "ana@a.com", "ABC"))
                .isEqualTo("otpauth://totp/ShopEasy%3Aana%40a.com?secret=ABC&issuer=ShopEasy&algorithm=SHA1&digits=6&period=30");
    }
}
