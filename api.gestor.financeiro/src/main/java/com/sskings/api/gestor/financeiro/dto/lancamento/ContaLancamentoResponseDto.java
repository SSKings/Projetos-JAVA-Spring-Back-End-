package com.sskings.api.gestor.financeiro.dto.lancamento;

import com.sskings.api.gestor.financeiro.models.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaLancamentoResponseDto(BigDecimal valor, UsuarioModel usuario, TipoLancamentoModel tipo,
                                         FonteLancamentoModel fonte, LocalDate dataLancamento, ContaModel conta) {

    public ContaLancamentoResponseDto(ContaLancamentoModel contaLancamentoModel){
        this(contaLancamentoModel.getValor(),contaLancamentoModel.getUsuario(), contaLancamentoModel.getTipo()
                ,contaLancamentoModel.getFonte(),contaLancamentoModel.getDataLancamento(),contaLancamentoModel.getConta());
    }
}
