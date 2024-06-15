package com.sskings.api.gestor.financeiro.dto.lancamento.fonte;

import com.sskings.api.gestor.financeiro.models.FonteLancamentoModel;

import java.util.UUID;

public record FonteResponseDto(String fonte) {
    public FonteResponseDto(FonteLancamentoModel fonte) {
        this(fonte.getNome());
    }
}
