package com.sskings.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private static String key(int bytes, int fill) {
        byte[] k = new byte[bytes];
        java.util.Arrays.fill(k, (byte) fill);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    @DisplayName("AES-GCM: cifra e decifra; IV aleatório gera textos diferentes")
    void roundTrip() {
        CryptoService crypto = new CryptoService(key(32, 1));
        String a = crypto.encrypt("segredo-totp");
        String b = crypto.encrypt("segredo-totp");
        assertThat(a).isNotEqualTo(b).doesNotContain("segredo");
        assertThat(crypto.decrypt(a)).isEqualTo("segredo-totp");
    }

    @Test
    @DisplayName("Chave errada ou texto adulterado não decifram (autenticação do GCM)")
    void tamperAndWrongKey() {
        String encrypted = new CryptoService(key(32, 1)).encrypt("x");
        assertThatThrownBy(() -> new CryptoService(key(32, 2)).decrypt(encrypted)).hasMessage("Decryption failed");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> new CryptoService(key(32, 1)).decrypt(Base64.getEncoder().encodeToString(raw)))
                .hasMessage("Decryption failed");
        assertThatThrownBy(() -> new CryptoService(key(32, 1)).decrypt("@@@")).hasMessage("Decryption failed");
    }

    @Test
    @DisplayName("Chave precisa ser base64 de 32 bytes")
    void invalidKey() {
        assertThatThrownBy(() -> new CryptoService(key(16, 1))).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new CryptoService("não é base64!")).hasMessageContaining("base64");
    }
}
