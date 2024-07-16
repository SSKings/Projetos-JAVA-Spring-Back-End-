package com.sskings.api.gestor.financeiro.dto.lancamento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record LancamentoRequestDto(
        @Positive(message = "O campo valor deve ser preenchido com números positivos.")
        @NotNull(message = "campo valor é obrigatório")
        BigDecimal valor,
        @NotNull(message = "Campo usuario_id é obrigatório")
        UUID usuario_id,
        @NotNull(message = "Campo tipo_id é obrigatório")
        UUID tipo_id,
        @NotNull(message = "Campo fonte_id é obrigatório")
        UUID fonte_id,
        @NotNull(message = "Campo cartao_id é obrigatório")
        UUID cartao_id,
        @NotNull(message = "Campo conta_id é obrigatório")
        UUID conta_id ) {
}
