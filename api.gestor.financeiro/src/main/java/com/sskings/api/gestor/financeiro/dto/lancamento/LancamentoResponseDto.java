package com.sskings.api.gestor.financeiro.dto.lancamento;

import com.sskings.api.gestor.financeiro.models.LancamentoModel;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record LancamentoResponseDto(BigDecimal valor, String usuario, String tipo,
                                    String fonte, LocalDate dataLancamento, Long cartao, Long conta) {

}
