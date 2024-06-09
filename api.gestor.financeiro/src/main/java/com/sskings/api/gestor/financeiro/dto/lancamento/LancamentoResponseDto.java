package com.sskings.api.gestor.financeiro.dto.lancamento;

import com.sskings.api.gestor.financeiro.models.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoResponseDto(BigDecimal valor, UsuarioModel usuario, TipoLancamentoModel tipo,
                                    FonteLancamentoModel fonte, LocalDate dataLancamento, CartaoModel cartao, ContaModel conta) {


}
