package com.sskings.api.gestor.financeiro.dto.usuario;

import com.sskings.api.gestor.financeiro.dto.cartao.CartaoResponseDto;
import com.sskings.api.gestor.financeiro.dto.conta.ContaResponseDto;
import lombok.Builder;

import java.util.Set;

@Builder
public record UsuarioResponseDto(String nome, String email, Set<CartaoResponseDto> cartoes, Set<ContaResponseDto> contas) {
}
