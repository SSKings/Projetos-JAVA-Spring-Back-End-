package com.sskings.api.gestor.financeiro.dto.lancamento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoResponseDto(BigDecimal valor, String usuario, String tipo,
                                    String fonte, LocalDate dataLancamento, Long cartao, Long conta) {

}
