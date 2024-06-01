package com.sskings.api.gestor.financeiro.dto.conta;

import java.math.BigDecimal;
import java.util.UUID;

public record ContaRequestDto(long numero, String banco, UUID usuario_id, BigDecimal saldo) {
}
