package com.sskings.api.gestor.financeiro.dto.conta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ContaRequestDto(

        @Positive(message = "O campo número deve ser preenchido com números positivos.")
        @NotNull(message = "Campo número é obrigatório.")
        Long numero,
        @NotBlank(message = "Campo banco é o obrigatório.")
        String banco,
        @NotNull(message = "campo usuário_id é obrigatório")
        UUID usuario_id,
        @Positive(message = "O campo saldo deve ser preenchido com números positivos.")
        @NotNull(message = "campo saldo é obrigatório")
        BigDecimal saldo) {
}
