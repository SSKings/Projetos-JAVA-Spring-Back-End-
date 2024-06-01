package com.sskings.api.gestor.financeiro.dto.cartao;

import com.sskings.api.gestor.financeiro.models.CartaoModel;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CartaoResponseDto(long numero, String banco, LocalDate vencimento, BigDecimal limite) {

    public CartaoResponseDto(CartaoModel cartaoModel){
        this(cartaoModel.getNumero(), cartaoModel.getBanco(), cartaoModel.getVencimento(), cartaoModel.getLimite());
    }
}
