package com.sskings.api.gestor.financeiro.dto.lancamento.tipo;

import com.sskings.api.gestor.financeiro.models.TipoLancamentoModel;

public record TipoResponseDto(String tipo) {
    public TipoResponseDto(TipoLancamentoModel tipo) {
        this(tipo.getNome());
    }
}
