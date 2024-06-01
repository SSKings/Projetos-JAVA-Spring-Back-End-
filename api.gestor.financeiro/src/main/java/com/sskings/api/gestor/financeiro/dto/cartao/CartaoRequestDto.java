package com.sskings.api.gestor.financeiro.dto.cartao;

import com.sskings.api.gestor.financeiro.models.CartaoModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CartaoRequestDto(long numero, String banco, LocalDate vencimento, UUID usuario_id, BigDecimal limite) {
}
