package com.sskings.api.gestor.financeiro.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Revisão de segurança 3: com o segredo padrão "secret-key" (público no repositório) qualquer um forjava tokens. */
class JwtTokenServiceTest {

    @Test
    @DisplayName("Segredo ausente, curto ou já público impede a aplicação de subir")
    void rejectsWeakSecrets() {
        for (String weak : new String[]{null, "", "secret-key", "curto-demais", "x".repeat(31)}) {
            assertThatThrownBy(() -> new JwtTokenService(weak))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        }
    }

    @Test
    @DisplayName("Segredo aleatório de 32+ caracteres é aceito")
    void acceptsStrongSecret() {
        assertThatCode(() -> new JwtTokenService("x".repeat(32))).doesNotThrowAnyException();
    }
}
