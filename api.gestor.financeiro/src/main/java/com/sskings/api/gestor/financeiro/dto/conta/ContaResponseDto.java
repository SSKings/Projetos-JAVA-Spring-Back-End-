package com.sskings.api.gestor.financeiro.dto.conta;

import com.sskings.api.gestor.financeiro.models.ContaModel;

import java.math.BigDecimal;

public record ContaResponseDto(long numero, String banco, BigDecimal saldo) {

    public ContaResponseDto(ContaModel contaModel){
        this(contaModel.getNumero(), contaModel.getBanco(), contaModel.getSaldo());
    }
}
