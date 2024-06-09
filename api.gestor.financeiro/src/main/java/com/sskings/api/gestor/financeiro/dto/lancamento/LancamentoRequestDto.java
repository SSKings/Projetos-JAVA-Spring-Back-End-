package com.sskings.api.gestor.financeiro.dto.lancamento;

import java.math.BigDecimal;
import java.util.UUID;

public record LancamentoRequestDto(
        BigDecimal valor, UUID usuario_id,UUID tipo_id,UUID fonte_id, UUID cartao_id, UUID conta_id ) {
}
