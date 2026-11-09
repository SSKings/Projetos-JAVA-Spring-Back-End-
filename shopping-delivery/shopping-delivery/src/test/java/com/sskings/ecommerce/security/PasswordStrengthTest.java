package com.sskings.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.sskings.ecommerce.security.PasswordStrength.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Revisão 5, R5-1: senhas fáceis de adivinhar. */
class PasswordStrengthTest {

    @ParameterizedTest
    @ValueSource(strings = {"password1", "senha123", "Senha2026!", "12345678a", "qwerty123", "admin123", "Mudar123",
            "brasil2024", "123mudar", "PASSW0RD1", "S3nh@2026", "p4ssw0rd99", "trustno1", "1q2w3e4r", "Flamengo10", "a1234567"})
    @DisplayName("Senhas que o pentest cadastrou (e parecidas) são recusadas como comuns")
    void common(String password) {
        assertThat(problem(password, "Ana Souza", "ana@teste.com")).contains(TOO_COMMON);
    }

    @Test
    @DisplayName("Nome da loja, parte do e-mail ou nome da pessoa dentro da senha são recusados (sem maiúscula/acento)")
    void personal() {
        assertThat(problem("ShopEasy2026x", null, null)).contains(PERSONAL);
        assertThat(problem("joao.silva99", "Outro Nome", "joao.silva@teste.com")).contains(PERSONAL);
        assertThat(problem("xJoaoSilva2026", null, "joao_silva@teste.com")).contains(PERSONAL);
        assertThat(problem("Conceição2026x", "Maria da Conceição", null)).contains(PERSONAL);
        assertThat(problem("ana12345xyz", "Ana Lu", "al@x.com")).isEmpty();          // nomes curtos (< 4) não contam
    }

    @Test
    @DisplayName("Senhas fortes passam; nula é ignorada (a validação de formato cuida)")
    void strong() {
        for (String ok : new String[]{"Vagalume4821", "correto-cavalo-bateria-7", "tR9!kw2#Lm", "novaSenha9x"}) {
            assertThat(problem(ok, "Ana Souza", "ana@teste.com")).as(ok).isEmpty();
        }
        assertThat(problem(null, null, null)).isEmpty();
        assertThat(commonPasswords()).hasSizeGreaterThan(300).contains("password1", "senha123");
    }
}
